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
constexpr int BASE_WIDTH = 500;
constexpr int BASE_HEIGHT = 604;

constexpr UINT_PTR TIMER_ANIM = 1;
constexpr UINT_PTR TIMER_SCAN = 2;
constexpr UINT WM_INJECT_EVENT = WM_USER + 201;
constexpr UINT WM_INJECT_PROGRESS = WM_USER + 202;
constexpr UINT WM_SCAN_EVENT = WM_USER + 203;
constexpr UINT WM_FIT_WINDOW = WM_USER + 204;

enum class ViewTab { Status, Activity };

struct LogEntry {
    std::wstring time;
    std::wstring tag;
    std::wstring message;
    Gdiplus::Color tagColor;
};

struct ScanSnapshot {
    std::vector<McProcess> processes;
    std::vector<CompatibilityResult> assessments;
};

// Global GUI State
struct GuiState {
    HWND hwnd = nullptr;
    UINT dpi = 96;

    // Process discovery
    Injector injector;
    std::vector<McProcess> processes;
    std::vector<CompatibilityResult> assessments;
    DWORD selectedPid = 0;
    bool isLoadedInTarget = false;
    bool scanPending = false;

    // Operation status
    bool isBusy = false;
    bool isSuccess = false;
    bool isError = false;
    std::wstring statusHeadline = L"Scanning for Minecraft processes...";
    std::wstring statusDetail = L"";
    std::wstring activeStage = L"Discovery";

    // Compact view state
    ViewTab activeTab = ViewTab::Status;
    int pressedControl = 0;

    // Animations
    float animPulse = 0.0f;
    float btnHoverAlpha = 0.0f;
    float btnTargetHover = 0.0f;
    float spinnerAngle = 0.0f;

    // Hovered elements
    int hoveredControl = 0;

    // Logs & History
    std::vector<LogEntry> logs;
    std::mutex logMutex;

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
    if (g.scanPending || !g.hwnd) return;
    g.scanPending = true;
    HWND hwnd = g.hwnd;
    std::thread([hwnd] {
        auto* snapshot = new ScanSnapshot;
        ProcessScanner scanner;
        snapshot->processes = scanner.scan();
        for (const auto& process : snapshot->processes)
            snapshot->assessments.push_back(assessCompatibility(process));
        if (!PostMessageW(hwnd, WM_SCAN_EVENT, 0, reinterpret_cast<LPARAM>(snapshot)))
            delete snapshot;
    }).detach();
}

void applyScanResult(ScanSnapshot&& snapshot) {
    const DWORD oldPid = g.selectedPid;
    g.processes = std::move(snapshot.processes);
    g.assessments = std::move(snapshot.assessments);
    g.scanPending = false;
    if (!g.processes.empty()) {
        bool found = false;
        for (const auto& p : g.processes) {
            if (p.pid == oldPid) { found = true; break; }
        }
        if (!found) g.selectedPid = pickBestTargetPid();
    } else {
        g.selectedPid = 0;
    }

    if (g.selectedPid != oldPid) {
        g.isError = false;
        g.isSuccess = false;
    }
    if (g.selectedPid != 0) {
        g.isLoadedInTarget = isModuleLoaded(g.selectedPid, L"rain-payload.dll");
        const auto* res = getSelectedAssessment();
        if (res && !g.isBusy && !g.isError && !g.isSuccess) {
            if (g.isLoadedInTarget) {
                g.statusHeadline = L"Rain is already loaded";
                g.statusDetail = L"Restart Minecraft before trying again.";
            } else if (res->level == CompatibilityLevel::Supported) {
                g.statusHeadline = L"Ready to inject";
                g.statusDetail = res->reason;
            } else {
                g.statusHeadline = compatibilityName(res->level);
                g.statusDetail = res->reason;
            }
        }
    } else {
        g.statusHeadline = L"Waiting for Minecraft";
        g.statusDetail = L"Open Minecraft 1.8.9 in Forge or Badlion.";
        g.isLoadedInTarget = false;
        g.isError = false;
        g.isSuccess = false;
    }

    if (g.hwnd) InvalidateRect(g.hwnd, nullptr, FALSE);
}

// Background injection worker thread
void injectWorkerThread(HWND hwnd, DWORD pid) {
    LOG_I("Starting injection worker thread for PID %lu", pid);
    g.addLog(L"Inject", L"Starting injection for PID " + std::to_wstring(pid), Palette::AccentCyan);
    auto postStage = [hwnd](const std::wstring& stage) {
        wchar_t* copy = _wcsdup(stage.c_str());
        if (copy && !PostMessageW(hwnd, WM_INJECT_PROGRESS, 0, reinterpret_cast<LPARAM>(copy)))
            free(copy);
    };
    postStage(L"Preparing Rain...");

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
    postStage(L"Loading into Minecraft...");
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
    std::wstring lastStage;
    std::wstring lastMessage;
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
        } else if (!status.stage.empty() &&
                   (status.stage != lastStage || status.message != lastMessage)) {
            lastStage = status.stage;
            lastMessage = status.message;
            g.addLog(status.stage, status.message, Palette::TextSecondary);
            postStage(status.message.empty() ? status.stage : status.message);
        }
    }

    if (!finished) {
        std::wstring timeoutMsg = L"In-process bootstrap timed out or target is waiting.";
        g.addLog(L"Timeout", timeoutMsg, Palette::AccentAmber);
        PostMessageW(hwnd, WM_INJECT_EVENT, 2, reinterpret_cast<LPARAM>(_wcsdup(timeoutMsg.c_str())));
    }
}

enum ControlId {
    ControlNone = 0, ControlInject = 1, ControlRefresh = 2,
    ControlMinimize = 3, ControlClose = 4, ControlTarget = 5,
    ControlStatus = 6, ControlActivity = 7, ControlCopy = 8,
    ControlDetails = 9
};

Gdiplus::RectF asRect(const UiRect& rect) {
    return Gdiplus::RectF(static_cast<float>(rect.x), static_cast<float>(rect.y),
                          static_cast<float>(rect.width), static_cast<float>(rect.height));
}

void drawLabel(Gdiplus::Graphics& canvas, const std::wstring& value,
               const Gdiplus::Font& font, const Gdiplus::RectF& bounds,
               const Gdiplus::Color& color,
               Gdiplus::StringAlignment align = Gdiplus::StringAlignmentNear,
               bool wrap = false) {
    Gdiplus::SolidBrush brush(color);
    Gdiplus::StringFormat format;
    format.SetAlignment(align);
    format.SetLineAlignment(Gdiplus::StringAlignmentCenter);
    format.SetTrimming(Gdiplus::StringTrimmingEllipsisCharacter);
    if (!wrap) format.SetFormatFlags(Gdiplus::StringFormatFlagsNoWrap);
    canvas.DrawString(value.c_str(), -1, &font, bounds, &format, &brush);
}

bool canInject() {
    const auto* assessment = getSelectedAssessment();
    return !g.isBusy && !g.isSuccess && !g.isLoadedInTarget &&
           g.selectedPid != 0 && assessment &&
           assessment->strategy != BootstrapStrategy::None &&
           (assessment->level == CompatibilityLevel::Supported ||
            assessment->level == CompatibilityLevel::Unverified);
}

int hitControl(int x, int y, int width) {
    const auto m = uiLayoutMetrics(width, g.dpi);
    if (m.close.contains(x, y)) return ControlClose;
    if (m.minimize.contains(x, y)) return ControlMinimize;
    if (m.refresh.contains(x, y)) return ControlRefresh;
    if (m.target.contains(x, y) && g.processes.size() > 1 && !g.isBusy) return ControlTarget;
    if (m.inject.contains(x, y) && canInject()) return ControlInject;
    if (m.statusTab.contains(x, y)) return ControlStatus;
    if (m.activityTab.contains(x, y)) return ControlActivity;
    if (g.activeTab == ViewTab::Status && m.copyLog.contains(x, y)) return ControlDetails;
    if (g.activeTab == ViewTab::Activity && m.copyLog.contains(x, y)) return ControlCopy;
    return ControlNone;
}

void copyActivity(HWND hwnd) {
    std::wstring allLogs;
    {
        std::lock_guard<std::mutex> lock(g.logMutex);
        for (const auto& item : g.logs)
            allLogs += item.time + L"  [" + item.tag + L"]  " + item.message + L"\r\n";
    }
    if (!OpenClipboard(hwnd)) return;
    EmptyClipboard();
    const size_t bytes = (allLogs.size() + 1) * sizeof(wchar_t);
    HGLOBAL block = GlobalAlloc(GMEM_MOVEABLE, bytes);
    if (block) {
        void* destination = GlobalLock(block);
        if (destination) {
            memcpy(destination, allLogs.c_str(), bytes);
            GlobalUnlock(block);
            if (!SetClipboardData(CF_UNICODETEXT, block)) GlobalFree(block);
        } else {
            GlobalFree(block);
        }
    }
    CloseClipboard();
}

void showStatusDetails(HWND hwnd) {
    const auto* assessment = getSelectedAssessment();
    const std::wstring title = g.isError ? g.statusHeadline :
                               assessment ? assessment->client : L"No game detected";
    const std::wstring detail = g.isError ? g.statusDetail :
                                assessment ? assessment->reason :
                                L"Launch Forge or Badlion with Minecraft 1.8.9.";
    MessageBoxW(hwnd, detail.c_str(), title.c_str(), MB_OK | MB_ICONINFORMATION);
}

void startInjection(HWND hwnd) {
    if (!canInject()) return;
    g.isBusy = true;
    g.isSuccess = false;
    g.isError = false;
    g.activeStage = L"Preparing Rain...";
    g.statusHeadline = L"Injecting Rain";
    g.statusDetail = L"Preparing the in-game runtime.";
    InvalidateRect(hwnd, nullptr, FALSE);
    std::thread(injectWorkerThread, hwnd, g.selectedPid).detach();
}

void drawRainMark(Gdiplus::Graphics& canvas, float x, float y, float size) {
    using namespace Gdiplus;
    GraphicsPath drop;
    const float mid = x + size * 0.5f;
    drop.StartFigure();
    drop.AddBezier(mid, y, x + size * 0.94f, y + size * 0.49f,
                   x + size * 0.86f, y + size * 0.94f, mid, y + size);
    drop.AddBezier(mid, y + size, x + size * 0.14f, y + size * 0.94f,
                   x + size * 0.06f, y + size * 0.49f, mid, y);
    drop.CloseFigure();
    LinearGradientBrush fill(RectF(x, y, size, size),
                             Color(255, 224, 244, 255), Color(255, 78, 151, 243),
                             LinearGradientModeVertical);
    canvas.FillPath(&fill, &drop);
    Pen edge(Color(235, 239, 249, 255), 1.0f);
    canvas.DrawPath(&edge, &drop);
    SolidBrush shine(Color(155, 255, 255, 255));
    canvas.FillEllipse(&shine, x + size * 0.27f, y + size * 0.39f,
                       size * 0.15f, size * 0.28f);
}

void drawWindowButton(Gdiplus::Graphics& canvas, const UiRect& rect,
                      int id, Gdiplus::Color color) {
    using namespace Gdiplus;
    const RectF r = asRect(rect);
    if (g.hoveredControl == id) {
        drawGlassCard(canvas, r, r.Width * 0.5f,
                      id == ControlClose ? Color(56, 235, 88, 113) : Color(52, 255, 255, 255),
                      Color(17, 255, 255, 255), Color(67, 255, 255, 255), false);
    }
    const float cx = r.X + r.Width * 0.5f;
    const float cy = r.Y + r.Height * 0.5f;
    Pen pen(color, static_cast<float>(scaleDpi(1, g.dpi)) + 0.5f);
    pen.SetStartCap(LineCapRound);
    pen.SetEndCap(LineCapRound);
    if (id == ControlClose) {
        canvas.DrawLine(&pen, cx - 5, cy - 5, cx + 5, cy + 5);
        canvas.DrawLine(&pen, cx + 5, cy - 5, cx - 5, cy + 5);
    } else if (id == ControlMinimize) {
        canvas.DrawLine(&pen, cx - 6, cy + 2, cx + 6, cy + 2);
    } else {
        canvas.DrawArc(&pen, cx - 7.0f, cy - 7.0f, 14.0f, 14.0f, -50.0f, 290.0f);
        canvas.DrawLine(&pen, cx + 6, cy - 5, cx + 8, cy - 8);
    }
}

void paintWindow(HWND hwnd, HDC hdc) {
    using namespace Gdiplus;
    RECT cr{};
    GetClientRect(hwnd, &cr);
    const int width = cr.right;
    const int height = cr.bottom;
    if (width <= 0 || height <= 0) return;
    const auto m = uiLayoutMetrics(width, g.dpi);
    const float unit = static_cast<float>(g.dpi) / 96.0f;

    HDC memory = CreateCompatibleDC(hdc);
    HBITMAP bitmap = CreateCompatibleBitmap(hdc, width, height);
    HGDIOBJ previous = SelectObject(memory, bitmap);
    Graphics canvas(memory);
    canvas.SetSmoothingMode(SmoothingModeAntiAlias);
    canvas.SetTextRenderingHint(TextRenderingHintClearTypeGridFit);
    canvas.SetPixelOffsetMode(PixelOffsetModeHighQuality);

    LinearGradientBrush background(RectF(0, 0, static_cast<float>(width), static_cast<float>(height)),
                                   Color(255, 25, 32, 43), Color(255, 9, 13, 21),
                                   LinearGradientModeVertical);
    canvas.FillRectangle(&background, 0, 0, width, height);
    GraphicsPath ambient;
    ambient.AddEllipse(RectF(static_cast<float>(scaleDpi(74, g.dpi)),
                             static_cast<float>(scaleDpi(59, g.dpi)),
                             static_cast<float>(width - scaleDpi(148, g.dpi)),
                             static_cast<float>(scaleDpi(265, g.dpi))));
    PathGradientBrush ambientBrush(&ambient);
    ambientBrush.SetCenterColor(Color(38, 66, 113, 177));
    Color outer(0, 20, 30, 48);
    int outerCount = 1;
    ambientBrush.SetSurroundColors(&outer, &outerCount);
    canvas.FillPath(&ambientBrush, &ambient);
    Pen frame(Color(117, 193, 214, 240), unit);
    GraphicsPath outline;
    addRoundedRect(outline, RectF(unit * 0.5f, unit * 0.5f,
                                 width - unit, height - unit), 24.0f * unit);
    canvas.DrawPath(&frame, &outline);

    Font brand(L"Segoe UI", 19 * unit, FontStyleBold, UnitPixel);
    Font brandSub(L"Segoe UI", 10 * unit, FontStyleRegular, UnitPixel);
    Font headlineFont(L"Segoe UI", 27 * unit, FontStyleBold, UnitPixel);
    Font subtitleFont(L"Segoe UI", 12 * unit, FontStyleRegular, UnitPixel);
    Font targetFont(L"Segoe UI", 17 * unit, FontStyleBold, UnitPixel);
    Font buttonFont(L"Segoe UI", 17 * unit, FontStyleBold, UnitPixel);
    Font bodyFont(L"Segoe UI", 12 * unit, FontStyleRegular, UnitPixel);
    Font bodyBold(L"Segoe UI", 12 * unit, FontStyleBold, UnitPixel);
    Font smallFont(L"Segoe UI", 10 * unit, FontStyleRegular, UnitPixel);
    const Color white(255, 244, 248, 253);
    const Color muted(255, 158, 174, 194);
    const Color faint(255, 104, 119, 140);
    const Color blue(255, 147, 200, 255);
    const Color green(255, 91, 217, 145);
    const Color amber(255, 255, 187, 91);

    drawRainMark(canvas, 30 * unit, 24 * unit, 28 * unit);
    drawLabel(canvas, L"Rain", brand, RectF(68 * unit, 19 * unit, 190 * unit, 30 * unit), white);
    drawLabel(canvas, L"INJECTOR", brandSub, RectF(70 * unit, 46 * unit, 160 * unit, 17 * unit), muted);
    drawWindowButton(canvas, m.refresh, ControlRefresh, muted);
    drawWindowButton(canvas, m.minimize, ControlMinimize, muted);
    drawWindowButton(canvas, m.close, ControlClose, muted);

    const auto* process = getSelectedProcess();
    const auto* assessment = getSelectedAssessment();
    std::wstring headline = L"Ready when you are";
    std::wstring subtitle = L"Select your game and inject to begin.";
    if (g.isBusy) {
        headline = L"Starting Rain";
        subtitle = L"Keep Minecraft open while setup finishes.";
    } else if (g.isSuccess || g.isLoadedInTarget) {
        headline = L"Rain is running";
        subtitle = L"You're all set in Minecraft.";
    } else if (g.isError) {
        headline = L"Needs attention";
        subtitle = L"Check the message below for the next step.";
    } else if (!process) {
        headline = L"Waiting for Minecraft";
        subtitle = L"Open Minecraft 1.8.9 to get started.";
    } else if (!canInject()) {
        headline = L"Check your game";
        subtitle = L"This instance is not ready for Rain.";
    }
    drawLabel(canvas, headline, headlineFont,
              RectF(24 * unit, 87 * unit, width - 48 * unit, 44 * unit),
              white, StringAlignmentCenter);
    drawLabel(canvas, subtitle, subtitleFont,
              RectF(25 * unit, 130 * unit, width - 50 * unit, 24 * unit),
              muted, StringAlignmentCenter);

    const RectF targetRect = asRect(m.target);
    drawGlassCard(canvas, targetRect, 21 * unit,
                  Color(74, 226, 238, 252), Color(23, 184, 204, 230),
                  g.hoveredControl == ControlTarget ? Color(145, 222, 239, 255) :
                                                     Color(83, 211, 229, 252));
    RectF iconTile(targetRect.X + 16 * unit, targetRect.Y + 17 * unit, 56 * unit, 56 * unit);
    drawGlassCard(canvas, iconTile, 17 * unit,
                  Color(88, 224, 237, 254), Color(19, 107, 142, 184),
                  Color(85, 221, 240, 255), false);
    drawCubeIcon(canvas, iconTile.X + 8 * unit, iconTile.Y + 8 * unit, 40 * unit);
    const float targetTextX = targetRect.X + 87 * unit;
    const float targetTextWidth = targetRect.Width - 129 * unit;
    drawLabel(canvas, assessment ? assessment->client : L"No game found",
              targetFont, RectF(targetTextX, targetRect.Y + 13 * unit,
                                targetTextWidth, 28 * unit), white);
    std::wstring targetLine = assessment ?
        (assessment->version.empty() ? L"Minecraft version unknown" :
         L"Minecraft " + assessment->version) :
        L"Open Forge or Badlion 1.8.9";
    drawLabel(canvas, targetLine, bodyFont,
              RectF(targetTextX, targetRect.Y + 41 * unit,
                    targetTextWidth, 20 * unit), muted);
    Color stateColor = !assessment ? faint :
        (assessment->level == CompatibilityLevel::Supported ? green :
         assessment->level == CompatibilityLevel::Unverified ? amber : faint);
    std::wstring stateText = !assessment ? L"Looking for a game" :
        g.isLoadedInTarget ? L"Rain loaded" :
        assessment->level == CompatibilityLevel::Supported ? L"Ready" :
        assessment->level == CompatibilityLevel::Unverified ? L"Needs verification" :
        assessment->level == CompatibilityLevel::Starting ? L"Starting" : L"Unavailable";
    drawStatusDot(canvas, targetTextX + 4 * unit, targetRect.Y + 72 * unit, 4 * unit, stateColor, false);
    drawLabel(canvas, stateText, smallFont,
              RectF(targetTextX + 15 * unit, targetRect.Y + 62 * unit,
                    targetTextWidth - 15 * unit, 21 * unit), stateColor);
    if (g.processes.size() > 1) {
        Pen chevron(muted, 2 * unit);
        chevron.SetStartCap(LineCapRound);
        chevron.SetEndCap(LineCapRound);
        const float cx = targetRect.GetRight() - 26 * unit;
        const float cy = targetRect.Y + 44 * unit;
        canvas.DrawLine(&chevron, cx - 5 * unit, cy - 2 * unit, cx, cy + 3 * unit);
        canvas.DrawLine(&chevron, cx, cy + 3 * unit, cx + 5 * unit, cy - 2 * unit);
    }

    const RectF button = asRect(m.inject);
    const bool enabled = canInject();
    GraphicsPath buttonPath;
    addRoundedRect(buttonPath, button, 30 * unit);
    const float hover = g.btnHoverAlpha;
    SolidBrush halo(Color(enabled ? static_cast<BYTE>(28 + 27 * hover) : 7,
                          79, 156, 249));
    RectF haloRect(button.X - 3 * unit, button.Y - 3 * unit,
                   button.Width + 6 * unit, button.Height + 8 * unit);
    GraphicsPath haloPath;
    addRoundedRect(haloPath, haloRect, 33 * unit);
    canvas.FillPath(&halo, &haloPath);
    LinearGradientBrush buttonFill(button,
        enabled ? Color(static_cast<BYTE>(132 + 30 * hover), 168, 205, 249) :
                  Color(42, 159, 174, 196),
        enabled ? Color(static_cast<BYTE>(58 + 20 * hover), 34, 66, 111) :
                  Color(18, 38, 51, 69), LinearGradientModeVertical);
    canvas.FillPath(&buttonFill, &buttonPath);
    Pen buttonEdge(enabled ? Color(245, 189, 222, 255) : Color(56, 140, 159, 184), 1.5f * unit);
    canvas.DrawPath(&buttonEdge, &buttonPath);
    Pen buttonHighlight(Color(enabled ? 215 : 55, 255, 255, 255), unit);
    canvas.DrawArc(&buttonHighlight, button.X + 7 * unit, button.Y + 2 * unit,
                   button.Width - 14 * unit, button.Height - 5 * unit, 194, 150);
    std::wstring buttonText = g.isBusy ? L"Injecting Rain..." :
                              g.isLoadedInTarget || g.isSuccess ? L"Rain is running" :
                              L"Inject Rain";
    drawLabel(canvas, buttonText, buttonFont, button, enabled || g.isBusy ? white : muted,
              StringAlignmentCenter);

    const RectF rail = asRect(m.progress);
    Pen railPen(Color(69, 139, 161, 191), 1.5f * unit);
    canvas.DrawLine(&railPen, rail.X + 5 * unit, rail.Y + 9 * unit,
                    rail.GetRight() - 5 * unit, rail.Y + 9 * unit);
    constexpr int dots = 8;
    for (int i = 0; i < dots; ++i) {
        const float cx = rail.X + 5 * unit + i * (rail.Width - 10 * unit) / (dots - 1);
        float intensity = 0.0f;
        if (g.isBusy) {
            const float phase = std::fmod(g.animPulse * 1.9f, static_cast<float>(dots));
            float distance = std::abs(phase - i);
            distance = std::min(distance, static_cast<float>(dots) - distance);
            intensity = std::max(0.0f, 1.0f - distance / 1.5f);
        } else if (g.isSuccess || g.isLoadedInTarget) intensity = 1.0f;
        else if (i == 0 && process) intensity = 0.8f;
        if (intensity > 0.01f) {
            SolidBrush glow(Color(static_cast<BYTE>(75 * intensity),
                                  g.isSuccess ? 91 : 128, g.isSuccess ? 217 : 183, 255));
            const float radius = (5 + 3 * intensity) * unit;
            canvas.FillEllipse(&glow, cx - radius, rail.Y + 9 * unit - radius,
                               radius * 2, radius * 2);
        }
        SolidBrush dot(Color(static_cast<BYTE>(110 + 145 * intensity),
                             g.isSuccess ? 111 : 185, g.isSuccess ? 224 : 212, 255));
        const float dotRadius = (3.2f + 1.7f * intensity) * unit;
        canvas.FillEllipse(&dot, cx - dotRadius, rail.Y + 9 * unit - dotRadius,
                           dotRadius * 2, dotRadius * 2);
    }
    std::wstring progressText = g.isBusy ? g.activeStage :
        g.isSuccess ? L"Rain started successfully" :
        g.isError ? L"See the status below" :
        g.isLoadedInTarget ? L"Already loaded in this game" :
        !process ? L"Looking for a running game" :
        canInject() ? L"Ready to inject" : L"Choose a compatible game";
    drawLabel(canvas, progressText, bodyFont, asRect(m.statusText), muted,
              StringAlignmentCenter);

    const RectF tabs = asRect(m.tabs);
    drawGlassCard(canvas, tabs, 24 * unit,
                  Color(41, 178, 195, 218), Color(20, 8, 14, 23),
                  Color(64, 190, 208, 234), false);
    const UiRect activeTabRect = g.activeTab == ViewTab::Status ? m.statusTab : m.activityTab;
    drawGlassCard(canvas, asRect(activeTabRect), 20 * unit,
                  Color(100, 194, 213, 237), Color(47, 87, 122, 161),
                  Color(171, 226, 242, 255), false);
    drawLabel(canvas, L"Status", bodyBold, asRect(m.statusTab),
              g.activeTab == ViewTab::Status ? white : faint, StringAlignmentCenter);
    drawLabel(canvas, L"Activity", bodyBold, asRect(m.activityTab),
              g.activeTab == ViewTab::Activity ? white : faint, StringAlignmentCenter);

    const RectF content = asRect(m.content);
    drawGlassCard(canvas, content, 19 * unit,
                  Color(49, 230, 240, 255), Color(19, 147, 173, 210),
                  Color(69, 200, 221, 249), false);
    if (g.activeTab == ViewTab::Status) {
        std::wstring title;
        std::wstring detail;
        Color indicator = faint;
        if (g.isError) {
            title = g.statusHeadline;
            detail = g.statusDetail;
            indicator = amber;
        } else if (g.isBusy) {
            title = L"Working in Minecraft";
            detail = g.activeStage;
            indicator = blue;
        } else if (g.isSuccess) {
            title = L"Injection complete";
            detail = L"Rain initialized successfully in the selected game.";
            indicator = green;
        } else if (g.isLoadedInTarget) {
            title = L"Already loaded";
            detail = L"Restart Minecraft before trying again.";
            indicator = amber;
        } else if (!assessment) {
            title = L"No game detected";
            detail = L"Launch Forge or Badlion with Minecraft 1.8.9.";
        } else {
            title = assessment->level == CompatibilityLevel::Supported ?
                    L"Compatible" : compatibilityName(assessment->level);
            detail = assessment->reason;
            indicator = assessment->level == CompatibilityLevel::Supported ?
                        green : assessment->level == CompatibilityLevel::Unverified ?
                        amber : faint;
        }
        drawStatusDot(canvas, content.X + 24 * unit, content.Y + 29 * unit,
                      6 * unit, indicator, false);
        drawLabel(canvas, title, bodyBold,
                  RectF(content.X + 43 * unit, content.Y + 12 * unit,
                        content.Width - 136 * unit, 25 * unit), white);
        drawLabel(canvas, L"Details", smallFont, asRect(m.copyLog),
                  g.hoveredControl == ControlDetails ? white : blue,
                  StringAlignmentCenter);
        drawLabel(canvas, detail, smallFont,
                  RectF(content.X + 43 * unit, content.Y + 36 * unit,
                        content.Width - 61 * unit, 42 * unit), muted,
                  StringAlignmentNear, true);
    } else {
        drawLabel(canvas, L"Recent activity", bodyBold,
                  RectF(content.X + 17 * unit, content.Y + 8 * unit,
                        content.Width - 100 * unit, 24 * unit), white);
        drawLabel(canvas, L"Copy", smallFont, asRect(m.copyLog),
                  g.hoveredControl == ControlCopy ? white : blue,
                  StringAlignmentCenter);
        std::lock_guard<std::mutex> lock(g.logMutex);
        const size_t count = std::min<size_t>(3, g.logs.size());
        for (size_t row = 0; row < count; ++row) {
            const auto& item = g.logs[g.logs.size() - count + row];
            const std::wstring line = item.time + L"  " + item.message;
            drawLabel(canvas, line, smallFont,
                      RectF(content.X + 18 * unit,
                            content.Y + (31 + static_cast<int>(row) * 17) * unit,
                            content.Width - 36 * unit, 18 * unit), muted);
        }
    }

    BitBlt(hdc, 0, 0, width, height, memory, 0, 0, SRCCOPY);
    SelectObject(memory, previous);
    DeleteObject(bitmap);
    DeleteDC(memory);
}

LRESULT CALLBACK WndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    switch (msg) {
        case WM_CREATE: {
            g.hwnd = hwnd;
            g.dpi = GetDpiForWindow(hwnd);
            DWM_WINDOW_CORNER_PREFERENCE corner = DWMWCP_ROUND;
            DwmSetWindowAttribute(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE, &corner, sizeof(corner));
            BOOL dark = TRUE;
            DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, &dark, sizeof(dark));
            DWM_SYSTEMBACKDROP_TYPE backdrop = DWMSBT_TRANSIENTWINDOW;
            DwmSetWindowAttribute(hwnd, DWMWA_SYSTEMBACKDROP_TYPE, &backdrop, sizeof(backdrop));
            SetTimer(hwnd, TIMER_ANIM, 16, nullptr);
            SetTimer(hwnd, TIMER_SCAN, 2500, nullptr);
            refreshProcesses();
            g.addLog(L"Ready", L"Injector opened", Palette::AccentCyan);
            return 0;
        }
        case WM_DPICHANGED: {
            g.dpi = HIWORD(wParam);
            auto* bounds = reinterpret_cast<RECT*>(lParam);
            SetWindowPos(hwnd, nullptr, bounds->left, bounds->top,
                         bounds->right - bounds->left, bounds->bottom - bounds->top,
                         SWP_NOZORDER | SWP_NOACTIVATE);
            PostMessageW(hwnd, WM_FIT_WINDOW, 0, 0);
            InvalidateRect(hwnd, nullptr, FALSE);
            return 0;
        }
        case WM_FIT_WINDOW: {
            const UINT monitorDpi = GetDpiForWindow(hwnd);
            RECT current{};
            GetWindowRect(hwnd, &current);
            int left = current.left;
            int top = current.top;
            MONITORINFO monitorInfo{ sizeof(monitorInfo) };
            g.dpi = monitorDpi;
            if (GetMonitorInfoW(MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST), &monitorInfo)) {
                const RECT& work = monitorInfo.rcWork;
                const int availableWidth = work.right - work.left - 32;
                const int availableHeight = work.bottom - work.top - 32;
                const UINT fitDpi = static_cast<UINT>(std::min(
                    MulDiv(availableWidth, 96, BASE_WIDTH),
                    MulDiv(availableHeight, 96, BASE_HEIGHT)));
                g.dpi = std::min(monitorDpi, std::max(80u, fitDpi));
                const int scaledWidth = scaleDpi(BASE_WIDTH, g.dpi);
                const int scaledHeight = scaleDpi(BASE_HEIGHT, g.dpi);
                left = work.left + ((work.right - work.left) - scaledWidth) / 2;
                top = work.top + ((work.bottom - work.top) - scaledHeight) / 2;
                SetWindowPos(hwnd, nullptr, left, top, scaledWidth, scaledHeight,
                             SWP_NOZORDER | SWP_NOACTIVATE);
            } else {
                SetWindowPos(hwnd, nullptr, left, top,
                             scaleDpi(BASE_WIDTH, g.dpi), scaleDpi(BASE_HEIGHT, g.dpi),
                             SWP_NOZORDER | SWP_NOACTIVATE);
            }
            return 0;
        }
        case WM_TIMER: {
            if (wParam == TIMER_ANIM) {
                g.animPulse = std::fmod(g.animPulse + 0.07f, 6.283185f);
                const float desired = g.hoveredControl == ControlInject ? 1.0f : 0.0f;
                g.btnHoverAlpha += (desired - g.btnHoverAlpha) * 0.20f;
                if (g.isBusy || std::abs(desired - g.btnHoverAlpha) > 0.01f)
                    InvalidateRect(hwnd, nullptr, FALSE);
            } else if (wParam == TIMER_SCAN && !g.isBusy) refreshProcesses();
            return 0;
        }
        case WM_SCAN_EVENT: {
            std::unique_ptr<ScanSnapshot> snapshot(reinterpret_cast<ScanSnapshot*>(lParam));
            if (snapshot) applyScanResult(std::move(*snapshot));
            return 0;
        }
        case WM_INJECT_PROGRESS: {
            wchar_t* stage = reinterpret_cast<wchar_t*>(lParam);
            if (stage) {
                g.activeStage = stage;
                free(stage);
                InvalidateRect(hwnd, nullptr, FALSE);
            }
            return 0;
        }
        case WM_INJECT_EVENT: {
            g.isBusy = false;
            if (wParam == 1) {
                g.isSuccess = true;
                g.isError = false;
                g.isLoadedInTarget = true;
                g.statusHeadline = L"Injection complete";
                g.statusDetail = L"Rain initialized successfully in Minecraft.";
            } else {
                g.isSuccess = false;
                g.isError = true;
                wchar_t* message = reinterpret_cast<wchar_t*>(lParam);
                g.statusHeadline = wParam == 2 ? L"Setup needs attention" : L"Injection failed";
                g.statusDetail = message ? message : L"An unknown error occurred.";
                free(message);
                g.activeTab = ViewTab::Status;
            }
            InvalidateRect(hwnd, nullptr, FALSE);
            return 0;
        }
        case WM_NCHITTEST: {
            POINT point{ GET_X_LPARAM(lParam), GET_Y_LPARAM(lParam) };
            ScreenToClient(hwnd, &point);
            RECT cr{};
            GetClientRect(hwnd, &cr);
            if (point.y < scaleDpi(72, g.dpi) &&
                point.x < cr.right - scaleDpi(145, g.dpi))
                return HTCAPTION;
            return HTCLIENT;
        }
        case WM_MOUSEMOVE: {
            RECT cr{};
            GetClientRect(hwnd, &cr);
            const int hovered = hitControl(GET_X_LPARAM(lParam), GET_Y_LPARAM(lParam), cr.right);
            if (hovered != g.hoveredControl) {
                g.hoveredControl = hovered;
                TRACKMOUSEEVENT tracking{ sizeof(tracking), TME_LEAVE, hwnd, 0 };
                TrackMouseEvent(&tracking);
                SetCursor(LoadCursorW(nullptr, hovered ? IDC_HAND : IDC_ARROW));
                InvalidateRect(hwnd, nullptr, FALSE);
            }
            return 0;
        }
        case WM_MOUSELEAVE:
            g.hoveredControl = ControlNone;
            InvalidateRect(hwnd, nullptr, FALSE);
            return 0;
        case WM_LBUTTONDOWN: {
            RECT cr{};
            GetClientRect(hwnd, &cr);
            g.pressedControl = hitControl(GET_X_LPARAM(lParam), GET_Y_LPARAM(lParam), cr.right);
            SetCapture(hwnd);
            return 0;
        }
        case WM_LBUTTONUP: {
            const int pressed = g.pressedControl;
            g.pressedControl = ControlNone;
            ReleaseCapture();
            RECT cr{};
            GetClientRect(hwnd, &cr);
            const int released = hitControl(GET_X_LPARAM(lParam), GET_Y_LPARAM(lParam), cr.right);
            if (pressed != released) return 0;
            switch (pressed) {
                case ControlClose: DestroyWindow(hwnd); break;
                case ControlMinimize: ShowWindow(hwnd, SW_MINIMIZE); break;
                case ControlRefresh:
                    refreshProcesses();
                    g.addLog(L"Scan", L"Game list refreshed", Palette::AccentCyan);
                    break;
                case ControlTarget:
                    for (size_t i = 0; i < g.processes.size(); ++i)
                        if (g.processes[i].pid == g.selectedPid) {
                            g.selectedPid = g.processes[(i + 1) % g.processes.size()].pid;
                            break;
                        }
                    g.isError = false;
                    g.isSuccess = false;
                    refreshProcesses();
                    break;
                case ControlInject: startInjection(hwnd); break;
                case ControlStatus: g.activeTab = ViewTab::Status; break;
                case ControlActivity: g.activeTab = ViewTab::Activity; break;
                case ControlCopy: copyActivity(hwnd); break;
                case ControlDetails: showStatusDetails(hwnd); break;
                default: break;
            }
            InvalidateRect(hwnd, nullptr, FALSE);
            return 0;
        }
        case WM_KEYDOWN:
            if (wParam == VK_RETURN) startInjection(hwnd);
            else if (wParam == 'R') refreshProcesses();
            else if (wParam == VK_LEFT) g.activeTab = ViewTab::Status;
            else if (wParam == VK_RIGHT) g.activeTab = ViewTab::Activity;
            else if (wParam == 'D' && g.activeTab == ViewTab::Status) showStatusDetails(hwnd);
            else if (wParam == VK_ESCAPE) DestroyWindow(hwnd);
            InvalidateRect(hwnd, nullptr, FALSE);
            return 0;
        case WM_PAINT: {
            PAINTSTRUCT ps{};
            HDC hdc = BeginPaint(hwnd, &ps);
            paintWindow(hwnd, hdc);
            EndPaint(hwnd, &ps);
            return 0;
        }
        case WM_DESTROY:
            KillTimer(hwnd, TIMER_ANIM);
            KillTimer(hwnd, TIMER_SCAN);
            PostQuitMessage(0);
            return 0;
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

    // The initial size is corrected to the monitor's real per-window DPI
    // immediately after creation. GetDeviceCaps(LOGPIXELSY) can still report
    // 96 on a per-monitor-aware process running on a scaled display.
    int winW = BASE_WIDTH;
    int winH = BASE_HEIGHT;

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
    PostMessageW(hwnd, WM_FIT_WINDOW, 0, 0);

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
