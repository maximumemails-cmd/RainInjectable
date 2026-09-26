#pragma once

#include <windows.h>

namespace rain {

// Entry point invoked from WinMain. Creates the main window and runs the message loop.
int runGui(HINSTANCE hInst, int nCmdShow);

} // namespace rain
