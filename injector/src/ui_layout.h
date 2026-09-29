#pragma once

#include <windows.h>

namespace rain {

// Painting and hit testing share these DPI-aware rectangles.
struct UiRect {
    int x, y, width, height;
    bool contains(int px, int py) const {
        return px >= x && py >= y && px < x + width && py < y + height;
    }
};

struct UiLayoutMetrics {
    UiRect refresh, minimize, close;
    UiRect target, inject, progress, statusText, tabs, statusTab, activityTab;
    UiRect content, copyLog;
};

inline UiLayoutMetrics uiLayoutMetrics(int clientWidth, UINT dpi) {
    auto px = [dpi](int value) { return MulDiv(value, static_cast<int>(dpi), 96); };
    const int inset = px(28);
    const int fullWidth = clientWidth - inset * 2;
    const int tabWidth = fullWidth / 2;
    return {
        {clientWidth - px(132), px(21), px(32), px(32)},
        {clientWidth - px(88), px(21), px(32), px(32)},
        {clientWidth - px(48), px(21), px(32), px(32)},
        {inset, px(166), fullWidth, px(90)},
        {inset, px(277), fullWidth, px(60)},
        {inset + px(10), px(363), fullWidth - px(20), px(18)},
        {inset, px(387), fullWidth, px(22)},
        {inset, px(430), fullWidth, px(48)},
        {inset + px(4), px(434), tabWidth - px(4), px(40)},
        {inset + tabWidth, px(434), tabWidth - px(4), px(40)},
        {inset, px(490), fullWidth, px(86)},
        {clientWidth - inset - px(71), px(502), px(59), px(26)}
    };
}

} // namespace rain
