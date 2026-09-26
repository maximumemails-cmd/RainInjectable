#pragma once

#include <string>
#include <windows.h>

namespace lion {

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

bool writeConfigJson(const std::wstring& path,
                     const std::wstring& jarPath,
                     const std::wstring& logDir);

bool copyAssetTo(const std::wstring& src, const std::wstring& dst);

} // namespace lion
