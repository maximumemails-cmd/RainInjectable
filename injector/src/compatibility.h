#pragma once

#include "process_scanner.h"

namespace rain {

enum class CompatibilityLevel { Supported, Starting, Unverified, Unsupported };
enum class BootstrapStrategy { None, ForgeLaunchWrapper, BadlionNotch };
enum class TargetStage { Discovery, Client, MinecraftJvm, Architecture, Minecraft,
                         Framework, Attach, Payload, Bootstrap, RainRuntime };
enum class StageState { Confirmed, Pending, Unverified, Unsupported, Failed };

struct StageFinding {
    TargetStage stage;
    StageState state;
    std::wstring reason;
    DWORD win32Error = 0;
    std::wstring code;
    std::wstring nextAction;
};

struct CompatibilityResult {
    DWORD pid = 0;
    std::wstring client;
    std::wstring version;
    std::wstring jvm;
    std::wstring architecture;
    std::wstring framework;
    std::wstring javaVersion;
    DWORD parentPid = 0;
    CompatibilityLevel level = CompatibilityLevel::Unsupported;
    BootstrapStrategy strategy = BootstrapStrategy::None;
    std::wstring reason;
    std::vector<StageFinding> stages;
};

// ProcessScanner finds candidates. This pure function identifies the client and
// decides whether Rain's Forge 1.8.9 bootstrap can run there.
CompatibilityResult assessCompatibility(const McProcess& process);
const wchar_t* compatibilityName(CompatibilityLevel level);
const wchar_t* stageName(TargetStage stage);
const wchar_t* stageStateName(StageState state);
// Diagnostic text is never allowed to expose authentication arguments.
std::wstring redactSensitive(const std::wstring& text);

} // namespace rain
