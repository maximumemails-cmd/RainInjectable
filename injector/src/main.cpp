// RainInjectable — Win32 entry point.

#ifndef WIN32_LEAN_AND_MEAN
#  define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#include "gui.h"

#ifdef _MSC_VER
#  pragma comment(linker,"/manifestdependency:\"type='win32' "                                           \
                         "name='Microsoft.Windows.Common-Controls' version='6.0.0.0' "                  \
                         "processorArchitecture='*' publicKeyToken='6595b64144ccf1df' language='*'\"")
#endif

int APIENTRY wWinMain(HINSTANCE hInst, HINSTANCE, LPWSTR, int nCmdShow) {
    if (!SetProcessDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2))
        SetProcessDPIAware();
    return rain::runGui(hInst, nCmdShow);
}
