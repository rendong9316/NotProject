#pragma once
#include <windows.h>

// --- 各题型固定答题数量 ---
static const int QUESTION_COUNT_SINGLE = 3;
static const int QUESTION_COUNT_MULTIPLE = 10;
static const int QUESTION_COUNT_FILL = 2;
static const int FILL_SCORE_OPTION_COUNT = 6;
static const int FILL_SCORE_OPTIONS[FILL_SCORE_OPTION_COUNT] = {10, 20, 30, 40, 50, 60};
static const int NO_SCORE = 0;
static const int NO_TIME_SECONDS = 0;

// --- 每题限时（秒） ---
static const int QUESTION_TIME_LIMIT_SECONDS = 30;
static const int SCORE_SELECT_CARD_WIDTH = 760;
static const int SCORE_SELECT_CARD_HEIGHT = 430;
static const int SCORE_SELECT_BUTTON_WIDTH = 210;
static const int SCORE_SELECT_BUTTON_HEIGHT = 58;
static const int SCORE_SELECT_BUTTON_GAP = 24;
static const int SCORE_SELECT_COLUMNS = 3;
static const int SCORE_SELECT_ROWS = 2;
static const int SCORE_SELECT_CARD_Y = 120;
static const int SCORE_SELECT_TITLE_Y = 34;
static const int SCORE_SELECT_BUTTON_Y = 150;
static const int SCORE_SELECT_BUTTON_ROW_STEP = 90;
static const int UI_CENTER_DIVISOR = 2;
static const int PAGE_HORIZONTAL_MARGIN = 80;
static const int SCORE_SELECT_CARD_RADIUS = 24;
static const int SCORE_SELECT_TITLE_FONT_SIZE = 25;
static const int SCORE_SELECT_HINT_FONT_SIZE = 14;
static const int SCORE_SELECT_TITLE_HEIGHT = 42;
static const int SCORE_SELECT_HINT_Y = 88;
static const int SCORE_SELECT_HINT_HEIGHT = 30;
static const int QUIZ_METRIC_GAP = 12;
static const int QUIZ_METRIC_SIDE_PADDING = 22;
static const int QUIZ_METRIC_COUNT_UNTIMED = 2;
static const int QUIZ_METRIC_COUNT_TIMED_NO_TOTAL = 3;
static const int QUIZ_METRIC_COUNT_TIMED_WITH_TOTAL = 4;
// CHOICE_OPTION_COUNT is defined in ui_draw.h — don't redefine here
static const int QUESTION_TEXT_OFFSET_X = 34;
static const int QUESTION_TEXT_OFFSET_Y = 20;
static const int QUESTION_TEXT_WIDTH_INSET = 68;
static const int QUESTION_TEXT_HEIGHT = 78;
static const int QUESTION_FONT_MAX_SIZE = 18;
static const int QUESTION_FONT_MIN_SIZE = 10;
static const int QUESTION_FONT_STEP = 1;
static const int QUESTION_MEASURE_HEIGHT = 1000;
static const int QUESTION_HINT_OFFSET_Y = 100;
static const int QUESTION_HINT_HEIGHT = 24;
static const int CHOICE_OPTION_START_OFFSET_Y = 132;
static const int CHOICE_OPTION_LEFT_INSET = 34;
static const int CHOICE_OPTION_WIDTH_INSET = 68;
static const int CHOICE_OPTION_TEXT_OFFSET_X = 62;
static const int CHOICE_OPTION_TEXT_WIDTH_INSET = 78;
static const int CHOICE_FEEDBACK_GAP_Y = 10;
static const int CHOICE_OPTION_FONT_MAX_SIZE = 16;
static const int CHOICE_OPTION_FONT_MIN_SIZE = 10;
static const int CHOICE_FONT_SHRINK_STEPS = 8;
static const int CHOICE_DYNAMIC_QUESTION_MIN_HEIGHT = 36;
static const int CHOICE_DYNAMIC_QUESTION_PADDING = 4;
static const int CHOICE_DYNAMIC_QUESTION_HINT_GAP = 2;
static const int CHOICE_DYNAMIC_HINT_OPTION_GAP = 6;
static const int CHOICE_DYNAMIC_OPTION_MIN_HEIGHT = 44;
static const int CHOICE_DYNAMIC_OPTION_PADDING = 10;
static const int CHOICE_DYNAMIC_OPTION_GAP = 6;
static const int CHOICE_DYNAMIC_BOTTOM_PADDING = 12;
static const int CHOICE_FEEDBACK_HEIGHT = 82;

// --- 窗口类名 ---
static const wchar_t* CFG_CLASS_NAME = L"JusticeQuizAppClass";

// --- 字体 ---
static const wchar_t* CFG_FONT_FAMILY = L"Microsoft YaHei";

// --- 题库资源 ID ---
static const int RES_ID_SINGLE = 2001;
static const int RES_ID_MULTIPLE = 2002;
static const int RES_ID_FILL = 2003;

// --- 错误提示 ---
static const wchar_t* CFG_ERR_LOAD_FAILED = L"题库加载失败";
static const wchar_t* CFG_NO_SELECTION = L"请先选择答案。";
static const wchar_t* CFG_ERR_NO_QUESTIONS = L"题库没有足够的未使用题目。";
static const wchar_t* CFG_ERR_SINGLE_EXHAUSTED = L"本次启动下单选题已全部抽完，请重新启动程序。";

// --- 对话框标题 ---
static const wchar_t* CFG_DLG_TITLE = L"提示";
static const wchar_t* CFG_DLG_ERR = L"题库错误";

// --- 题库错误提示 ---
static const wchar_t* CFG_ERR_FILE_NOT_FOUND = L"未找到题库文件：";
static const wchar_t* CFG_ERR_FILE_READ = L"无法读取题库文件：";
static const wchar_t* CFG_ERR_JSON_ROOT = L"题库 JSON 格式错误：根节点应为数组。";
static const wchar_t* CFG_ERR_JSON_ITEM = L"题库 JSON 格式错误：题目项应为对象。";
static const wchar_t* CFG_ERR_INVALID_Q = L"题库文件中存在无效题目：";
static const wchar_t* CFG_ERR_Q_ID = L"（题目 ID ";
static const wchar_t* CFG_ERR_MISSING_GROUPS = L"题库至少需要包含 easy、medium、hard 三类题目：";
static const wchar_t* CFG_ERR_MIN_QUESTIONS = L"题库至少需要 10 道有效题目：";

// --- 应用标识 ---
static const wchar_t* CFG_APP_TITLE = L"法律知识答题竞赛系统";
static const wchar_t* CFG_APP_TAGLINE = L"V1.0.5";
static const wchar_t* CFG_APP_PAGE_SUBTITLE = L"请选择答题模式";
static const wchar_t* CFG_APP_CARD_TITLE = L"法律知识答题";
static const wchar_t* CFG_APP_CARD_DESC = L"系统随机抽题，不同模式采用独立的答题规则。";

// --- 答题规则 ---
static const wchar_t* CFG_RULES_TITLE = L"答题规则";
static const wchar_t* CFG_RULES[] = {
    L"1. 单选题只能选择一个答案；多选题可选择多个答案；填空题请直接输入答案。",
    L"2. 多选题须与标准答案完全一致，漏选或多选均不得分。",
    L"3. 填空/简答题提交后展示参考答案，不进行正误判定和分值计算。",
    L"4. 单选题和多选题每题限时30秒；填空/简答题不限时。",
    L"5. 单选题和多选题超时后本题锁定，请点击\"下一题\"继续作答。",
    L"6. 答题过程中可点击右上角\"返回主页\"按钮放弃本次答题，需二次确认。"
};
static const int CFG_RULES_COUNT = 6;

// --- 按钮文案 ---
static const wchar_t* CFG_BTN_SINGLE = L"必答题";
static const wchar_t* CFG_BTN_MULTIPLE = L"抢答题";
static const wchar_t* CFG_BTN_FILL = L"风险题";
static const wchar_t* CFG_BTN_RETURN_HOME = L"返回主页";
static const wchar_t* CFG_BTN_CONFIRM = L"确认答案";
static const wchar_t* CFG_BTN_NEXT = L"下一题";
static const wchar_t* CFG_BTN_SUBMIT = L"提交结果";
static const wchar_t* CFG_BTN_RESTART = L"再次答题";

// --- 题型提示 ---
static const wchar_t* CFG_HINT_SINGLE = L"请选择一个正确答案后确认";
static const wchar_t* CFG_HINT_MULTIPLE = L"请选择所有正确答案后确认";
static const wchar_t* CFG_HINT_FILL = L"请在下方输入框中填写答案，提交后显示参考答案";

// --- 难度名称 ---
static const wchar_t* CFG_DIFF_NAMES[] = {L"简单题", L"中档题", L"高档题"};

// --- 指标标签 ---
static const wchar_t* CFG_METRIC_PROGRESS = L"当前进度";
static const wchar_t* CFG_METRIC_DIFFICULTY = L"当前难度";
static const wchar_t* CFG_METRIC_REMAINING = L"本题剩余";
static const wchar_t* CFG_METRIC_TOTAL_TIME = L"总用时";
static const wchar_t* CFG_METRIC_MODE = L"答题模式";
static const wchar_t* CFG_METRIC_CORRECT_TOTAL = L"答对 / 总题数";
static const wchar_t* CFG_METRIC_DURATION = L"全程用时";
static const wchar_t* CFG_METRIC_END_TIME = L"答题结束";
static const wchar_t* CFG_METRIC_DIFF_STATS = L"难度统计";

// --- 结果页 ---
static const wchar_t* CFG_RESULT_TITLE = L"答题结果";
static const wchar_t* CFG_RESULT_SCORE_LABEL = L"总分";
static const wchar_t* CFG_METRIC_SELECTED_SCORE = L"选择分值";
static const wchar_t* CFG_SCORE_SELECT_TITLE = L"请选择本次答题分值";
static const wchar_t* CFG_SCORE_SELECT_HINT = L"选择分值后进入填空/简答题";
static const wchar_t* CFG_SCORE_SUFFIX = L"分";

// --- 反馈文案 ---
static const wchar_t* CFG_FEEDBACK_CORRECT = L"回答正确。";
static const wchar_t* CFG_FEEDBACK_TIMEOUT = L"答题超时，本题按错误结算。";
static const wchar_t* CFG_FEEDBACK_WRONG_PREFIX = L"回答错误，正确答案：";
static const wchar_t* CFG_FEEDBACK_FILL_REFERRAL = L"参考答案：";
static const wchar_t* CFG_FEEDBACK_SUFFIX = L"。";

// --- 填空题 ---
static const wchar_t* CFG_FILL_PLACEHOLDER = L"";
static const wchar_t* CFG_FILL_UNANSWERED = L"（未作答）";
static const wchar_t* CFG_FILL_PROMPT = L"请先在输入框中填写答案。";
static const wchar_t* CFG_FILL_USER_ANSWER_LABEL = L"你的答案：";

// --- 选项分隔符 ---
static const wchar_t* CFG_OPTION_SEPARATOR = L"、";

// --- 确认对话框 ---
static const wchar_t* CFG_CONFIRM_TITLE = L"返回主页确认";
static const wchar_t* CFG_CONFIRM_MESSAGE = L"确定要放弃本次答题并返回主页吗？\n本次答题记录将被丢弃，无法恢复。";

// --- 音效 ---
static const int SOUND_CORRECT_ID = 1001;
static const int SOUND_WRONG_ID = 1002;
static const int SOUND_TIMEOUT_ID = 1003;
static const COLORREF CLR_SCORE_BUTTON_SECONDARY = RGB(0, 112, 56);
static const COLORREF CLR_BACK_BUTTON_SECONDARY = RGB(160, 30, 30);
