#pragma once

#include <string>

namespace rain {

// Extract the DLL and JAR embedded in the injector to a versioned user cache.
bool extractEmbeddedPayload(std::wstring& dllPath, std::wstring& jarPath,
                            std::wstring& error);

} // namespace rain
