// ============================================================================
// entry.cpp — Windows 桌面程序入口点
// 负责：初始化随机数种子、设置 DPI 感知、加载三个题库资源、
//      注册窗口类、创建主窗口、启动消息循环、释放 GDI+ 资源
// ============================================================================

#include "state.h"                                     // 包含 state.h：全局状态变量、页面枚举、Q&A 结构
#include "ui_draw.h"                                   // 包含 ui_draw.h：绘图函数声明（DrawHomePage 等）
#include "ui_control.h"                                // 包含 ui_control.h：WndProc 函数声明
#include "config.h"                                    // 包含 config.h：CFG_* 常量、RES_ID_* 资源编号

#include <windows.h>                                   // 引入 Windows API 头文件（HWND、CreateWindowExW 等）
#include <gdiplus.h>                                   // 引入 GDI+ 头文件（Graphics、SolidBrush 等绘图类）
#include <random>                                      // 引入 C++11 random 库（std::mt19937 随机数引擎）

#ifndef IDI_ICON                                        // #ifndef：如果 IDI_ICON 宏还没被定义过
#define IDI_ICON 101                                   //   则定义默认图标资源编号 101（可通过编译器 -D 覆盖）
#endif                                                 // #endif：条件编译结束

using namespace Gdiplus;                              // 使用 Gdiplus 命名空间：后续 GDI+ 类型无需加 Gdiplus:: 前缀

int main() {                                           // int main()：标准 C++ 入口函数（GUI 程序等价于 WinMain）

    // 初始化随机数引擎，确保每次运行抽题序列不同
    std::random_device rd;                            //   std::random_device：C++11 非确定性随机数生成器
                                                         //     从操作系统底层获取熵（硬件噪声、中断时间差等不可预测数据）
    rng.seed(rd());                                   //   rng 是全局 std::mt19937 引擎（梅森旋转算法）
                                                         //     rd() 调用运算符重载生成随机种子，seed() 将其注入引擎
                                                         //     这样每次程序启动，抽奖序列都是不同的

    // 设置 DPI 感知（Windows Vista+），避免高分辨率下窗口模糊
    HMODULE user32 = GetModuleHandleW(L"user32.dll"); //   GetModuleHandleW：获取 user32.dll 的模块基址（返回 HMODULE）
                                                         //   L 前缀表示宽字符字符串（Unicode）
    if (user32) {                                     //   if(user32)：判断是否成功获取到模块句柄（非空即真）
        typedef BOOL (WINAPI *SetDpiAwareFn)();       //   typedef：定义函数指针类型 SetDpiAwareFn
                                                         //     BOOL 表示返回值类型为布尔型（长整型）
                                                         //     WINAPI 是 __stdcall 调用约定
                                                         //     () 表示不接受参数
        SetDpiAwareFn setDpiAware = (SetDpiAwareFn)GetProcAddress(user32, "SetProcessDPIAware");
                                                         //   GetProcAddress：从 user32.dll 查找导出函数"SetProcessDPIAware"的地址
                                                         //   (SetDpiAwareFn)：强制类型转换，将通用指针转为函数指针类型
                                                         //   SetProcessDPIAware：通知 Windows 本进程已感知 DPI 变化，
                                                         //     防止系统在高分辨率屏幕上自动缩放窗口导致模糊
        if (setDpiAware) setDpiAware();               //   if(setDpiAware)：如果函数指针非空（函数存在），则调用它
                                                         //     简写形式，等价于：if (setDpiAware != nullptr) { setDpiAware(); }
    }                                                 // } 结束 if(user32) 块

    // 加载三个题库资源（单��、多选、填空）
    std::wstring error;                               //   std::wstring error：宽字符字符串变量，用于接收错误信息
                                                         //   如果 LoadQuestionBank 失败，error 会被填充错误描述
    if (!LoadQuestionBank(RES_ID_SINGLE, MODE_SINGLE, g_banks[0], error) || // || 是短路逻辑或运算符
                                                         //   !LoadQuestionBank(...)：取反，true 表示"加载失败"
                                                         //   || 保证第一个为 true 时不再计算后面的
        !LoadQuestionBank(RES_ID_MULTIPLE, MODE_MULTIPLE, g_banks[1], error) ||
        !LoadQuestionBank(RES_ID_FILL, MODE_FILL, g_banks[2], error)) { // g_banks[0/1/2]：三个全局题库数组
                                                         //   RES_ID_SINGLE = 2001：单选题题库的资源编号
        MessageBoxW(nullptr, error.c_str(), CFG_ERR_LOAD_FAILED, MB_OK | MB_ICONERROR);
                                                         //   MessageBoxW：弹出标准 Windows 对话框
                                                         //     nullptr：没有父窗口
                                                         //     error.c_str()：std::wstring 转 C 风格字符串
                                                         //     MB_OK | MB_ICONERROR：按钮 + 错误图标标志位
        return 1;                                     //   return 1：返回退出码 1（约定 0=成功，非 0=失败）
    }                                                 // } 结束 if 块
    g_state.page = PAGE_HOME;                         //   初始页面设置为"主页"（PAGE_HOME = 0）

    // ==================== 初始化 GDI+ 运行时 ====================
    GdiplusStartupInput gdiplusStartupInput;          //   GdiplusStartupInput：GDI+ 初始化输入结构
                                                         //     空结构体意味着全部使用默认配置
    ULONG_PTR gdiplusToken;                           //   ULONG_PTR：平台无关的无符号整数类型（32位/64位适配）
                                                         //     gdiplusToken：GDI+ 返回的令牌，Shutdown 时需原样传回
    GdiplusStartup(&gdiplusToken, &gdiplusStartupInput, nullptr);
                                                         //   GdiplusStartup：初始化 GDI+ 运行时
                                                         //     &gdiplusToken：输出参数地址，调用后包含 token 值
                                                         //     &gdiplusStartupInput：输入参数地址
                                                         //     nullptr：不需要接收版本信息回调

    // ==================== 注册 Windows 窗口类 ====================
    WNDCLASSEXW wc = {};                              //   WNDCLASSEXW：扩展窗口类结构体（W = Wide Unicode）
                                                         //   ={}：聚合初始化，将所有成员置为零值
    wc.cbSize = sizeof(WNDCLASSEXW);                  //   sizeof：编译期运算符，返回结构体占用字节数
                                                         //     告诉 Windows API 当前使用的是哪个版本的 WNDCLASSEX 结构体
    wc.lpfnWndProc = WndProc;                         //   lpfnWndProc：窗口过程回调函数
                                                         //     每当窗口收到消息（鼠标点击、键盘按下、重绘等），Windows 会调用此函数
    wc.hInstance = GetModuleHandleW(nullptr);         //   hInstance：当前进程模块实例句柄
                                                         //     用于后续 LoadImageW 等资源定位
    HINSTANCE hInst = GetModuleHandleW(nullptr);      //   HINSTANCE hInst：别名（与 hInstance 相同值）
    wc.hCursor = LoadCursorW(hInst, IDC_ARROW);       //   LoadCursorW：从系统加载光标资源（IDC_ARROW = 默认箭头）
    wc.hIcon = (HICON)LoadImageW(hInst, MAKEINTRESOURCEW(IDI_ICON), IMAGE_ICON, 32, 32, LR_DEFAULTSIZE);
                                                         //   LoadImageW：从资源中加载图标
                                                         //   IMAGE_ICON：要加载的资源类型
                                                         //   32, 32：图标尺寸 32x32 像素
                                                         //   LR_DEFAULTSIZE：使用默认尺寸
    wc.hIconSm = (HICON)LoadImageW(hInst, MAKEINTRESOURCEW(IDI_ICON), IMAGE_ICON, 16, 16, LR_DEFAULTSIZE);
                                                         //   hIconSm：小图标（任务栏图标，16x16 像素）
    wc.hbrBackground = (HBRUSH)(COLOR_WINDOW + 1);    //   hbrBackground：背景画刷
                                                         //     COLOR_WINDOW + 1：系统窗口背景色
    wc.lpszClassName = CFG_CLASS_NAME;                //   lpszClassName：窗口类名（宽字符字符串）
    RegisterClassExW(&wc);                            //   RegisterClassExW：向 Windows 注册窗口类

    // ==================== 创建主窗口 ====================
    HWND hwnd = CreateWindowExW(0, CFG_CLASS_NAME, CFG_APP_TITLE, WS_OVERLAPPEDWINDOW, CW_USEDEFAULT, CW_USEDEFAULT, INITIAL_WINDOW_W, INITIAL_WINDOW_H, nullptr, nullptr, GetModuleHandleW(nullptr), nullptr);
                                                         //   CreateWindowExW：创建带有扩展风格的窗口
                                                         //   0：无扩展风格
                                                         //   CFG_CLASS_NAME：类名（与 RegisterClassExW 中的名字匹配）
                                                         //   CFG_APP_TITLE：窗口标题（显示在标题栏上）
                                                         //   WS_OVERLAPPEDWINDOW：标准窗口样式（可拖动、可缩放、有关闭按钮）
                                                         //   CW_USEDEFAULT：让系统自动选择 X/Y 位置
                                                         //   INITIAL_WINDOW_W/H：初始宽高
                                                         //   最后四个 nullptr：无子窗口 ID、无菜单、无额外参数
    if (!hwnd) {                                      //   if(!hwnd)：如果窗口创建失败（nullptr 为假）
        GdiplusShutdown(gdiplusToken);                //     先关闭 GDI+（防止泄漏）
        return 1;                                     //     返回退出码 1
    }                                                 // } 结束 if 块

    ShowWindow(hwnd, SW_MAXIMIZE);                    //   ShowWindow：显示窗口，SW_MAXIMIZE 最大化
    UpdateWindow(hwnd);                               //   UpdateWindow：发送 WM_PAINT 消息触发首次绘制

    // ==================== 消息循环 ====================
    MSG msg;                                          //   MSG 结构体：存储 Windows 消息（类型、按键、鼠标位置等）
    while (GetMessageW(&msg, nullptr, 0, 0)) {        //   GetMessageW：从消息队列取一条消息放入 msg 结构体
                                                         //     返回 > 0：有消息，继续循环
                                                         //     返回 0：收到 WM_QUIT，退出循环（程序终止）
                                                         //     返回 -1：出错（理论上不会发生）
        TranslateMessage(&msg);                       //     TranslateMessage：将虚拟键消息转换为字符消息（用于 WM_CHAR）
        DispatchMessage(&msg);                        //     DispatchMessage：将消息分发给 WndProc 处理
    }                                                 // } 结束 while 循环

    // ==================== 清理 GDI+ 资源 ====================
    GdiplusShutdown(gdiplusToken);                    //   关闭 GDI+ 运行时（与 Startup 配对）
    return 0;                                         //   return 0：正常退出，向操作系统返回成功码
}                                                     // } 结束 main 函数
