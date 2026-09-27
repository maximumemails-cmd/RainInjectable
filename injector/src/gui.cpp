#include "gui.h"
#include "compatibility.h"
#include "embedded_payload.h"
#include "injector.h"
#include "logger.h"
#include "process_scanner.h"
#include "status_protocol.h"
#include "ui_layout.h"

#include <windows.h>
#include <windowsx.h>
#include <dwmapi.h>
#include <objidl.h>
#include <gdiplus.h>

#include <algorithm>
#include <chrono>
#include <cmath>
#include <memory>
#include <mutex>
#include <sstream>
#include <string>
#include <thread>
#include <vector>

#pragma comment(lib, "gdiplus.lib")
#pragma comment(lib, "dwmapi.lib")
#pragma comment(lib, "uxtheme.lib")

namespace rain {

namespace {

// DPI Scaling Helper
int scaleDpi(int px, UINT dpi) {
    return MulDiv(px, static_cast<int>(dpi), 96);
}

// Color Palette - Apple Liquid Glass on Obsidian
namespace Palette {
    using namespace Gdiplus;
    const Color BgDark(255, 12, 15, 22);           // #0c0f16 deep obsidian
    const Color BgTint(220, 16, 20, 28);
    const Color TextPrimary(255, 245, 245, 247);   // Apple crisp white
    const Color TextSecondary(255, 152, 162, 179); // Refined slate
    const Color TextTertiary(255, 99, 108, 122);   // Muted neutral
    const Color AccentEmerald(255, 16, 185, 129);  // #10b981
    const Color AccentEmeraldGlow(120, 16, 185, 129);
    const Color AccentCyan(255, 56, 189, 248);     // #38bdf8
    const Color AccentAmber(255, 245, 158, 11);    // #f59e0b
    const Color AccentRose(255, 244, 63, 94);      // #f43f5e
    const Color GlassTop(55, 255, 255, 255);       // Refraction highlight
    const Color GlassBottom(14, 255, 255, 255);
    const Color GlassBorder(35, 255, 255, 255);    // 14% white hairline
    const Color Specular(80, 255, 255, 255);
    const Color Shadow(60, 0, 0, 0);
}

// Window state & metrics
constexpr int BASE_WIDTH = 460;
constexpr int COLLAPSED_HEIGHT = 380;
constexpr int EXPANDED_HEIGHT = 640;

constexpr UINT_PTR TIMER_ANIM = 1;
constexpr UINT_PTR TIMER_SCAN = 2;
constexpr UINT WM_INJECT_EVENT = WM_USER + 201;

enum class DrawerTab { Status, Diagnostics, Logs };

struct LogEntry {
    std::wstring time;
    std::wstring tag;
    std::wstring message;
    Gdiplus::Color tagColor;
};

// Global GUI State
struct GuiState {
    HWND hwnd = nullptr;
    UINT dpi = 96;

    // Process discovery
    ProcessScanner scanner;
    Injector injector;
    std::vector<McProcess> processes;
    std::vector<CompatibilityResult> assessments;
    DWORD selectedPid = 0;
    bool isLoadedInTarget = false;

    // Operation status
    bool isBusy = false;
    bool isSuccess = false;
    bool isError = false;
    std::wstring statusHeadline = L"Scanning for Minecraft processes...";
    std::wstring statusDetail = L"";
    std::wstring activeStage = L"Discovery";

    // Layout & UI controls
    bool drawerExpanded = false;
    float drawerProgress = 0.0f; // 0.0 = collapsed, 1.0 = expanded
    DrawerTab activeTab = DrawerTab::Status;
    bool showSettings = false;

    // Animations
    float animPulse = 0.0f;
    float btnHoverAlpha = 0.0f;
    float btnTargetHover = 0.0f;
    float spinnerAngle = 0.0f;

    // Hovered elements
    int hoveredControl = 0; // 0=none, 1=Inject, 2=Details, 3=Refresh, 4=Minimize, 5=Close, 6=TargetCard, 8=Settings, 9=CopyLog, 10=ClearLog

    // Logs & History
    std::vector<LogEntry> logs;
    std::mutex logMutex;
    int logScrollOffset = 0;

    void addLog(const std::wstring& tag, const std::wstring& msg, Gdiplus::Color col = Palette::TextSecondary) {
        SYSTEMTIME st;
        GetLocalTime(&st);
        wchar_t tbuf[32];
        swprintf_s(tbuf, L"%02d:%02d:%02d", st.wHour, st.wMinute, st.wSecond);
        std::lock_guard<std::mutex> lock(logMutex);
        logs.push_back({ tbuf, tag, msg, col });
        if (logs.size() > 500) logs.erase(logs.begin());
    }
} g;

// GDI+ geometry helper: rounded rectangle path
void addRoundedRect(Gdiplus::GraphicsPath& path, const Gdiplus::RectF& rect, float radius) {
    float dia = radius * 2.0f;
    if (dia > rect.Width) dia = rect.Width;
    if (dia > rect.Height) dia = rect.Height;
    path.AddArc(rect.X, rect.Y, dia, dia, 180, 90);
    path.AddArc(rect.GetRight() - dia, rect.Y, dia, dia, 270, 90);
    path.AddArc(rect.GetRight() - dia, rect.GetBottom() - dia, dia, dia, 0, 90);
    path.AddArc(rect.X, rect.GetBottom() - dia, dia, dia, 90, 90);
    path.CloseFigure();
}

// Draws a genuine Liquid Glass surface with specular top refraction & ambient drop shadow
void drawGlassCard(Gdiplus::Graphics& g_ctx, const Gdiplus::RectF& bounds, float radius,
                   Gdiplus::Color topColor = Palette::GlassTop,
                   Gdiplus::Color botColor = Palette::GlassBottom,
                   Gdiplus::Color borderColor = Palette::GlassBorder,
                   bool drawShadow = true) {
    using namespace Gdiplus;

    if (drawShadow) {
        RectF shadowBounds = bounds;
        shadowBounds.Y += 2.5f;
        GraphicsPath shadowPath;
        addRoundedRect(shadowPath, shadowBounds, radius);
        SolidBrush shadowBrush(Palette::Shadow);
        g_ctx.FillPath(&shadowBrush, &shadowPath);
    }

    GraphicsPath path;
    addRoundedRect(path, bounds, radius);

    // Glass body gradient fill
    LinearGradientBrush bodyBrush(bounds, topColor, botColor, LinearGradientModeVertical);
    g_ctx.FillPath(&bodyBrush, &path);

    // Hairline crisp border
    Pen borderPen(borderColor, 1.0f);
    g_ctx.DrawPath(&borderPen, &path);

    // Top specular highlight line (Liquid Glass hallmark)
    if (bounds.Height > 10.0f && bounds.Width > radius * 2) {
        Pen specPen(Palette::Specular, 1.0f);
        g_ctx.DrawLine(&specPen,
                       bounds.X + radius, bounds.Y + 1.0f,
                       bounds.GetRight() - radius, bounds.Y + 1.0f);
    }
}

// Draws a sleek Minecraft block icon
void drawCubeIcon(Gdiplus::Graphics& g_ctx, float x, float y, float size) {
    using namespace Gdiplus;
    float s = size;
    float cx = x + s * 0.5f;

    // Isometric cube faces
    PointF topPts[4] = {
        PointF(cx, y + s * 0.12f),
        PointF(x + s * 0.88f, y + s * 0.35f),
        PointF(cx, y + s * 0.58f),
        PointF(x + s * 0.12f, y + s * 0.35f)
    };
    PointF leftPts[4] = {
        PointF(x + s * 0.12f, y + s * 0.35f),
        PointF(cx, y + s * 0.58f),
        PointF(cx, y + s * 0.92f),
        PointF(x + s * 0.12f, y + s * 0.69f)
    };
    PointF rightPts[4] = {
        PointF(cx, y + s * 0.58f),
        PointF(x + s * 0.88f, y + s * 0.35f),
        PointF(x + s * 0.88f, y + s * 0.69f),
        PointF(cx, y + s * 0.92f)
    };

    SolidBrush topBrush(Color(255, 74, 180, 100));     // grass green
    SolidBrush leftBrush(Color(255, 120, 85, 55));     // dirt brown
    SolidBrush rightBrush(Color(255, 95, 65, 40));     // dark dirt
    Pen edgePen(Color(180, 255, 255, 255), 1.0f);

    g_ctx.FillPolygon(&topBrush, topPts, 4);
    g_ctx.FillPolygon(&leftBrush, leftPts, 4);
    g_ctx.FillPolygon(&rightBrush, rightPts, 4);

    g_ctx.DrawPolygon(&edgePen, topPts, 4);
    g_ctx.DrawPolygon(&edgePen, leftPts, 4);
    g_ctx.DrawPolygon(&edgePen, rightPts, 4);
}

// Draws a glowing status dot with ripple
void drawStatusDot(Gdiplus::Graphics& g_ctx, float cx, float cy, float radius,
                   Gdiplus::Color color, bool pulse = true) {
    using namespace Gdiplus;
    if (pulse) {
        float glowRad = radius + 3.0f + 2.0f * std::sin(g.animPulse);
        Color glowColor(static_cast<BYTE>(60 + 30 * std::sin(g.animPulse)), color.GetR(), color.GetG(), color.GetB());
        SolidBrush glowBrush(glowColor);
        g_ctx.FillEllipse(&glowBrush, cx - glowRad, cy - glowRad, glowRad * 2.0f, glowRad * 2.0f);
    }
    SolidBrush coreBrush(color);
    g_ctx.FillEllipse(&coreBrush, cx - radius, cy - radius, radius * 2.0f, radius * 2.0f);
}

const McProcess* getSelectedProcess() {
    for (const auto& p : g.processes) {
        if (p.pid == g.selectedPid) return &p;
    }
    if (!g.processes.empty()) return &g.processes[0];
    return nullptr;
}

const CompatibilityResult* getSelectedAssessment() {
    for (const auto& a : g.assessments) {
        if (a.pid == g.selectedPid) return &a;
    }
    if (!g.assessments.empty()) return &g.assessments[0];
    return nullptr;
}

// Picks the PID of the most injectable running instance, preferring the one
// Rain can actually bootstrap (Forge 1.8.9 == Supported) over incompatible
// clients like Badlion/Lunar that may also be running. CompatibilityLevel is
// ordered best-first (Supported < Starting < Unverified < Unsupported), so the
// lowest enum value wins; ties keep enumeration order.
DWORD pickBestTargetPid() {
    DWORD bestPid = 0;
    int bestRank = 999;
    for (const auto& a : g.assessments) {
        int rank = static_cast<int>(a.level);
        if (rank < bestRank) {
            bestRank = rank;
            bestPid = a.pid;
        }
    }
    if (bestPid == 0 && !g.processes.empty()) bestPid = g.processes[0].pid;
    return bestPid;
}

void refreshProcesses() {
    auto oldPid = g.selectedPid;
    g.processes = g.scanner.scan();
    g.assessments.clear();

    for (const auto& p : g.processes) {
        g.assessments.push_back(assessCompatibility(p));
    }

    if (!g.processes.empty()) {
        bool found = false;
        for (const auto& p : g.processes) {
            if (p.pid == oldPid) { found = true; break; }
        }
        if (!found) g.selectedPid = pickBestTargetPid();
    } else {
        g.selectedPid = 0;
    }

    if (g.selectedPid != 0) {
        g.isLoadedInTarget = isModuleLoaded(g.selectedPid, L"rain-payload.dll");
        const auto* res = getSelectedAssessment();
        if (res) {
            if (g.isLoadedInTarget) {
                g.statusHeadline = L"Native payload loaded in target.";
                g.statusDetail = L"Check Logs for the Java bootstrap result.";
            } else if (res->level == CompatibilityLevel::Supported) {
                g.statusHeadline = res->client + L" detected · Ready to inject";
                g.statusDetail = L"PID " + std::to_wstring(res->pid) + L" · " + res->architecture + L" · Forge 1.8.9 confirmed.";
            } else {
                g.statusHeadline = res->client + L" detected (PID " + std::to_wstring(res->pid) + L")";
                g.statusDetail = res->reason;
            }
        }
    } else {
        g.statusHeadline = L"No Minecraft process detected";
        g.statusDetail = L"Launch Minecraft 1.8.9 or your preferred client to begin.";
        g.isLoadedInTarget = false;
    }

    if (g.hwnd) InvalidateRect(g.hwnd, nullptr, FALSE);
}

// Background injection worker thread
void injectWorkerThread(HWND hwnd, DWORD pid) {
    LOG_I("Starting injection worker thread for PID %lu", pid);
    g.addLog(L"Inject", L"Starting injection for PID " + std::to_wstring(pid), Palette::AccentCyan);

    std::wstring dllPath, jarPath, error;
    if (!extractEmbeddedPayload(dllPath, jarPath, error)) {
        LOG_E("Payload extraction failed: %ls", error.c_str());
        g.addLog(L"Error", L"Payload extraction: " + error, Palette::AccentRose);
        PostMessageW(hwnd, WM_INJECT_EVENT, 0, reinterpret_cast<LPARAM>(_wcsdup(error.c_str())));
        return;
    }

    g.addLog(L"Payload", L"Using payload: " + dllPath, Palette::TextTertiary);

    std::wstring dir = dllPath;
    auto pos = dir.find_last_of(L"\\/");
    if (pos != std::wstring::npos) dir = dir.substr(0, pos);
    const std::wstring statusPath = dir + L"\\rain-status-" + std::to_wstring(pid) + L".txt";
    if (!DeleteFileW(statusPath.c_str()) && GetLastError() != ERROR_FILE_NOT_FOUND) {
        const std::wstring message = L"Could not clear the previous bootstrap status (Windows error " +
                                     std::to_wstring(GetLastError()) + L").";
        PostMessageW(hwnd, WM_INJECT_EVENT, 0, reinterpret_cast<LPARAM>(_wcsdup(message.c_str())));
        return;
    }

    // Execute native injection
    auto res = g.injector.inject(pid, dllPath);
    if (!res.ok) {
        std::wstring errStr(res.message.begin(), res.message.end());
        g.addLog(L"Failed", L"Injection failed: " + errStr, Palette::AccentRose);
        PostMessageW(hwnd, WM_INJECT_EVENT, 0, reinterpret_cast<LPARAM>(_wcsdup(errStr.c_str())));
        return;
    }

    g.addLog(L"Native", L"LoadLibraryW completed and verified in target", Palette::AccentEmerald);

    // Poll status protocol file
    PayloadStatus status;
    bool finished = false;
    // RainCore may wait 20 seconds for Minecraft and another 10 seconds for
    // the game-thread task. Leave time for JNI setup and file delivery too.
    for (int i = 0; i < 240; ++i) {
        std::this_thread::sleep_for(std::chrono::milliseconds(150));
        HANDLE hFile = CreateFileW(statusPath.c_str(), GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE,
                                   nullptr, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr);
        if (hFile != INVALID_HANDLE_VALUE) {
            char buf[4096]{};
            DWORD readBytes = 0;
            if (ReadFile(hFile, buf, sizeof(buf) - 1, &readBytes, nullptr) && readBytes > 0) {
                parsePayloadStatus(std::string(buf, readBytes), status);
            }
            CloseHandle(hFile);
        }

        if (status.stage == L"Complete") {
            finished = true;
            g.addLog(L"Success", L"Rain runtime initialized successfully!", Palette::AccentEmerald);
            PostMessageW(hwnd, WM_INJECT_EVENT, 1, 0);
            return;
        } else if (status.stage == L"Failed") {
            finished = true;
            std::wstring failMsg = status.message;
            if (!status.action.empty()) failMsg += L" (" + status.action + L")";
            g.addLog(L"Bootstrap", status.message, Palette::AccentAmber);
            if (!status.action.empty()) g.addLog(L"Action", status.action, Palette::AccentCyan);
            PostMessageW(hwnd, WM_INJECT_EVENT, 2, reinterpret_cast<LPARAM>(_wcsdup(failMsg.c_str())));
            return;
        } else if (!status.stage.empty()) {
            g.addLog(status.stage, status.message, Palette::TextSecondary);
        }
    }

    if (!finished) {
        std::wstring timeoutMsg = L"In-process bootstrap timed out or target is waiting.";
        g.addLog(L"Timeout", timeoutMsg, Palette::AccentAmber);
        PostMessageW(hwnd, WM_INJECT_EVENT, 2, reinterpret_cast<LPARAM>(_wcsdup(timeoutMsg.c_str())));
    }
}

// Window procedure
LRESULT CALLBACK WndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    switch (msg) {
        case WM_CREATE: {
            g.hwnd = hwnd;
            g.dpi = GetDpiForWindow(hwnd);

            // Enable Windows 11 rounded corners & dark frame
            DWM_WINDOW_CORNER_PREFERENCE corner = DWMWCP_ROUND;
            DwmSetWindowAttribute(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE, &corner, sizeof(corner));
            BOOL dark = TRUE;
            DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, &dark, sizeof(dark));

            // Set Acrylic / Transient Window Backdrop if supported
            DWM_SYSTEMBACKDROP_TYPE backdrop = DWMSBT_TRANSIENTWINDOW;
            DwmSetWindowAttribute(hwnd, DWMWA_SYSTEMBACKDROP_TYPE, &backdrop, sizeof(backdrop));

            SetTimer(hwnd, TIMER_ANIM, 16, nullptr); // ~60fps smooth animation
            SetTimer(hwnd, TIMER_SCAN, 1500, nullptr); // Periodic background scan

            refreshProcesses();
            g.addLog(L"Init", L"RainInjectable Liquid Glass UI initialized", Palette::AccentCyan);
            return 0;
        }

        case WM_DPICHANGED: {
            g.dpi = HIWORD(wParam);
            auto* prc = reinterpret_cast<RECT*>(lParam);
            SetWindowPos(hwnd, nullptr, prc->left, prc->top,
                         prc->right - prc->left, prc->bottom - prc->top,
                         SWP_NOZORDER | SWP_NOACTIVATE);
            InvalidateRect(hwnd, nullptr, FALSE);
            return 0;
        }

        case WM_TIMER: {
            if (wParam == TIMER_ANIM) {
                // Update animation frames
                g.animPulse += 0.05f;
                if (g.animPulse > 6.283185f) g.animPulse -= 6.283185f;

                if (g.isBusy) {
                    g.spinnerAngle += 7.0f;
                    if (g.spinnerAngle >= 360.0f) g.spinnerAngle -= 360.0f;
                }

                // Smooth hover transition
                float hoverDiff = g.btnTargetHover - g.btnHoverAlpha;
                if (std::abs(hoverDiff) > 0.01f) {
                    g.btnHoverAlpha += hoverDiff * 0.25f;
                } else {
                    g.btnHoverAlpha = g.btnTargetHover;
                }

                // Smooth drawer height animation
                float targetDrawer = g.drawerExpanded ? 1.0f : 0.0f;
                float drawerDiff = targetDrawer - g.drawerProgress;
                if (std::abs(drawerDiff) > 0.005f) {
                    g.drawerProgress += drawerDiff * 0.22f;

                    int targetH = static_cast<int>(scaleDpi(COLLAPSED_HEIGHT, g.dpi) +
                        (scaleDpi(EXPANDED_HEIGHT - COLLAPSED_HEIGHT, g.dpi) * g.drawerProgress));
                    RECT r;
                    GetWindowRect(hwnd, &r);
                    SetWindowPos(hwnd, nullptr, 0, 0, r.right - r.left, targetH, SWP_NOMOVE | SWP_NOZORDER | SWP_NOACTIVATE);
                } else {
                    g.drawerProgress = targetDrawer;
                }

                InvalidateRect(hwnd, nullptr, FALSE);
            } else if (wParam == TIMER_SCAN && !g.isBusy) {
                // Background refresh without log noise
                auto prevCount = g.processes.size();
                auto oldPid = g.selectedPid;
                g.processes = g.scanner.scan();
                g.assessments.clear();
                for (const auto& p : g.processes) g.assessments.push_back(assessCompatibility(p));

                if (g.processes.size() != prevCount || g.selectedPid != oldPid) {
                    refreshProcesses();
                }
            }
            return 0;
        }

        case WM_INJECT_EVENT: {
            g.isBusy = false;
            if (wParam == 1) { // Success
                g.isSuccess = true;
                g.isError = false;
                g.isLoadedInTarget = true;
                g.statusHeadline = L"Injection Successful";
                g.statusDetail = L"Rain Anticheat runtime initialized and registered.";
            } else if (wParam == 2) { // Diagnostic / Action required
                g.isSuccess = false;
                g.isError = true;
                wchar_t* msgStr = reinterpret_cast<wchar_t*>(lParam);
                if (msgStr) {
                    g.statusHeadline = L"Injection Completed · Framework Action";
                    g.statusDetail = msgStr;
                    free(msgStr);
                }
                g.drawerExpanded = true; // Automatically expand details drawer
            } else { // Error
                g.isSuccess = false;
                g.isError = true;
                wchar_t* msgStr = reinterpret_cast<wchar_t*>(lParam);
                if (msgStr) {
                    g.statusHeadline = L"Injection Failed";
                    g.statusDetail = msgStr;
                    free(msgStr);
                }
                g.drawerExpanded = true;
            }
            InvalidateRect(hwnd, nullptr, FALSE);
            return 0;
        }

        case WM_NCHITTEST: {
            POINT pt{ GET_X_LPARAM(lParam), GET_Y_LPARAM(lParam) };
            ScreenToClient(hwnd, &pt);

            int titleHeight = scaleDpi(44, g.dpi);
            RECT clientRect;
            GetClientRect(hwnd, &clientRect);

            // Close, minimize, settings, refresh button areas are client clicks
            int btnW = scaleDpi(36, g.dpi);
            if (pt.y < titleHeight && pt.x > clientRect.right - btnW * 4) {
                return HTCLIENT;
            }

            if (pt.y < titleHeight) {
                return HTCAPTION; // Allows dragging the frameless window naturally
            }
            return HTCLIENT;
        }

        case WM_MOUSEMOVE: {
            int x = GET_X_LPARAM(lParam);
            int y = GET_Y_LPARAM(lParam);

            RECT cr;
            GetClientRect(hwnd, &cr);
            int width = cr.right;

            // Header buttons
            int btnW = scaleDpi(32, g.dpi);
            int rightX = width - scaleDpi(16, g.dpi);

            int closeLeft = rightX - btnW;
            int minLeft = closeLeft - btnW;
            int setLeft = minLeft - btnW;
            int refLeft = setLeft - btnW;

            int oldHover = g.hoveredControl;
            g.hoveredControl = 0;

            if (y >= scaleDpi(6, g.dpi) && y <= scaleDpi(38, g.dpi)) {
                if (x >= closeLeft && x < rightX) g.hoveredControl = 5;       // Close
                else if (x >= minLeft && x < closeLeft) g.hoveredControl = 4; // Minimize
                else if (x >= setLeft && x < minLeft) g.hoveredControl = 8;   // Settings
                else if (x >= refLeft && x < setLeft) g.hoveredControl = 3;   // Refresh
            }

            // Target hero card
            int cardX = scaleDpi(20, g.dpi);
            int cardY = scaleDpi(52, g.dpi);
            int cardW = width - cardX * 2;
            int cardH = scaleDpi(86, g.dpi);
            if (x >= cardX && x <= cardX + cardW && y >= cardY && y <= cardY + cardH) {
                g.hoveredControl = 6;
            }

            // Primary Inject Button
            int btnInjectY = cardY + cardH + scaleDpi(16, g.dpi);
            int btnInjectH = scaleDpi(46, g.dpi);
            int btnInjectW = cardW;
            if (x >= cardX && x <= cardX + btnInjectW && y >= btnInjectY && y <= btnInjectY + btnInjectH) {
                g.hoveredControl = 1;
                g.btnTargetHover = 1.0f;
            } else {
                g.btnTargetHover = 0.0f;
            }

            // Status & Details banner
            int statusY = btnInjectY + btnInjectH + scaleDpi(14, g.dpi);
            int statusH = scaleDpi(52, g.dpi);
            if (x >= cardX && x <= cardX + cardW && y >= statusY && y <= statusY + statusH) {
                g.hoveredControl = 2; // Details toggle
            }

            // Drawer tab buttons (if expanded)
            if (g.drawerProgress > 0.5f) {
                int drawerY = statusY + statusH + scaleDpi(16, g.dpi);
                int tabH = scaleDpi(30, g.dpi);
                if (y >= drawerY && y <= drawerY + tabH) {
                    int tabW = scaleDpi(90, g.dpi);
                    if (x >= cardX && x < cardX + tabW) g.hoveredControl = 21;
                    else if (x >= cardX + tabW && x < cardX + tabW * 2) g.hoveredControl = 22;
                    else if (x >= cardX + tabW * 2 && x < cardX + tabW * 3) g.hoveredControl = 23;
                }

                // Copy Log & Clear Log buttons
                int footerY = cr.bottom - scaleDpi(42, g.dpi);
                if (y >= footerY && y <= footerY + scaleDpi(32, g.dpi)) {
                    if (x >= cardX && x <= cardX + scaleDpi(90, g.dpi)) g.hoveredControl = 9; // Copy Log
                    if (x >= cardX + scaleDpi(100, g.dpi) && x <= cardX + scaleDpi(170, g.dpi)) g.hoveredControl = 10; // Clear
                }
            }

            if (oldHover != g.hoveredControl) {
                TRACKMOUSEEVENT tme{ sizeof(tme), TME_LEAVE, hwnd, 0 };
                TrackMouseEvent(&tme);
                InvalidateRect(hwnd, nullptr, FALSE);
            }
            return 0;
        }

        case WM_MOUSELEAVE: {
            g.hoveredControl = 0;
            g.btnTargetHover = 0.0f;
            InvalidateRect(hwnd, nullptr, FALSE);
            return 0;
        }

        case WM_LBUTTONDOWN: {
            SetCapture(hwnd);
            return 0;
        }

        case WM_LBUTTONUP: {
            ReleaseCapture();
            if (g.hoveredControl == 5) { // Close
                DestroyWindow(hwnd);
                return 0;
            } else if (g.hoveredControl == 4) { // Minimize
                ShowWindow(hwnd, SW_MINIMIZE);
                return 0;
            } else if (g.hoveredControl == 3) { // Refresh
                refreshProcesses();
                g.addLog(L"Scan", L"Process scan refreshed manually", Palette::AccentCyan);
                return 0;
            } else if (g.hoveredControl == 8) { // Settings toggle
                g.showSettings = !g.showSettings;
                InvalidateRect(hwnd, nullptr, FALSE);
                return 0;
            } else if (g.hoveredControl == 2) { // Details / Drawer toggle
                g.drawerExpanded = !g.drawerExpanded;
                InvalidateRect(hwnd, nullptr, FALSE);
                return 0;
            } else if (g.hoveredControl == 6 && g.processes.size() > 1) { // Cycle target
                for (size_t i = 0; i < g.processes.size(); ++i) {
                    if (g.processes[i].pid == g.selectedPid) {
                        g.selectedPid = g.processes[(i + 1) % g.processes.size()].pid;
                        break;
                    }
                }
                refreshProcesses();
                return 0;
            } else if (g.hoveredControl == 1 && !g.isBusy) { // Inject
                if (g.selectedPid == 0) {
                    g.statusHeadline = L"No Target Selected";
                    g.statusDetail = L"Launch Minecraft before injecting.";
                    InvalidateRect(hwnd, nullptr, FALSE);
                    return 0;
                }

                const auto* assessment = getSelectedAssessment();
                if (!assessment || assessment->strategy == BootstrapStrategy::None ||
                    (assessment->level != CompatibilityLevel::Supported &&
                     assessment->level != CompatibilityLevel::Unverified)) {
                    g.isError = true;
                    g.statusHeadline = L"Rain cannot start in this game";
                    g.statusDetail = assessment ? assessment->reason : L"Target compatibility is unknown.";
                    g.addLog(L"Compatibility", g.statusDetail, Palette::AccentAmber);
                    InvalidateRect(hwnd, nullptr, FALSE);
                    return 0;
                }
                if (isModuleLoaded(g.selectedPid, L"rain-payload.dll")) {
                    g.isLoadedInTarget = true;
                    g.statusHeadline = L"Rain payload already loaded";
                    g.statusDetail = L"Restart Minecraft before retrying a failed bootstrap.";
                    InvalidateRect(hwnd, nullptr, FALSE);
                    return 0;
                }

                g.isBusy = true;
                g.isSuccess = false;
                g.isError = false;
                g.statusHeadline = L"Injecting into " + (getSelectedProcess() ? getSelectedProcess()->exeName : L"Minecraft") + L"...";
                g.statusDetail = L"Creating remote thread & attaching JNI runtime.";
                InvalidateRect(hwnd, nullptr, FALSE);

                DWORD targetPid = g.selectedPid;
                std::thread(injectWorkerThread, hwnd, targetPid).detach();
                return 0;
            } else if (g.hoveredControl == 21) {
                g.activeTab = DrawerTab::Status;
                InvalidateRect(hwnd, nullptr, FALSE);
            } else if (g.hoveredControl == 22) {
                g.activeTab = DrawerTab::Diagnostics;
                InvalidateRect(hwnd, nullptr, FALSE);
            } else if (g.hoveredControl == 23) {
                g.activeTab = DrawerTab::Logs;
                InvalidateRect(hwnd, nullptr, FALSE);
            } else if (g.hoveredControl == 9) { // Copy Log
                std::wstring allLogs;
                {
                    std::lock_guard<std::mutex> lock(g.logMutex);
                    for (const auto& l : g.logs) {
                        allLogs += l.time + L"  [" + l.tag + L"]  " + l.message + L"\r\n";
                    }
                }
                if (OpenClipboard(hwnd)) {
                    EmptyClipboard();
                    size_t bytes = (allLogs.size() + 1) * sizeof(wchar_t);
                    HGLOBAL hGlob = GlobalAlloc(GMEM_MOVEABLE, bytes);
                    if (hGlob) {
                        memcpy(GlobalLock(hGlob), allLogs.c_str(), bytes);
                        GlobalUnlock(hGlob);
                        SetClipboardData(CF_UNICODETEXT, hGlob);
                    }
                    CloseClipboard();
                    g.addLog(L"Clipboard", L"Logs copied to Windows clipboard", Palette::AccentCyan);
                }
            } else if (g.hoveredControl == 10) { // Clear Log
                std::lock_guard<std::mutex> lock(g.logMutex);
                g.logs.clear();
                g.logs.push_back({ L"", L"System", L"Log cleared", Palette::TextTertiary });
                InvalidateRect(hwnd, nullptr, FALSE);
            }
            return 0;
        }

        case WM_PAINT: {
            PAINTSTRUCT ps;
            HDC hdc = BeginPaint(hwnd, &ps);

            RECT clientRect;
            GetClientRect(hwnd, &clientRect);
            int width = clientRect.right;
            int height = clientRect.bottom;

            // Double Buffering via compatible memory DC
            HDC memDC = CreateCompatibleDC(hdc);
            HBITMAP memBmp = CreateCompatibleBitmap(hdc, width, height);
            HGDIOBJ oldBmp = SelectObject(memDC, memBmp);

            Gdiplus::Graphics graphics(memDC);
            graphics.SetSmoothingMode(Gdiplus::SmoothingModeAntiAlias);
            graphics.SetTextRenderingHint(Gdiplus::TextRenderingHintClearTypeGridFit);
            graphics.SetPixelOffsetMode(Gdiplus::PixelOffsetModeHighQuality);

            // 1. Background Fill with subtle radial ambient glow
            Gdiplus::SolidBrush bgBrush(Palette::BgDark);
            graphics.FillRectangle(&bgBrush, 0, 0, width, height);

            // Ambient glow behind target card
            Gdiplus::GraphicsPath glowPath;
            glowPath.AddEllipse(Gdiplus::RectF(static_cast<float>(scaleDpi(50, g.dpi)),
                                               static_cast<float>(scaleDpi(20, g.dpi)),
                                               static_cast<float>(width - scaleDpi(100, g.dpi)),
                                               static_cast<float>(scaleDpi(180, g.dpi))));
            Gdiplus::PathGradientBrush glowBrush(&glowPath);
            Gdiplus::Color glowCenter(40, 56, 189, 248);
            Gdiplus::Color glowSurround(0, 12, 15, 22);
            int count = 1;
            glowBrush.SetCenterColor(glowCenter);
            glowBrush.SetSurroundColors(&glowSurround, &count);
            graphics.FillPath(&glowBrush, &glowPath);

            // Fonts
            Gdiplus::Font fontTitle(L"Segoe UI", static_cast<float>(scaleDpi(11, g.dpi)), Gdiplus::FontStyleBold, Gdiplus::UnitPixel);
            Gdiplus::Font fontHero(L"Segoe UI", static_cast<float>(scaleDpi(16, g.dpi)), Gdiplus::FontStyleBold, Gdiplus::UnitPixel);
            Gdiplus::Font fontRegular(L"Segoe UI", static_cast<float>(scaleDpi(11, g.dpi)), Gdiplus::FontStyleRegular, Gdiplus::UnitPixel);
            Gdiplus::Font fontSmall(L"Segoe UI", static_cast<float>(scaleDpi(9, g.dpi)), Gdiplus::FontStyleRegular, Gdiplus::UnitPixel);
            Gdiplus::Font fontCode(L"Consolas", static_cast<float>(scaleDpi(9, g.dpi)), Gdiplus::FontStyleRegular, Gdiplus::UnitPixel);

            // 2. Header Bar (Height 44px)
            {
                // Raindrop glyph / mark
                float markX = static_cast<float>(scaleDpi(20, g.dpi));
                float markY = static_cast<float>(scaleDpi(13, g.dpi));
                drawStatusDot(graphics, markX + static_cast<float>(scaleDpi(6, g.dpi)),
                              markY + static_cast<float>(scaleDpi(8, g.dpi)),
                              static_cast<float>(scaleDpi(4, g.dpi)), Palette::AccentCyan, false);

                // Branding text
                Gdiplus::SolidBrush textWhite(Palette::TextPrimary);
                Gdiplus::SolidBrush textMuted(Palette::TextTertiary);
                Gdiplus::PointF brandPt(markX + static_cast<float>(scaleDpi(16, g.dpi)), markY);
                graphics.DrawString(L"RainInjectable", -1, &fontTitle, brandPt, &textWhite);

                // Version badge
                Gdiplus::PointF verPt(brandPt.X + scaleDpi(96, g.dpi), brandPt.Y + scaleDpi(2, g.dpi));
                graphics.DrawString(L"v1.2", -1, &fontSmall, verPt, &textMuted);

                // Live beacon
                float beaconX = static_cast<float>(scaleDpi(210, g.dpi));
                float beaconY = static_cast<float>(scaleDpi(21, g.dpi));
                bool hasTarget = (g.selectedPid != 0);
                drawStatusDot(graphics, beaconX, beaconY, static_cast<float>(scaleDpi(3, g.dpi)),
                              hasTarget ? Palette::AccentEmerald : Palette::TextTertiary, hasTarget);

                Gdiplus::PointF beaconTextPt(beaconX + scaleDpi(8, g.dpi), markY + scaleDpi(1, g.dpi));
                graphics.DrawString(hasTarget ? L"Live" : L"Idle", -1, &fontSmall, beaconTextPt,
                                    hasTarget ? &textWhite : &textMuted);

                // Window Controls (Refresh, Settings, Minimize, Close)
                int btnW = scaleDpi(30, g.dpi);
                int btnH = scaleDpi(30, g.dpi);
                int rightX = width - scaleDpi(18, g.dpi);

                auto drawHeaderBtn = [&](int idx, int bx, const wchar_t* glyph) {
                    bool hov = (g.hoveredControl == idx);
                    if (hov) {
                        Gdiplus::SolidBrush hovBrush(idx == 5 ? Gdiplus::Color(60, 244, 63, 94) : Gdiplus::Color(35, 255, 255, 255));
                        Gdiplus::GraphicsPath hovPath;
                        addRoundedRect(hovPath, Gdiplus::RectF(static_cast<float>(bx), static_cast<float>(scaleDpi(7, g.dpi)), static_cast<float>(btnW), static_cast<float>(btnH)), 6.0f);
                        graphics.FillPath(&hovBrush, &hovPath);
                    }
                    Gdiplus::SolidBrush gBrush(hov ? Palette::TextPrimary : Palette::TextSecondary);
                    Gdiplus::StringFormat sf;
                    sf.SetAlignment(Gdiplus::StringAlignmentCenter);
                    sf.SetLineAlignment(Gdiplus::StringAlignmentCenter);
                    Gdiplus::RectF rf(static_cast<float>(bx), static_cast<float>(scaleDpi(7, g.dpi)), static_cast<float>(btnW), static_cast<float>(btnH));
                    graphics.DrawString(glyph, -1, &fontRegular, rf, &sf, &gBrush);
                };

                drawHeaderBtn(5, rightX - btnW, L"✕");
                drawHeaderBtn(4, rightX - btnW * 2, L"─");
                drawHeaderBtn(8, rightX - btnW * 3, L"⚙");
                drawHeaderBtn(3, rightX - btnW * 4, L"⟳");
            }

            // 3. Target Section Hero Card
            int cardX = scaleDpi(20, g.dpi);
            int cardY = scaleDpi(50, g.dpi);
            int cardW = width - cardX * 2;
            int cardH = scaleDpi(88, g.dpi);

            {
                Gdiplus::RectF cardBounds(static_cast<float>(cardX), static_cast<float>(cardY),
                                          static_cast<float>(cardW), static_cast<float>(cardH));
                drawGlassCard(graphics, cardBounds, 14.0f,
                              Palette::GlassTop, Palette::GlassBottom,
                              (g.hoveredControl == 6) ? Gdiplus::Color(70, 255, 255, 255) : Palette::GlassBorder);

                // Cube icon
                drawCubeIcon(graphics, static_cast<float>(cardX + scaleDpi(16, g.dpi)),
                             static_cast<float>(cardY + scaleDpi(18, g.dpi)),
                             static_cast<float>(scaleDpi(50, g.dpi)));

                // Target text
                float textLeft = static_cast<float>(cardX + scaleDpi(78, g.dpi));
                float textTop = static_cast<float>(cardY + scaleDpi(14, g.dpi));

                const auto* p = getSelectedProcess();
                const auto* a = getSelectedAssessment();

                Gdiplus::SolidBrush primaryText(Palette::TextPrimary);
                Gdiplus::SolidBrush secText(Palette::TextSecondary);
                Gdiplus::SolidBrush tertText(Palette::TextTertiary);

                if (p && a) {
                    // Client title
                    graphics.DrawString(a->client.c_str(), -1, &fontHero,
                                        Gdiplus::PointF(textLeft, textTop), &primaryText);

                    // Running badge
                    float statusY = textTop + scaleDpi(24, g.dpi);
                    drawStatusDot(graphics, textLeft + scaleDpi(4, g.dpi), statusY + scaleDpi(6, g.dpi),
                                  static_cast<float>(scaleDpi(3, g.dpi)), Palette::AccentEmerald, true);
                    graphics.DrawString(L"Running", -1, &fontRegular,
                                        Gdiplus::PointF(textLeft + scaleDpi(14, g.dpi), statusY), &primaryText);

                    // Metadata chips
                    std::wstring meta = L"PID " + std::to_wstring(p->pid) + L"  ·  " +
                                        a->architecture + L"  ·  " +
                                        (a->javaVersion.empty() ? L"Java" : L"Java " + a->javaVersion) + L"  ·  " +
                                        a->version;
                    graphics.DrawString(meta.c_str(), -1, &fontSmall,
                                        Gdiplus::PointF(textLeft, statusY + scaleDpi(18, g.dpi)), &secText);

                    // Dropdown indicator if multiple candidates
                    if (g.processes.size() > 1) {
                        Gdiplus::StringFormat rFormat;
                        rFormat.SetAlignment(Gdiplus::StringAlignmentFar);
                        rFormat.SetLineAlignment(Gdiplus::StringAlignmentCenter);
                        Gdiplus::RectF arrowRect(static_cast<float>(cardX + cardW - scaleDpi(40, g.dpi)),
                                                 static_cast<float>(cardY),
                                                 static_cast<float>(scaleDpi(28, g.dpi)),
                                                 static_cast<float>(cardH));
                        graphics.DrawString(L"▾", -1, &fontRegular, arrowRect, &rFormat, &secText);
                    }
                } else {
                    graphics.DrawString(L"No Minecraft Process", -1, &fontHero,
                                        Gdiplus::PointF(textLeft, textTop + scaleDpi(8, g.dpi)), &primaryText);
                    graphics.DrawString(L"Launch Minecraft 1.8.9, Badlion, or Lunar to connect.", -1, &fontRegular,
                                        Gdiplus::PointF(textLeft, textTop + scaleDpi(32, g.dpi)), &secText);
                }
            }

            // 4. Primary Inject Action Button (Liquid Glass Button)
            int btnInjectY = cardY + cardH + scaleDpi(16, g.dpi);
            int btnInjectH = scaleDpi(46, g.dpi);
            int btnInjectW = cardW;

            {
                Gdiplus::RectF btnBounds(static_cast<float>(cardX), static_cast<float>(btnInjectY),
                                         static_cast<float>(btnInjectW), static_cast<float>(btnInjectH));

                // Button glass material tint based on state
                Gdiplus::Color topC = Palette::GlassTop;
                Gdiplus::Color botC = Palette::GlassBottom;
                Gdiplus::Color borderC = Palette::GlassBorder;

                if (g.isSuccess) {
                    topC = Gdiplus::Color(70, 16, 185, 129);
                    botC = Gdiplus::Color(25, 16, 185, 129);
                    borderC = Gdiplus::Color(120, 52, 211, 153);
                } else if (g.isError) {
                    topC = Gdiplus::Color(60, 244, 63, 94);
                    botC = Gdiplus::Color(20, 244, 63, 94);
                    borderC = Gdiplus::Color(110, 251, 113, 133);
                } else {
                    // Normal state with smooth hover interpolation
                    BYTE topAlpha = static_cast<BYTE>(45 + 35 * g.btnHoverAlpha);
                    BYTE botAlpha = static_cast<BYTE>(18 + 20 * g.btnHoverAlpha);
                    BYTE bdrAlpha = static_cast<BYTE>(40 + 40 * g.btnHoverAlpha);
                    topC = Gdiplus::Color(topAlpha, 255, 255, 255);
                    botC = Gdiplus::Color(botAlpha, 255, 255, 255);
                    borderC = Gdiplus::Color(bdrAlpha, 255, 255, 255);
                }

                drawGlassCard(graphics, btnBounds, 12.0f, topC, botC, borderC);

                // Button label / spinner
                Gdiplus::StringFormat sf;
                sf.SetAlignment(Gdiplus::StringAlignmentCenter);
                sf.SetLineAlignment(Gdiplus::StringAlignmentCenter);
                Gdiplus::SolidBrush btnText(Palette::TextPrimary);

                if (g.isBusy) {
                    // Minimalist rotating arc spinner
                    float spCenter = btnBounds.X + btnBounds.Width * 0.5f - scaleDpi(40, g.dpi);
                    float spY = btnBounds.Y + btnBounds.Height * 0.5f;
                    float spRad = static_cast<float>(scaleDpi(7, g.dpi));
                    Gdiplus::Pen spinPen(Palette::AccentCyan, 2.0f);
                    graphics.DrawArc(&spinPen, spCenter - spRad, spY - spRad, spRad * 2, spRad * 2, g.spinnerAngle, 260.0f);

                    Gdiplus::RectF textRect(btnBounds.X + scaleDpi(15, g.dpi), btnBounds.Y, btnBounds.Width - scaleDpi(15, g.dpi), btnBounds.Height);
                    graphics.DrawString(L"Injecting...", -1, &fontTitle, textRect, &sf, &btnText);
                } else {
                    const wchar_t* btnLabel = g.isLoadedInTarget ? L"Rain payload already loaded" :
                                              g.isSuccess ? L"Injected Successfully" :
                                              g.isError ? L"Inject & Verify" : L"Inject";
                    graphics.DrawString(btnLabel, -1, &fontTitle, btnBounds, &sf, &btnText);
                }
            }

            // 5. Status & Diagnostics Banner
            int statusY = btnInjectY + btnInjectH + scaleDpi(14, g.dpi);
            int statusH = scaleDpi(52, g.dpi);

            {
                Gdiplus::RectF statusBounds(static_cast<float>(cardX), static_cast<float>(statusY),
                                            static_cast<float>(cardW), static_cast<float>(statusH));
                drawGlassCard(graphics, statusBounds, 10.0f,
                              (g.hoveredControl == 2) ? Gdiplus::Color(35, 255, 255, 255) : Gdiplus::Color(20, 255, 255, 255),
                              Palette::GlassBottom, Palette::GlassBorder);

                // Status icon dot
                Gdiplus::Color statDotColor = g.isSuccess ? Palette::AccentEmerald :
                                              g.isError ? Palette::AccentAmber :
                                              (g.selectedPid != 0) ? Palette::AccentCyan : Palette::TextTertiary;
                drawStatusDot(graphics, static_cast<float>(cardX + scaleDpi(16, g.dpi)),
                              static_cast<float>(statusY + scaleDpi(26, g.dpi)),
                              static_cast<float>(scaleDpi(4, g.dpi)), statDotColor, false);

                // Status text lines
                float sTextX = static_cast<float>(cardX + scaleDpi(30, g.dpi));
                Gdiplus::SolidBrush sPrimary(Palette::TextPrimary);
                Gdiplus::SolidBrush sDetail(Palette::TextSecondary);

                graphics.DrawString(g.statusHeadline.c_str(), -1, &fontTitle,
                                    Gdiplus::PointF(sTextX, static_cast<float>(statusY + scaleDpi(8, g.dpi))), &sPrimary);

                graphics.DrawString(g.statusDetail.c_str(), -1, &fontSmall,
                                    Gdiplus::PointF(sTextX, static_cast<float>(statusY + scaleDpi(28, g.dpi))), &sDetail);

                // Details expander arrow button on right
                const wchar_t* expandText = g.drawerExpanded ? L"Details ▴" : L"Details ▾";
                Gdiplus::StringFormat sfRight;
                sfRight.SetAlignment(Gdiplus::StringAlignmentFar);
                sfRight.SetLineAlignment(Gdiplus::StringAlignmentCenter);
                Gdiplus::RectF arrowRect(static_cast<float>(cardX + cardW - scaleDpi(90, g.dpi)),
                                         static_cast<float>(statusY),
                                         static_cast<float>(scaleDpi(76, g.dpi)),
                                         static_cast<float>(statusH));
                Gdiplus::SolidBrush arrowBrush(g.hoveredControl == 2 ? Palette::TextPrimary : Palette::TextSecondary);
                graphics.DrawString(expandText, -1, &fontSmall, arrowRect, &sfRight, &arrowBrush);
            }

            // 6. Expandable Diagnostics Drawer (Visible when expanded or expanding)
            if (g.drawerProgress > 0.05f) {
                int drawerY = statusY + statusH + scaleDpi(14, g.dpi);
                int drawerH = height - drawerY - scaleDpi(48, g.dpi);

                if (drawerH > 20) {
                    // Segmented Tab Bar [ Status | Diagnostics | Logs ]
                    int tabW = scaleDpi(80, g.dpi);
                    int tabH = scaleDpi(26, g.dpi);
                    auto drawTab = [&](DrawerTab tab, int tx, const wchar_t* title) {
                        bool act = (g.activeTab == tab);
                        Gdiplus::RectF tr(static_cast<float>(tx), static_cast<float>(drawerY),
                                          static_cast<float>(tabW), static_cast<float>(tabH));
                        if (act) {
                            Gdiplus::SolidBrush tabAct(Gdiplus::Color(40, 255, 255, 255));
                            Gdiplus::GraphicsPath tp;
                            addRoundedRect(tp, tr, 6.0f);
                            graphics.FillPath(&tabAct, &tp);
                        }
                        Gdiplus::SolidBrush tText(act ? Palette::TextPrimary : Palette::TextTertiary);
                        Gdiplus::StringFormat sf;
                        sf.SetAlignment(Gdiplus::StringAlignmentCenter);
                        sf.SetLineAlignment(Gdiplus::StringAlignmentCenter);
                        graphics.DrawString(title, -1, &fontSmall, tr, &sf, &tText);
                    };

                    drawTab(DrawerTab::Status, cardX, L"Status");
                    drawTab(DrawerTab::Diagnostics, cardX + tabW + scaleDpi(4, g.dpi), L"Stages");
                    drawTab(DrawerTab::Logs, cardX + tabW * 2 + scaleDpi(8, g.dpi), L"Logs");

                    // Drawer Content Box
                    int boxY = drawerY + tabH + scaleDpi(8, g.dpi);
                    int boxH = drawerH - tabH - scaleDpi(8, g.dpi);
                    Gdiplus::RectF boxBounds(static_cast<float>(cardX), static_cast<float>(boxY),
                                             static_cast<float>(cardW), static_cast<float>(boxH));
                    drawGlassCard(graphics, boxBounds, 10.0f,
                                  Gdiplus::Color(14, 255, 255, 255),
                                  Gdiplus::Color(6, 255, 255, 255),
                                  Palette::GlassBorder, false);

                    if (g.activeTab == DrawerTab::Diagnostics) {
                        // Display Stages Checklist
                        const auto* a = getSelectedAssessment();
                        if (a && !a->stages.empty()) {
                            float rowY = boxBounds.Y + scaleDpi(8, g.dpi);
                            float rowH = static_cast<float>(scaleDpi(18, g.dpi));
                            for (size_t i = 0; i < a->stages.size() && rowY + rowH < boxBounds.GetBottom(); ++i) {
                                const auto& st = a->stages[i];
                                Gdiplus::Color stCol = (st.state == StageState::Confirmed) ? Palette::AccentEmerald :
                                                       (st.state == StageState::Unsupported) ? Palette::AccentRose :
                                                       (st.state == StageState::Unverified) ? Palette::AccentAmber : Palette::TextTertiary;

                                drawStatusDot(graphics, boxBounds.X + static_cast<float>(scaleDpi(12, g.dpi)),
                                              rowY + static_cast<float>(scaleDpi(9, g.dpi)),
                                              static_cast<float>(scaleDpi(3, g.dpi)), stCol, false);

                                Gdiplus::SolidBrush stageBrush(Palette::TextPrimary);
                                graphics.DrawString(stageName(st.stage), -1, &fontSmall,
                                                    Gdiplus::PointF(boxBounds.X + scaleDpi(22, g.dpi), rowY + scaleDpi(2, g.dpi)), &stageBrush);

                                Gdiplus::SolidBrush reasonBrush(Palette::TextSecondary);
                                std::wstring reasonText = st.reason;
                                if (!st.code.empty()) reasonText += L" [" + st.code + L"]";
                                graphics.DrawString(reasonText.c_str(), -1, &fontSmall,
                                                    Gdiplus::PointF(boxBounds.X + scaleDpi(120, g.dpi), rowY + scaleDpi(2, g.dpi)), &reasonBrush);

                                rowY += rowH;
                            }
                        }
                    } else if (g.activeTab == DrawerTab::Logs) {
                        // Monospace Log Console
                        std::lock_guard<std::mutex> lock(g.logMutex);
                        float lineY = boxBounds.Y + scaleDpi(8, g.dpi);
                        float lineH = static_cast<float>(scaleDpi(15, g.dpi));
                        size_t maxLines = static_cast<size_t>((boxBounds.Height - scaleDpi(16, g.dpi)) / lineH);
                        size_t startIdx = (g.logs.size() > maxLines) ? g.logs.size() - maxLines : 0;

                        for (size_t i = startIdx; i < g.logs.size() && lineY + lineH < boxBounds.GetBottom(); ++i) {
                            const auto& item = g.logs[i];
                            Gdiplus::SolidBrush timeBrush(Palette::TextTertiary);
                            graphics.DrawString(item.time.c_str(), -1, &fontCode,
                                                Gdiplus::PointF(boxBounds.X + scaleDpi(10, g.dpi), lineY), &timeBrush);

                            Gdiplus::SolidBrush tagBrush(item.tagColor);
                            std::wstring tagStr = L"[" + item.tag + L"]";
                            graphics.DrawString(tagStr.c_str(), -1, &fontCode,
                                                Gdiplus::PointF(boxBounds.X + scaleDpi(66, g.dpi), lineY), &tagBrush);

                            Gdiplus::SolidBrush msgBrush(Palette::TextPrimary);
                            graphics.DrawString(item.message.c_str(), -1, &fontCode,
                                                Gdiplus::PointF(boxBounds.X + scaleDpi(145, g.dpi), lineY), &msgBrush);

                            lineY += lineH;
                        }
                    } else {
                        // Status Overview Tab
                        const auto* a = getSelectedAssessment();
                        Gdiplus::SolidBrush hBrush(Palette::TextPrimary);
                        Gdiplus::SolidBrush bBrush(Palette::TextSecondary);

                        float oY = boxBounds.Y + scaleDpi(12, g.dpi);
                        graphics.DrawString(L"Environment Summary", -1, &fontTitle,
                                            Gdiplus::PointF(boxBounds.X + scaleDpi(14, g.dpi), oY), &hBrush);

                        std::wstring sum = L"Client: " + (a ? a->client : L"None") + L"\r\n" +
                                           L"Minecraft: " + (a ? a->version : L"Unknown") + L"\r\n" +
                                           L"Java Runtime: " + (a ? a->javaVersion : L"Unknown") + L" (" + (a ? a->architecture : L"x64") + L")\r\n" +
                                           L"Compatibility: " + (a ? compatibilityName(a->level) : L"Unavailable") + L"\r\n" +
                                           L"Framework: " + (a ? a->framework : L"None");
                        graphics.DrawString(sum.c_str(), -1, &fontRegular,
                                            Gdiplus::PointF(boxBounds.X + scaleDpi(14, g.dpi), oY + scaleDpi(22, g.dpi)), &bBrush);
                    }

                    // Drawer footer controls (Copy Log, Clear)
                    int footerY = height - scaleDpi(36, g.dpi);
                    auto drawFootBtn = [&](int idx, int bx, int bw, const wchar_t* text) {
                        bool hov = (g.hoveredControl == idx);
                        Gdiplus::RectF fr(static_cast<float>(bx), static_cast<float>(footerY),
                                          static_cast<float>(bw), static_cast<float>(scaleDpi(24, g.dpi)));
                        drawGlassCard(graphics, fr, 6.0f,
                                      hov ? Gdiplus::Color(40, 255, 255, 255) : Palette::GlassBottom,
                                      Palette::GlassBottom, Palette::GlassBorder, false);
                        Gdiplus::StringFormat sf;
                        sf.SetAlignment(Gdiplus::StringAlignmentCenter);
                        sf.SetLineAlignment(Gdiplus::StringAlignmentCenter);
                        Gdiplus::SolidBrush btnB(hov ? Palette::TextPrimary : Palette::TextSecondary);
                        graphics.DrawString(text, -1, &fontSmall, fr, &sf, &btnB);
                    };

                    drawFootBtn(9, cardX, scaleDpi(76, g.dpi), L"Copy Log");
                    drawFootBtn(10, cardX + scaleDpi(84, g.dpi), scaleDpi(56, g.dpi), L"Clear");
                }
            }

            // Blit to screen
            BitBlt(hdc, 0, 0, width, height, memDC, 0, 0, SRCCOPY);

            // Clean up GDI memory objects
            SelectObject(memDC, oldBmp);
            DeleteObject(memBmp);
            DeleteDC(memDC);

            EndPaint(hwnd, &ps);
            return 0;
        }

        case WM_DESTROY: {
            KillTimer(hwnd, TIMER_ANIM);
            KillTimer(hwnd, TIMER_SCAN);
            PostQuitMessage(0);
            return 0;
        }

        default:
            return DefWindowProcW(hwnd, msg, wParam, lParam);
    }
}

} // namespace

int runGui(HINSTANCE hInst, int nCmdShow) {
    // Initialize GDI+
    Gdiplus::GdiplusStartupInput gdiInput;
    ULONG_PTR gdiToken = 0;
    if (Gdiplus::GdiplusStartup(&gdiToken, &gdiInput, nullptr) != Gdiplus::Ok) {
        MessageBoxW(nullptr, L"Could not initialize GDI+ graphics library.", L"RainInjectable Error", MB_ICONERROR);
        return 1;
    }

    // Register modern frameless window class
    WNDCLASSEXW wc{ sizeof(wc) };
    wc.lpfnWndProc = WndProc;
    wc.hInstance = hInst;
    wc.lpszClassName = L"RainInjectable_LiquidGlass";
    wc.hCursor = LoadCursorW(nullptr, IDC_ARROW);
    wc.hIcon = LoadIconW(hInst, IDI_APPLICATION);
    wc.style = CS_HREDRAW | CS_VREDRAW;
    RegisterClassExW(&wc);

    UINT dpi = 96;
    HDC screenDc = GetDC(nullptr);
    if (screenDc) {
        dpi = GetDeviceCaps(screenDc, LOGPIXELSY);
        ReleaseDC(nullptr, screenDc);
    }

    int winW = scaleDpi(BASE_WIDTH, dpi);
    int winH = scaleDpi(COLLAPSED_HEIGHT, dpi);

    // Center on primary monitor
    int screenW = GetSystemMetrics(SM_CXSCREEN);
    int screenH = GetSystemMetrics(SM_CYSCREEN);
    int winX = (screenW - winW) / 2;
    int winY = (screenH - winH) / 2;

    HWND hwnd = CreateWindowExW(
        WS_EX_APPWINDOW,
        wc.lpszClassName,
        L"RainInjectable",
        WS_POPUP | WS_VISIBLE | WS_MINIMIZEBOX,
        winX, winY, winW, winH,
        nullptr, nullptr, hInst, nullptr
    );

    if (!hwnd) {
        Gdiplus::GdiplusShutdown(gdiToken);
        return 1;
    }

    ShowWindow(hwnd, nCmdShow);
    UpdateWindow(hwnd);

    // Standard Win32 Message Loop
    MSG msg{};
    while (GetMessageW(&msg, nullptr, 0, 0)) {
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }

    Gdiplus::GdiplusShutdown(gdiToken);
    return static_cast<int>(msg.wParam);
}

} // namespace rain
