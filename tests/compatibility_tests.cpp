#include "compatibility.h"
#include "status_protocol.h"
#undef NDEBUG
#include <cassert>

using namespace rain;

int main() {
    McProcess forge;
    forge.pid = 42;
    forge.parentPid = 7;
    forge.parentExeName = L"MinecraftLauncher.exe";
    forge.exeName = L"javaw.exe";
    forge.exePath = L"C:\\Java\\jdk-8.0.504\\bin\\javaw.exe";
    forge.jvmPath = L"C:\\Java\\jdk-8.0.504\\bin\\server\\jvm.dll";
    forge.commandLine = L"javaw -cp forge-1.8.9.jar net.minecraft.launchwrapper.Launch --tweakClass net.minecraftforge.fml.common.launcher.FMLTweaker";
    forge.hasJvm = true;
    forge.hasLwjgl = true;
    forge.architectureKnown = true;
    auto result = assessCompatibility(forge);
    assert(result.level == CompatibilityLevel::Supported);
    assert(result.strategy == BootstrapStrategy::ForgeLaunchWrapper);
    assert(result.pid == 42 && result.parentPid == 7);
    assert(result.version == L"1.8.9" && result.javaVersion == L"8.0.504");
    assert(result.stages.size() == 10);
    assert(result.stages[2].state == StageState::Confirmed);
    assert(result.stages[7].state == StageState::Pending);

    forge.hasJvm = false;
    assert(assessCompatibility(forge).level == CompatibilityLevel::Starting);
    forge.hasJvm = true;
    forge.architectureKnown = false;
    forge.architectureError = ERROR_ACCESS_DENIED;
    result = assessCompatibility(forge);
    assert(result.level == CompatibilityLevel::Unverified);
    assert(result.stages[3].win32Error == ERROR_ACCESS_DENIED);
    forge.architectureKnown = true;
    forge.x64 = false;
    assert(assessCompatibility(forge).level == CompatibilityLevel::Unsupported);
    forge.x64 = true;
    forge.commandLine = L"javaw -cp forge-1.20.1.jar net.minecraft.launchwrapper.Launch --version 1.20.1";
    assert(assessCompatibility(forge).level == CompatibilityLevel::Unsupported);
    forge.commandLine = L"javaw -cp minecraft-1.8.9.jar net.minecraft.client.main.Main";
    assert(assessCompatibility(forge).level == CompatibilityLevel::Unsupported);

    forge.parentExeName = L"Badlion Client.exe";
    forge.commandLine = L"javaw -XX:+DisableAttachMechanism -cp BLClient.jar net.minecraft.client.main.Main --version 1.8.9 --accessToken private --badlionToken other";
    result = assessCompatibility(forge);
    assert(result.client == L"Badlion Client");
    assert(result.level == CompatibilityLevel::Unverified);
    assert(result.strategy == BootstrapStrategy::BadlionNotch);
    assert(result.stages[5].code == L"BADLION_ADAPTER_PENDING");
    assert(result.stages[6].code == L"EXTERNAL_ATTACH_DISABLED");
    assert(result.stages[8].state == StageState::Pending);
    assert(redactSensitive(forge.commandLine).find(L"private") == std::wstring::npos);
    assert(redactSensitive(forge.commandLine).find(L"other") == std::wstring::npos);
    forge.commandLine = L"javaw -cp BLClient.jar --version 1.20.1";
    assert(assessCompatibility(forge).level == CompatibilityLevel::Unsupported);
    forge.parentExeName.clear();
    forge.commandLine = L"javaw -cp lunarclient-1.8.9.jar forge-1.8.9.jar";
    assert(assessCompatibility(forge).level == CompatibilityLevel::Unsupported);
    forge.commandLine = L"javaw -cp forge-1.8.9.jar fmltweaker -Dlauncher=PrismLauncher";
    assert(assessCompatibility(forge).level == CompatibilityLevel::Supported);
    forge.commandLine = L"javaw -cp forge-1.8.9.jar fmltweaker -Dlauncher=Feather";
    assert(assessCompatibility(forge).level == CompatibilityLevel::Supported);
    forge.commandLine = L"javaw -Dlauncher=PrismLauncher --version 1.8.9";
    result = assessCompatibility(forge);
    assert(result.level == CompatibilityLevel::Unverified);
    assert(result.strategy == BootstrapStrategy::ForgeLaunchWrapper);

    PayloadStatus status;
    assert(parsePayloadStatus("Failed\nForge not detected\nFORGE_UNAVAILABLE\nUse Forge 1.8.9.", status));
    assert(status.stage == L"Failed" && status.code == L"FORGE_UNAVAILABLE");
    assert(parsePayloadStatus("Complete\nRain running", status));
    assert(status.code.empty());
    assert(!parsePayloadStatus("truncated", status));
    assert(!parsePayloadStatus("Failed\ninvalid\ncode\naction\nextra", status));
    assert(parsePayloadStatus("Failed\n--accessToken secret\nX\n", status));
    assert(status.message.find(L"secret") == std::wstring::npos);
    return 0;
}
