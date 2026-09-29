#include "ui_layout.h"

int main() {
    const UINT scales[] = {80u, 96u, 120u, 144u, 192u};
    for (UINT dpi : scales) {
        const int width = MulDiv(500, static_cast<int>(dpi), 96);
        const int height = MulDiv(604, static_cast<int>(dpi), 96);
        const auto m = rain::uiLayoutMetrics(width, dpi);
        if (m.target.y <= m.close.y + m.close.height) return 1;
        if (m.inject.y <= m.target.y + m.target.height) return 2;
        if (m.progress.y <= m.inject.y + m.inject.height) return 3;
        if (m.tabs.y <= m.statusText.y + m.statusText.height) return 4;
        if (m.content.y <= m.tabs.y + m.tabs.height) return 5;
        if (m.content.y + m.content.height >= height) return 6;
        if (!m.statusTab.contains(m.statusTab.x + 1, m.statusTab.y + 1)) return 7;
        if (m.statusTab.contains(m.activityTab.x + 1, m.activityTab.y + 1)) return 8;
        if (m.copyLog.x + m.copyLog.width > m.content.x + m.content.width) return 9;
    }
    return 0;
}
