#include "ui_layout.h"

int main() {
    for (UINT dpi : {96u, 120u, 144u, 192u}) {
        for (int logicalHeight : {580, 600, 720}) {
            int height = MulDiv(logicalHeight, static_cast<int>(dpi), 96);
            auto m = rain::uiLayoutMetrics(height, dpi);
            if (m.actionTop < 0 || m.actionBottom >= m.statusTop) return 1;
            if (m.compatibilityRowsBottom >= m.compatibilityDetailTop) return 2;
            if (m.compatibilityDetailTop >= m.footerTop) return 3;
            if (m.logBottom >= m.footerTop) return 4;
        }
    }
    return 0;
}
