#include "compatibility.h"
#include <algorithm>
#include <cwctype>

namespace rain {
namespace {
std::wstring lower(std::wstring value) {
    std::transform(value.begin(), value.end(), value.begin(), ::towlower);
    return value;
}
bool contains(const std::wstring& source, const wchar_t* marker) {
    return lower(source).find(lower(marker)) != std::wstring::npos;
}
std::wstring versionOf(const std::wstring& commandLine, const std::wstring& title) {
    const auto command = lower(commandLine);
    auto marker = command.find(L"--version ");
    if (marker != std::wstring::npos) {
        marker += 10;
        while (marker < command.size() && iswspace(command[marker])) ++marker;
        auto end = command.find_first_of(L" \t\r\n\"", marker);
        auto version = command.substr(marker, end == std::wstring::npos ? end : end - marker);
        if (version.find(L"1.8.9") != std::wstring::npos) return L"1.8.9";
        if (!version.empty()) return version;
    }
    if (contains(command, L"forge-1.8.9") || contains(command, L"minecraft-1.8.9") ||
        contains(title, L"1.8.9")) return L"1.8.9";
    for (const wchar_t* versionMarker : {L"1.7.", L"1.8.", L"1.9.", L"1.10.", L"1.12.",
                                  L"1.16.", L"1.18.", L"1.19.", L"1.20.", L"1.21."}) {
        if (contains(command, versionMarker)) return versionMarker;
    }
    return L"Unknown";
}
std::wstring javaVersionOf(const McProcess& p) {
    std::wstring path = lower(p.jvmPath.empty() ? p.exePath : p.jvmPath);
    auto at = path.find(L"jdk-");
    if (at == std::wstring::npos) at = path.find(L"jre-");
    if (at == std::wstring::npos) return L"Unknown";
    at += 4;
    auto end = at;
    while (end < path.size() && (iswdigit(path[end]) || path[end] == L'.')) ++end;
    return end == at ? L"Unknown" : path.substr(at, end - at);
}
void add(CompatibilityResult& r, TargetStage stage, StageState state,
         const std::wstring& reason, const std::wstring& code = L"",
         const std::wstring& action = L"", DWORD win32 = 0) {
    r.stages.push_back({stage, state, reason, win32, code, action});
}
} // namespace

CompatibilityResult assessCompatibility(const McProcess& p) {
    CompatibilityResult r;
    r.pid = p.pid;
    r.parentPid = p.parentPid;
    r.version = versionOf(p.commandLine, p.windowTitle);
    r.javaVersion = javaVersionOf(p);
    r.jvm = p.hasJvm ? L"Detected" : p.moduleError ? L"Inspection unavailable" : L"Starting";
    r.architecture = p.architectureKnown ? (p.x64 ? L"x64" : L"x86") : L"Unknown";
    const auto evidence = p.commandLine + L" " + p.windowTitle + L" " + p.exePath + L" " + p.parentExeName;
    const bool badlion = contains(evidence, L"badlion");
    const bool lunar = contains(evidence, L"lunarclient") || contains(evidence, L"lunar client");
    const bool feather = contains(evidence, L"feather");
    const bool prism = contains(evidence, L"prismlauncher") || contains(evidence, L"multimc");
    const bool forge = contains(p.commandLine, L"fmltweaker") ||
                       contains(p.commandLine, L"minecraftforge") ||
                       contains(p.commandLine, L"forge-1.8.9");
    const bool fabric = contains(p.commandLine, L"net.fabricmc") || contains(p.commandLine, L"fabric-loader");
    r.client = badlion ? L"Badlion Client" : lunar ? L"Lunar Client" :
               feather ? L"Feather" : prism ? L"Prism / MultiMC" :
               fabric ? L"Fabric" : forge ? L"Minecraft Forge" : L"Minecraft";
    r.framework = badlion ? L"Rain Badlion 1.8.9 adapter" :
                  forge ? L"Forge markers found" : fabric ? L"Fabric" : L"Forge not confirmed";

    add(r, TargetStage::Discovery, StageState::Confirmed,
        L"Game process PID " + std::to_wstring(p.pid) +
        (p.parentPid ? L"; parent PID " + std::to_wstring(p.parentPid) : L""));
    add(r, TargetStage::Client, StageState::Confirmed, r.client);
    add(r, TargetStage::MinecraftJvm,
        p.hasJvm ? StageState::Confirmed : p.moduleError ? StageState::Unverified : StageState::Pending,
        p.hasJvm ? L"jvm.dll loaded" : p.moduleError ? L"Module inspection failed" : L"Waiting for jvm.dll",
        p.moduleError ? L"MODULE_ENUM_FAILED" : L"",
        p.moduleError ? L"Retry with process read access." : L"", p.moduleError);
    add(r, TargetStage::Architecture,
        !p.architectureKnown ? StageState::Unverified : p.x64 ? StageState::Confirmed : StageState::Unsupported,
        r.architecture, !p.architectureKnown ? L"ARCH_QUERY_FAILED" : p.x64 ? L"" : L"ARCH_MISMATCH",
        !p.architectureKnown ? L"Retry architecture inspection." : p.x64 ? L"" : L"Use an x64 game JVM.",
        p.architectureError);
    add(r, TargetStage::Minecraft,
        r.version == L"1.8.9" ? StageState::Confirmed : r.version == L"Unknown" ? StageState::Unverified : StageState::Unsupported,
        r.version, r.version == L"Unknown" ? L"VERSION_UNKNOWN" : r.version == L"1.8.9" ? L"" : L"VERSION_UNSUPPORTED",
        r.version == L"1.8.9" ? L"" : L"Use Minecraft 1.8.9.");
    add(r, TargetStage::Framework,
        forge ? StageState::Confirmed : badlion ? StageState::Unverified :
        fabric ? StageState::Unsupported : StageState::Unverified,
        r.framework, forge ? L"" : badlion ? L"BADLION_ADAPTER_PENDING" :
        fabric ? L"FRAMEWORK_UNSUPPORTED" : L"FORGE_NOT_CONFIRMED",
        forge ? L"" : badlion ? L"Inject to verify Rain's Badlion adapter in this JVM." :
        L"Use a Forge 1.8.9 profile or an officially supported client integration.");
    const bool externalAttachDisabled = contains(p.commandLine, L"-XX:+DisableAttachMechanism");
    add(r, TargetStage::Attach, externalAttachDisabled ? StageState::Unverified : StageState::Pending,
        externalAttachDisabled ? L"JDK external Attach API disabled; native loading is a separate, untested capability."
                               : L"Native payload load has not been tested.",
        externalAttachDisabled ? L"EXTERNAL_ATTACH_DISABLED" : L"",
        L"Do not assume attach capability from process discovery.");
    add(r, TargetStage::Payload, StageState::Pending, L"Payload not loaded.");
    add(r, TargetStage::Bootstrap, StageState::Pending, L"Java bootstrap not started.");
    add(r, TargetStage::RainRuntime, StageState::Pending, L"Rain runtime not initialized.");

    if (badlion) {
        r.stages[5].state = StageState::Unverified;
        r.stages[5].code = L"BADLION_ADAPTER_PENDING";
        r.stages[5].reason = L"Rain's Badlion adapter requires the obfuscated Minecraft 1.8.9 runtime.";
        r.stages[5].nextAction = L"Inject to validate the game-thread detector and GUI setup.";
        if (!p.architectureKnown) {
            r.level = CompatibilityLevel::Unverified;
            r.reason = L"Cannot determine Badlion JVM architecture.";
        } else if (!p.x64 || r.version != L"1.8.9") {
            r.reason = L"Rain's Badlion adapter requires an x64 Minecraft 1.8.9 JVM.";
        } else if (!p.hasJvm || !p.hasLwjgl) {
            r.level = CompatibilityLevel::Starting;
            r.reason = L"Badlion's game JVM or LWJGL is still starting.";
        } else {
            r.level = CompatibilityLevel::Unverified;
            r.strategy = BootstrapStrategy::BadlionNotch;
            r.reason = L"Badlion 1.8.9 found; the in-process adapter must verify game classes and scheduling.";
        }
    } else if (lunar) {
        r.reason = r.client + L" uses a custom client runtime and does not provide a verified Forge mod integration for Rain.";
        if (!forge) r.reason += L" Forge is not confirmed in this game JVM.";
        r.level = CompatibilityLevel::Unsupported;
        r.stages[5].state = StageState::Unsupported;
        r.stages[5].code = L"CLIENT_RUNTIME_UNSUPPORTED";
        r.stages[5].reason = r.reason;
        r.stages[5].nextAction = L"Use a supported Forge 1.8.9 profile; do not bypass client protections.";
    } else if (fabric) {
        r.reason = L"Rain requires Forge 1.8.9; this process uses Fabric.";
    } else if (!p.architectureKnown) {
        r.level = CompatibilityLevel::Unverified;
        r.reason = L"Cannot determine game JVM architecture (Win32 " + std::to_wstring(p.architectureError) + L").";
    } else if (!p.x64) {
        r.reason = L"This build requires an x64 game JVM.";
    } else if (r.version != L"1.8.9" && r.version != L"Unknown") {
        r.reason = L"Rain is built for Minecraft 1.8.9.";
    } else if (!p.hasJvm || !p.hasLwjgl) {
        r.level = p.moduleError ? CompatibilityLevel::Unverified : CompatibilityLevel::Starting;
        r.reason = p.moduleError ? L"JVM modules could not be inspected." : L"The game JVM or LWJGL is still starting.";
    } else if (!forge && (feather || prism) && r.version == L"1.8.9") {
        r.level = CompatibilityLevel::Unverified;
        r.strategy = BootstrapStrategy::ForgeLaunchWrapper;
        r.reason = L"Launcher found, but Forge is hidden. Java bootstrap must verify Forge.";
    } else if (!forge) {
        r.reason = L"Forge 1.8.9 could not be confirmed in this game JVM.";
    } else if (r.version == L"Unknown") {
        r.level = CompatibilityLevel::Unverified;
        r.reason = L"Forge found, but Minecraft version is unknown.";
    } else {
        r.level = CompatibilityLevel::Supported;
        r.strategy = BootstrapStrategy::ForgeLaunchWrapper;
        r.reason = L"Forge 1.8.9 markers found; native load and Rain startup remain unverified.";
    }
    return r;
}

const wchar_t* compatibilityName(CompatibilityLevel level) {
    switch (level) {
        case CompatibilityLevel::Supported: return L"Ready to verify";
        case CompatibilityLevel::Starting: return L"Starting";
        case CompatibilityLevel::Unverified: return L"Needs verification";
        default: return L"Unavailable";
    }
}
const wchar_t* stageName(TargetStage stage) {
    switch (stage) {
        case TargetStage::Discovery: return L"Discovery";
        case TargetStage::Client: return L"Client";
        case TargetStage::MinecraftJvm: return L"JVM";
        case TargetStage::Architecture: return L"Architecture";
        case TargetStage::Minecraft: return L"Minecraft";
        case TargetStage::Framework: return L"Framework";
        case TargetStage::Attach: return L"Compatibility";
        case TargetStage::Payload: return L"Payload";
        case TargetStage::Bootstrap: return L"Bootstrap";
        case TargetStage::RainRuntime: return L"Rain";
    }
    return L"Stage";
}
const wchar_t* stageStateName(StageState state) {
    switch (state) {
        case StageState::Confirmed: return L"Confirmed";
        case StageState::Pending: return L"Pending";
        case StageState::Unverified: return L"Unverified";
        case StageState::Unsupported: return L"Unsupported";
        case StageState::Failed: return L"Failed";
    }
    return L"Unknown";
}
std::wstring redactSensitive(const std::wstring& source) {
    std::wstring value = source;
    std::wstring folded = lower(source);
    for (const wchar_t* key : {L"--accesstoken", L"--badliontoken", L"--refreshtoken",
                               L"access_token=", L"token=", L"password="}) {
        size_t search = 0;
        const std::wstring marker(key);
        while ((search = folded.find(marker, search)) != std::wstring::npos) {
            size_t begin = search + marker.size();
            if (marker.back() != L'=') {
                while (begin < value.size() && iswspace(value[begin])) ++begin;
            }
            if (begin < value.size() && value[begin] == L'"') ++begin;
            size_t end = begin;
            while (end < value.size() && !iswspace(value[end]) && value[end] != L'"' && value[end] != L'&') ++end;
            if (end > begin) {
                value.replace(begin, end - begin, L"[redacted]");
                folded = lower(value);
            }
            search = begin + 10;
        }
    }
    return value;
}
} // namespace rain
