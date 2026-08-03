#pragma once

#include <windows.h>

#define CLR_BG_PAGE           RGB(246,248,250)
#define CLR_BG_SURFACE        RGB(255,255,255)
#define CLR_BG_HOVER          RGB(243,244,246)
#define CLR_BORDER            RGB(208,215,222)
#define CLR_TEXT_PRIMARY      RGB(31,35,40)
#define CLR_TEXT_SECONDARY    RGB(87,96,106)
#define CLR_TEXT_TERTIARY     RGB(110,119,129)
#define CLR_GREEN_0           RGB(235,237,240)
#define CLR_GREEN_1           RGB(155,233,168)
#define CLR_GREEN_2           RGB(64,196,99)
#define CLR_GREEN_3           RGB(48,161,78)
#define CLR_GREEN_4           RGB(33,110,57)
#define CLR_ACCENT            RGB(31,136,61)
#define CLR_DANGER_BG         RGB(255,235,233)
#define CLR_DANGER_TEXT       RGB(207,34,46)
#define CLR_SUCCESS_BG        RGB(218,251,225)
#define CLR_SUCCESS_TEXT      RGB(17,99,41)

#define CLR_DARK_BG_PAGE      RGB(13,17,23)
#define CLR_DARK_BG_SURFACE   RGB(22,27,34)
#define CLR_DARK_BG_HOVER     RGB(33,38,45)
#define CLR_DARK_BORDER       RGB(48,54,61)
#define CLR_DARK_TEXT_PRIMARY RGB(230,237,243)
#define CLR_DARK_TEXT_SEC     RGB(139,148,158)
#define CLR_DARK_GREEN_0      RGB(22,27,34)
#define CLR_DARK_GREEN_1      RGB(14,68,41)
#define CLR_DARK_GREEN_2      RGB(0,109,50)
#define CLR_DARK_GREEN_3      RGB(38,166,65)
#define CLR_DARK_GREEN_4      RGB(57,211,83)
#define CLR_DARK_ACCENT       RGB(63,185,80)

#define APP_CLASS             L"GitLocalWindow"
#define APP_TITLE             L"Git Local"
#define IDI_ICON              101
#define IDC_SEARCH            2001
#define WM_APP_OPERATION_DONE (WM_APP + 1)
#define WM_APP_PROGRESS       (WM_APP + 2)

#define TOPBAR_H              58
#define SIDEBAR_W             250
#define CONTENT_PAD           28
#define DAY_SIZE              12
#define DAY_GAP               3
#define FONT_FAMILY           L"Microsoft YaHei UI"
