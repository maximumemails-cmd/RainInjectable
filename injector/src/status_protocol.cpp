#include "status_protocol.h"
#include "compatibility.h"
#include <windows.h>
#include <vector>

namespace rain {
namespace {
bool decode(const std::string& source, std::wstring& out) {
    if (source.empty()) { out.clear(); return true; }
    int length = MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, source.data(),
                                    static_cast<int>(source.size()), nullptr, 0);
    if (length <= 0) return false;
    out.resize(length);
    return MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, source.data(),
                               static_cast<int>(source.size()), out.data(), length) == length;
}
} // namespace
bool parsePayloadStatus(const std::string& utf8, PayloadStatus& out) {
    if (utf8.empty() || utf8.size() > 8192) return false;
    std::vector<std::string> fields;
    size_t start = 0;
    while (start <= utf8.size()) {
        auto end = utf8.find('\n', start);
        fields.push_back(utf8.substr(start, end == std::string::npos ? end : end - start));
        if (end == std::string::npos) break;
        start = end + 1;
    }
    if (fields.size() < 2 || fields.size() > 4 || fields[0].empty()) return false;
    PayloadStatus parsed;
    if (!decode(fields[0], parsed.stage) || !decode(fields[1], parsed.message)) return false;
    if (fields.size() > 2 && !decode(fields[2], parsed.code)) return false;
    if (fields.size() > 3 && !decode(fields[3], parsed.action)) return false;
    parsed.message = redactSensitive(parsed.message);
    parsed.action = redactSensitive(parsed.action);
    out = std::move(parsed);
    return true;
}
} // namespace rain
