#pragma once
#include <algorithm>
#include <windows.h>

namespace rain {
struct UiLayoutMetrics {
    int actionTop;
    int actionBottom;
    int statusTop;
    int compatibilityRowsBottom;
    int compatibilityDetailTop;
    int logBottom;
    int footerTop;
};
inline UiLayoutMetrics uiLayoutMetrics(int clientHeight, UINT dpi) {
    auto px = [dpi](int value) { return MulDiv(value, static_cast<int>(dpi), 96); };
    const int actionTop = std::min(clientHeight - px(132), px(368));
    return {
        actionTop, actionTop + px(50),
        std::min(clientHeight - px(88), px(443)),
        px(176) + px(11 * 24) + px(22),
        clientHeight - px(97),
        px(178) + std::max(px(160), clientHeight - px(225)),
        clientHeight - px(29)
    };
}
} // namespace rain
