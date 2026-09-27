#pragma once
#include <string>

namespace rain {
struct PayloadStatus {
    std::wstring stage, message, code, action;
};
// Accepts the current four-line payload status and older two-line snapshots.
// Rejects malformed/truncated data and redacts credential-like values.
bool parsePayloadStatus(const std::string& utf8, PayloadStatus& out);
}
