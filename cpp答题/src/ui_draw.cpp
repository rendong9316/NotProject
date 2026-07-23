// ============================================================================
// ui_draw.cpp — 所有页面和组件的绘制实现
// 使用 GDI+ Graphics 进行绘图，所有坐标单位为逻辑像素（非物理像素）
// 通过 g_fontScale 缩放比，将布局系数统一为逻辑值，WM_PAINT 时整体放大到物理像素
// ============================================================================

#include "ui_draw.h"                                   // 包含本文件的头声明
#include "state.h"                                     // 引入答题状态管理模块，提供 g_state 等全局状态变量
#include "config.h"                                    // 引入配置常量模块，提供 CFG_XXX / CLR_XXX 等宏定义
#include <algorithm>                                   // 包含 C++ 标准库算法，如 std::max、std::find 等
#include <cmath>                                       // 包含数学函数库，如 std::ceil（向上取整）

// ============================================================================
// 全局绘图状态 — 在 ui_draw.cpp 中定义，其他文件通过 extern 引用
// ============================================================================

int g_clientW = 2440, g_clientH = 1760;               // 窗口客户区物理像素尺寸，默认值为 2440x1760 像素
int g_w = 2440, g_h = 1760;                          // 逻辑像素尺寸（客户端尺寸 ÷ g_fontScale），用于所有内部坐标计算
                                                         // g_w/g_h = g_clientW/g_clientH / g_fontScale
HDC g_hdcMem = nullptr;                               // 离屏内存 DC（双缓冲用），避免屏幕闪烁
HBITMAP g_hbmMem = nullptr;                           // 离屏位图（双缓冲用），画完再一次性拷贝到屏幕上
float g_fontScale = 2.0f;                             // 字体/布局缩放比例，默认 2.0x（大屏设计）
                                                         // 用户可通过 A+/A- 按钮或 Ctrl+滚轮调整范围 [1.0, 3.0]

UIRect g_choiceOptionRects[CHOICE_OPTION_COUNT];      // 选项可点击区域缓存，记录每个选项矩形的 x/y/w/h
int g_choiceOptionRectCount = 0;                      // 已填充的区域数量（由 DrawQuizPage 在绘制时设置）

// ============================================================================
// 布局辅助函数
// ============================================================================

/**
 * 根据窗口客户区大小和缩放比更新逻辑分辨率。
 * g_w = max(720, clientW / fontScale)，确保最小逻辑尺寸可用
 */
void UpdateLayoutSize() {           // 更新布局尺寸的函数入口
    g_w = std::max(720, (int)(g_clientW / g_fontScale));  // 逻辑宽度 = 物理宽度 ÷ 缩放比，向下取整后与 720 取较大值
                                                         // std::max(a,b)：返回较大者（保证最小宽 720）
    g_h = std::max(560, (int)(g_clientH / g_fontScale));  // 同理计算逻辑高度，下限 560
}                                                   // 函数体结束

/** 将逻辑坐标缩放到物理像素坐标（用于 SetWindowPos 等 Win32 API）*/
int ScaleCoord(int v) {             // 缩放函数：接收一个逻辑坐标值 v
    return (int)(v * g_fontScale + 0.5f);  // 乘以缩放比并四舍五入（+0.5f 后截断小数）转换为 int
}                                                     // 返回物理像素值

/**
 * 调整离屏画布尺寸以匹配当前窗口客户区。
 * 先删除旧的 DC 和位图，然后让 WM_PAINT 自动创建新的。
 */
void ResizeBackBuffer(HWND hwnd) {  // 接收主窗口句柄，用于后续重绘通知
    if (g_hdcMem) {                 // 如果已有旧的记忆化 DC（非空指针）
        DeleteObject(g_hbmMem);     // 先删除旧的内存位图对象，释放 GDI 资源
        DeleteDC(g_hdcMem);         // 再删除旧的记忆化设备上下文，防止内存泄漏
                                                         // DeleteDC/DeleteObject 是 GDI 专用清理函数
        g_hdcMem = nullptr;         // 清空 DC 指针，标记为无
        g_hbmMem = nullptr;         // 清空位图指针，标记为无
    }
    InvalidateRect(hwnd, nullptr, FALSE);          // 标记整个客户区需要重绘，FALSE 表示不擦除背景
                                                         // InvalidateRect 会触发 WM_PAINT 消息
}                                                   // 下一次 WM_PAINT 时会重建 DC 和位图

// ============================================================================
// 点击检测与字体缩放
// ============================================================================

/**
 * 检测鼠标坐标是否在矩形区域内
 * @param mx,my 鼠标点击的逻辑坐标
 * @param x,y,w,h 矩形的左上角和宽高（逻辑坐标）
 * @return true 表示点在矩形内
 */
bool Hit(int mx, int my, int x, int y, int w, int h) {  // 矩形碰撞检测函数入口
    return mx >= x && mx <= x + w && my >= y && my <= y + h;
                                                         // && 表示"且"：x 方向在 [x, x+w] 范围内 且 y 方向在 [y, y+h] 范围内
}                                                     // 若两个条件同时满足则返回 true，否则返回 false

/**
 * 调整全局字体缩放比例。
 * @param delta 变化量（正数放大，负数缩小）
 * 范围限制在 [1.0, 3.0] 之间
 */
void AdjustFont(float delta) {      // delta 为正时放大字体，为负时缩小字体
    g_fontScale += delta;           // 将变化量累加到全局缩放比例上
                                                         // += 复合赋值运算符，等价于 g_fontScale = g_fontScale + delta
    if (g_fontScale < 1.0f) g_fontScale = 1.0f;  // 下限钳制：如果小于 1.0 则设为 1.0（最小一倍）
    if (g_fontScale > 3.0f) g_fontScale = 3.0f;  // 上限钳制：如果大于 3.0 则设为 3.0（最大三倍）
    UpdateLayoutSize();             // 缩放比例变了之后，重新计算逻辑分辨率 g_w/g_h
    ResizeBackBuffer(g_hwndMain);   // 通知主窗口重新分配画布，触发 WM_PAINT 刷新界面
}                                                   // A-/A+ 按钮点击此响应

// ============================================================================
// 颜色转换与路径绘制工具
// ============================================================================

/** COLORREF → GDI+ Color，Alpha 通道固定为 255（不透明）*/
Color GdiColor(COLORREF cr) { return Color(255, GetRValue(cr), GetGValue(cr), GetBValue(cr)); }
// ^^^^^^^^^^^^^^^ 定义 GdiColor 函数，传入 Windows COLORREF（32位颜色值）
// ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ 返回值是 GDI+ Color 对象（GDI+ 使用的颜色类型）
// ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Color 构造函数：四个参数分别为 alpha、red、green、blue
// ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Alpha=255 表示完全不透明
// ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ GetRValue 从 COLORREF 提取红色分量
// ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ GetGValue 提取绿色分量
// ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ GetBValue 提取蓝色分量

/**
 * 构建带圆角的 GraphicsPath。
 * 通过四条弧线（Arc）连接四个直角，模拟圆角矩形效果。
 * 每条弧线参数：(x, y, width, height, startAngle, sweepAngle)
 * 例如左上角：从 180° 画 90° 弧，正好是一个四分之一椭圆
 */
void AddRoundRect(GraphicsPath& path, float x, float y, float w, float h, float r) {
    // ^^ 函数签名分解：返回值 void；形参 GraphicsPath& path 是通过引用传递的路径对象（& 表示按引用传递，避免拷贝）
    // ^^ 形参 x,y,w,h 分别是矩形左上角坐标和宽高；r 是圆角半径
    path.AddArc(x, y, r * 2, r * 2, 180, 90);              // 绘制左上角圆弧：外接矩形正方形边长为 r*2，起始角度 180°，扫过 90°
    path.AddArc(x + w - r * 2, y, r * 2, r * 2, 270, 90);  // 绘制右上角圆弧：x 偏移 w-r*2，起始角 270°，顺时针扫 90°
    path.AddArc(x + w - r * 2, y + h - r * 2, r * 2, r * 2, 0, 90);       // 绘制右下角圆弧：x 偏移 w-r*2，y 偏移 h-r*2，起始角 0°
    path.AddArc(x, y + h - r * 2, r * 2, r * 2, 90, 90);    // 绘制左下角圆弧：x 回到左边缘，y 偏移 h-r*2，起始角 90°
    path.CloseFigure();                                       // 闭合路径：用直线连接起点和终点，形成完整封闭轮廓
}                                             // }} 完成：四个 Arc + CloseFigure 构建一个完整的圆角矩形路径

/** 填充圆角矩形（无边框）*/
void FillRoundRect(Graphics& g, float x, float y, float w, float h, float r, Color fillColor) {
    // ^^ Graphics& g 通过引用传入绘图上下文；fillColor 是要填充的颜色对象
    GraphicsPath path;          // 声明并创建一个空的 GraphicsPath 对象，用于存储路径数据
    AddRoundRect(path, x, y, w, h, r);  // 调用上面定义的 AddRoundRect，向 path 中添加圆角矩形轮廓
    SolidBrush br(fillColor);   // 创建实心刷子（SolidBrush）对象，用指定颜色填充图形
                                                         // SolidBrush：GDI+ 用于纯色填充的绘图工具
    g.FillPath(&br, &path);     // 调用 Graphics::FillPath，&br 是刷子指针，&path 是路径指针（-> 运算符访问对象成员）
}
// ^^ 完成：path 通过值传递被填充，绘制结束后 br 和 path 自动析构

/** 填充圆角矩形并绘制边框 */
void FillRoundRectBorder(Graphics& g, float x, float y, float w, float h, float r,
                         Color fillColor, Color borderColor, float penW) {
    // ^^ penW 是画笔宽度（实数），borderColor 是边框颜色
    FillRoundRect(g, x, y, w, h, r, fillColor);  // 先调用无框版本填充底色
    GraphicsPath path;          // 再次创建新路径（前面的路径已在 FillRoundRect 内析构）
    AddRoundRect(path, x, y, w, h, r);  // 将圆角矩形轮廓加入路径
    Pen pen(borderColor, penW);  // 创建画笔对象（Pen），构造函数参数：颜色和线宽
                                                         // Pen：GDI+ 用于绘制线条的工具
    g.DrawPath(&pen, &path);    // 调用 DrawPath，&pen 是指针，&path 是路径指针，沿路径轮廓绘制线条
}
// ^^ 组合完成：先填色再描边，两层操作叠加得到带边框的圆角矩形

/**
 * 绘制卡片效果 — 一个带阴影的卡片。
 * 实现方式：先绘制一个偏移 4px、下移 6px 的深色矩形作为阴影，
 * 再在正确位置绘制白底卡片。两层叠加形成浮起感。
 */
void DrawCard(Graphics& g, float x, float y, float w, float h, float r) {
    FillRoundRect(g, x + 4, y + 6, w, h, r, Color(28, 25, 45, 80));  // 阴影层：向右偏移 4px，向下偏移 6px，Alpha=28 半透明深色
    // ^^^ Color(alpha, red, green, blue)：Alpha 在前，这是 GDI+ Color 的重载构造函数
    FillRoundRectBorder(g, x, y, w, h, r, GdiColor(CLR_PANEL),      // 卡片本体：从 (x,y) 开始无偏移，颜色来自配置的 CLR_PANEL
                        GdiColor(CLR_LINE), 1.2f);                   // 边框颜色用 CLR_LINE（浅灰色），线宽 1.2 像素
}
// ^^ 阴影在前、本体在后，阴影被本体遮挡一部分，自然产生深度效果

// ============================================================================
// 字体与文本绘制工具
// ============================================================================

/** 工厂方法：创建一个新字体对象（调用者负责 delete）*/
Font* MakeFont(float size, int style = FontStyleRegular) {
                                                         // Font*：返回堆上新分配的指针，调用者必须 delete
    FontFamily ff(CFG_FONT_FAMILY);   // 使用配置的字体族名称创建 FontFamily 对象
                                                         // FontFamily：GDI+ 中表示字体族（如"微软雅黑"）
    return new Font(&ff, size, (FontStyle)style, UnitPixel);
                                                         // new：在堆上分配 Font 对象
                                                         // (FontStyle)style：强制转换为 GDI+ 的 FontStyle 枚举类型
                                                         // UnitPixel：字号单位（像素）
}                                                     // 调用者必须手动 delete 释放内存

/**
 * 在指定矩形区域内居中绘制文本（水平+垂直居中）。
 */
void TextCenter(Graphics& g, const std::wstring& text, Font* font, Color color,
                float x, float y, float w, float h) {
    RectF rect(x, y, w, h);                 // 创建一个浮点矩形区域
    StringFormat fmt;                       // 创建字符串格式化对象
    fmt.SetAlignment(StringAlignmentCenter);        // 水平居中（文字在整个矩形内左右居中）
    fmt.SetLineAlignment(StringAlignmentCenter);    // 垂直居中（文字在行内上下居中）
                                                         // StringAlignment：枚举类型，Center 是其中一个值
    SolidBrush brush(color);                  // 创建指定颜色的实心刷子
    g.DrawString(text.c_str(), -1, font, rect, &fmt, &brush);
                                                         // text.c_str()：C 风格字符串指针
                                                         // -1：自动计算字符串长度
                                                         // font：字体对象
                                                         // rect：绘制区域
                                                         // &fmt：格式化对象地址
                                                         // &brush：刷子指针
}                                                     // } 结束 TextCenter 函数

/** 在指定矩形区域内左对齐绘制文本，超长内容截断为省略号。*/
void TextLeft(Graphics& g, const std::wstring& text, Font* font, Color color,
              float x, float y, float w, float h) {
    RectF rect(x, y, w, h);
    StringFormat fmt;
    fmt.SetTrimming(StringTrimmingEllipsisWord);    // 按单词边界截断并在末尾显示省略号（...）
                                                         // StringTrimmingEllipsisWord：只在完整单词处截断
    SolidBrush brush(color);
    g.DrawString(text.c_str(), -1, font, rect, &fmt, &brush);
}                                                     // } 结束 TextLeft 函数

/** 在指定区域内绘制多行文本（自动换行），保持行距均匀分布。*/
void TextWrap(Graphics& g, const std::wstring& text, Font* font, Color color,
              float x, float y, float w, float h) {
    RectF rect(x, y, w, h);
    StringFormat fmt;
    fmt.SetFormatFlags(StringFormatFlagsLineLimit);       // 只显示完整行，不显示半行
    fmt.SetTrimming(StringTrimmingNone);                  // 不截断文字
    fmt.SetLineAlignment(StringAlignmentCenter);          // 垂直居中
    SolidBrush brush(color);
    g.DrawString(text.c_str(), -1, font, rect, &fmt, &brush);
}                                                     // } 结束 TextWrap 函数

/**
 * 测量一段可换行文本在给定宽度下实际需要的高度。
 * @param g     GDI+ Graphics
 * @param text  待测量的文本
 * @param font  字体
 * @param width 文本区域的宽度
 * @return 文本所需的像素高度（向上取整），测量失败返回 0
 */
int MeasureWrappedTextHeight(Graphics& g, const std::wstring& text,
                             Font* font, float width) {
    StringFormat fmt;
    fmt.SetFormatFlags(StringFormatFlagsLineLimit);
    fmt.SetTrimming(StringTrimmingNone);
    RectF layout(0.0f, 0.0f, width, (REAL)QUESTION_MEASURE_HEIGHT);
                                                         // REAL 是 float 的 typedef（GDI+ 中使用）
                                                         // (REAL)QUESTION_MEASURE_HEIGHT：强制转换为 REAL 类型
    RectF bounds;                                         // 输出参数：GDI+ 会将测量结果写入 bounds
    Status status = g.MeasureString(text.c_str(), -1, font, layout, &fmt, &bounds);
                                                         // status：MeasureString 的返回值，Ok 表示成功
    if (status != Ok) return 0;                           // !=：不等于运算符
    return (int)std::ceil(bounds.Height);                 // std::ceil：向上取整函数（<cmath>）
                                                         // (int)：将 double 转为 int（截断小数）
}                                                     // } 结束 MeasureWrappedTextHeight

/**
 * 动态选择最大可适配字号的题干字体。
 * 从 QUESTION_FONT_MAX_SIZE 开始逐档缩小，
 * 直到字体大小能装进指定宽度×高度的矩形内。
 * 如果所有档位都放不下，就返回最小字号的版本。
 */
Font* MakeFittingQuestionFont(Graphics& g, const std::wstring& text,
                              float width, float height) {
    StringFormat fmt;
    fmt.SetFormatFlags(StringFormatFlagsLineLimit);
    RectF layout(0.0f, 0.0f, width, (REAL)QUESTION_MEASURE_HEIGHT);
    for (int fontSize = QUESTION_FONT_MAX_SIZE;         // 从最大字号开始逐档尝试
         fontSize >= QUESTION_FONT_MIN_SIZE;             // 只要不小于最小字号就继续循环
         fontSize -= QUESTION_FONT_STEP) {               // 每次减少 STEP（通常是 1）
                                                         // for 循环的三部分：初始化、条件、步进
        Font* font = MakeFont((float)fontSize, FontStyleBold);
                                                         // (float)fontSize：显式类型转换 int → float
        RectF bounds;                                     // 用于接收测量结果的矩形
        Status status = g.MeasureString(text.c_str(), -1, font, layout, &fmt, &bounds);
        if (status == Ok && bounds.Height <= height) return font;
                                                         // == 是相等比较运算符
                                                         // 如果测量成功且高度不超过限制，说明这个字号够用（返回它）
                                                         // 找到的是最大的合适字号（因为从大到小遍历）
        delete font;                                      // 放不下：销毁这个字体对象
    }                                                     // } 结束 for 逐档缩小
    return MakeFont((float)QUESTION_FONT_MIN_SIZE, FontStyleBold);
                                                         // 全部放不下就用最小字号的版本
}                                                     // } 结束 MakeFittingQuestionFont

// ============================================================================
// 通用页面组件绘制
// ============================================================================

/**
 * 绘制通用页面背景：
 * 1. 垂直渐变底色（浅蓝到更深的蓝灰）
 * 2. 顶部白色横条（标题栏区域）
 * 3. 金色分隔线（标题条下方 2px）
 */
void DrawBackground(Graphics& g) {
    LinearGradientBrush bg(RectF(0, 0, (REAL)g_w, (REAL)g_h),
                                                         // LinearGradientBrush：线性渐变画刷
                                                           //   RectF(0, 0, g_w, g_h)：渐变覆盖整个页面
                                                         //   LinearGradientModeVertical：从上到下的渐变方向
                           Color(255, 240, 244, 249),    // 顶部渐变色（浅蓝灰）
                           Color(255, 226, 233, 243),  // 底部渐变色（稍深）
                           LinearGradientModeVertical); // 第四个参数：渐变方向（垂直）
    g.FillRectangle(&bg, 0, 0, g_w, g_h);              // 用渐变刷填充整个页面
    SolidBrush header(Color(255, 246, 249, 253));      // 创建顶部白色横条的纯色刷
    g.FillRectangle(&header, 0, 0, g_w, 84);           // 填充 y=0 到 y=84 的区域（标题栏）
    SolidBrush gold(Color(255, 214, 143, 28));         // 金色分隔线（接近橙色/琥珀色）
    g.FillRectangle(&gold, 0, 82, g_w, 3);             // 标题条下方 3 像素高
}                                                     // } 结束 DrawBackground

/** 绘制页面顶栏：应用名、版本号、可选右侧文字 */
void DrawHeader(Graphics& g, const std::wstring& rightText) {
    Font* title = MakeFont(24, FontStyleBold);         // 创建粗体标题字体（24pt）
    Font* sub = MakeFont(13, FontStyleBold);           // 创建粗体副标题字体（13pt）
    TextLeft(g, CFG_APP_TITLE, title, GdiColor(CLR_INK), 34, 14, 500, 34);
                                                         // 左上大字（34,14），宽 500，高 34
    TextLeft(g, CFG_APP_TAGLINE, sub, GdiColor(CLR_INK), 36, 52, 390, 24);
                                                         // 左上小字（36,52），宽 390，高 24
    if (!rightText.empty()) TextLeft(g, rightText, sub, GdiColor(CLR_INK), g_w - 370, 34, 330, 26);
                                                         // 右上方显示文字（如答题模式名）
                                                         // g_w - 370：从右往左 370px 处开始
    delete title;                                       // 用完记得释放（new 创建的必须 delete）
    delete sub;
}                                                     // } 结束 DrawHeader

/** 绘制单个指标卡片：白色圆角框 + 左侧彩色竖条 + 标签和数值 */
void DrawMetric(Graphics& g, int x, int y, int w, int h, const std::wstring& label,
                const std::wstring& value, COLORREF accent) {
    FillRoundRectBorder(g, (float)x, (float)y, (float)w, (float)h, 16,
                        Color(255, 255, 255, 255), GdiColor(CLR_LINE), 1.2f);
                                                         // 白色卡片 + 淡灰边框
    FillRoundRect(g, (float)x, (float)y, 8, (float)h, 4, GdiColor(accent));
                                                         // 左侧彩色标记条（宽 8px，圆角 4px）
                                                         // (float)x：强制转换为浮点数，因为 GDI+ 使用 float
    Font* lf = MakeFont(12, FontStyleBold);   // 小号标签字体
    Font* vf = MakeFont(18, FontStyleBold);   // 大号数值字体
    TextLeft(g, label, lf, GdiColor(CLR_MUTED), x + 22, y + 10, w - 32, 22);
    TextLeft(g, value, vf, GdiColor(accent), x + 22, y + 34, w - 32, 30);
    delete lf;                                          // 释放字体对象
    delete vf;
}                                                     // } 结束 DrawMetric

/**
 * 绘制带渐变的按钮。
 * @param outline 如果为 true，只绘制描边（空心按钮），否则绘制实心渐变填充
 */
void DrawButton(Graphics& g, int x, int y, int w, int h, const std::wstring& text,
                COLORREF c1, COLORREF c2, bool outline) {
    if (outline)
        // 空心按钮：白色填充 + 品牌色边框
        FillRoundRectBorder(g, (float)x, (float)y, (float)w, (float)h, 12,
                            Color(255, 255, 255, 255), GdiColor(c1), 1.5f);
    else {
        // 实心按钮：横向渐变填充 + 圆角路径
        LinearGradientBrush br(RectF((REAL)x, (REAL)y, (REAL)w, (REAL)h),
                               GdiColor(c1), GdiColor(c2), LinearGradientModeHorizontal);
                                                         // Horizontal：从左到右的水平渐变
        GraphicsPath path;                              // 创建路径对象
        AddRoundRect(path, (float)x, (float)y, (float)w, (float)h, 12);
                                                         // 添加圆角矩形轮廓
        g.FillPath(&br, &path);                         // 用渐变刷填充路径
    }                                                   // } 结束 if/else
    Font* f = MakeFont(15, FontStyleBold);
                                                         // 空心按钮用品牌色文字，实心按钮用白色文字
    TextCenter(g, text, f, outline ? GdiColor(c1) : Color(255, 255, 255, 255),
               (float)x, (float)y, (float)w, (float)h);
                                                         // ?: 三元运算符，outline=true 则用边框色文字，否则用白色
    delete f;                                           // 释放字体
}                                                     // } 结束 DrawButton

/** 绘制右上角 A- / A+ 字体缩放控制按钮 */
void DrawFontControls(Graphics& g) {
    Font* f = MakeFont(12, FontStyleBold);            // 创建 12pt 粗体字体
                                                         // 两个按钮都使用相同的金色边框配色
    FillRoundRectBorder(g, (float)(g_w - 154), 16, 48, 32, 10,
                        Color(255, 255, 255, 255), GdiColor(CLR_GOLD), 1.6f);
    FillRoundRectBorder(g, (float)(g_w - 98), 16, 48, 32, 10,
                        Color(255, 255, 255, 255), GdiColor(CLR_GOLD), 1.6f);
    TextCenter(g, L"A-", f, GdiColor(CLR_NAVY), (float)(g_w - 154), 17, 48, 30);
                                                         // L"A-"：L 前缀表示宽字符字符串字面量
    TextCenter(g, L"A+", f, GdiColor(CLR_NAVY), (float)(g_w - 98), 17, 48, 30);
    delete f;                                           // 释放字体
}                                                     // } 结束 DrawFontControls

// ============================================================================
// 页面绘制：主页
// ============================================================================
void DrawHomePage(Graphics& g) {
    DrawBackground(g);                                  // 通用背景 + 顶栏 + 金色分割线
    DrawHeader(g, CFG_APP_PAGE_SUBTITLE);              // 页面副标题："请选择答题模式"
    DrawFontControls(g);                                // A- / A+ 按钮

    // 主体卡片
    int cw = std::min(g_w - 80, 880), ch = 540;        // 卡片宽度 = min(g_w-80, 880)
    int cx = (g_w - cw) / 2, cy = 110;                 // 居中计算：左边距 = (总宽 - 卡片宽) / 2
                                                         // cy = 110：距离顶部的 Y 偏移
    DrawCard(g, (float)cx, (float)cy, (float)cw, (float)ch, 24);

    // 卡片内文本：标题、描述
    Font* title = MakeFont(26, FontStyleBold);          // 26pt 粗体（卡片标题）
    Font* sub = MakeFont(14);                           // 14pt 普通（描述）
    Font* h = MakeFont(16, FontStyleBold);              // 16pt 粗体（规则标题）
    Font* body = MakeFont(13);                          // 13pt 普通（规则正文）
    TextCenter(g, CFG_APP_CARD_TITLE, title, GdiColor(CLR_NAVY), cx, cy + 28, cw, 42);
                                                         // 标题居中绘制
    TextCenter(g, CFG_APP_CARD_DESC, sub, GdiColor(CLR_MUTED), cx + 30, cy + 76, cw - 60, 30);

    // 规则说明面板（柔和底色卡片）
    int bx = cx + 50, by = cy + 126, bw = cw - 100, bh = 252;
    // 规则面板相对于大卡片的偏移
    FillRoundRect(g, (float)bx, (float)by, (float)bw, (float)bh, 18, GdiColor(CLR_SOFT));
    TextLeft(g, CFG_RULES_TITLE, h, GdiColor(CLR_NAVY), bx + 28, by + 20, 180, 28);
    for (int i = 0; i < CFG_RULES_COUNT; ++i)           // 逐行绘制规则文本
        TextLeft(g, CFG_RULES[i], body, GdiColor(CLR_INK),
                 bx + 30, by + 60 + i * 32, bw - 60, 27);
                                                         // i * 32：每行间距 32px，第 i 行的 Y 偏移

    // 三个模式按钮（等宽排列 + 间距）
    int btnW = 210, btnGap = 30;
    // btnW * 3 + btnGap * 2：三个按钮 + 两个间隙的总宽度
    int totalBtnW = btnW * 3 + btnGap * 2;
    int btnStartX = cx + (cw - totalBtnW) / 2;         // 按钮区域水平居中
    int btnY = cy + 408;                                // 按钮 Y 坐标

    DrawButton(g, btnStartX, btnY, btnW, 56, CFG_BTN_SINGLE, CLR_BLUE, RGB(30, 64, 175));
    // 蓝→深蓝渐变（单选题）
    DrawButton(g, btnStartX + (btnW + btnGap), btnY, btnW, 56, CFG_BTN_MULTIPLE, CLR_NAVY, CLR_NAVY_2);
    // 深蓝→更深的蓝渐变（多选题），x 偏移 btnW + btnGap
    DrawButton(g, btnStartX + (btnW + btnGap) * 2, btnY, btnW, 56, CFG_BTN_FILL, CLR_GREEN, RGB(0, 112, 56));
    // 绿→深绿渐变（填空），x 偏移 2*(btnW+btnGap)

    // 按钮下方的题数提示
    Font* countFont = MakeFont(12, FontStyleBold);
    TextCenter(g, IntToWStr(QUESTION_COUNT_SINGLE) + L" 题", countFont, GdiColor(CLR_BLUE),
               btnStartX, btnY + 58, btnW, 24);
                                                         // IntToWStr() 将 int 转为宽字符串
    TextCenter(g, IntToWStr(QUESTION_COUNT_MULTIPLE) + L" 题", countFont, GdiColor(CLR_NAVY),
               btnStartX + btnW + btnGap, btnY + 58, btnW, 24);
    TextCenter(g, IntToWStr(QUESTION_COUNT_FILL) + L" 题", countFont, GdiColor(CLR_GREEN),
               btnStartX + (btnW + btnGap) * 2, btnY + 58, btnW, 24);
    // L" 题"：L 宽字符前缀 + "题" 中文字符
    delete countFont;                                   // 释放所有 font
    delete title;
    delete sub;
    delete h;
    delete body;
}                                                     // } 结束 DrawHomePage

// ============================================================================
// 页面绘制：分值选择页
// ============================================================================
void DrawFillScoreSelectPage(Graphics& g) {
    DrawBackground(g);                                  // 通用背景 + 顶栏 + 金线
    DrawHeader(g, CFG_SCORE_SELECT_TITLE);             // 页面标题："请选择本次答题分值"
    DrawFontControls(g);                                // A- / A+ 按钮

    // 中心卡片容器
    int cardW = std::min(g_w - PAGE_HORIZONTAL_MARGIN, SCORE_SELECT_CARD_WIDTH);
                                                         // 卡片宽度 = min(剩余空间, 固定宽度)
    int cardX = (g_w - cardW) / UI_CENTER_DIVISOR;     // 卡片水平居中
    DrawCard(g, (float)cardX, (float)SCORE_SELECT_CARD_Y, (float)cardW,
             (float)SCORE_SELECT_CARD_HEIGHT, SCORE_SELECT_CARD_RADIUS);

    // 卡片内标题和提示
    Font* title = MakeFont(SCORE_SELECT_TITLE_FONT_SIZE, FontStyleBold);
    Font* hint = MakeFont(SCORE_SELECT_HINT_FONT_SIZE);
    TextCenter(g, CFG_SCORE_SELECT_TITLE, title, GdiColor(CLR_NAVY), cardX,
               SCORE_SELECT_CARD_Y + SCORE_SELECT_TITLE_Y, cardW, SCORE_SELECT_TITLE_HEIGHT);
    TextCenter(g, CFG_SCORE_SELECT_HINT, hint, GdiColor(CLR_MUTED), cardX,
               SCORE_SELECT_CARD_Y + SCORE_SELECT_HINT_Y, cardW, SCORE_SELECT_HINT_HEIGHT);

    // 六个分值按钮（3列×2行网格排列）
    for (int i = NO_SCORE; i < FILL_SCORE_OPTION_COUNT; ++i) {
        UIRect button = FillScoreButtonRect(i);         // 计算第 i 个按钮的位置和尺寸
                                                         // FillScoreButtonRect(i) 返回 UIRect{ x, y, w, h }
        std::wstring label = IntToWStr(FILL_SCORE_OPTIONS[i]) + CFG_SCORE_SUFFIX;
                                                         // 拼接分值数字 + "分"后缀
    DrawButton(g, button.x, button.y, button.w, button.h, label,
                   CLR_GREEN, CLR_SCORE_BUTTON_SECONDARY);
                                                         // 绿色渐变（10分→60分都用绿色系）
    }                                                   // } 结束 for

    // 返回主页按钮（空心红色按钮）
    UIRect backRect = ReturnHomeButtonRect();
    DrawButton(g, backRect.x, backRect.y, backRect.w, backRect.h,
               CFG_BTN_RETURN_HOME, CLR_RED, CLR_BACK_BUTTON_SECONDARY, true);
                                                         // 最后一个参数 true = 空心描边模式
    delete title;                                       // 释放字体
    delete hint;
}                                                     // } 结束 DrawFillScoreSelectPage

// ============================================================================
// 各按钮矩形计算 — 返回逻辑像素下的位置和尺寸
// ============================================================================

/** 主页三个模式按钮的位置 */
UIRect HomeButtonRect(int idx) {
                                                         // idx: 0=单选, 1=多选, 2=填空
    int cw = std::min(g_w - 80, 880);
    int cx = (g_w - cw) / 2, cy = 110;
    int btnW = 210, btnGap = 30;
    int totalBtnW = btnW * 3 + btnGap * 2;
    int btnStartX = cx + (cw - totalBtnW) / 2;
    int btnY = cy + 408;
    return {btnStartX + idx * (btnW + btnGap), btnY, btnW, 56};
                                                         // {x, y, w, h} 返回括号列表初始化的 UIRect
}                                                     // } 结束 HomeButtonRect

/** 分值选择页六个分值按钮的位置（3×2 网格排列） */
UIRect FillScoreButtonRect(int idx) {
                                                         // idx: NO_SCORE(0) ~ FILL_SCORE_OPTION_COUNT-1
    int cardW = std::min(g_w - PAGE_HORIZONTAL_MARGIN, SCORE_SELECT_CARD_WIDTH);
    int cardX = (g_w - cardW) / UI_CENTER_DIVISOR;     // 卡片居左偏移 = (总宽 - 卡片宽) / 2
    int totalButtonW = SCORE_SELECT_BUTTON_WIDTH * SCORE_SELECT_COLUMNS
                     + SCORE_SELECT_BUTTON_GAP * (SCORE_SELECT_COLUMNS - 1);
                                                         // 总宽度 = 每列宽之和 + 间隙之和
                                                         // (n列 - 1) = n-1 个间隙
    int startX = cardX + (cardW - totalButtonW) / UI_CENTER_DIVISOR;
                                                         // startX：按钮组在卡片内的起始 x 坐标（水平居中）
    int column = idx % SCORE_SELECT_COLUMNS;            // % 取模运算：idx 对列数取余，得到当前按钮的列序号（0/1/2）
    int row = idx / SCORE_SELECT_COLUMNS;               // / 整数除法：idx 除以列数，得到当前按钮的行序号（0/1）
    return {                                              // 返回花括号初始化列表 {x, y, w, h}
        startX + column * (SCORE_SELECT_BUTTON_WIDTH + SCORE_SELECT_BUTTON_GAP),
                                                         // x = 起始 x + 列序号 × (单列宽+间隙)
        SCORE_SELECT_CARD_Y + SCORE_SELECT_BUTTON_Y + row * SCORE_SELECT_BUTTON_ROW_STEP,
                                                         // y = 卡片顶 + 按钮行偏移 + 行序号 × 行间距
        SCORE_SELECT_BUTTON_WIDTH,                      // w = 固定按钮宽度
        SCORE_SELECT_BUTTON_HEIGHT                      // h = 固定按钮高度
    };                                                  // } 结束 UIRect 初始化列表
}                                                     // } 结束 FillScoreButtonRect

/** 右上角返回主页按钮的位置 */
UIRect ReturnHomeButtonRect() {
    return {g_w - 300, 14, 138, 36};                    // {x, y, w, h} 返回括号列表初始化的 UIRect
}                                                     // } 结束 ReturnHomeButtonRect

/** 填空题编辑框位置（答题页） */
UIRect FillEditRect() {
    int cardX = 38, cardY = 236, cardW = g_w - 76;     // 卡片位置（硬编码基准偏移量）
    int boxY = cardY + CHOICE_OPTION_START_OFFSET_Y;   // 编辑框 Y = 卡片顶 + 选项区起始 Y
    return {cardX + 34, boxY, cardW - 68, 56};         // {x, y, w, h}
}                                                     // } 结束 FillEditRect

/** 确认/下一题/提交按钮位置 */
UIRect ConfirmButtonRect() {
    return {g_w / 2 - 116, g_h - 78, 232, 54};         // {x, y, w, h} = 底部居中
                                                         // g_w/2 - 116：按钮宽度 232 的一半 = (g_w-232)/2 实现水平居中
}                                                     // } 结束 ConfirmButtonRect

// ============================================================================
// 页面绘制：答题页 — 程序最核心的页面
// 包含：顶栏指标、题目、选项、反馈面板、进度条
// ============================================================================
void DrawQuizPage(Graphics& g) {
    DrawBackground(g);                                  // 通用背景 + 顶栏 + 金线

    // 计算实时指标
    int remaining = QuestionRemainingSeconds();         // 本题剩余秒数
    int totalElapsed = NO_TIME_SECONDS;                 // 总用时（只有单选题有）
    if (TracksTotalTime()) {
                                                         // system_clock::now()：获取当前系统时间
        totalElapsed = (int)std::chrono::duration_cast<std::chrono::seconds>(
            std::chrono::system_clock::now() - g_state.quizStart).count();
                                                         // duration_cast：将 chrono duration 从默认单位转换为 seconds
                                                         // .count()：获取时间的刻度数（此处单位为秒）
    }                                                   // } 结束 if

    DrawHeader(g, ModeName());                          // 页面顶栏 + 答题模式名
    DrawFontControls(g);                                // A- / A+ 按钮

    const Question* q = CurrentQuestion();              // 获取当前题目指针
                                                         // CurrentQuestion() 返回 &ActiveBank().all[curQIdx]
    if (!q) return;                                     // 如果指针为空（题目索引无效）直接退出
                                                         // !q：指针为空时条件为真（nullptr 的布尔值为 false）

    // ==================== 顶部指标卡片栏 ====================
    int topX = 38, topY = 104, topW = g_w - 76, topH = 112;
    DrawCard(g, (float)topX, (float)topY, (float)topW, (float)topH, 18);

    Font* meta = MakeFont(14, FontStyleBold);           // 指标中的标题用 14pt 粗体
    Font* meta2 = MakeFont(13);                         // 指标中的值用 13pt 常规

    // 根据模式确定指标卡片数量（不限时=2，限时=3或4）
    int metricCount = TracksTotalTime() ? QUIZ_METRIC_COUNT_TIMED_WITH_TOTAL
                    : (IsTimedMode() ? QUIZ_METRIC_COUNT_TIMED_NO_TOTAL : QUIZ_METRIC_COUNT_UNTIMED);
                                                         // ?: 三元运算符嵌套（判断逻辑分支）

    // 计算每个指标卡片的宽度（均分 + 间距）
    int metricW = (topW - QUIZ_METRIC_SIDE_PADDING * UI_CENTER_DIVISOR
                  - QUIZ_METRIC_GAP * (metricCount - 1)) / metricCount;
                                                         // 指标宽度 = (总宽 - 两边距 - 中间间隙) / 卡片数

    // 倒计时最后 5 秒的闪烁标记
    bool shouldFlash = IsTimedMode() && remaining <= 5 && g_state.flashVisible;
    // flashVisible：true=可见，false=隐藏。每 1 秒翻转一次

    int metricX = topX + QUIZ_METRIC_SIDE_PADDING;     // 第一个指标卡片的 X 坐标
    DrawMetric(g, metricX, topY + 18, metricW, 70, CFG_METRIC_PROGRESS,
               L"第 " + IntToWStr(g_state.qNum) + L" / " + IntToWStr(g_state.total) + L" 题", CLR_BLUE);
                                                         // L"" 宽字符字符串字面量拼接
                                                         // + 运算符连接多个 wstring
    metricX += metricW + QUIZ_METRIC_GAP;              // 移动到下一个卡片的 X 位置
                                                         // += 复合赋值运算符：metricX = metricX + metricW + gap

    DrawMetric(g, metricX, topY + 18, metricW, 70, CFG_METRIC_MODE, ModeName(), CLR_NAVY);
                                                         // 绘制第二个指标：当前模式名
    if (IsTimedMode()) {
        metricX += metricW + QUIZ_METRIC_GAP;
        DrawMetric(g, metricX, topY + 18, metricW, 70, CFG_METRIC_REMAINING,
                   FormatDuration(remaining), shouldFlash ? CLR_RED : CLR_GOLD);
                                                         // shouldFlash ? CLR_RED : CLR_GOLD：倒计时≤5秒时显示红色
    }                                                   // } 结束 if(IsTimedMode)
    if (TracksTotalTime()) {
        metricX += metricW + QUIZ_METRIC_GAP;
        DrawMetric(g, metricX, topY + 18, metricW, 70, CFG_METRIC_TOTAL_TIME,
                   FormatDuration(totalElapsed), CLR_RED);
                                                         // CLR_RED：总用时用红色标记
    }                                                   // } 结束 if(TracksTotalTime)

    // 进度条轨道（灰色底）
    FillRoundRect(g, (float)(topX + 24), (float)(topY + 96), (float)(topW - 48), 10, 5,
                  Color(255, 219, 227, 238));            // Color(alpha, r, g, b)
                                                         // Color：GDI+ 颜色类，前四位是透明度
    // 进度条填充色（倒计时≤5秒时变红）
    COLORREF progressBarColor = shouldFlash ? CLR_RED : CLR_BLUE;
                                                         // ?: 三元运算符
    FillRoundRect(g, (float)(topX + 24), (float)(topY + 96),
                  (float)((topW - 48) * g_state.qNum / g_state.total), 10, 5,
                  GdiColor(progressBarColor));
                                                         // 填充宽度 = (topW-48) * 已完成/总题数
                                                         // g_state.qNum/g_state.total：当前进度比例（整数除法）
                                                         // * (topW-48)：按比例缩放进度条宽度

    // ==================== 主体题目卡片 ====================
    int cardX = 38, cardY = 236, cardW = g_w - 76, cardH = g_h - 336;
                                                         // cardH = g_h - 336：减去顶栏(112)+底部按钮(54+28)+边距 ≈ g_h - 336
    DrawCard(g, (float)cardX, (float)cardY, (float)cardW, (float)cardH, 22);

    // 题干文本（含题号和题库 ID）
    std::wstring questionText = IntToWStr(g_state.qNum) + L". " + q->q +
                                L" (题库第" + IntToWStr(q->id) + L"题)";
                                                         // q->q：箭头运算符，等价于 (*q).q
                                                         // -> 解引用指针并访问成员

    Font* qFont = nullptr;                               // 题干字体指针（未初始化，空指针）
    Font* optFont = nullptr;                             // 选项字体指针
    int questionHeight = QUESTION_TEXT_HEIGHT;           // 题干区域高度（默认固定值）
    int hintOffsetY = QUESTION_HINT_OFFSET_Y;            // 题干提示的 Y 偏移（默认值）
    int optionStartY = CHOICE_OPTION_START_OFFSET_Y;     // 选项区起始 Y 偏移（默认值）
    int optionHeights[CHOICE_OPTION_COUNT] = {};         // 每个选项的高度（初始化为 0）

    // ==================== 填空题分支 ====================
    if (g_state.mode == MODE_FILL) {
                                                         // 填空题不需要选项面板，只需题干字体
        qFont = MakeFittingQuestionFont(
            g, questionText, (float)(cardW - QUESTION_TEXT_WIDTH_INSET),
                                                         // (float) 强制类型转换
            (float)QUESTION_TEXT_HEIGHT);                // 适配字体的题干字体
                                                         // auto type deduction via 'auto' keyword in newer compilers
        optFont = MakeFont(CHOICE_OPTION_FONT_MAX_SIZE); // 选项字体（填空题不需要，但保留以防后续扩展）
        g_choiceOptionRectCount = 0;                     // 填空题无选项区域，计数器设为 0
    } else {
        // ==================== 选择题布局自适应 ====================
        // 预留反馈面板空间（已答则留出，未答不留）
        int feedbackReserve = g_state.answered
                            ? CHOICE_FEEDBACK_HEIGHT + CHOICE_FEEDBACK_GAP_Y
                            : NO_TIME_SECONDS;
        // 可用的内容区域高度
        int availableHeight = cardH - QUESTION_TEXT_OFFSET_Y
                            - CHOICE_DYNAMIC_BOTTOM_PADDING - feedbackReserve;
        // 选项文字的可用宽度（扣除左右边距）
        int optionTextWidth = cardW - CHOICE_OPTION_WIDTH_INSET
                            - CHOICE_OPTION_TEXT_WIDTH_INSET;

        // 逐档缩小字号，找到能完全容纳的最小字体组合
        for (int shrink = NO_SCORE; shrink <= CHOICE_FONT_SHRINK_STEPS; ++shrink) {
                                                         // shrink 从 0 到 8（最多缩小 8 档）
            // 计算当前 shrinking 档的题号和字号
            int questionFontSize = std::max(
                QUESTION_FONT_MIN_SIZE, QUESTION_FONT_MAX_SIZE - shrink);
            int optionFontSize = std::max(
                CHOICE_OPTION_FONT_MIN_SIZE, CHOICE_OPTION_FONT_MAX_SIZE - shrink);
            // 创建候选字体
            Font* candidateQuestionFont = MakeFont(questionFontSize, FontStyleBold);
            Font* candidateOptionFont = MakeFont(optionFontSize);
            // 测量题干所需高度（向上取整）
            int candidateQuestionHeight = std::max(
                CHOICE_DYNAMIC_QUESTION_MIN_HEIGHT,       // 防止题干行太矮
                MeasureWrappedTextHeight(
                    g, questionText, candidateQuestionFont,
                    (float)(cardW - QUESTION_TEXT_WIDTH_INSET))
                    + CHOICE_DYNAMIC_QUESTION_PADDING);  // 加上上下内边距
            // 计算所有选项需要的总高度（从题干 + 提示 + 选项 + 间隙逐步累加）
            int requiredHeight = candidateQuestionHeight
                               + CHOICE_DYNAMIC_QUESTION_HINT_GAP
                               + QUESTION_HINT_HEIGHT
                               + CHOICE_DYNAMIC_HINT_OPTION_GAP;
            int candidateOptionHeights[CHOICE_OPTION_COUNT] = {};
            for (int i = NO_SCORE; i < CHOICE_OPTION_COUNT; ++i) {
                                                         // 逐个测量选项文字的高度
                candidateOptionHeights[i] = std::max(
                    CHOICE_DYNAMIC_OPTION_MIN_HEIGHT,     // 每个选项最小高度
                    MeasureWrappedTextHeight(
                        g, q->opts[i], candidateOptionFont, (float)optionTextWidth)
                        + CHOICE_DYNAMIC_OPTION_PADDING);
                requiredHeight += candidateOptionHeights[i];
                                                         // += 复合赋值运算符：累加到所需总高度
                if (i + 1 < CHOICE_OPTION_COUNT) requiredHeight += CHOICE_DYNAMIC_OPTION_GAP;
                                                         // 如果不是最后一个选项，加上行间距
            }                                           // } 结束 for(测量选项高度)

            bool smallestFonts = questionFontSize == QUESTION_FONT_MIN_SIZE
                              && optionFontSize == CHOICE_OPTION_FONT_MIN_SIZE;
                                                         // == 比较运算符
            // 如果当前档位能放下，或者已是最小字号：选中此档位
            if (requiredHeight <= availableHeight || smallestFonts) {
                qFont = candidateQuestionFont;
                optFont = candidateOptionFont;
                questionHeight = candidateQuestionHeight;
                for (int i = NO_SCORE; i < CHOICE_OPTION_COUNT; ++i) {
                    optionHeights[i] = candidateOptionHeights[i];
                }                                       // 保存每个选项的高度供后面使用
                break;                                   // break：跳出 for(shrink) 循环
                                                         // 找到了最大的合适字号，不用再试更小的了
            } else {                                    // 当前档位放不下，销毁候选字体后试下一档
                delete candidateQuestionFont;           // delete 释放堆上分配的 Font 对象
                delete candidateOptionFont;
            }                                           // } 结束 if(requiredHeight <= availableHeight)
        }                                               // } 结束 for(shrink)

        // 计算题干提示和选项区域的 Y 偏移（基于自适应后的布局）
        hintOffsetY = QUESTION_TEXT_OFFSET_Y + questionHeight
                    + CHOICE_DYNAMIC_QUESTION_HINT_GAP; // hint 位于题干下方
        optionStartY = hintOffsetY + QUESTION_HINT_HEIGHT
                     + CHOICE_DYNAMIC_HINT_OPTION_GAP; // 选项区域位于提示下方
    }                                                   // } 结束 else(选择题)

    // 绘制题干文本（支持自动换行）
    TextWrap(g, questionText, qFont, GdiColor(CLR_INK),
             cardX + QUESTION_TEXT_OFFSET_X,
             cardY + QUESTION_TEXT_OFFSET_Y,
             cardW - QUESTION_TEXT_WIDTH_INSET,
             questionHeight);

    // 返回主页按钮（答题页右上角）
    UIRect backRect = ReturnHomeButtonRect();
    DrawButton(g, backRect.x, backRect.y, backRect.w, backRect.h, CFG_BTN_RETURN_HOME,
               CLR_RED, RGB(160, 30, 30), true);        // 红色空心按钮

    // ==================== 填空题渲染 ====================
    if (g_state.mode == MODE_FILL) {
        // 显示填空题提示文字
        TextLeft(g, CFG_HINT_FILL, meta2, GdiColor(CLR_MUTED),
                 cardX + QUESTION_TEXT_OFFSET_X,
                 cardY + QUESTION_HINT_OFFSET_Y,
                 cardW - QUESTION_TEXT_WIDTH_INSET,
                 QUESTION_HINT_HEIGHT);

        UIRect editRect = FillEditRect();               // 获取编辑框区域
        if (g_state.answered) {                         // 已提交：显示静态文本框替代输入框
            Color fill = Color(255, 244, 248, 253);     // 柔和的白色填充
            Color border = GdiColor(CLR_LINE);          // 淡灰边框
            FillRoundRectBorder(g, (float)editRect.x, (float)editRect.y,
                                (float)editRect.w, (float)editRect.h, 14, fill, border, 1.5f);
                                                         // 绘制一个静态文本框替代编辑框
            std::wstring shown = g_state.userFill.empty() ? CFG_FILL_UNANSWERED : g_state.userFill;
            // userFill.empty() 为空返回 "（未作答）"，否则显示用户输入
            TextLeft(g, CFG_FILL_USER_ANSWER_LABEL + shown, optFont, GdiColor(CLR_INK),
                     editRect.x + 18, editRect.y + 16, editRect.w - 36, 28);
        }                                               // } 结束 if(answered)

        // 显示参考答案面板（仅已答时展示）
        if (g_state.answered) {
            int fy = editRect.y + editRect.h + 14;      // 参考面板起始 Y = 编辑框底 + 间隙
            FillRoundRectBorder(g, (float)(cardX + 34), (float)fy,
                                (float)(cardW - 68), 98, 14,
                                Color(255, 244, 248, 253), GdiColor(CLR_LINE), 1.5f);
            TextLeft(g, CFG_FEEDBACK_FILL_REFERRAL, meta, GdiColor(CLR_NAVY),
                     cardX + 54, fy + 10, cardW - 108, 24);
            // 拼接标准答案和备选答案（用 "/" 分隔多个答案）
            std::wstring reference = q->fillAnswer;     // q->fillAnswer：箭头运算符
            for (const auto& alt : q->fillAlts) reference += L" / " + alt;
                                                         // for(const auto& alt : ...)：range-based for 循环
                                                         // const auto&：常量引用
            TextWrap(g, reference, meta2, GdiColor(CLR_INK),
                     cardX + 54, fy + 36, cardW - 108, 52);
        }                                               // } 结束 if(answered, fill mode)
    } else {
        // ==================== 选择题渲染 ====================
        TextLeft(g, g_state.mode == MODE_MULTIPLE ? CFG_HINT_MULTIPLE : CFG_HINT_SINGLE,
                 meta2, GdiColor(CLR_MUTED),
                 cardX + QUESTION_TEXT_OFFSET_X,
                 cardY + hintOffsetY,
                 cardW - QUESTION_TEXT_WIDTH_INSET,
                 QUESTION_HINT_HEIGHT);

        int optY = cardY + optionStartY;                // 第一个选项的起始 Y
        int nextOptionY = optY;                         // 追踪下一个选项的 Y 位置
        g_choiceOptionRectCount = CHOICE_OPTION_COUNT; // 标记有 4 个选项区域

        // 绘制四个选项
        for (int i = NO_SCORE; i < CHOICE_OPTION_COUNT; ++i) {
                                                         // 逐个绘制选项卡片
            int ox = cardX + CHOICE_OPTION_LEFT_INSET;  // 选项卡片左边缘
            int oy = nextOptionY;                       // 当前选项上边缘
            int ow = cardW - CHOICE_OPTION_WIDTH_INSET; // 选项卡片宽度
            int oh = optionHeights[i];                  // 当前选项高度（自适应）
            g_choiceOptionRects[i] = {ox, oy, ow, oh};  // 缓存选项矩形，供点击检测使用
                                                         // {ox, oy, ow, oh} 花括号初始化 UIRect 结构
            nextOptionY += oh + CHOICE_DYNAMIC_OPTION_GAP; // 更新下一个选项的 Y（当前高度 + 间隙）

            bool selected = g_state.selected[i];
                                                         // selected：该选项是否被用户选中
            // 判断此选项是否为正确答案
            bool correctAnswer = std::find(q->answers.begin(), q->answers.end(), i) != q->answers.end();
                                                         // std::find 在 [begin, end) 区间查找值 i
                                                         // 返回迭代器，不等于 end() 表示找到了
            bool showCorrect = g_state.answered && correctAnswer;
            // 错误选择高亮（红色系）
            bool showWrong = g_state.answered && selected && !correctAnswer;
                                                         // && 逻辑与, ! 逻辑非

            // 根据状态选择颜色
            Color border = selected ? GdiColor(CLR_BLUE) : GdiColor(CLR_LINE);
            Color fill = selected ? Color(255, 224, 238, 255) : Color(255, 255, 255, 255);
            if (showWrong) { fill = Color(255, 254, 226, 226); border = GdiColor(CLR_RED); }
            if (showCorrect) { fill = Color(255, 231, 248, 238); border = GdiColor(CLR_GREEN); }

            // 绘制选项卡片边框 + 填充
            FillRoundRectBorder(g, (float)ox, (float)oy, (float)ow, (float)oh, 14, fill, border,
                                selected || showCorrect || showWrong ? 2.4f : 1.5f);
            // 选中/对错高亮的选项使用更粗的边框 (2.4px)
                                                         // ?: 三元运算符

            // 字母标识（A/B/C/D 圆形徽章）
            wchar_t letter[2] = {(wchar_t)(L'A' + i), L'\0'};
                                                         // wchar_t：宽字符类型
                                                         // (wchar_t)(L'A' + i)：将 'A'+0='A', 'A'+1='B' 等转为字符
            // 徽章颜色：正确答案绿，错误选红，选中蓝，默认深蓝色
            COLORREF letterColor = showCorrect ? CLR_GREEN : showWrong ? CLR_RED : selected ? CLR_BLUE : CLR_NAVY;
            int letterY = oy + (oh - 32) / UI_CENTER_DIVISOR;
            // 徽章垂直居中于选项卡片内
            FillRoundRect(g, (float)(ox + 14), (float)letterY, 32, 32, 16, GdiColor(letterColor));
                                                         // 绘制圆形徽章（圆角矩形半径=一半边长=16）
            Font* lf = MakeFont(13, FontStyleBold);
            TextCenter(g, letter, lf, Color(255, 255, 255, 255), ox + 14, letterY, 32, 32);
            // 徽章内居中绘制字母 A/B/C/D
            TextWrap(g, q->opts[i], optFont, GdiColor(CLR_INK),
                     ox + CHOICE_OPTION_TEXT_OFFSET_X,
                     oy + CHOICE_DYNAMIC_OPTION_PADDING / UI_CENTER_DIVISOR,
                     ow - CHOICE_OPTION_TEXT_WIDTH_INSET,
                     oh - CHOICE_DYNAMIC_OPTION_PADDING);
            // 在徽章右侧绘制选项文字（自动换行）
            delete lf;                                  // 释放临时字体
        }                                               // } 结束 for(选项)

        // ==================== 答案反馈面板 ====================
        if (g_state.answered) {                         // 仅已作答时显示反馈面板
            const UIRect& lastOption = g_choiceOptionRects[CHOICE_OPTION_COUNT - 1];
            // const UIRect&：常量引用，最后一个选项的矩形
            int fy = lastOption.y + lastOption.h + CHOICE_FEEDBACK_GAP_Y;
            // 反馈面板 Y = 最后一个选项的底边 + 间隙

            // 面板背景色：对=绿色系，错=红色系
            bool ok = g_state.lastCorrect;              // lastCorrect：上一题的对错结果
            FillRoundRectBorder(g, (float)(cardX + 34), (float)fy, (float)(cardW - 68), 82, 14,
                                ok ? Color(255, 231, 248, 238) : Color(255, 254, 226, 226),
                                ok ? GdiColor(CLR_GREEN) : GdiColor(CLR_RED), 2.0f);
            std::wstring fb = ok ? CFG_FEEDBACK_CORRECT
                   : (g_state.timedOut ? CFG_FEEDBACK_TIMEOUT
                                       : CFG_FEEDBACK_WRONG_PREFIX) + AnswerLetters(*q) + CFG_FEEDBACK_SUFFIX;
            // ?: 三元运算符嵌套：ok ? "正确" : (timedOut ? "超时" : "错误"+正确答案)
            TextLeft(g, fb, meta, ok ? GdiColor(CLR_GREEN) : GdiColor(CLR_RED), cardX + 54, fy + 12, cardW - 108, 26);
            // 答案解析（如果有）
            if (!q->exp.empty()) TextLeft(g, q->exp, meta2, GdiColor(CLR_INK), cardX + 54, fy + 44, cardW - 108, 30);
            // q->exp：答案解释字段，箭头运算符
        }                                               // } 结束 if(answered)
    }                                                   // } 结束 else(选择题)

    // ==================== 确认/下一题/提交按钮 ====================
    bool finalQ = g_state.answered && g_state.qNum >= g_state.total;
    // finalQ：是否为最后一题且已答完
                                                         // ?: 三元运算符：最后一题 → 绿色"提交"，否则 → 蓝色"确认"或"下一题"
    COLORREF cBtn = finalQ ? CLR_GREEN : CLR_BLUE;
    COLORREF cBtn2 = finalQ ? RGB(0, 112, 56) : RGB(0, 69, 170);
    const wchar_t* btnText = finalQ ? CFG_BTN_SUBMIT
              : (g_state.answered ? CFG_BTN_NEXT : CFG_BTN_CONFIRM);
                                                         // ?: 三元运算符嵌套
    UIRect confirmRect = ConfirmButtonRect();           // 获取按钮位置
    DrawButton(g, confirmRect.x, confirmRect.y, confirmRect.w, confirmRect.h, btnText, cBtn, cBtn2);
                                                         // 绘制底部按钮

    delete meta;                                        // 释放所有字体（必须配对 delete）
    delete meta2;
    delete qFont;
    delete optFont;
}                                                     // } 结束 DrawQuizPage

// ============================================================================
// 页面绘制：结果页
// ============================================================================
void DrawResultPage(Graphics& g) {
    DrawBackground(g);                                  // 通用背景 + 顶栏 + 金线
    DrawHeader(g, ModeName());                          // 页面顶栏 + 答题模式名
    DrawFontControls(g);                                // A- / A+ 按钮

    // 计算分数：填空题直接显示选择的分值，选择题计算百分制
    bool fillMode = g_state.mode == MODE_FILL;
    int score = fillMode ? g_state.selectedScore
                         : (int)((double)g_state.correct / g_state.total * 100.0 + 0.5);
    // (int)：强制类型转换
    // (double)：将 correct 转为 double（避免整数除法截断）
    // +0.5：四舍五入技巧
    // 结果：如果 fillMode 为 true 取 selectedScore，否则 correct/total*100 四舍五入

    // 计算总用时（仅单选题计入）
    int totalSeconds = NO_TIME_SECONDS;
    if (TracksTotalTime()) {
        totalSeconds = (int)std::chrono::duration_cast<std::chrono::seconds>(
            g_state.quizEnd - g_state.quizStart).count();
                                                         // 使用 quizEnd 和 quizStart 相减得到时间段
    }                                                   // } 结束 if

    // 结果卡片
    int cw = std::min(g_w - 80, 760), ch = 500;        // 卡片宽度取 min
    int cx = (g_w - cw) / 2, cy = 120;                 // 卡片居左位置
    DrawCard(g, (float)cx, (float)cy, (float)cw, (float)ch, 24);

    Font* title = MakeFont(25, FontStyleBold);          // 25pt 粗体（卡片标题）
    Font* scoreFont = MakeFont(46, FontStyleBold);      // 46pt 超大粗体（分数显示）
    Font* stat = MakeFont(16, FontStyleBold);           // 16pt 粗体（统计项）
    Font* body = MakeFont(13);                          // 13pt 普通（统计标签）

    // 卡片标题："答题结果"
    TextCenter(g, CFG_RESULT_TITLE, title, GdiColor(CLR_NAVY), cx, cy + 28, cw, 40);
    // 分数显示 — 颜色按分值变化：及格绿、不及格红
    std::wstring scoreText = IntToWStr(score) + (fillMode ? CFG_SCORE_SUFFIX : L"");
    // Ternary operator ?:
    Color scoreColor = fillMode ? GdiColor(CLR_GREEN)
                               : (score >= 80 ? GdiColor(CLR_GREEN)     // >= 大于等于
                                         : (score >= 60 ? GdiColor(CLR_GOLD) : GdiColor(CLR_RED)));
                                                         // ?: 三元运算符嵌套（及格线 80/60）
    TextCenter(g, scoreText, scoreFont, scoreColor, cx, cy + 78, cw, 70);
    // 分数下方的说明文字（"总分"或"选择分值"）
    TextCenter(g, fillMode ? CFG_METRIC_SELECTED_SCORE : CFG_RESULT_SCORE_LABEL,
               body, GdiColor(CLR_MUTED), cx, cy + 142, cw, 24);

    // 统计信息行
    int sy = cy + 190;                                   // 统计区域起始 Y 坐标
    if (fillMode) {                                     // 填空题只显示模式名
        FillRoundRect(g, (float)(cx + 54), (float)sy, (float)(cw - 108), 34, 10, GdiColor(CLR_SOFT));
        TextLeft(g, CFG_METRIC_MODE, body, GdiColor(CLR_MUTED), cx + 76, sy + 7, 140, 22);
        TextLeft(g, ModeName(), stat, GdiColor(CLR_INK), cx + 220, sy + 5, cw - 310, 24);
    } else {                                            // 选择题显示模式、对错数、总耗时、结束时间
        int row = 0;                                    // 当前行号计数器
        auto drawStat = [&](const std::wstring& label, const std::wstring& value) {
                                                         // [=]：捕获外部变量（按值捕获，包括 this）
            int y = sy + row * 42;                      // row 从 0 开始递增（自增在 lambda 内部）
            // auto：自动类型推导（C++11）
            // lambda [&]：lambda 表达式捕获外部变量
            // drawStat：内部函数定义（匿名函数，只读）
            FillRoundRect(g, (float)(cx + 54), (float)y, (float)(cw - 108), 34, 10, GdiColor(CLR_SOFT));
            TextLeft(g, label, body, GdiColor(CLR_MUTED), cx + 76, y + 7, 140, 22);
            TextLeft(g, value, stat, GdiColor(CLR_INK), cx + 220, y + 5, cw - 310, 24);
            ++row;                                      // 内部自增：row++ 的另一种写法
                                                         // 内部 ++row：前置自增运算符
        };                                              // }; lambda 定义结束
        drawStat(CFG_METRIC_MODE, ModeName());          // 模式
        drawStat(CFG_METRIC_CORRECT_TOTAL,
                 IntToWStr(g_state.correct) + L" / " + IntToWStr(g_state.total));
        if (TracksTotalTime()) drawStat(CFG_METRIC_DURATION, FormatDuration(totalSeconds));
        drawStat(CFG_METRIC_END_TIME, FormatTime(g_state.quizEnd));
        // FormatTime 将 quizEnd 转成人类可读时间字符串
    }                                                   // } 结束 else(选择题)

    // 底部两个操作按钮：再次答题 / 返回主页
    DrawButton(g, cx + cw / 2 - 210, cy + ch - 82, 190, 48, CFG_BTN_RESTART, CLR_BLUE, RGB(30, 64, 175));
    // 左侧按钮："再次答题"（蓝色实心）
    DrawButton(g, cx + cw / 2 + 20, cy + ch - 82, 190, 48, CFG_BTN_RETURN_HOME,
               CLR_NAVY, RGB(34, 70, 118), true);       // 右侧按钮："返回主页"（红色空心）
                                                         // true：空心模式

    delete title;                                       // 释放所有字体（配对 delete）
    delete scoreFont;
    delete stat;
    delete body;
}                                                     // } 结束 DrawResultPage
