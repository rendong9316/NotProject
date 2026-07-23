// ============================================================================
// ui_control.cpp — 窗口消息分发（WndProc）、点击处理、控件管理
// 核心职责：
//   1. 创建和维护填空题文字编辑框（EDIT 控件）
//   2. 应用深色标题栏样式（Windows 10+ DWM API）
//   3. 根据页面状态分发鼠标点击到对应的处理函数
//   4. 管理 WM_PAINT 中的双缓冲绘制
// ============================================================================

#include "ui_control.h"                                // 包含本文件的头声明，导出 WndProc 等函数符号
#include "ui_draw.h"                                   // 包含绘图函数的声明，如 DrawHomePage、DrawQuizPage 等
#include "state.h"                                     // 包含程序全局状态 g_state 的声明
#include "config.h"                                    // 包含配置文件常量的声明，如 CFG_* 系列字符串

#include <windows.h>                                   // 包含所有 Windows API 的头文件
#include <string>                                      // 包含 C++ 标准字符串类 std::wstring 的定义
#include <algorithm>                                   // 包含 STL 算法 std::min 等

// ==================== DWM API 常量定义 ====================
// 这些常量在较旧的 Windows SDK 中可能未定义，需要手动补充
#ifndef DWMWA_USE_IMMERSIVE_DARK_MODE                  // 如果 DWMWA_USE_IMMERSIVE_DARK_MODE 尚未被 SDK 定义
#define DWMWA_USE_IMMERSIVE_DARK_MODE 20               // 则手动定义为 20：启用沉浸式深色模式
#endif
#ifndef DWMWA_CAPTION_COLOR                            // 如果 DWMWA_CAPTION_COLOR 尚未被 SDK 定义
#define DWMWA_CAPTION_COLOR 35                         // 则手动定义为 35：设置标题栏颜色
#endif
#ifndef DWMWA_TEXT_COLOR                               // 如果 DWMWA_TEXT_COLOR 尚未被 SDK 定义
#define DWMWA_TEXT_COLOR 36                            // 则手动定义为 36：设置标题栏文字颜色
#endif

// ==================== GET_X/Y_LPARAM 宏 ====================
// 从 LPARAM 中提取 X 和 Y 坐标（Windows 消息中常用，如鼠标位置参数）
#ifndef GET_X_LPARAM                                   // 如果 GET_X_LPARAM 宏尚未被 SDK 定义
#define GET_X_LPARAM(lp) ((int)(short)LOWORD(lp))      // 从 lp 低字提取 X 坐标并转为 int
#endif
#ifndef GET_Y_LPARAM                                   // 如果 GET_Y_LPARAM 宏尚未被 SDK 定义
#define GET_Y_LPARAM(lp) ((int)(short)HIWORD(lp))      // 从 lp 高字提取 Y 坐标并转为 int
#endif

#ifndef IDI_ICON                                       // 如果窗口图标资源 ID 尚未被定义
#define IDI_ICON 101                                   // 使用自定义资源 ID 101 作为窗口图标
#endif

// 全局窗口句柄 — 其他文件可通过 extern 访问
HWND g_hwndEdit = nullptr;                            // 填空题输入框控件的窗口句柄，初始化为空指针
HFONT g_hfontEdit = nullptr;                          // 编辑框使用的字体对象句柄，初始化为空指针
HWND g_hwndMain = nullptr;                            // 主窗口的窗口句柄，初始化为空指针

// Forward declarations (defined in ui_draw.cpp)       // 前置声明（实现在 ui_draw.cpp 中）
bool Hit(int mx, int my, int x, int y, int w, int h); // 碰撞检测：判断点(mx,my)是否在矩形内
void AdjustFont(float delta);                         // 调整全局字体缩放比例的函数声明

// ============================================================================
// 编辑控件管理
// ============================================================================

/** 去除字符串首尾空白字符（空格、制表符、换行等） */
std::wstring TrimString(const std::wstring& s) {      // const std::wstring& s：接收宽字符串的常量引用，避免拷贝
    size_t a = s.find_first_not_of(L" \t\r\n");       // find_first_not_of：从左往右找到第一个非空白字符的位置索引
    size_t b = s.find_last_not_of(L" \t\r\n");        // find_last_not_of：从右往左找到第一个非空白字符的位置索引
    if (a == std::wstring::npos) return L"";          // 如果找不到非空白字符说明全是空白，返回空串
                                                         // npos 是 string::npos，表示未找到
    return s.substr(a, b - a + 1);                    // substr：提取 [a, b] 子串作为去空白后的结果
                                                         // （长度 = b - a + 1，包含两端）
}                                                     // } 结束 TrimString 函数

/** 根据当前缩放比例创建或更新编辑框的字体 */
void ApplyEditFont() {                                 // 参数：无；返回值：无
    if (!g_hwndEdit) return;                          // 如果编辑框控件还没创建过就提前返回不做任何事
                                                         // !g_hwndEdit：当句柄为 nullptr 时条件为真
    int px = (int)(18 * g_fontScale + 0.5f);          // 按缩放比计算实际像素字号
                                                         //   18 是基准大小，g_fontScale 是缩放因子（1.0~3.0）
                                                         //   +0.5f 是四舍五入技巧：int(值 + 0.5) 等价于 round()
                                                         //   (int) 是强制类型转换：将 float 转为 int（截断小数部分）
    if (g_hfontEdit) DeleteObject(g_hfontEdit);       // 如果旧字体存在就删除它，防止 GDI 对象泄漏
                                                         // DeleteObject：GDI 函数，释放指定的 GDI 对象
                                                         // 不删除会导致内存泄漏
    g_hfontEdit = CreateFontW(-px, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE,
                              DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
                              CLEARTYPE_QUALITY, FF_DONTCARE, CFG_FONT_FAMILY);
                                                         // CreateFontW：创建一个逻辑字体
                                                         //   -px：字体高度为正值时的负数（负数表示字符主体高度）
                                                         //   FW_NORMAL：字体粗细为正常（非粗体）
                                                         //   CLEARTYPE_QUALITY：清屏渲染质量（文字更清晰）
                                                         //   CFG_FONT_FAMILY：字体族名（微软雅黑）
    SendMessageW(g_hwndEdit, WM_SETFONT, (WPARAM)g_hfontEdit, TRUE);
                                                         // SendMessageW：向编辑框窗口发送消息
                                                         //   WM_SETFONT：设置字体的消息
                                                         //   (WPARAM)g_hfontEdit：强制转换为 WPARAM 类型的新字体句柄
                                                         //   TRUE：要求编辑框立即重绘以显示新字体
}                                                     // } 结束 ApplyEditFont 函数

/** 如果编辑框尚未创建则创建一个 EDIT 子控件 */
void EnsureEditControl(HWND parent) {                  // 参数 parent 是父窗口的句柄
    if (g_hwndEdit) return;                            // 如果编辑框已经存在就直接返回不做任何事
    g_hwndEdit = CreateWindowExW(WS_EX_CLIENTEDGE, L"EDIT", L"",
                                 WS_CHILD | ES_AUTOHSCROLL,
                                                         // WS_EX_CLIENTEDGE：扩展风格，带凹陷边框
                                                         // L"EDIT"：窗口类名（Windows 内置的编辑控件类）
                                                         // WS_CHILD：子窗口风格（依赖父窗口存在）
                                                         // ES_AUTOHSCROLL：水平自动滚动
                                 0, 0, 100, 30, parent, nullptr,
                                                         // 初始位置(0,0)，大小 100x30，父窗口是 parent
                                 GetModuleHandleW(nullptr), nullptr);
                                                         // 模块句柄传 nullptr 表示当前进程，菜单 ID 传 nullptr
    ApplyEditFont();                                   // 控件刚创建好立即调用上面函数设置正确字体
}                                                     // } 结束 EnsureEditControl 函数

/**
 * 根据当前程序状态更新编辑框的显示/隐藏和位置。
 * 仅在"答题页 + 填空题模式 + 未作答"时可见。
 * 已答过的题目不显示编辑框（改为显示静态文本）。
 */
void UpdateEditForState() {                             // 无参函数：根据 g_state 的状态来管理编辑框
    if (!g_hwndEdit) return;                           // 如果编辑框控件根本没创建过就跳过不执行
    bool shouldShow = g_state.page == PAGE_QUIZ        // 检查当前页面是不是答题页
                      && g_state.mode == MODE_FILL     // 并且答题模式是不是填空题模式
                      && !g_state.answered;            // 并且本题是否还没有被提交（未作答才显示输入框）
                                                         // &&：逻辑与运算符，所有条件都满足才返回 true
    if (shouldShow) {                                  // 如果所有条件都满足则显示编辑框
        ReadEditIntoState();                            // 先把编辑框里之前用户输入的文本读回到状态变量中
        UIRect r = FillEditRect();                     // 获取编辑框在当前页面应有的位置和大小的结构体
                                                         // FillEditRect() 返回 UIRect {x, y, w, h}
        SetWindowPos(g_hwndEdit, HWND_TOP,             // 调用 Win32 API 设置窗口位置和 z 顺序
                     ScaleCoord(r.x), ScaleCoord(r.y), // 将逻辑坐标乘以缩放比转为实际屏幕像素坐标
                                                         // ScaleCoord(v) = (int)(v * g_fontScale + 0.5f)
                     ScaleCoord(r.w), ScaleCoord(r.h),
                     SWP_SHOWWINDOW | SWP_NOACTIVATE); // SWP_SHOWWINDOW 显示窗口，SWP_NOACTIVATE 不抢焦点
                                                         // ShowWindow 的参数：控制窗口如何显示
        // 恢复用户之前输入的内容（方便修改）
        if (g_state.userFill.empty()) {                // 如果状态变量里的答案记录为空字符串
            SetWindowTextW(g_hwndEdit, L"");           // 就把编辑框清空（显示空白）
        } else {                                       // 否则
            SetWindowTextW(g_hwndEdit, g_state.userFill.c_str());
                                                         // c_str() 将 std::wstring 转为 const wchar_t*（C 风格字符串）
        }                                             // } 结束 else
    }                                                 // } 结束 if(shouldShow)
    else {                                             // 如果不满足显示条件（非答题页、非填空模式、或已作答）
        // 不满足条件时：清空未答题的状态数据，隐藏编辑框
        if (g_state.page != PAGE_QUIZ || !g_state.answered) {
                                                         //   !=：不等于运算符
                                                         //   ||：逻辑或运算符
                                                         //   如果不是答题页 或者 这道题还没被作答
            g_state.userFill.clear();                   // 清空答案记录变量里的内容
                                                         // clear() 是 std::wstring 成员方法，将字符串内容置为空
        }                                             // } 结束 if
        ShowWindow(g_hwndEdit, SW_HIDE);               // 无论如何隐藏掉编辑框控件（SW_HIDE = 隐藏标志）
                                                         // ShowWindow 是 Win32 API，第二个参数控制显示方式
    }                                                 // } 结束 else
}                                                     // } 结束 UpdateEditForState 函数

/** 销毁编辑控件及其字体对象（WM_DESTROY 时调用） */
void DestroyEditControl() {                            // 在程序退出时释放编辑框相关资源
    if (g_hwndEdit) {                                 // 如果编辑框窗口确实存在
        DestroyWindow(g_hwndEdit);                     // 销毁这个子窗口（向系统请求回收资源）
                                                         // DestroyWindow 会触发 WM_DESTROY/WM_NCDESTROY
        g_hwndEdit = nullptr;                         // 置空全局句柄，避免悬空指针
    }                                                 // } 结束 if(g_hwndEdit)
    if (g_hfontEdit) {                                // 如果字体对象确实存在
        DeleteObject(g_hfontEdit);                     // 删除 GDI 字体对象，释放资源
                                                         // DeleteObject 是 GDI 专用函数
        g_hfontEdit = nullptr;                         // 置空全局句柄
    }                                                 // } 结束 if(g_hfontEdit)
}                                                     // } 结束 DestroyEditControl 函数

/** 读取编辑控件中的文本到 g_state.userFill */
void ReadEditIntoState() {                             // 把 EDIT 控件里用户输入的文字读到状态变量 userFill 中
    if (!g_hwndEdit) return;                           // 编辑框不存在的话直接返回不做任何事
    int len = GetWindowTextLengthW(g_hwndEdit);       // 查询编辑框中文本的字符长度（不含结尾 \0）
                                                         // GetWindowTextLengthW 返回不包含终止 null 的长度
    if (len <= 0) { g_state.userFill.clear(); return; } // 长度为 0 或负数就清空状态变量并返回
                                                         // <= 是小于等于运算符
    std::wstring buf(len + 1, L'\0');                 // 分配一个比文本长度多 1 个字符的宽字符串缓冲区
                                                         // len + 1：留出空间给结尾的 \0 空字符
                                                         // L'\0'：用空字符填充所有位置
    int got = GetWindowTextW(g_hwndEdit, &buf[0], len + 1);
                                                         // GetWindowTextW：从窗口读取文本到 buf
                                                         //   &buf[0]：缓冲区起始地址
                                                         //   len + 1：最多读取的字符数
    if (got < 0) got = 0;                              // 如果 GetWindowText 失败（返回 -1）就读了 0 个处理
    buf.resize(got);                                   // resize() 将缓冲区截断到实际读到的字符数
    g_state.userFill = buf;                            // 把读到的字符串赋值给全局状态变量的用户填空答案字段
                                                         // =：赋值运算符，std::wstring 支持直接拷贝
}                                                     // } 结束 ReadEditIntoState 函数

// ============================================================================
// 深色标题栏 — Windows 10+ 沉浸式暗色模式
// 通过动态加载 dwmapi.dll 实现，兼容旧版 Windows（找不到则回退默认）
// ============================================================================
void ApplyTitleBarStyle(HWND hwnd) {                   // 参数 hwnd：要设置标题栏颜色的目标窗口句柄
    HMODULE dwm = LoadLibraryW(L"dwmapi.dll");        // 动态加载 dwmapi.dll 库，获取模块句柄返回给 dwm
                                                         // LoadLibraryW：运行时加载 DLL 到进程
                                                         // 如果加载失败（没有这个 DLL 或系统太老）则返回 nullptr
    if (!dwm) return;                                  // 如果加载失败就提前返回（静默回退到默认亮色标题栏）
                                                         // !dwm：如果句柄为 nullptr（假），取反后为真
    typedef HRESULT (WINAPI *DwmSetWindowAttributeFn)(HWND, DWORD, LPCVOID, DWORD);
                                                         // typedef：定义一个函数指针类型别名
                                                         // HRESULT：Windows 返回码类型（成功 = S_OK = 0）
                                                         // WINAPI：__stdcall 调用约定
                                                         // HWND, DWORD, LPCVOID, DWORD：四个参数类型
    DwmSetWindowAttributeFn setAttr = (DwmSetWindowAttributeFn)GetProcAddress(dwm, "DwmSetWindowAttribute");
                                                         // GetProcAddress：从已加载的 dwm.dll 中查找"DwmSetWindowAttribute"函数
                                                         // (DwmSetWindowAttributeFn)：强制转换为函数指针类型
                                                         // 获取不到函数指针说明系统版本过低（Win7 以下）
    if (setAttr) {                                     // 如果函数指针非空
        BOOL dark = TRUE;                              // BOOL dark = TRUE：启用深色模式
        COLORREF caption = RGB(13, 42, 86);            // RGB(r,g,b) 宏：将三个颜色分量打包成 32 位 COLORREF
        COLORREF text = RGB(255, 255, 255);            // 白色标题栏文字
        setAttr(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, &dark, sizeof(dark));
                                                         // &dark：取地址运算符 &
                                                         // sizeof：编译期运算符，返回 dark 类型的字节数
        setAttr(hwnd, DWMWA_CAPTION_COLOR, &caption, sizeof(caption));
        setAttr(hwnd, DWMWA_TEXT_COLOR, &text, sizeof(text));
                                                         // DwmSetWindowAttribute：DWM API 核心函数
                                                         // 三个调用分别设置：深色模式开关、标题栏背景色、文字颜色
    }                                                 // } 结束 if(setAttr)
    FreeLibrary(dwm);                                  // 用完即释放 dwm 库的引用（保持进程轻量）
                                                         // FreeLibrary：减少模块引用计数
}                                                     // } 结束 ApplyTitleBarStyle 函数

// ============================================================================
// 全局字体控制点击 — 右上角 A- / A+ 按钮
// ============================================================================
void HandleGlobalClick(int mx, int my) {               // 处理全局范围的点击区域（不区分页面）
    bool changed = false;                              // 标记是否有变化发生
    if (Hit(mx, my, g_w - 154, 16, 48, 32)) {        // 检查鼠标位置是否在 A- 按钮区域内
                                                         // g_w - 154：A- 按钮的左边缘 x 坐标
        AdjustFont(-0.06f);                            // 缩小字体（负数 = 缩小）
        changed = true;                                // 设置变化标记为 true
    }                                                 // } 结束 if A-
    if (Hit(mx, my, g_w - 98, 16, 48, 32)) {         // 检查鼠标位置是否在 A+ 按钮区域内
        AdjustFont(0.06f);                             // 放大字体（正数 = 放大）
        changed = true;                                // 设置变化标记为 true
    }                                                 // } 结束 if A+
    if (changed) { ApplyEditFont(); UpdateEditForState(); }
                                                         // 如果有变化：重新应用编辑框字体，更新编辑框可见性
                                                         // 这两个函数都会在字体缩放后确保编辑框显示正确
}                                                     // } 结束 HandleGlobalClick 函数

// ============================================================================
// 各页面的点击事件处理器
// ============================================================================

/** 主页点击：识别三个模式的按钮点击 */
static void HandleHomePageClick(int mx, int my, HWND hwnd) {
                                                         // static：限制此函数只在当前翻译单元内可见（降低耦合度）
    for (int i = 0; i < 3; ++i) {                      // for 循环：i 从 0 开始递增到 2（共 3 次迭代）
                                                         //   ++i 是前缀自增：先 i = i + 1 再将结果用作表达式值
        UIRect r = HomeButtonRect(i);                  // HomeButtonRect(i) 返回第 i 个按钮的矩形区域
        if (Hit(mx, my, r.x, r.y, r.w, r.h)) {        // 判断点击是否在某个模式按钮的范围内
            if (i == 2) OpenFillScoreSelect();         // 如果是第三个按钮（i == 2），打开分值选择页
                                                         // else 是前两个：i == 0 → 单选题, i == 1 → 多选题
            else StartQuiz(i == 0 ? MODE_SINGLE : MODE_MULTIPLE);
                                                         // ?: 是三元运算符（条件 ? 真值 : 假值）
                                                         //   i == 0 为真 → 用 MODE_SINGLE，为假 → 用 MODE_MULTIPLE
            UpdateEditForState();                       // 根据新页面状态更新编辑框可见性
            if (g_state.page == PAGE_QUIZ && g_state.mode == MODE_FILL && g_hwndEdit) {
                SetFocus(g_hwndEdit);                  // SetFocus：将键盘焦点聚焦到编辑控件
            }                                           // } 结束 if
            InvalidateRect(hwnd, nullptr, FALSE);       // InvalidateRect：通知窗口"客户区需要重绘"
                                                         // nullptr：重绘整个客户区
                                                         // FALSE：不擦除背景（因为我们自己绘制）
            return;                                    // 处理完毕，直接返回（break 用于跳出 for）
        }                                             // } 结束 if(Hit)
    }                                                 // } 结束 for
}                                                     // } 结束 HandleHomePageClick

/** 分值选择页点击：识别返回按钮和六个分值按钮 */
static void HandleFillScoreSelectClick(int mx, int my, HWND hwnd) {
    UIRect backRect = ReturnHomeButtonRect();          // 获取"返回主页"按钮的区域
                                                         // ReturnHomeButtonRect() 返回 UIRect 结构体 {x, y, w, h}
    if (Hit(mx, my, backRect.x, backRect.y, backRect.w, backRect.h)) {
                                                         // 判断是否点击了返回按钮
        g_state.resetQuiz(ActiveBank());               // resetQuiz 重置全部状态（计数、used 数组等）
                                                         // ActiveBank() 根据当前模式返回对应题库的引用
        g_state.page = PAGE_HOME;                      // 切换回主页
        UpdateEditForState();                          // 更新编辑框可见性
        InvalidateRect(hwnd, nullptr, FALSE);           // 触发重绘
        return;                                        // 已处理，直接返回
    }                                                 // } 结束 if(backRect)
    // 检查是否点中了某个分值按钮
    for (int i = NO_SCORE; i < FILL_SCORE_OPTION_COUNT; ++i) {
                                                         // 遍历 6 个分值按钮（NO_SCORE=0 占位，实际按钮 0~5）
        UIRect scoreRect = FillScoreButtonRect(i);     // 获取第 i 个分值按钮的区域
                                                         // FillScoreButtonRect(i) 返回 UIRect 结构体
        if (Hit(mx, my, scoreRect.x, scoreRect.y, scoreRect.w, scoreRect.h)) {
                                                         // 判断鼠标是否在这个分值按钮范围内
            StartFillQuiz(FILL_SCORE_OPTIONS[i], i);   // 启动对应分值的答题
                                                         // FILL_SCORE_OPTIONS[i]：取出分值数字（10/20/.../60）
                                                         // i：按钮索引（0~5）
            UpdateEditForState();                       // 更新编辑框可见性
            if (g_hwndEdit) SetFocus(g_hwndEdit);      // 如果有编辑框则将焦点设过去
            InvalidateRect(hwnd, nullptr, FALSE);       // 触发重绘
            return;                                    // 已处理，return 跳出
        }                                             // } 结束 if(Hit)
    }                                                 // } 结束 for
}                                                     // } 结束 HandleFillScoreSelectClick

/** 答题页点击：核心交互逻辑 — 选项选择、提交、跳转下一题 */
static void HandleQuizPageClick(int mx, int my, HWND hwnd) {
    // === 1. 返回主页按钮 ===
    UIRect backRect = ReturnHomeButtonRect();
    if (Hit(mx, my, backRect.x, backRect.y, backRect.w, backRect.h)) {
        int rc = MessageBoxW(hwnd,
            CFG_CONFIRM_MESSAGE,                       // 确认对话框内容
            CFG_CONFIRM_TITLE, MB_YESNO | MB_ICONQUESTION | MB_DEFBUTTON2);
                                                         // MB_YESNO：弹出 Yes/No 两个按钮
                                                         // MB_ICONQUESTION：问号图标
                                                         // MB_DEFBUTTON2：第二个按钮（No）是默认选中
                                                         // MessageBoxW 返回值：IDYES 或 IDCANCEL（用户点的）
        if (rc == IDYES) {                             // IDYES：宏定义，代表"Yes"按钮的返回值
            g_state.resetQuiz(ActiveBank());           // 完全重置答题状态
            g_state.page = PAGE_HOME;                  // 切换到主页
            UpdateEditForState();
            SetFocus(hwnd);                            // 焦点回到主窗口本身
        }                                             // } 结束 if(rc == IDYES)
        InvalidateRect(hwnd, nullptr, FALSE);          // 触发重绘（无论用户点哪个按钮都需要刷新）
        return;
    }                                                 // } 结束 if(backRect)

    // === 2. 限时模式超时后自动提交 ===
    if (IsTimedMode() && !g_state.answered && QuestionRemainingSeconds() <= 0) {
                                                         // && 是短路逻辑与：前面为假则不再计算后面
        SettleCurrentQuestion(hwnd, true);             // true= 表示超时而结算（而非用户主动提交）
    }                                                 // } 结束 if

    // === 3. 选项点击（仅选择题有效，填空题无选项面板） ===
    if (g_state.mode != MODE_FILL) {                   // != 不等于运算符
        for (int i = NO_SCORE; i < g_choiceOptionRectCount; ++i) {
                                                         // 遍历所有可点击的选项矩形区域
                                                         // g_choiceOptionRectCount：由 DrawQuizPage 填充的选项数量
            const UIRect& optionRect = g_choiceOptionRects[i];
                                                         // const UIRect&：常量引用（只读访问，避免拷贝）
                                                         // g_choiceOptionRects[] 在 DrawQuizPage 中被填充
            if (!g_state.answered                       // 已结算的不可再改（防误触）
                && Hit(mx, my, optionRect.x, optionRect.y,
                       optionRect.w, optionRect.h)) {  // 且鼠标点击在该选项范围内
                if (g_state.mode == MODE_SINGLE) {
                                                         // 单选题：清除所有选中，只保留当前点击的选项
                    g_state.selected[0] = g_state.selected[1] = g_state.selected[2] = g_state.selected[3] = false;
                                                         // 链式赋值：从右往左，先算最后一个赋值给 selected[2]
                    g_state.selected[i] = true;         // 将本次点击的选项设为选中
                } else {                               // 多选题
                    g_state.selected[i] = !g_state.selected[i];
                                                         // !：逻辑非运算符，true→false，false→true
                                                         // 切换选中状态（点中选变不选，点不中选变选）
                }
                InvalidateRect(hwnd, nullptr, FALSE);   // 选项选中状态变了需要重绘
                return;                                // 已处理，直接 return
            }                                         // } 结束 if(!answered && Hit)
        }                                             // } 结束 for 选项
    }                                                 // } 结束 if(mode != FILL)

    // === 4. 确认/下一题/提交按钮 ===
    UIRect confirmRect = ConfirmButtonRect();          // 获取按钮区域
    if (Hit(mx, my, confirmRect.x, confirmRect.y, confirmRect.w, confirmRect.h)) {
                                                         // 判断点击是否在按钮范围内
        if (!g_state.answered) {                       // 情况 A：用户尚未作答
            // Just confirmed — read input then settle
            if (g_state.mode == MODE_FILL) {           // 如果是填空题模式
                ReadEditIntoState();                   // 先将编辑框内容读入 g_state.userFill
                if (TrimString(g_state.userFill).empty()) {
                                                         // TrimString 去空白后判断是否为空
                    MessageBoxW(hwnd, CFG_FILL_PROMPT, CFG_DLG_TITLE, MB_OK | MB_ICONINFORMATION);
                                                         // 弹出提示："请先在输入框中填写答案"
                    return;                            // 不允许空答案提交
                }                                     // } 结束 if(empty)
            } else if (!HasSelection()) {              // 如果是选择题但没选中任何选项
                MessageBoxW(hwnd, CFG_NO_SELECTION, CFG_DLG_TITLE, MB_OK | MB_ICONINFORMATION);
                                                         // HasSelection() 遍历 selected[4] 数组检查
                return;                                // 不允许空选提交
            }                                         // } 结束 else if
            SettleCurrentQuestion(hwnd, false);        // false=非超时（用户主动提交）
            UpdateEditForState();                      // 更新编辑框可见性（答题后隐藏）
            SetFocus(hwnd);                            // 焦点回到主窗口
        } else if (g_state.qNum >= g_state.total) {   // 情况 B：已答完最后一题
                                                         // qNum 是当前题号，total 是总题数
                                                         // >= 大于等于
            g_state.quizEnd = std::chrono::system_clock::now();
                                                         // 记录结束时间
                                                         // system_clock::now()：获取当前系统时间
            g_state.page = PAGE_RESULT;                // 切换到结果页
            UpdateEditForState();
        } else {                                       // 情况 C：已答完但不是最后一题
            ++g_state.qNum;                            // qNum 加 1：下一题（++ 前缀自增）
                                                         // ++qNum 等价于 qNum = qNum + 1
            // Clear edit box content from previous question to avoid residue
            if (g_hwndEdit) SetWindowTextW(g_hwndEdit, L"");
                                                         // 清空编辑框，防止上一题的答案残留
            g_choiceOptionRectCount = 0;               // 重置选项区域缓存（下一题会重新填充）
            if (!StartQuestion()) {                    // 尝试抽取新题
                                                         // StartQuestion() 返回 false 表示没有更多可用题目
                --g_state.qNum;                        // 抽不到题：回滚计数（-- 前缀自减）
                MessageBoxW(hwnd, CFG_ERR_SINGLE_EXHAUSTED, CFG_DLG_ERR, MB_OK | MB_ICONERROR);
                                                         // 显示错误："单选题已全部抽完，请重新启动程序"
            }                                         // } 结束 if(!StartQuestion)
            UpdateEditForState();                      // 更新编辑框状态
            if (g_state.mode == MODE_FILL && g_hwndEdit) SetFocus(g_hwndEdit);
                                                         // 如果是填空题且编辑框存在，聚焦到编辑框
        }                                             // } 结束 else
        InvalidateRect(hwnd, nullptr, FALSE);          // 无论哪种情况都要重绘
    }                                                 // } 结束 if(Hit button)
}                                                     // } 结束 HandleQuizPageClick

/** 结果页点击：重新开始 / 返回主页 */
static void HandleResultPageClick(int mx, int my, HWND hwnd) {
    int cw = std::min(g_w - 80, 760), ch = 500, cx = (g_w - cw) / 2, cy = 120;
                                                         // std::min(a, b)：返回 a 和 b 中较小者
                                                         // cw：结果卡片宽度
                                                         // ch：结果卡片高度
                                                         // cx：卡片居中的 x 坐标
                                                         // cy：卡片 y 坐标
    // 左侧按钮：再次答题
    if (Hit(mx, my, cx + cw / 2 - 210, cy + ch - 82, 190, 48)) {
                                                         // cw / 2：整数除法，cw 的一半
        if (g_state.mode == MODE_FILL) OpenFillScoreSelect();
                                                         // 填空题需要先进入分值选择页
        else StartQuiz(g_state.mode);                   // 其他模式直接进入答题
        UpdateEditForState();
        if (g_state.page == PAGE_QUIZ && g_state.mode == MODE_FILL && g_hwndEdit) {
            SetFocus(g_hwndEdit);                      // 聚焦到编辑框
        }                                             // } 结束 if
    }                                                 // } 结束 if(restart button)
    // 右侧按钮：返回主页
    else if (Hit(mx, my, cx + cw / 2 + 20, cy + ch - 82, 190, 48)) {
                                                         // cw / 2 + 20：左按钮右边留 20px 空隙
        g_state.resetQuiz(ActiveBank());               // 完全重置状态
        g_state.page = PAGE_HOME;                      // 切换到主页
        UpdateEditForState();
    }                                                 // } 结束 if(home button)
    InvalidateRect(hwnd, nullptr, FALSE);              // 触发重绘
}                                                     // } 结束 HandleResultPageClick

// ============================================================================
// WndProc — Windows 窗口过程
// 处理所有发给主窗口的消息，是消息驱动的核心调度器
// ============================================================================
LRESULT CALLBACK WndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
                                                         // LRESULT：Windows 回调函数的标准返回值类型
                                                         // CALLBACK：__stdcall 调用约定宏
                                                         // HWND hwnd：消息所属的窗口句柄
                                                         // UINT msg：消息类型编号（如 WM_PAINT = 0x000F）
                                                         // WPARAM wParam：附加参数（通常按键码或鼠标修饰键）
                                                         // LPARAM lParam：附加参数（通常鼠标坐标等）
    switch (msg) {                                     // switch-case：根据消息编号分派到不同处理分支

    // ==================== WM_SETCURSOR：鼠标悬停时改变光标样式 ====================
    // 让用户知道哪些区域是可点击的（手型光标 IDC_HAND）
    case WM_SETCURSOR: {
        DWORD dwMousePos = GetMessagePos();            // GetMessagePos：获取当前鼠标屏幕坐标（打包为 DWORD）
                                                         // 高 16 位 = Y，低 16 位 = X
        short mx = (short)(GET_X_LPARAM(dwMousePos) / g_fontScale);
                                                         // (short)：截取低 16 位为有符号短整型
        short my = (short)(GET_Y_LPARAM(dwMousePos) / g_fontScale);
                                                         // GET_X/LPARAM 从 DWORD 中提取 X/Y
                                                         // 除以 g_fontScale 得到逻辑坐标（用于碰撞检测）

        // 根据当前页面检查各个可点击区域
        if (g_state.page == PAGE_HOME) {               // == 是相等比较运算符
            for (int i = 0; i < 3; ++i) {              // 遍历三个主页模式按钮
                UIRect r = HomeButtonRect(i);          // 获取第 i 个按钮的矩形
                if (Hit(mx, my, r.x, r.y, r.w, r.h)) { // 检查鼠标是否在按钮区域内
                    SetCursor(LoadCursorW(nullptr, IDC_HAND));
                                                         // SetCursor：设置当前光标形状
                                                         // LoadCursorW(nullptr, IDC_HAND)：从系统加载手型光标
                    return TRUE;                       // TRUE：告知 Windows 我们已经处理了光标设置
                }                                     // } 结束 if
            }                                         // } 结束 for
        } else if (g_state.page == PAGE_FILL_SCORE_SELECT) {
                                                         // == 是相等比较运算符（else if 链）
            UIRect backRect = ReturnHomeButtonRect();
            if (Hit(mx, my, backRect.x, backRect.y, backRect.w, backRect.h)) {
                SetCursor(LoadCursorW(nullptr, IDC_HAND));
                return TRUE;
            }                                         // } 结束 if
            for (int i = NO_SCORE; i < FILL_SCORE_OPTION_COUNT; ++i) {
                UIRect scoreRect = FillScoreButtonRect(i);
                if (Hit(mx, my, scoreRect.x, scoreRect.y, scoreRect.w, scoreRect.h)) {
                    SetCursor(LoadCursorW(nullptr, IDC_HAND));
                    return TRUE;
                }                                     // } 结束 if
            }                                         // } 结束 for
        } else if (g_state.page == PAGE_QUIZ) {
            UIRect backRect = ReturnHomeButtonRect();
            if (Hit(mx, my, backRect.x, backRect.y, backRect.w, backRect.h)) {
                SetCursor(LoadCursorW(nullptr, IDC_HAND));
                return TRUE;
            }                                         // } 结束 if
            if (g_state.mode != MODE_FILL) {           // 只显示可交互选项的手型光标
                for (int i = NO_SCORE; i < g_choiceOptionRectCount; ++i) {
                    const UIRect& optionRect = g_choiceOptionRects[i];
                    if (!g_state.answered              // && 短路：已答不可再选，无需手型
                        && Hit(mx, my, optionRect.x, optionRect.y,
                               optionRect.w, optionRect.h)) {
                        SetCursor(LoadCursorW(nullptr, IDC_HAND));
                        return TRUE;
                    }                                 // } 结束 if
                }                                     // } 结束 for
            }                                         // } 结束 if(mode != FILL)
            UIRect confirmRect = ConfirmButtonRect();
            if (Hit(mx, my, confirmRect.x, confirmRect.y, confirmRect.w, confirmRect.h)) {
                SetCursor(LoadCursorW(nullptr, IDC_HAND));
                return TRUE;
            }                                         // } 结束 if(confirm)
        } else if (g_state.page == PAGE_RESULT) {
            int cw = std::min(g_w - 80, 760), ch = 500, cx = (g_w - cw) / 2, cy = 120;
            if (Hit(mx, my, cx + cw / 2 - 210, cy + ch - 82, 190, 48)) {
                SetCursor(LoadCursorW(nullptr, IDC_HAND));
                return TRUE;
            }                                         // } 结束 if(restart)
            if (Hit(mx, my, cx + cw / 2 + 20, cy + ch - 82, 190, 48)) {
                SetCursor(LoadCursorW(nullptr, IDC_HAND));
                return TRUE;
            }                                         // } 结束 if(home)
        }                                             // } 结束 if/else-if 链
        SetCursor(LoadCursorW(nullptr, IDC_ARROW));    // 无可交互元素 → 恢复默认箭头光标
        return TRUE;                                   // 告诉 Windows 我们已处理了光标
    }                                                 // } 结束 case WM_SETCURSOR

    // ==================== WM_CREATE：窗口创建时的初始化 ====================
    case WM_CREATE: {
        g_hwndMain = hwnd;                             // 保存主窗口句柄供全局使用（ResizeBackBuffer 需要）
        ApplyTitleBarStyle(hwnd);                      // 应用深色标题栏样式
        SetTimer(hwnd, 1, 1000, nullptr);              // 创建定时器：ID=1，间隔 1000ms（1 秒）
                                                         // SetTimer 每隔一秒触发一次 WM_TIMER 消息
        EnsureEditControl(hwnd);                      // 创建填空题编辑框

        HINSTANCE hInst = GetModuleHandleW(nullptr);   // 再次获取模块实例（用于 LoadImageW）
        // 设置窗口图标（大图标和小图标），提升任务栏美观度
        HICON hBig = (HICON)LoadImageW(hInst, MAKEINTRESOURCEW(IDI_ICON), IMAGE_ICON, 32, 32, LR_DEFAULTSIZE);
                                                         // HICON：图标句柄类型
        HICON hSmall = (HICON)LoadImageW(hInst, MAKEINTRESOURCEW(IDI_ICON), IMAGE_ICON, 16, 16, LR_DEFAULTSIZE);
        if (hBig) SendMessageW(hwnd, WM_SETICON, ICON_BIG, (LPARAM)hBig);
                                                         // SendMessageW(hwnd, WM_SETICON, ...)：设置窗口图标
        if (hSmall) SendMessageW(hwnd, WM_SETICON, ICON_SMALL, (LPARAM)hSmall);
                                                         // ICON_BIG / ICON_SMALL：分别设置大图标和小图标
        return 0;                                     // 返回 0 表示创建成功（非零会导致窗口创建失败）
    }                                                 // } 结束 case WM_CREATE

    // ==================== WM_SIZE：窗口大小变化 ====================
    case WM_SIZE: {
        // 设置最小尺寸限制（防止过小导致布局崩坏）
        g_clientW = std::max(720, (int)LOWORD(lParam)); // std::max(a, b)：返回较大者
        // wParam：窗口新宽度，lParam：新高度打包为 LPARAM，LOWORD/HIWORD 提取高低 16 位
        g_clientH = std::max(560, (int)HIWORD(lParam)); // HIWORD：高 16 位，LOWORD：低 16 位
        UpdateLayoutSize();                            // 重新计算逻辑分辨率（g_w/g_h）
        ResizeBackBuffer(hwnd);                        // 调整离屏画布尺寸
        UpdateEditForState();                          // 更新编辑框位置
        return 0;                                     // 返回 0 表示已处理
    }                                                 // } 结束 case WM_SIZE

    // ==================== WM_MOUSEWHEEL：鼠标滚轮缩放字体 ====================
    case WM_MOUSEWHEEL: {
        // 只在按住 Ctrl 键时触发字体缩放（不干扰正常滚动）
        if (GetKeyState(VK_CONTROL) & 0x8000) {        // 检查 Ctrl 键是否按下
                                                         // GetKeyState：返回按键状态
                                                         // VK_CONTROL：虚拟键码，Ctrl 键
                                                         // & 是按位与运算符：0x8000 = 符号位（最高位=1 表示按下）
                                                         // &：bitwise AND — 逐位进行与操作
        short z = GET_WHEEL_DELTA_WPARAM(wParam);       // GET_WHEEL_DELTA_WPARAM：从 wParam 提取滚轮增量
                                                         // z > 0 = 向上滚，z < 0 = 向下滚
        AdjustFont(z > 0 ? 0.06f : -0.06f);           // ?: 三元运算符：条件 ? 真值 : 假值
        ApplyEditFont();                               // 同步更新编辑框字体
        UpdateEditForState();
        return 0;                                     // 消息已处理，阻止传播
        }                                             // } 结束 if(VK_CONTROL)
    }                                                 // } 结束 case WM_MOUSEWHEEL

    // ==================== WM_TIMER：定时器回调 ====================
    case WM_TIMER: {
        if (g_state.page == PAGE_QUIZ && IsTimedMode() && !g_state.answered) {
                                                         // && 全部满足才执行下面逻辑
            int remaining = QuestionRemainingSeconds(); // 获取剩余秒数
            if (remaining <= 0) {                      // <= 小于等于
                // 倒计时归零：自动结算为超时错误
                SettleCurrentQuestion(hwnd, true);
                UpdateEditForState();
                SetFocus(hwnd);
            } else if (remaining <= 5) {               // 最后 5 秒：闪烁效果
                // 闪烁效果，营造紧迫感
                static int flashTick = 0;              // static：静态局部变量，只在第一次初始化后保留值
                                                         // 这里 flashTick 跨多次定时器调用累积增长
                flashTick++;                           // 每次定时器触发加 1（++ 前缀自增）
                g_state.flashVisible = (flashTick % 2 == 0);
                                                         // %：取模运算符，flashTick % 2 在 0/1 之间交替
                // == 是相等比较
                InvalidateRect(hwnd, nullptr, FALSE);   // 触发重绘以显示闪烁效果
            } else {                                   // 最后 5 秒以上：稳定显示
                g_state.flashVisible = false;          // 确保闪烁标记关闭
                InvalidateRect(hwnd, nullptr, FALSE);
            }                                         // } 结束 else
        }                                             // } 结束 if
        return 0;                                     // 返回 0 表示已处理
    }                                                 // } 结束 case WM_TIMER

    // ==================== WM_LBUTTONDOWN：鼠标左键单击 ====================
    case WM_LBUTTONDOWN: {
        // 转换为逻辑坐标（除以缩放比）
        int mx = (int)(GET_X_LPARAM(lParam) / g_fontScale);
        int my = (int)(GET_Y_LPARAM(lParam) / g_fontScale);
        HandleGlobalClick(mx, my);                    // 先处理全局按钮（A-/A+）
                                                         // 全局按钮优先级最高，可以在任何页面点击缩放

        // 根据当前页面分发到对应的点击处理器
        switch (g_state.page) {                       // switch：根据 page 枚举值分派
            case PAGE_HOME:       HandleHomePageClick(mx, my, hwnd); break;
                                                         // case PAGE_HOME：处理主页点击
                                                         // break：跳出 switch（防止 fall-through 到下一个 case）
            case PAGE_FILL_SCORE_SELECT: HandleFillScoreSelectClick(mx, my, hwnd); break;
            case PAGE_QUIZ:      HandleQuizPageClick(mx, my, hwnd); break;
            case PAGE_RESULT:    HandleResultPageClick(mx, my, hwnd); break;
                                                         // break：每个 case 的结束点，防止 fall-through
        }                                             // } 结束 switch
        return 0;                                     // 已处理
    }                                                 // } 结束 case WM_LBUTTONDOWN

    // ==================== WM_PAINT：屏幕绘制 ====================
    case WM_PAINT: {
        PAINTSTRUCT ps;                              // PAINTSTRUCT：存储绘制信息的结构体
        HDC hdc = BeginPaint(hwnd, &ps);             // BeginPaint：开始绘制，获取屏幕 DC
        RECT rc;                                     // RECT：矩形结构体 {left, top, right, bottom}
        GetClientRect(hwnd, &rc);                    // GetClientRect：获取客户区矩形
        int clientW = rc.right - rc.left,            // rc.right - rc.left：右边缘 - 左边缘 = 宽度
            clientH = rc.bottom - rc.top;            // rc.bottom - rc.top：下边缘 - 上边缘 = 高度

        // 如果离屏画布尚未创建或需要调整，则初始化
        if (!g_hdcMem) {
            g_hdcMem = CreateCompatibleDC(hdc);      // CreateCompatibleDC：创建与屏幕兼容的内存 DC
            g_hbmMem = CreateCompatibleBitmap(hdc, clientW, clientH);
                                                         // CreateCompatibleBitmap：创建兼容位图
            SelectObject(g_hdcMem, g_hbmMem);        // SelectObject：将位图选入 DC（作为绘制目标）
                                                         // 之后所有对 hdcMem 的绘图都会画到位图上
        }                                             // } 结束 if

        // 在离屏 DC 上绘图（避免闪烁），使用 GDI+ Graphics 对象
        Graphics memGfx(g_hdcMem);                   // Graphics(memGfx)：封装 HDC 为 GDI+ 绘图上下文
        memGfx.SetSmoothingMode(SmoothingModeAntiAlias);
                                                         // AntiAlias：抗锯齿（线条平滑）
        memGfx.SetTextRenderingHint(TextRenderingHintClearTypeGridFit);
                                                         // ClearTypeGridFit：ClearType 清屏渲染（中文更清晰）
        memGfx.SetCompositingQuality(CompositingQualityHighQuality);
                                                         // HighQuality：高质量混合
        memGfx.ScaleTransform(g_fontScale, g_fontScale);
                                                         // ScaleTransform：全局缩放——绘图坐标系放大 fontScale 倍
                                                         // 因为我们的布局计算都在逻辑像素上，放大后自然适配高分辨率

        // 根据当前页面绘制对应内容
        if (g_state.page == PAGE_HOME) DrawHomePage(memGfx);
        else if (g_state.page == PAGE_FILL_SCORE_SELECT) DrawFillScoreSelectPage(memGfx);
        else if (g_state.page == PAGE_QUIZ) DrawQuizPage(memGfx);
        else DrawResultPage(memGfx);

        // 将离屏画布一次性拷贝到屏幕上（双缓冲，消除闪烁）
        BitBlt(hdc, 0, 0, clientW, clientH, g_hdcMem, 0, 0, SRCCOPY);
                                                         // BitBlt：Bit Block Transfer — 位块传输
                                                         // 将 g_hdcMem 的 (0,0)-(clientW,clientH) 区域拷贝到 hdc
                                                         // SRCCOPY：复制模式（直接覆盖）
        EndPaint(hwnd, &ps);                           // EndPaint：结束绘制，释放资源
        return 0;                                     // 返回 0 表示已处理
    }                                                 // } 结束 case WM_PAINT

    // ==================== WM_ERASEBKGND：阻止默认背景擦除 ====================
    // 返回 1 告诉系统不要擦除背景
    case WM_ERASEBKGND:
        return 1;                                    // 返回 1：告诉 Windows 不要擦除背景

    // ==================== WM_DESTROY：程序退出清理 ====================
    case WM_DESTROY: {
        KillTimer(hwnd, 1);                          // KillTimer：销毁 ID 为 1 的定时器
                                                         // 防止定时器继续触发 WM_TIMER 消息
        DestroyEditControl();                        // 销毁编辑框和释放字体
        // 释放离屏画布资源
        if (g_hdcMem) {                              // 如果内存 DC 存在
            DeleteObject(g_hbmMem);                  // 删除内存位图
            DeleteDC(g_hdcMem);                      // 删除内存 DC
        }                                             // } 结束 if
        PostQuitMessage(0);                          // PostQuitMessage：发送 WM_QUIT 消息
                                                         // GetMessage 返回 0 时会退出 while 循环
                                                         // 0：退出码（成功退出）
        return 0;                                     // 返回 0 表示已处理
    }                                                 // } 结束 case WM_DESTROY

    default:
        break;                                       // break：跳出 switch，继续往下走到 DefWindowProcW

    }                                                 // } 结束 switch(msg)

    // 未处理的消息交给 Windows 默认处理
    return DefWindowProcW(hwnd, msg, wParam, lParam); // DefWindowProcW：调用 Windows 默认窗口过程
                                                         // 处理我们没有显式处理的消息（移动窗口、缩放等）
}                                                     // } 结束 WndProc 函数
