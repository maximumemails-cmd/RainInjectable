#include "gui.h"
#include "injector.h"
#include "logger.h"
#include "process_scanner.h"

#include <commctrl.h>
#include <shellapi.h>
#include <shlobj.h>
#include <dwmapi.h>
#include <uxtheme.h>

#ifdef min
#undef min
#endif
#ifdef max
#undef max
#endif
#include <algorithm>
#include <gdiplus.h>

#include <string>
#include <vector>
#include <map>

#pragma comment(lib, "comctl32.lib")
#pragma comment(lib, "shell32.lib")
#pragma comment(lib, "user32.lib")
#pragma comment(lib, "gdi32.lib")
#pragma comment(lib, "dwmapi.lib")
#pragma comment(lib, "gdiplus.lib")
#pragma comment(lib, "uxtheme.lib")

#ifndef DWMWA_USE_IMMERSIVE_DARK_MODE
#define DWMWA_USE_IMMERSIVE_DARK_MODE 20
#endif
#ifndef DWMWA_WINDOW_CORNER_PREFERENCE
#define DWMWA_WINDOW_CORNER_PREFERENCE 33
#endif
#ifndef DWMWCP_ROUND
#define DWMWCP_ROUND 2
#endif
#ifndef DWMWA_BORDER_COLOR
#define DWMWA_BORDER_COLOR 34
#endif
#ifndef DWMWA_CAPTION_COLOR
#define DWMWA_CAPTION_COLOR 35
#endif

using Gdiplus::Graphics;
using Gdiplus::GraphicsPath;
using Gdiplus::SolidBrush;
using Gdiplus::LinearGradientBrush;
using Gdiplus::Pen;
using Gdiplus::Color;
using Gdiplus::Rect;
using Gdiplus::RectF;
using Gdiplus::PointF;
using Gdiplus::StringFormat;
using Gdiplus::StringAlignmentCenter;
using Gdiplus::FontStyleRegular;
using Gdiplus::FontStyleBold;
using Gdiplus::SmoothingModeAntiAlias;
using Gdiplus::TextRenderingHintClearTypeGridFit;
using Gdiplus::REAL;
using Gdiplus::LinearGradientModeVertical;
using Gdiplus::LinearGradientModeForwardDiagonal;
using Gdiplus::InterpolationModeHighQualityBicubic;

namespace rain {
namespace {

enum : int {
    IDC_PROCESS_LIST = 1001,
    IDC_REFRESH      = 1002,
    IDC_INJECT       = 1003,
    IDC_LOG          = 1004,
};

namespace Clr {
    const COLORREF BG        = RGB( 23,  31,  39);
    const COLORREF SIDEBAR   = RGB( 28,  37,  45);
    const COLORREF PANEL     = RGB( 34,  44,  54);
    const COLORREF ACCENT    = RGB( 74, 158, 255);
    const COLORREF ACCENTLT  = RGB(132, 185, 255);
    const COLORREF ACCENT2   = RGB( 46,  98, 158);
    const COLORREF TEXT      = RGB(241, 244, 248);
    const COLORREF TEXTDIM   = RGB(172, 183, 194);
    const COLORREF TEXTSUB   = RGB(126, 137, 149);
    const COLORREF LISTBG    = RGB( 23,  31,  39);
    const COLORREF LISTALT   = RGB( 28,  37,  45);
    const COLORREF LISTSEL   = RGB( 38,  78, 130);
    const COLORREF HDRBG     = RGB( 31,  41,  51);
    const COLORREF BORDER    = RGB( 57,  70,  82);
    const COLORREF BORDERLT  = RGB( 67,  80,  93);
    const COLORREF LOGBG     = RGB( 16,  23,  30);
}

constexpr int SIDEBAR_W    = 240;
constexpr int BTN_W        = 200;
constexpr int BTN_H        = 38;
constexpr int BTN_INJECT_H = 52;
constexpr int BTN_GAP      = 8;
constexpr int BTN_TOP_PAD  = 28;
constexpr int SECTION_H    = 30;
constexpr int CONTENT_PAD  = 18;
constexpr int ROW_H        = 30;

HWND gMain = nullptr;
HWND gList = nullptr;
HWND gLog  = nullptr;

HFONT gFont     = nullptr;
HFONT gFontTiny = nullptr;

HBRUSH gBrBg  = nullptr;
HBRUSH gBrLog = nullptr;

ULONG_PTR gGpToken = 0;
HIMAGELIST gRowSizer = nullptr;

std::map<HWND, bool> gBtnHover;

std::vector<McProcess> gProcesses;
ProcessScanner gScanner;
Injector gInjector;

Color gpClr(COLORREF c, BYTE a = 255) {
    return Color(a, GetRValue(c), GetGValue(c), GetBValue(c));
}

void addRoundRect(GraphicsPath& path, REAL x, REAL y, REAL w, REAL h, REAL r) {
    REAL d = r * 2;
    if (d > w) d = w;
    if (d > h) d = h;
    path.AddArc(x,         y,         d, d, 180, 90);
    path.AddArc(x + w - d, y,         d, d, 270, 90);
    path.AddArc(x + w - d, y + h - d, d, d,   0, 90);
    path.AddArc(x,         y + h - d, d, d,  90, 90);
    path.CloseFigure();
}

const wchar_t* launcherNameW(LauncherKind k) {
    switch (k) {
        case LauncherKind::Vanilla:  return L"Vanilla";
        case LauncherKind::Forge:    return L"Forge";
        case LauncherKind::Fabric:   return L"Fabric";
        case LauncherKind::OptiFine: return L"OptiFine";
        default:                     return L"Unknown";
    }
}

Color launcherColor(LauncherKind k) {
    switch (k) {
        case LauncherKind::Vanilla:  return Color(255,  76, 175,  80);
        case LauncherKind::Forge:    return Color(255, 230, 128,  40);
        case LauncherKind::Fabric:   return Color(255, 158, 117,  91);
        case LauncherKind::OptiFine: return Color(255, 240, 195,  35);
        default:                     return Color(255, 130, 145, 165);
    }
}

std::wstring exeDir() {
    wchar_t buf[MAX_PATH];
    GetModuleFileNameW(nullptr, buf, MAX_PATH);
    std::wstring p = buf;
    auto pos = p.find_last_of(L"\\/");
    return (pos == std::wstring::npos) ? L"." : p.substr(0, pos);
}

LRESULT CALLBACK btnSubclassProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp,
                                  UINT_PTR, DWORD_PTR) {
    switch (msg) {
        case WM_MOUSEMOVE:
            if (!gBtnHover[hwnd]) {
                gBtnHover[hwnd] = true;
                TRACKMOUSEEVENT tme{ sizeof(tme), TME_LEAVE, hwnd, 0 };
                TrackMouseEvent(&tme);
                InvalidateRect(hwnd, nullptr, FALSE);
            }
            break;
        case WM_MOUSELEAVE:
            gBtnHover[hwnd] = false;
            InvalidateRect(hwnd, nullptr, FALSE);
            break;
        case WM_ERASEBKGND:
            return 1;
    }
    return DefSubclassProc(hwnd, msg, wp, lp);
}

LRESULT CALLBACK headerSubclassProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp,
                                     UINT_PTR, DWORD_PTR) {
    switch (msg) {
        case WM_ERASEBKGND:
            return 1;

        case WM_PAINT: {
            PAINTSTRUCT ps;
            HDC dc = BeginPaint(hwnd, &ps);

            RECT rc; GetClientRect(hwnd, &rc);
            int W = rc.right, H = rc.bottom;

            HDC mem = CreateCompatibleDC(dc);
            HBITMAP bm = CreateCompatibleBitmap(dc, W, H);
            HBITMAP oldBm = (HBITMAP)SelectObject(mem, bm);

            HBRUSH bgBr = CreateSolidBrush(Clr::HDRBG);
            FillRect(mem, &rc, bgBr);
            DeleteObject(bgBr);

            int count = (int)SendMessageW(hwnd, HDM_GETITEMCOUNT, 0, 0);
            HFONT useFont = gFontTiny ? gFontTiny : (HFONT)GetStockObject(DEFAULT_GUI_FONT);
            HFONT oldFont = (HFONT)SelectObject(mem, useFont);
            SetBkMode(mem, TRANSPARENT);
            SetTextColor(mem, Clr::TEXTDIM);

            for (int i = 0; i < count; ++i) {
                RECT itemRc{};
                SendMessageW(hwnd, HDM_GETITEMRECT, i, (LPARAM)&itemRc);

                wchar_t text[64] = {};
                HDITEMW hdi{};
                hdi.mask       = HDI_TEXT;
                hdi.pszText    = text;
                hdi.cchTextMax = 64;
                SendMessageW(hwnd, HDM_GETITEMW, i, (LPARAM)&hdi);

                HPEN sep = CreatePen(PS_SOLID, 1, Clr::BORDER);
                HPEN op = (HPEN)SelectObject(mem, sep);
                MoveToEx(mem, itemRc.right - 1, itemRc.top + 6, nullptr);
                LineTo  (mem, itemRc.right - 1, itemRc.bottom - 6);
                SelectObject(mem, op);
                DeleteObject(sep);

                RECT trc = itemRc;
                trc.left += 10;
                trc.bottom -= 2;
                DrawTextW(mem, text, -1, &trc, DT_LEFT | DT_VCENTER | DT_SINGLELINE);
            }

            HPEN bot = CreatePen(PS_SOLID, 1, Clr::BORDER);
            HPEN op = (HPEN)SelectObject(mem, bot);
            MoveToEx(mem, rc.left,  rc.bottom - 1, nullptr);
            LineTo  (mem, rc.right, rc.bottom - 1);
            SelectObject(mem, op);
            DeleteObject(bot);

            SelectObject(mem, oldFont);

            BitBlt(dc, 0, 0, W, H, mem, 0, 0, SRCCOPY);

            SelectObject(mem, oldBm);
            DeleteObject(bm);
            DeleteDC(mem);

            EndPaint(hwnd, &ps);
            return 0;
        }
    }
    return DefSubclassProc(hwnd, msg, wp, lp);
}

void setListColumns() {
    LVCOLUMNW c{}; c.mask = LVCF_TEXT | LVCF_WIDTH;
    struct { const wchar_t* n; int w; } cols[] = {
        {L"PID", 70}, {L"Process", 150}, {L"Arch", 60},
        {L"Launcher", 110}, {L"Window Title", 260}, {L"JVM", 50}, {L"LWJGL", 60},
    };
    for (int i = 0; i < (int)(sizeof(cols)/sizeof(*cols)); ++i) {
        c.pszText = const_cast<LPWSTR>(cols[i].n);
        c.cx = cols[i].w;
        ListView_InsertColumn(gList, i, &c);
    }
    ListView_SetExtendedListViewStyle(gList,
        LVS_EX_FULLROWSELECT | LVS_EX_DOUBLEBUFFER);
    SendMessage(gList, LVM_SETTEXTCOLOR,   0, (LPARAM)Clr::TEXT);
    SendMessage(gList, LVM_SETBKCOLOR,     0, (LPARAM)Clr::LISTBG);
    SendMessage(gList, LVM_SETTEXTBKCOLOR, 0, (LPARAM)Clr::LISTBG);
}

void fitLastColumn() {
    if (!gList) return;
    RECT lvRc; GetClientRect(gList, &lvRc);
    int used = 0;
    for (int i = 0; i < 6; ++i) used += ListView_GetColumnWidth(gList, i);
    int last = (lvRc.right - used) - 1;
    if (last < 60) last = 60;
    ListView_SetColumnWidth(gList, 6, last);
    HWND hdr = ListView_GetHeader(gList);
    if (hdr) InvalidateRect(hdr, nullptr, FALSE);
}

void refreshList() {
    ListView_DeleteAllItems(gList);
    gProcesses = gScanner.scan();
    int idx = 0;
    for (auto& p : gProcesses) {
        LVITEMW it{}; it.mask = LVIF_TEXT; it.iItem = idx;
        std::wstring pid = std::to_wstring(p.pid);
        it.pszText = const_cast<LPWSTR>(pid.c_str());
        ListView_InsertItem(gList, &it);

        ListView_SetItemText(gList, idx, 1, const_cast<LPWSTR>(p.exeName.c_str()));
        ListView_SetItemText(gList, idx, 2, const_cast<LPWSTR>(p.x64 ? L"x64" : L"x86"));
        ListView_SetItemText(gList, idx, 3, const_cast<LPWSTR>(launcherNameW(p.launcher)));
        ListView_SetItemText(gList, idx, 4, const_cast<LPWSTR>(p.windowTitle.c_str()));
        ListView_SetItemText(gList, idx, 5, const_cast<LPWSTR>(p.hasJvm ? L"yes" : L"no"));
        ListView_SetItemText(gList, idx, 6, const_cast<LPWSTR>(p.hasLwjgl ? L"yes" : L"no"));
        idx++;
    }
    InvalidateRect(gMain, nullptr, FALSE);
    LOG_I("Found %d Minecraft process(es).", idx);
}

void doInject() {
    int sel = (int)SendMessage(gList, LVM_GETNEXTITEM, (WPARAM)-1, LVNI_SELECTED);
    if (sel < 0 || sel >= (int)gProcesses.size()) {
        MessageBoxW(gMain, L"Select a Minecraft process first.", L"Rain Injector", MB_ICONWARNING);
        return;
    }
    const McProcess& p = gProcesses[sel];
    LOG_I("Injecting into pid=%lu (%ls)", p.pid, p.exeName.c_str());

    std::wstring payloadPath = exeDir() + L"\\rain-payload.dll";
    std::wstring jarPath     = exeDir() + L"\\rain-runtime.jar";

    DWORD attr = GetFileAttributesW(payloadPath.c_str());
    if (attr == INVALID_FILE_ATTRIBUTES) {
        MessageBoxW(gMain,
            L"rain-payload.dll is missing.\nIt must sit next to RainInjector.exe.",
            L"Rain Injector", MB_ICONERROR);
        return;
    }
    attr = GetFileAttributesW(jarPath.c_str());
    if (attr == INVALID_FILE_ATTRIBUTES) {
        MessageBoxW(gMain,
            L"rain-runtime.jar is missing.\nIt must sit next to RainInjector.exe.",
            L"Rain Injector", MB_ICONERROR);
        return;
    }

    if (p.launcher == LauncherKind::Unknown) {
        // Some launchers (Prism/MultiMC-style wrappers) pass the real main class
        // and classpath over stdin, so the command line cannot prove Forge.
        // The Java bootstrap performs the definitive Forge check and refuses
        // to start if Forge is absent, so let the user decide here.
        int choice = MessageBoxW(gMain,
            L"Could not confirm Forge 1.8.9 from this process's command line.\n\n"
            L"Rain only works inside a Forge 1.8.9 game. If this is a Forge profile "
            L"started by a wrapper launcher you can continue; Rain will refuse to start "
            L"if Forge is not present.\n\nInject anyway?",
            L"Rain Injector", MB_ICONQUESTION | MB_YESNO | MB_DEFBUTTON2);
        if (choice != IDYES) {
            LOG_I("Injection cancelled: launcher could not be classified as Forge.");
            return;
        }
        LOG_W("Launcher unclassified; proceeding on user confirmation (Java-side Forge check still applies).");
    } else if (p.launcher != LauncherKind::Forge) {
        std::wstring msg = L"Rain requires a Forge 1.8.9 process.\n\nThe selected process was classified as: ";
        msg += launcherNameW(p.launcher);
        msg += L".\nStart Minecraft with a Forge 1.8.9 profile (Feather counts as Forge) and refresh.";
        MessageBoxW(gMain, msg.c_str(), L"Rain Injector", MB_ICONWARNING);
        LOG_W("Rain requires a Forge 1.8.9 process. The selected process was classified as: %ls.",
              launcherNameW(p.launcher));
        return;
    }
    if (!p.x64) {
        MessageBoxW(gMain, L"Rain Injector only supports 64-bit Java processes.",
            L"Rain Injector", MB_ICONERROR);
        return;
    }
    if (isModuleLoaded(p.pid, L"rain-payload.dll")) {
        MessageBoxW(gMain,
            L"Rain is already loaded in this process.\nRe-injecting is a no-op; restart Minecraft to load a new build.",
            L"Rain Injector", MB_ICONINFORMATION);
        LOG_I("Rain is already loaded in this process. Re-injecting is a no-op; restart Minecraft to load a new build.");
        return;
    }

    EnableWindow(GetDlgItem(gMain, IDC_INJECT), FALSE);
    auto r = gInjector.inject(p.pid, payloadPath);
    EnableWindow(GetDlgItem(gMain, IDC_INJECT), TRUE);

    if (!r.ok) {
        std::wstring wmsg(r.message.begin(), r.message.end());
        MessageBoxW(gMain, wmsg.c_str(), L"Injection failed", MB_ICONERROR);
    } else {
        MessageBoxW(gMain,
            L"Injection complete.\n\nRain is now running inside Minecraft. Press RIGHT SHIFT in-game to open the Rain GUI.\n\nIf nothing happens, check rain-payload.log and rain-bootstrap.log next to RainInjector.exe.",
            L"Rain Injector", MB_ICONINFORMATION);
    }
}

void drawButton(DRAWITEMSTRUCT* dis) {
    HDC dc = dis->hDC;
    RECT rcW = dis->rcItem;

    bool disabled = (dis->itemState & ODS_DISABLED) != 0;
    bool pressed  = (dis->itemState & ODS_SELECTED) != 0;
    bool hovered  = !disabled && gBtnHover[dis->hwndItem];
    bool isInject = (dis->CtlID == IDC_INJECT);

    {
        HBRUSH clr = CreateSolidBrush(Clr::SIDEBAR);
        FillRect(dc, &rcW, clr);
        DeleteObject(clr);
    }

    Graphics g(dc);
    g.SetSmoothingMode(SmoothingModeAntiAlias);
    g.SetTextRenderingHint(TextRenderingHintClearTypeGridFit);

    REAL x = (REAL)rcW.left;
    REAL y = (REAL)rcW.top;
    REAL w = (REAL)(rcW.right - rcW.left);
    REAL h = (REAL)(rcW.bottom - rcW.top);
    REAL pad = pressed ? 1.0f : 0.0f;

    GraphicsPath path;
    addRoundRect(path, x + 1 + pad, y + 1 + pad, w - 2, h - 2, 8.0f);

    wchar_t text[64] = {};
    GetWindowTextW(dis->hwndItem, text, 64);

    if (isInject) {
        Rect grad((INT)x, (INT)y, (INT)w, (INT)h);
        Color top = disabled ? gpClr(Clr::ACCENT2)
                  : hovered  ? gpClr(Clr::ACCENTLT)
                             : gpClr(Clr::ACCENT);
        Color bot = disabled ? gpClr(Clr::ACCENT2)
                  : hovered  ? gpClr(Clr::ACCENT)
                             : gpClr(Clr::ACCENT2);
        LinearGradientBrush gradBr(grad, top, bot, LinearGradientModeVertical);
        g.FillPath(&gradBr, &path);

        if (hovered) {
            Pen glowPen(Color(80, 132, 185, 255), 1.5f);
            g.DrawPath(&glowPen, &path);
        }

        Gdiplus::Font font(L"Segoe UI", 10.5f, FontStyleBold);
        StringFormat fmt;
        fmt.SetAlignment(StringAlignmentCenter);
        fmt.SetLineAlignment(StringAlignmentCenter);
        SolidBrush wht(disabled ? gpClr(Clr::TEXTDIM) : Color(255, 255, 255, 255));
        RectF txtRc(x + pad, y + pad, w, h);
        g.DrawString(text, -1, &font, txtRc, &fmt, &wht);
    } else {
        Color bgClr = pressed ? gpClr(Clr::ACCENT2, 160)
                    : hovered ? Color(255, 36, 50, 70)
                              : Color(0, 0, 0, 0);
        if (bgClr.GetA() > 0) {
            SolidBrush br(bgClr);
            g.FillPath(&br, &path);
        }

        Color borderClr = disabled ? gpClr(Clr::BORDER)
                        : hovered  ? gpClr(Clr::ACCENT)
                        : pressed  ? gpClr(Clr::ACCENTLT)
                                   : gpClr(Clr::BORDERLT);
        Pen pen(borderClr, 1.0f);
        g.DrawPath(&pen, &path);

        Gdiplus::Font font(L"Segoe UI", 9.5f, FontStyleRegular);
        StringFormat fmt;
        fmt.SetAlignment(StringAlignmentCenter);
        fmt.SetLineAlignment(StringAlignmentCenter);
        Color txtClr = disabled ? gpClr(Clr::TEXTDIM)
                     : hovered  ? gpClr(Clr::ACCENTLT)
                                : gpClr(Clr::TEXT);
        SolidBrush txtBr(txtClr);
        RectF txtRc(x + pad, y + pad, w, h);
        g.DrawString(text, -1, &font, txtRc, &fmt, &txtBr);
    }
}

void drawLauncherBadge(HDC dc, RECT subRc, LauncherKind k, bool selected, int row) {
    COLORREF rowBg = selected ? Clr::LISTSEL : (row % 2 == 0 ? Clr::LISTBG : Clr::LISTALT);
    HBRUSH bgBr = CreateSolidBrush(rowBg);
    FillRect(dc, &subRc, bgBr);
    DeleteObject(bgBr);

    Graphics g(dc);
    g.SetSmoothingMode(SmoothingModeAntiAlias);
    g.SetTextRenderingHint(TextRenderingHintClearTypeGridFit);

    const wchar_t* name = launcherNameW(k);
    Color clr = launcherColor(k);

    Gdiplus::Font font(L"Segoe UI", 8.0f, FontStyleBold);
    RectF mr;
    g.MeasureString(name, -1, &font, PointF(0, 0), &mr);

    int pillH = (subRc.bottom - subRc.top) - 10;
    if (pillH < 16) pillH = 16;
    int pillW = (int)mr.Width + 16;
    int pillX = subRc.left + 8;
    int pillY = subRc.top + ((subRc.bottom - subRc.top) - pillH) / 2;

    GraphicsPath pill;
    addRoundRect(pill, (REAL)pillX, (REAL)pillY, (REAL)pillW, (REAL)pillH, pillH / 2.0f);

    Color pillBg(48, clr.GetR(), clr.GetG(), clr.GetB());
    SolidBrush pillBgBr(pillBg);
    g.FillPath(&pillBgBr, &pill);

    Pen pillPen(Color(220, clr.GetR(), clr.GetG(), clr.GetB()), 1.0f);
    g.DrawPath(&pillPen, &pill);

    SolidBrush txtBr(Color(255, clr.GetR(), clr.GetG(), clr.GetB()));
    StringFormat fmt;
    fmt.SetAlignment(StringAlignmentCenter);
    fmt.SetLineAlignment(StringAlignmentCenter);
    g.DrawString(name, -1, &font,
        RectF((REAL)pillX, (REAL)pillY - 1, (REAL)pillW, (REAL)pillH),
        &fmt, &txtBr);
}

LRESULT handleListCustomDraw(NMLVCUSTOMDRAW* cd) {
    NMCUSTOMDRAW& nm = cd->nmcd;

    if (nm.dwDrawStage == CDDS_PREPAINT)
        return CDRF_NOTIFYITEMDRAW;

    if (nm.dwDrawStage == CDDS_ITEMPREPAINT) {
        int row = (int)nm.dwItemSpec;
        bool sel = (ListView_GetItemState(gList, row, LVIS_SELECTED) & LVIS_SELECTED) != 0;
        cd->clrText   = Clr::TEXT;
        cd->clrTextBk = sel ? Clr::LISTSEL
                      : (row % 2 == 0 ? Clr::LISTBG : Clr::LISTALT);
        return CDRF_NEWFONT | CDRF_NOTIFYPOSTPAINT;
    }

    if (nm.dwDrawStage == CDDS_ITEMPOSTPAINT) {
        int row = (int)nm.dwItemSpec;
        if (row >= 0 && row < (int)gProcesses.size()) {
            RECT subRc;
            ListView_GetSubItemRect(gList, row, 3, LVIR_BOUNDS, &subRc);
            bool sel = (ListView_GetItemState(gList, row, LVIS_SELECTED) & LVIS_SELECTED) != 0;
            drawLauncherBadge(nm.hdc, subRc, gProcesses[row].launcher, sel, row);
        }
        return CDRF_DODEFAULT;
    }

    return CDRF_DODEFAULT;
}

void drawSidebar(HDC dc, int H) {
    RECT srect = { 0, 0, SIDEBAR_W, H };
    HBRUSH br = CreateSolidBrush(Clr::SIDEBAR);
    FillRect(dc, &srect, br);
    DeleteObject(br);

    RECT lineR = { SIDEBAR_W - 1, 0, SIDEBAR_W, H };
    HBRUSH lb = CreateSolidBrush(Clr::BORDER);
    FillRect(dc, &lineR, lb);
    DeleteObject(lb);
}

void drawSectionHeader(Graphics& g, int x, int y, int w, const wchar_t* title,
                       const wchar_t* badge) {
    Gdiplus::Font titleF(L"Segoe UI", 11.0f, FontStyleBold);
    SolidBrush titleBr(gpClr(Clr::TEXT));
    g.DrawString(title, -1, &titleF, PointF((REAL)x, (REAL)y + 4), &titleBr);

    Pen accent(gpClr(Clr::ACCENT), 2.0f);
    g.DrawLine(&accent, x, y + 26, x + 18, y + 26);

    if (badge && *badge) {
        Gdiplus::Font bF(L"Segoe UI", 8.0f, FontStyleBold);
        RectF mr;
        g.MeasureString(badge, -1, &bF, PointF(0, 0), &mr);
        int bw = (int)mr.Width + 14;
        int bh = 20;
        int bx = x + w - bw;
        int by = y + 4;

        GraphicsPath bp;
        addRoundRect(bp, (REAL)bx, (REAL)by, (REAL)bw, (REAL)bh, 10.0f);
        SolidBrush bgBr(Color(55, gpClr(Clr::ACCENT).GetR(),
                                  gpClr(Clr::ACCENT).GetG(),
                                  gpClr(Clr::ACCENT).GetB()));
        g.FillPath(&bgBr, &bp);
        Pen bp1(gpClr(Clr::ACCENT, 180), 1.0f);
        g.DrawPath(&bp1, &bp);

        SolidBrush txtBr(gpClr(Clr::ACCENTLT));
        StringFormat fmt;
        fmt.SetAlignment(StringAlignmentCenter);
        fmt.SetLineAlignment(StringAlignmentCenter);
        g.DrawString(badge, -1, &bF,
            RectF((REAL)bx, (REAL)by - 1, (REAL)bw, (REAL)bh), &fmt, &txtBr);
    }
}

void drawContent(HDC dc, int W, int H) {
    Graphics g(dc);
    g.SetSmoothingMode(SmoothingModeAntiAlias);
    g.SetTextRenderingHint(TextRenderingHintClearTypeGridFit);

    int contentX = SIDEBAR_W + CONTENT_PAD;
    int contentW = W - SIDEBAR_W - CONTENT_PAD * 2;

    int innerH = H - CONTENT_PAD * 2;
    int splitH = innerH - SECTION_H * 2 - 4 - 4 - 14;
    int listH = splitH * 6 / 10;

    drawSectionHeader(g, contentX, CONTENT_PAD, contentW,
        L"Process Targets", nullptr);

    int logHdrY = CONTENT_PAD + SECTION_H + 4 + listH + 14;
    drawSectionHeader(g, contentX, logHdrY, contentW, L"Log Output", nullptr);
}

void layoutChildren(HWND hwnd) {
    RECT rc; GetClientRect(hwnd, &rc);
    int W = rc.right, H = rc.bottom;

    int btnX = (SIDEBAR_W - BTN_W) / 2;
    int btnY = BTN_TOP_PAD;

    MoveWindow(GetDlgItem(hwnd, IDC_INJECT), btnX, btnY, BTN_W, BTN_INJECT_H, TRUE);
    btnY += BTN_INJECT_H + BTN_GAP + 6;

    MoveWindow(GetDlgItem(hwnd, IDC_REFRESH), btnX, btnY, BTN_W, BTN_H, TRUE);

    int contentX = SIDEBAR_W + CONTENT_PAD;
    int contentW = W - SIDEBAR_W - CONTENT_PAD * 2;
    if (contentW < 100) contentW = 100;

    int innerH = H - CONTENT_PAD * 2;
    int splitH = innerH - SECTION_H * 2 - 4 - 4 - 14;
    if (splitH < 100) splitH = 100;
    int listH = splitH * 6 / 10;
    int logH  = splitH - listH;

    int y = CONTENT_PAD + SECTION_H + 4;
    MoveWindow(gList, contentX, y, contentW, listH, TRUE);
    y += listH + 14 + SECTION_H + 4;
    MoveWindow(gLog, contentX, y, contentW, logH, TRUE);

    fitLastColumn();
}

LRESULT CALLBACK wndProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
    switch (msg) {
        case WM_CREATE: {
            INITCOMMONCONTROLSEX ic{ sizeof(ic), ICC_LISTVIEW_CLASSES | ICC_STANDARD_CLASSES };
            InitCommonControlsEx(&ic);

            BOOL dark = TRUE;
            DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, &dark, sizeof(dark));
            int cornerPref = DWMWCP_ROUND;
            DwmSetWindowAttribute(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE,
                                  &cornerPref, sizeof(cornerPref));
            COLORREF cap = Clr::SIDEBAR;
            DwmSetWindowAttribute(hwnd, DWMWA_CAPTION_COLOR, &cap, sizeof(cap));
            COLORREF brd = Clr::BORDER;
            DwmSetWindowAttribute(hwnd, DWMWA_BORDER_COLOR, &brd, sizeof(brd));

            gBrBg  = CreateSolidBrush(Clr::BG);
            gBrLog = CreateSolidBrush(Clr::LOGBG);

            HDC tmpDc = GetDC(hwnd);
            int ppy = GetDeviceCaps(tmpDc, LOGPIXELSY);
            ReleaseDC(hwnd, tmpDc);

            auto mkFont = [ppy](int pt, int weight) -> HFONT {
                return CreateFontW(
                    -MulDiv(pt, ppy, 72), 0, 0, 0, weight, FALSE, FALSE, FALSE,
                    DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
                    CLEARTYPE_QUALITY, DEFAULT_PITCH | FF_DONTCARE, L"Segoe UI");
            };
            gFont     = mkFont(9, FW_NORMAL);
            gFontTiny = mkFont(8, FW_SEMIBOLD);

            gList = CreateWindowExW(0, WC_LISTVIEWW, L"",
                WS_CHILD | WS_VISIBLE | LVS_REPORT | LVS_SINGLESEL | LVS_SHOWSELALWAYS,
                0,0,0,0, hwnd, (HMENU)IDC_PROCESS_LIST, nullptr, nullptr);
            SendMessage(gList, WM_SETFONT, (WPARAM)gFont, TRUE);
            SetWindowTheme(gList, L"", L"");

            gRowSizer = ImageList_Create(1, ROW_H, ILC_COLOR32, 0, 1);
            ListView_SetImageList(gList, gRowSizer, LVSIL_SMALL);

            setListColumns();

            HWND hdr = ListView_GetHeader(gList);
            if (hdr) {
                SetWindowTheme(hdr, L"", L"");
                SetWindowSubclass(hdr, headerSubclassProc, 0, 0);
            }

            auto mkBtn = [&](const wchar_t* label, int id) -> HWND {
                HWND b = CreateWindowW(L"BUTTON", label,
                    WS_CHILD | WS_VISIBLE | WS_TABSTOP | BS_OWNERDRAW,
                    0,0,0,0, hwnd, (HMENU)(intptr_t)id, nullptr, nullptr);
                SetWindowSubclass(b, btnSubclassProc, 0, 0);
                gBtnHover[b] = false;
                return b;
            };
            mkBtn(L"INJECT",  IDC_INJECT);
            mkBtn(L"Refresh", IDC_REFRESH);

            gLog = CreateWindowExW(0, L"EDIT", L"",
                WS_CHILD | WS_VISIBLE | WS_VSCROLL | ES_MULTILINE | ES_AUTOVSCROLL | ES_READONLY,
                0,0,0,0, hwnd, (HMENU)IDC_LOG, nullptr, nullptr);
            SendMessage(gLog, WM_SETFONT, (WPARAM)gFont, TRUE);
            SetWindowTheme(gLog, L"", L"");

            Logger::get().attachUiSink(gLog);
            LOG_I("Rain Injector ready. Select a Forge 1.8.9 process and click INJECT.");

            refreshList();
            return 0;
        }

        case WM_ERASEBKGND:
            return 1;

        case WM_PAINT: {
            PAINTSTRUCT ps;
            HDC dc = BeginPaint(hwnd, &ps);

            RECT rc; GetClientRect(hwnd, &rc);
            int W = rc.right, H = rc.bottom;

            HDC mem = CreateCompatibleDC(dc);
            HBITMAP bm = CreateCompatibleBitmap(dc, W, H);
            HBITMAP oldBm = (HBITMAP)SelectObject(mem, bm);

            {
                RECT bgR = { SIDEBAR_W, 0, W, H };
                FillRect(mem, &bgR, gBrBg);
            }

            drawSidebar(mem, H);
            drawContent(mem, W, H);

            auto outline = [&](HWND child, COLORREF clr) {
                if (!child) return;
                RECT cr; GetWindowRect(child, &cr);
                MapWindowPoints(HWND_DESKTOP, hwnd, (LPPOINT)&cr, 2);
                InflateRect(&cr, 1, 1);
                HPEN p2 = CreatePen(PS_SOLID, 1, clr);
                HPEN op = (HPEN)SelectObject(mem, p2);
                HBRUSH ob = (HBRUSH)SelectObject(mem, GetStockObject(NULL_BRUSH));
                Rectangle(mem, cr.left, cr.top, cr.right, cr.bottom);
                SelectObject(mem, op);
                SelectObject(mem, ob);
                DeleteObject(p2);
            };
            outline(gList, Clr::BORDER);
            outline(gLog,  Clr::BORDER);

            BitBlt(dc, 0, 0, W, H, mem, 0, 0, SRCCOPY);

            SelectObject(mem, oldBm);
            DeleteObject(bm);
            DeleteDC(mem);

            EndPaint(hwnd, &ps);
            return 0;
        }

        case WM_SIZE: {
            layoutChildren(hwnd);
            InvalidateRect(hwnd, nullptr, FALSE);
            return 0;
        }
        case WM_GETMINMAXINFO: {
            auto* mmi = (MINMAXINFO*)lp;
            mmi->ptMinTrackSize.x = 960;
            mmi->ptMinTrackSize.y = 600;
            return 0;
        }
        case WM_CTLCOLOREDIT:
        case WM_CTLCOLORSTATIC: {
            HDC dc = (HDC)wp;
            HWND hCtl = (HWND)lp;
            if (hCtl == gLog) {
                SetBkColor(dc, Clr::LOGBG);
                SetTextColor(dc, Clr::TEXT);
                SetBkMode(dc, OPAQUE);
                return (LRESULT)gBrLog;
            }
            return DefWindowProcW(hwnd, msg, wp, lp);
        }
        case WM_DRAWITEM: {
            auto* dis = (DRAWITEMSTRUCT*)lp;
            if (dis->CtlType == ODT_BUTTON) {
                drawButton(dis);
                return TRUE;
            }
            return 0;
        }
        case WM_COMMAND: {
            switch (LOWORD(wp)) {
                case IDC_REFRESH: refreshList(); return 0;
                case IDC_INJECT:  doInject();    return 0;
            }
            return 0;
        }
        case WM_NOTIFY: {
            auto* nm = (LPNMHDR)lp;
            if (nm->hwndFrom == gList) {
                if (nm->code == NM_DBLCLK) { doInject(); return 0; }
                if (nm->code == NM_CUSTOMDRAW)
                    return handleListCustomDraw((NMLVCUSTOMDRAW*)lp);
            }
            return 0;
        }
        case WM_DESTROY:
            if (gRowSizer) ImageList_Destroy(gRowSizer);
            if (gBrBg)     DeleteObject(gBrBg);
            if (gBrLog)    DeleteObject(gBrLog);
            if (gFont)     DeleteObject(gFont);
            if (gFontTiny) DeleteObject(gFontTiny);
            PostQuitMessage(0);
            return 0;
    }
    return DefWindowProcW(hwnd, msg, wp, lp);
}

} // namespace

int runGui(HINSTANCE hInst, int nCmdShow) {
    Gdiplus::GdiplusStartupInput gpsi;
    Gdiplus::GdiplusStartup(&gGpToken, &gpsi, nullptr);

    Logger::get().init(L"");

    WNDCLASSW wc{};
    wc.style          = CS_HREDRAW | CS_VREDRAW;
    wc.lpfnWndProc    = wndProc;
    wc.hInstance      = hInst;
    wc.hCursor        = LoadCursor(nullptr, IDC_ARROW);
    wc.hbrBackground  = nullptr;
    wc.lpszClassName  = L"RainInjectorMain";
    if (!RegisterClassW(&wc)) {
        MessageBoxW(nullptr, L"RegisterClass failed.", L"Rain Injector", MB_ICONERROR);
        return 1;
    }

    gMain = CreateWindowExW(0, wc.lpszClassName, L"Rain Injector",
        WS_OVERLAPPEDWINDOW | WS_CLIPCHILDREN,
        CW_USEDEFAULT, CW_USEDEFAULT, 1180, 720,
        nullptr, nullptr, hInst, nullptr);
    if (!gMain) {
        MessageBoxW(nullptr, L"CreateWindow failed.", L"Rain Injector", MB_ICONERROR);
        return 1;
    }
    ShowWindow(gMain, nCmdShow);
    UpdateWindow(gMain);

    MSG msg;
    while (GetMessage(&msg, nullptr, 0, 0) > 0) {
        if (IsDialogMessage(gMain, &msg)) continue;
        TranslateMessage(&msg);
        DispatchMessage(&msg);
    }

    Gdiplus::GdiplusShutdown(gGpToken);
    return (int)msg.wParam;
}

} // namespace rain
