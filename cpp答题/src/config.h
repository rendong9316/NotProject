// ============================================================================
// config.h — 全局配置常量与 UI 布局参数
// 集中管理所有硬编码的数值常量，方便统一调整界面和规则
// ============================================================================

#pragma once                                           // #pragma once：预处理指令，头文件只被包含一次

#include <windows.h>                                  // 引入 Windows API 头文件（提供 COLORREF 等类型）

// --- 各题型固定答题数量 ---
static const int QUESTION_COUNT_SINGLE = 3;            // 单选题每轮答题固定 3 道题
                                                         // static：在本翻译单元（.cpp 文件）内可见，不导出为符号
                                                         // const：编译期常量，值不能修改
static const int QUESTION_COUNT_MULTIPLE = 10;         // 多选题每轮答题固定 10 道题
static const int QUESTION_COUNT_FILL = 2;              // 填空题/风险题每轮答题固定 2 道题

// 风险题分值选项配置
static const int FILL_SCORE_OPTION_COUNT = 6;          // 风险题有 6 个分值可选（10/20/30/40/50/60）
static const int FILL_SCORE_OPTIONS[FILL_SCORE_OPTION_COUNT] = {10, 20, 30, 40, 50, 60};
                                                         // 初始化列表：FILL_SCORE_OPTIONS[0]=10, [1]=20 ... [5]=60
                                                         // 数组长度由 FILL_SCORE_OPTION_COUNT 决定为 6
static const int NO_SCORE = 0;                         // 占位值，对应上述数组的索引 0（实际值为 10）
static const int NO_TIME_SECONDS = 0;                  // 表示"不限时"的标记值（填空题没有倒计时）

// --- 每题限时（秒） ---
static const int QUESTION_TIME_LIMIT_SECONDS = 30;     // 单选题和多选题每题限时 30 秒
                                                         // 超过此时间未作答则自动判错并播放超时音效

// --- 分值选择页布局参数 ---
static const int SCORE_SELECT_CARD_WIDTH = 760;        // 分值选择页卡片宽度（逻辑像素单位）
static const int SCORE_SELECT_CARD_HEIGHT = 430;       // 分值选择页卡片高度
static const int SCORE_SELECT_BUTTON_WIDTH = 210;      // 分值按钮宽度
static const int SCORE_SELECT_BUTTON_HEIGHT = 58;      // 分值按钮高度
static const int SCORE_SELECT_BUTTON_GAP = 24;         // 两个相邻按钮之间的水平间距
static const int SCORE_SELECT_COLUMNS = 3;             // 分值按钮排列列数 = 3 列（形成 3×2 网格）
static const int SCORE_SELECT_ROWS = 2;                // 分值按钮排列行数 = 2 行
static const int SCORE_SELECT_CARD_Y = 120;            // 分值选择卡片距离页面顶部的 Y 偏移
static const int SCORE_SELECT_TITLE_Y = 34;            // 卡片内标题文字距离卡片顶部的 Y 偏移
static const int SCORE_SELECT_BUTTON_Y = 150;          // 分值按钮区域起始 Y 坐标（相对于卡片顶部）
static const int SCORE_SELECT_BUTTON_ROW_STEP = 90;    // 按钮行之间的垂直间距（第二行的 Y = 第一行 + 90）
static const int SCORE_SELECT_CARD_RADIUS = 24;        // 卡片的圆角半径（GDI+ 绘图中的弧线半径）
static const int SCORE_SELECT_TITLE_FONT_SIZE = 25;    // 分值选择页标题文字的字号大小（磅值）
static const int SCORE_SELECT_HINT_FONT_SIZE = 14;     // 分值选择页提示文字的字号
static const int SCORE_SELECT_TITLE_HEIGHT = 42;       // 标题行占用的高度空间
static const int SCORE_SELECT_HINT_Y = 88;             // 提示文字距离卡片顶部的 Y 偏移量
static const int SCORE_SELECT_HINT_HEIGHT = 30;        // 提示文字行占用的高度

// --- 通用布局常量 ---
static const int UI_CENTER_DIVISOR = 2;                // 居中计算用除数：左偏移 = (总宽 - 元素宽) / 2
                                                         // 除以 2 就是把剩余空间平分到左右两侧
static const int PAGE_HORIZONTAL_MARGIN = 80;          // 页面内容距离窗口左右边缘的水平安全边距

// --- 答题页指标栏布局 ---
static const int QUIZ_METRIC_GAP = 12;                 // 指标卡片之间的水平间距（进度、用时等小卡片）
static const int QUIZ_METRIC_SIDE_PADDING = 22;        // 指标栏区域左右两侧的填充距离

static const int QUIZ_METRIC_COUNT_UNTIMED = 2;        // 不限时模式的指标数量：进度 + 模式名
static const int QUIZ_METRIC_COUNT_TIMED_NO_TOTAL = 3; // 限时但不计总时的指标数量：进度 + 模式 + 剩余时间
static const int QUIZ_METRIC_COUNT_TIMED_WITH_TOTAL = 4; // 限时且计总时的指标数量：前3个 + 总用时

// --- 题目和选项区域布局 ---
static const int QUESTION_TEXT_OFFSET_X = 34;          // 题干文本距离卡片左边缘的缩进量
static const int QUESTION_TEXT_OFFSET_Y = 20;          // 题干文本距离卡片顶部的偏移量
static const int QUESTION_TEXT_WIDTH_INSET = 68;       // 题干文本区域的总水平内边距（左侧缩进 + 右侧缩进）
static const int QUESTION_TEXT_HEIGHT = 78;            // 题干文本区域的固定最大高度（多行自动换行）
static const int QUESTION_FONT_MAX_SIZE = 18;          // 题干文字的最大字号（18pt），文字长时可缩小
static const int QUESTION_FONT_MIN_SIZE = 10;          // 题干文字的最小字号（10pt），即使很长也不会再小
static const int QUESTION_FONT_STEP = 1;               // 字体缩小步长：每次减小 1 号
static const int QUESTION_MEASURE_HEIGHT = 1000;       // 测量文本高度时使用的虚拟高度（足够大确保不截断）
static const int QUESTION_HINT_OFFSET_Y = 100;         // 题干下方提示文字的默认Y偏移
static const int QUESTION_HINT_HEIGHT = 24;            // 提示文字行占用的高度（24像素）

// --- 选项布局 ---
static const int CHOICE_OPTION_START_OFFSET_Y = 132;   // 选择题选项区域起始 Y 偏移（从卡片顶部算起）
static const int CHOICE_OPTION_LEFT_INSET = 34;        // 选项卡片的左侧缩进
static const int CHOICE_OPTION_WIDTH_INSET = 68;       // 选项卡片的总水平内边距（左缩进 + 右缩进）
static const int CHOICE_OPTION_TEXT_OFFSET_X = 62;     // 选项文字相对选项卡片左边缘的缩进（跳过字母标识圈）
static const int CHOICE_OPTION_TEXT_WIDTH_INSET = 78;  // 选项文字区域的总水平内边距

// --- 动态字体适配 ---
static const int CHOICE_FEEDBACK_GAP_Y = 10;           // 最后一个选项与答案解析反馈面板之间的垂直间隙
static const int CHOICE_OPTION_FONT_MAX_SIZE = 16;     // 选项文字的最大字号（可随空间缩小到 10）
static const int CHOICE_OPTION_FONT_MIN_SIZE = 10;     // 选项文字的最小字号
static const int CHOICE_FONT_SHRINK_STEPS = 8;         // 题目卡片空间不够时最多尝试缩小 8 档字体

static const int CHOICE_DYNAMIC_QUESTION_MIN_HEIGHT = 36; // 题干行的最小高度（防止文字太少时被压扁）
static const int CHOICE_DYNAMIC_QUESTION_PADDING = 4;    // 题干文字上下各留 4px 内边距
static const int CHOICE_DYNAMIC_QUESTION_HINT_GAP = 2;   // 题干区域与提示文字之间的间距
static const int CHOICE_DYNAMIC_HINT_OPTION_GAP = 6;     // 提示文字与第一个选项卡片之间的间距
static const int CHOICE_DYNAMIC_OPTION_MIN_HEIGHT = 44;  // 每个选项卡的最小高度（保证可点击性）
static const int CHOICE_DYNAMIC_OPTION_PADDING = 10;     // 选项文字上下各留 10px 内边距
static const int CHOICE_DYNAMIC_OPTION_GAP = 6;          // 两个相邻选项卡片之间的垂直间距
static const int CHOICE_DYNAMIC_BOTTOM_PADDING = 12;   // 最后一个选项底部到卡片底边的留白
static const int CHOICE_FEEDBACK_HEIGHT = 82;          // 答案解析反馈面板的高度（固定 82 像素）

// --- 窗口类名 ---
static const wchar_t* CFG_CLASS_NAME = L"JusticeQuizAppClass";
                                                         // wchar_t*：宽字符指针（存储 Unicode 字符串）
                                                         // L 前缀：C++ 字符串字面量的宽字符标记
                                                         // 用于 Windows RegisterClassExW() 注册窗口类名

// --- 字体 ---
static const wchar_t* CFG_FONT_FAMILY = L"Microsoft YaHei";
                                                         // 程序使用的字体族名 — 微软雅黑，支持中文显示

// --- 题库资源 ID ---
static const int RES_ID_SINGLE = 2001;                 // 单选题题库 JSON 在资源文件 (.rc) 中对应的资源编号
static const int RES_ID_MULTIPLE = 2002;               // 多选题库 JSON 的资源编号
static const int RES_ID_FILL = 2003;                   // 填空题题库 JSON 的资源编号
                                                         // 编译时这些资源会被嵌入 .exe 文件中

// --- 错误提示 ---
static const wchar_t* CFG_ERR_LOAD_FAILED = L"题库加载失败"; // 三个题库都无法加载时的通用错误弹窗文案
static const wchar_t* CFG_NO_SELECTION = L"请先选择答案。";   // 用户没选任何选项就点"确认"时的提示
static const wchar_t* CFG_ERR_NO_QUESTIONS = L"题库没有足够的未使用题目。";
static const wchar_t* CFG_ERR_SINGLE_EXHAUSTED = L"本次启动下单选题已全部抽完，请重新启动程序。";

// --- 对话框标题 ---
static const wchar_t* CFG_DLG_TITLE = L"提示";            // MessageBox 弹出框的标题
static const wchar_t* CFG_DLG_ERR = L"题库错误";          // 错误对话框的标题

// --- 题库错误提示 ---
static const wchar_t* CFG_ERR_FILE_NOT_FOUND = L"未找到题库文件：";
static const wchar_t* CFG_ERR_FILE_READ = L"无法读取题库文件：";
static const wchar_t* CFG_ERR_JSON_ROOT = L"题库 JSON 格式错误：根节点应为数组。";
static const wchar_t* CFG_ERR_JSON_ITEM = L"题库 JSON 格式错误：题目项应为对象。";
static const wchar_t* CFG_ERR_INVALID_Q = L"题库文件中存在无效题目：";
static const wchar_t* CFG_ERR_Q_ID = L"（题目 ID ";      // 拼接在 CFG_ERR_INVALID_Q 后面
static const wchar_t* CFG_ERR_MISSING_GROUPS = L"题库至少需要包含 easy、medium、hard 三类题目：";
static const wchar_t* CFG_ERR_MIN_QUESTIONS = L"题库至少需要 10 道有效题目：";

// --- 应用标识 ---
static const wchar_t* CFG_APP_TITLE = L"法律知识答题竞赛系统"; // 窗口标题栏和应用名
static const wchar_t* CFG_APP_TAGLINE = L"V1.0.5";           // 版本号，显示在主页顶部
static const wchar_t* CFG_APP_PAGE_SUBTITLE = L"请选择答题模式";
static const wchar_t* CFG_APP_CARD_TITLE = L"法律知识答题";
static const wchar_t* CFG_APP_CARD_DESC = L"系统随机抽题，不同模式采用独立的答题规则。";

// --- 答题规则 ---
static const wchar_t* CFG_RULES_TITLE = L"答题规则";
static const wchar_t* CFG_RULES[] = {                              // 字符串数组：6 条答题规则
    L"1. 单选题只能选择一个答案；多选题可选择多个答案；填空题请直接输入答案。",
    L"2. 多选题须与标准答案完全一致，漏选或多选均不得分。",
    L"3. 填空/简答题提交后展示参考答案，不进行正误判定和分值计算。",
    L"4. 单选题和多选题每题限时30秒；填空/简答题不限时。",
    L"5. 单选题和多选题超时后本题锁定，请点击\"下一题\"继续作答。",
    L"6. 答题过程中可点击右上角\"返回主页\"按钮放弃本次答题，需二次确认。"
};                                                              // } 结束数组初始化
static const int CFG_RULES_COUNT = 6;                           // 规则总数（用于 for 循环边界）

// --- 按钮文案 ---
static const wchar_t* CFG_BTN_SINGLE = L"必答题";               // 单选模式按钮的文案
static const wchar_t* CFG_BTN_MULTIPLE = L"抢答题";             // 多选模式按钮的文案
static const wchar_t* CFG_BTN_FILL = L"风险题";                 // 填空模式按钮的文案
static const wchar_t* CFG_BTN_RETURN_HOME = L"返回主页";        // "返回主页"按钮
static const wchar_t* CFG_BTN_CONFIRM = L"确认答案";            // 尚未答完最后一题时的按钮文案
static const wchar_t* CFG_BTN_NEXT = L"下一题";                 // 已答完本题但还没到最后一题时的按钮文案
static const wchar_t* CFG_BTN_SUBMIT = L"提交结果";             // 答完最后一题后的按钮文案
static const wchar_t* CFG_BTN_RESTART = L"再次答题";            // 结果页重新开始的按钮文案

// --- 题型提示 ---
static const wchar_t* CFG_HINT_SINGLE = L"请选择一个正确答案后确认";  // 单选题页面上的引导文字
static const wchar_t* CFG_HINT_MULTIPLE = L"请选择所有正确答案后确认";
static const wchar_t* CFG_HINT_FILL = L"请在下方输入框中填写答案，提交后显示参考答案";

// --- 难度名称 ---
static const wchar_t* CFG_DIFF_NAMES[] = {L"简单题", L"中档题", L"高档题"};
                                                         // 难度等级名称映射数组：idx 0→简单, 1→中档, 2→高档

// --- 指标标签 ---
static const wchar_t* CFG_METRIC_PROGRESS = L"当前进度";        // "第 N/M 题" 指标的标签
static const wchar_t* CFG_METRIC_DIFFICULTY = L"当前难度";      // （预留字段，暂未使用）
static const wchar_t* CFG_METRIC_REMAINING = L"本题剩余";       // 倒计时指标标签
static const wchar_t* CFG_METRIC_TOTAL_TIME = L"总用时";        // 累计时间指标标签
static const wchar_t* CFG_METRIC_MODE = L"答题模式";            // 显示当前模式的标签
static const wchar_t* CFG_METRIC_CORRECT_TOTAL = L"答对 / 总题数";
static const wchar_t* CFG_METRIC_DURATION = L"全程用时";        // 结果页显示总耗时
static const wchar_t* CFG_METRIC_END_TIME = L"答题结束";       // 结果页显示结束时间

// --- 结果页 ---
static const wchar_t* CFG_RESULT_TITLE = L"答题结果";
static const wchar_t* CFG_RESULT_SCORE_LABEL = L"总分";
static const wchar_t* CFG_METRIC_SELECTED_SCORE = L"选择分值";
static const wchar_t* CFG_SCORE_SELECT_TITLE = L"请选择本次答题分值";
static const wchar_t* CFG_SCORE_SELECT_HINT = L"选择分值后进入填空/简答题";
static const wchar_t* CFG_SCORE_SUFFIX = L"分";                 // 分数值的后缀（如"30分"）

// --- 反馈文案 ---
static const wchar_t* CFG_FEEDBACK_CORRECT = L"回答正确。";
static const wchar_t* CFG_FEEDBACK_TIMEOUT = L"答题超时，本题按错误结算。";
static const wchar_t* CFG_FEEDBACK_WRONG_PREFIX = L"回答错误，正确答案：";
static const wchar_t* CFG_FEEDBACK_FILL_REFERRAL = L"参考答案：";
static const wchar_t* CFG_FEEDBACK_SUFFIX = L"。";             // 反馈文案的句末标点

// --- 填空题 ---
static const wchar_t* CFG_FILL_PLACEHOLDER = L"";               // 编辑框占位符（空字符串表示不显示文字）
static const wchar_t* CFG_FILL_UNANSWERED = L"（未作答）";      // 用户没填答案时的占位显示
static const wchar_t* CFG_FILL_PROMPT = L"请先在输入框中填写答案。";  // 没填就提交的提示弹窗
static const wchar_t* CFG_FILL_USER_ANSWER_LABEL = L"你的答案：";   // 填空结果显示的标签

// --- 选项分隔符 ---
static const wchar_t* CFG_OPTION_SEPARATOR = L"、";             // 选项之间的分隔符（如"A、B、C"）

// --- 确认对话框 ---
static const wchar_t* CFG_CONFIRM_TITLE = L"返回主页确认";
static const wchar_t* CFG_CONFIRM_MESSAGE = L"确定要放弃本次答题并返回主页吗？\n本次答题记录将被丢弃，无法恢复。";
                                                         // \n：转义字符，表示换行（MessageBox 会识别）

// --- 音效 ---
static const int SOUND_CORRECT_ID = 1001;              // 答对音效在资源文件中的编号
static const int SOUND_WRONG_ID = 1002;                // 答错音效编号
static const int SOUND_TIMEOUT_ID = 1003;              // 超时音效编号

static const COLORREF CLR_SCORE_BUTTON_SECONDARY = RGB(0, 112, 56);  // 风险题按钮渐变二级色
static const COLORREF CLR_BACK_BUTTON_SECONDARY = RGB(160, 30, 30);  // 返回按钮渐变二级色
                                                         // COLORREF：Windows 颜色类型，RGB(r,g,b) 是 32 位值
