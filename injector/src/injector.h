#pragma once

#include <string>
#include <windows.h>

namespace rain {

struct InjectionResult {
    bool ok = false;
    DWORD systemError = 0;
    std::string message;
};

class Injector {
public:
    // Performs a CreateRemoteThread/LoadLibraryW injection of `dllPath` into `pid`.
    // Validates architecture, handle access, write/alloc, and waits for the load to complete.
    InjectionResult inject(DWORD pid, const std::wstring& dllPath);

    // Verifies the payload DLL is present in the target's module list after injection.
    bool isPayloadLoaded(DWORD pid, const std::wstring& dllPath);
};

// Checks whether a module with the given base name (e.g. "rain-payload.dll")
// is loaded in the target process. Case-insensitive; returns false on any failure.
bool isModuleLoaded(DWORD pid, const std::wstring& moduleBaseName);

} // namespace rain
