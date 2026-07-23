# 法律知识答题竞赛系统

基于 C++ Win32 API + GDI+ 开发的桌面答题应用，支持单选题、多选题、填空/简答题三种模式，内嵌题库 JSON、音效和图标资源，编译后生成独立 exe 文件。

## 目录结构

```
cpp答题/
├── src/          — 源代码（11个文件）
│   ├── entry.cpp       — main() 入口，窗口注册与消息循环
│   ├── state.cpp/h     — 题库加载(JSON解析)、答题状态管理、计时、判分
│   ├── ui_draw.cpp/h   — 全部 UI 绘制逻辑（GDI+ 双缓冲绘制）
│   ├── ui_control.cpp/h— WndProc 消息分发、点击处理、Edit 控件
│   ├── config.h        — 全部文字常量、按钮文案、规则说明、尺寸参数
│   └── types.h         — 基础类型定义（Question, QuizState, Page 枚举等）
├── data/         — 题库数据源
│   ├── questions.json            — 单选题题库
│   ├── multiple_questions_from_xlsx.json — 多选题题库
│   ├── fill_questions.json       — 填空题题库
│   ├── 单选题.xlsx
│   ├── 多选题.xlsx
│   └── 填空题.xlsx
├── media/        — 媒体资源
│   ├── app_icon.ico   — 程序图标
│   ├── sound_correct.wav  — 回答正确音效
│   ├── sound_wrong.wav    — 回答错误音效
│   ├── sound_timeout.wav  — 超时音效
│   └── fbd1cbe1c24f6241156edee9c604f8ad.png — 截图
├── build/        — 编译产物 & 资源脚本
│   ├── quiz.exe         — 编译后的可执行文件
│   ├── app_res.obj      — 资源编译中间文件
│   └── app.rc           — Windows 资源描述文件
├── web/          — HTML 版本工具
│   ├── quiz.html   — HTML 网页版答题界面
│   └── html_quiz_gen.py — HTML 题库生成脚本
├── backup/       — 备份 / 辅助脚本
│   └── update.py
└── README.md
```

## 项目架构

### 核心流程

```
entry.cpp (main)
  ├── LoadQuestionBank() ×3  → 加载三种题库 JSON 到 g_banks[]
  ├── 注册窗口类 → CreateWindow → ShowWindow(SW_MAXIMIZE)
  └── 消息循环 GetMessage/DispatchMessage
        │
        ▼
  ui_control.cpp (WndProc)
  ├── WM_CREATE  → 初始化 dark title bar、定时器、Edit 控件
  ├── WM_PAINT   → 调用 DrawHomePage/DrawQuizPage/DrawResultPage
  ├── WM_LBUTTONDOWN → 页面路由处理点击
  ├── WM_TIMER   → 倒计时闪烁 + 超时判定
  ├── WM_SIZE    → 重绘双缓冲 backbuffer
  └── WM_DESTROY → 清理资源
        │
        ▼
  ui_draw.cpp
  ├── GDI+ 双缓冲绘制（内存 DC → BitBlt）
  ├── 自适应字体缩放（A+/A- 按钮 / Ctrl+滚轮）
  └── 动态布局计算（ScaleCoord / UpdateLayoutSize）
        │
        ▼
  state.cpp
  ├── 自研轻量 JSON 解析器（不依赖外部库）
  ├── PickRandomUnused() — 随机抽题，去重跟踪
  ├── SettleCurrentQuestion() — 判分 + 播放音效
  └── 计时器（steady_clock）
```

### 模块职责

| 文件 | 职责 | 关键接口 |
|------|------|----------|
| `config.h` | 纯常量定义，无实现 | `CFG_*`, `FILL_SCORE_OPTIONS` |
| `types.h` | 数据结构声明 | `Question`, `QuestionBank`, `Page`, `QuizMode` |
| `state.h/cpp` | 业务逻辑层 | `StartQuiz()`, `PickRandomUnused()`, `LoadQuestionBank()` |
| `ui_draw.h/cpp` | 渲染层 | `DrawHomePage/QuizPage/ResultPage()` |
| `ui_control.h/cpp` | 控制层 | `WndProc()`, 各类 HandleXxxClick() |
| `entry.cpp` | 启动层 | `main()`, 窗口注册与消息循环 |

### 三种答题模式

- **必答题（单选题）** — 3 题，每题 30 秒限时，全程记录总用时，答案生命周期内永不重复
- **抢答题（多选题）** — 10 题，每题 30 秒限时，漏选多选均不得分
- **风险题（填空/简答）** — 2 题，不限时，选择分值后进入，提交后展示参考答案

## 如何编译

### 环境要求

- **操作系统**: Windows 10/11
- **编译器**: MinGW-w64 (g++) 或 Microsoft Visual C++ (MSVC)
- **依赖库**: Windows SDK, GDI+, winmm.lib（音效播放）

### MinGW-w64 编译（推荐）

在项目根目录打开终端（或在 `src/` 文件夹下的终端中），执行以下指令：

```bash
# 1. 编译源文件为 object files
g++ -o build/state.o src/state.cpp -std=c++17 -DGDIPLUS_STATIC -Isrc
g++ -o build/ui_draw.o src/ui_draw.cpp -std=c++17 -DGDIPLUS_STATIC -Isrc
g++ -o build/ui_control.o src/ui_control.cpp -std=c++17 -DGDIPLUS_STATIC -Isrc
g++ -o build/entry.o src/entry.cpp -std=c++17 -DGDIPLUS_STATIC -Isrc

# 2. 编译资源文件
windres build/app.rc -O obj -o build/app_res.obj

# 3. 链接
g++ build/entry.o build/state.o build/ui_draw.o build/ui_control.o build/app_res.obj \
    -o build/quiz.exe \
    -lgdiplus -lwinmm -mwindows
```

### 一键编译（简化命令）

如果你使用的是 MinGW，可以在项目根目录运行：

```bash
g++ -std=c++17 -mwindows -DUNICODE -D_UNICODE -Isrc src/*.cpp build/app_res.obj -lgdiplus -lwinmm -o build/quiz.exe
```

### MSVC (Visual Studio) 编译

```cmd
:: 在 Visual Studio Developer Command Prompt 中执行
cl /Fe:build\quiz.exe /Isrc ^
   build\app_res.res ^
   src\entry.cpp src\state.cpp src\ui_draw.cpp src\ui_control.cpp ^
   /link gdiplus.lib winmm.lib user32.lib gdi32.lib shell32.lib dwmapi.lib /SUBSYSTEM:WINDOWS
```

### 运行

编译完成后，双击 `build/quiz.exe` 即可运行，窗口会自动最大化。

## 题库管理

### 题库格式

JSON 格式存储在 `data/` 目录下，由 Excel 题库通过 `web/html_quiz_gen.py` 转换而来。

**单选题/多选题格式：**

```json
[
  {
    "id": 1,
    "difficulty": 0,
    "question": "题目内容",
    "options": ["选项A", "选项B", "选项C", "选项D"],
    "answer": [0],
    "explanation": "解析说明"
  }
]
```

**填空题格式：**

```json
[
  {
    "id": 1,
    "question": "填空题目",
    "fill_answer": "标准答案",
    "fill_alternatives": ["备选答案1", "备选答案2"],
    "explanation": "解析"
  }
]
```

### 题库验证规则

程序启动时自动校验：
- JSON 根节点必须为数组
- 每种模式至少需要 10 道有效题目
- 选择类题目必须有 4 个非空选项和合法答案（答案索引 0-3，无重复）
- 填空类题目必须有 `question` 和 `fill_answer`

### 更换题库

1. 编辑 `data/` 下的 JSON 文件（建议先备份）
2. 修改 `build/app.rc` 中的资源 ID 映射关系
3. 重新编译

## 如何微调

### 修改题目数量和限时

在 `src/config.h` 中调整常量：

```cpp
static const int QUESTION_COUNT_SINGLE = 3;      // 单选题数量
static const int QUESTION_COUNT_MULTIPLE = 10;    // 多选题数量
static const int QUESTION_COUNT_FILL = 2;         // 填空题数量
static const int QUESTION_TIME_LIMIT_SECONDS = 30; // 每题限时（秒）
static const int FILL_SCORE_OPTIONS[FILL_SCORE_OPTION_COUNT] = {10, 20, 30, 40, 50, 60}; // 分值选项
```

### 修改 UI 配色

所有颜色定义在 `src/ui_draw.h` 中：

```cpp
static const COLORREF CLR_BG = RGB(232, 238, 247);   // 背景渐变色
static const COLORREF CLR_PANEL = RGB(255, 255, 255); // 卡片面板
static const COLORREF CLR_NAVY = RGB(13, 42, 86);     // 深蓝主色
static const COLORREF CLR_GREEN = RGB(0, 142, 73);    // 成功绿色
static const COLORREF CLR_RED = RGB(205, 38, 38);     // 错误红色
// ... 更多颜色
```

### 修改窗口尺寸

```cpp
const int INITIAL_WINDOW_W = 2440;  // 窗口初始宽度
const int INITIAL_WINDOW_H = 1760;  // 窗口初始高度
```

### 修改字体

```cpp
static const wchar_t* CFG_FONT_FAMILY = L"Microsoft YaHei";  // 在 src/config.h 中
```

### 调整缩放比例

默认 `g_fontScale = 2.0f`（在 `src/ui_draw.cpp` 中）。启动后可通过右上角 A+/A- 按钮或 Ctrl+鼠标滚轮实时调整，范围 1.0x ~ 3.0x。

### 修改文案/规则

全部文本集中在 `src/config.h` 中，按分类查找即可：

```cpp
static const wchar_t* CFG_APP_TITLE = L"法律知识答题竞赛系统";
static const wchar_t* CFG_RULES[] = { L"..." };  // 第7~12行
static const wchar_t* CFG_BTN_SINGLE = L"必答题"; // 第120行
// ... 搜索 CFG_ 前缀即可找到所有文案
```

### 更换图标和音效

替换 `media/` 下的同名文件，然后更新 `build/app.rc`：

```rc
IDI_ICON ICON "media/app_icon.ico"
IDS_SOUND_CORRECT WAVE "media/sound_correct.wav"
```

注意：使用 `windres` 编译资源文件时需要确保 `.rc` 中的相对路径指向正确的文件位置。

## 项目特点

- **零外部依赖**：仅使用 Windows SDK + GDI+，无需安装任何第三方库
- **内嵌资源**：题库 JSON、音效、图标全部编译入 exe，单文件即可分发
- **自定义 JSON 解析器**：不依赖 nlohmann/json 等外部库，体积极小
- **GDI+ 双缓冲绘制**：无闪烁的流畅体验，支持抗锯齿和 ClearType 字体
- **自适应 UI**：根据内容长度动态调整字体大小，长文本自动换行
- **高分辨率友好**：支持 @2x 渲染坐标系，4K 屏幕下依然清晰
- **深色标题栏**：利用 DWM API 实现自定义窗口标题栏颜色和文字
