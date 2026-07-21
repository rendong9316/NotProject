/* quiz.exe - 鸡东司法局法律知识答题竞赛系统 (Win32 + GDI+)
 * 编译: g++ -std=c++11 -o quiz.exe quiz.cpp app_res.obj -lgdi32 -lgdiplus -lwinmm -DUNICODE -D_UNICODE -mwindows
 */

#include <windows.h>
#ifndef IDI_ICON
#define IDI_ICON 101
#endif
#include <gdiplus.h>
#include <string>
#include <vector>
#include <algorithm>
#include <sstream>
#include <chrono>
#include <ctime>
#include <cwctype>
#include <cwchar>
#include <cstdio>
#include <mmsystem.h>
#include <random>
#pragma comment(lib, "winmm.lib")

#ifndef DWMWA_USE_IMMERSIVE_DARK_MODE
#define DWMWA_USE_IMMERSIVE_DARK_MODE 20
#endif
#ifndef DWMWA_CAPTION_COLOR
#define DWMWA_CAPTION_COLOR 35
#endif
#ifndef DWMWA_TEXT_COLOR
#define DWMWA_TEXT_COLOR 36
#endif

#ifndef GET_X_LPARAM
#define GET_X_LPARAM(lp) ((int)(short)LOWORD(lp))
#endif
#ifndef GET_Y_LPARAM
#define GET_Y_LPARAM(lp) ((int)(short)HIWORD(lp))
#endif

using namespace Gdiplus;

// ======================== 配置常量区 ========================
// 所有用户可见的文案、路径、提示语集中在此，便于维护与本地化。

// --- 各题型固定答题数量 ---
static const int QUESTION_COUNT_SINGLE = 5;
static const int QUESTION_COUNT_MULTIPLE = 5;
static const int QUESTION_COUNT_FILL = 5;

// --- 每题限时（秒） ---
static const int QUESTION_TIME_LIMIT_SECONDS = 30;

// --- 窗口类名 ---
static const wchar_t* CFG_CLASS_NAME = L"JusticeQuizAppClass";

// --- 字体 ---
static const wchar_t* CFG_FONT_FAMILY = L"Microsoft YaHei";

// --- 题库文件路径 ---
static const wchar_t* CFG_FILE_SINGLE = L"questions.json";
static const wchar_t* CFG_FILE_MULTIPLE = L"multiple_questions_from_xlsx.json";
static const wchar_t* CFG_FILE_FILL = L"fill_questions.json";

// --- 错误提示 ---
static const wchar_t* CFG_ERR_LOAD_FAILED = L"题库加载失败";
static const wchar_t* CFG_NO_SELECTION = L"请先选择答案。";
static const wchar_t* CFG_ERR_NO_QUESTIONS = L"题库没有足够的未使用题目。";

// --- 对话框标题 ---
static const wchar_t* CFG_DLG_TITLE = L"提示";
static const wchar_t* CFG_DLG_ERR = L"题库错误";

// --- 题库错误提示 ---
// 移除难度分组校验
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
static const wchar_t* CFG_APP_TAGLINE = L"V1.0.0";
static const wchar_t* CFG_APP_PAGE_SUBTITLE = L"请选择答题模式";
static const wchar_t* CFG_APP_CARD_TITLE = L"法律知识答题";
static const wchar_t* CFG_APP_CARD_DESC = L"系统根据答题情况动态调整难度。";

// --- 答题规则 ---
static const wchar_t* CFG_RULES_TITLE = L"答题规则";
static const wchar_t* CFG_RULES[] = {
    L"1. 单选题只能选择一个答案；多选题可选择多个答案；填空题请直接输入答案。",
    L"2. 多选题须与标准答案完全一致，漏选或多选均不得分。",
    L"3. 填空题答案不区分大小写与首尾空格，与标准答案或备选答案一致即判正确。",
    L"4. 每题限时30秒；超时后本题锁定并按错误结算。",
    L"5. 超时不会自动跳题，请点击\"下一题\"继续作答。",
    L"6. 答题过程中可点击右上角\"返回主页\"按钮放弃本次答题，需二次确认。"
};
static const int CFG_RULES_COUNT = 6;

// --- 按钮文案 ---
static const wchar_t* CFG_BTN_SINGLE = L"单选题模式";
static const wchar_t* CFG_BTN_MULTIPLE = L"多选题模式";
static const wchar_t* CFG_BTN_FILL = L"填空题模式";
static const wchar_t* CFG_BTN_RETURN_HOME = L"返回主页";
static const wchar_t* CFG_BTN_CONFIRM = L"确认答案";
static const wchar_t* CFG_BTN_NEXT = L"下一题";
static const wchar_t* CFG_BTN_SUBMIT = L"提交结果";
static const wchar_t* CFG_BTN_RESTART = L"再次答题";

// --- 题型提示 ---
static const wchar_t* CFG_HINT_SINGLE = L"请选择一个正确答案后确认";
static const wchar_t* CFG_HINT_MULTIPLE = L"请选择所有正确答案后确认";
static const wchar_t* CFG_HINT_FILL = L"请在下方输入框中填写答案后确认（不区分大小写与首尾空格）";

// --- 难度名称 ---
// 移除难度概念，保留常量供兼容
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

// --- 反馈文案 ---
static const wchar_t* CFG_FEEDBACK_CORRECT = L"回答正确。";
static const wchar_t* CFG_FEEDBACK_TIMEOUT = L"答题超时，本题按错误结算。";
static const wchar_t* CFG_FEEDBACK_WRONG_PREFIX = L"回答错误，正确答案：";
static const wchar_t* CFG_FEEDBACK_FILL_CORRECT = L"回答正确。";
static const wchar_t* CFG_FEEDBACK_FILL_TIMEOUT = L"答题超时，本题按错误结算。正确答案：";
static const wchar_t* CFG_FEEDBACK_FILL_WRONG = L"回答错误，正确答案：";
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
// WAV 资源 ID（嵌入 app.rc）
static const int SOUND_CORRECT_ID = 1001;
static const int SOUND_WRONG_ID = 1002;
static const int SOUND_TIMEOUT_ID = 1003;

// ======================== 配置常量区结束 ========================

struct Question {
    int id = 0;
    int difficulty = 0;
    std::wstring q;
    std::wstring opts[4];
    std::vector<int> answers;
    std::wstring exp;
    std::wstring fillAnswer;
    std::vector<std::wstring> fillAlts;
};

struct QuestionBank {
    std::vector<Question> all;  // 扁平题库，不再按难度分组

    bool ready() const { return !all.empty(); }
    int size() const { return (int)all.size(); }
};

enum Page { PAGE_HOME, PAGE_QUIZ, PAGE_RESULT };
enum QuizMode { MODE_SINGLE, MODE_MULTIPLE, MODE_FILL };

struct QuizState {
    Page page = PAGE_HOME;
    QuizMode mode = MODE_SINGLE;
    int qNum = 0;
    int total = 10;
    int correct = 0;
    int wrong = 0;
    bool answered = false;
    bool lastCorrect = false;
    bool timedOut = false;
    std::vector<bool> used;  // 单一向量，标记每题是否已出
    int curQIdx = -1;
    bool selected[4] = {false, false, false, false};
    std::wstring userFill;
    int settledSeconds = 0;
    std::chrono::system_clock::time_point quizStart;
    std::chrono::system_clock::time_point quizEnd;
    std::chrono::steady_clock::time_point questionStart;
    std::vector<int> questionSeconds;
    bool flashVisible = false;

    void resetQuiz(const QuestionBank& bank) {
        qNum = 0;
        correct = 0;
        wrong = 0;
        answered = false;
        lastCorrect = false;
        timedOut = false;
        curQIdx = -1;
        selected[0] = selected[1] = selected[2] = selected[3] = false;
        flashVisible = false;
        userFill.clear();
        settledSeconds = 0;
        questionSeconds.clear();
        used.assign(bank.all.size(), false);
    }
};

QuestionBank g_banks[3];
QuizState g_state;

HWND g_hwndMain = nullptr;
HWND g_hwndEdit = nullptr;
HFONT g_hfontEdit = nullptr;
int g_clientW = 2440, g_clientH = 1760;
int g_w = 2440, g_h = 1760;
HDC g_hdcMem = nullptr;
HBITMAP g_hbmMem = nullptr;
float g_fontScale = 2.0f;

const int INITIAL_WINDOW_W = 2440;
const int INITIAL_WINDOW_H = 1760;

static const COLORREF CLR_BG = RGB(232, 238, 247);
static const COLORREF CLR_PANEL = RGB(255, 255, 255);
static const COLORREF CLR_INK = RGB(20, 30, 48);
static const COLORREF CLR_MUTED = RGB(82, 96, 118);
static const COLORREF CLR_NAVY = RGB(13, 42, 86);
static const COLORREF CLR_NAVY_2 = RGB(20, 64, 122);
static const COLORREF CLR_BLUE = RGB(0, 91, 211);
static const COLORREF CLR_GOLD = RGB(214, 143, 28);
static const COLORREF CLR_GREEN = RGB(0, 142, 73);
static const COLORREF CLR_RED = RGB(205, 38, 38);
static const COLORREF CLR_LINE = RGB(196, 208, 224);
static const COLORREF CLR_SOFT = RGB(244, 248, 253);

// 全局随机数引擎
std::mt19937 rng;

std::wstring Utf8ToWide(const std::string& s) {
    if (s.empty()) return L"";
    int len = MultiByteToWideChar(CP_UTF8, 0, s.data(), (int)s.size(), nullptr, 0);
    std::wstring out(len, L'\0');
    MultiByteToWideChar(CP_UTF8, 0, s.data(), (int)s.size(), &out[0], len);
    return out;
}

std::wstring IntToWStr(int n) {
    std::wstringstream ss;
    ss << n;
    return ss.str();
}

std::wstring ExeDir() {
    wchar_t path[MAX_PATH];
    GetModuleFileNameW(nullptr, path, MAX_PATH);
    std::wstring p(path);
    size_t pos = p.find_last_of(L"\\/");
    return pos == std::wstring::npos ? L"." : p.substr(0, pos);
}

std::wstring JoinPath(const std::wstring& dir, const std::wstring& name) {
    if (dir.empty()) return name;
    wchar_t last = dir.back();
    if (last == L'\\' || last == L'/') return dir + name;
    return dir + L"\\" + name;
}

std::wstring FormatTime(std::chrono::system_clock::time_point tp) {
    std::time_t t = std::chrono::system_clock::to_time_t(tp);
    std::tm tmv{};
    std::tm* local = std::localtime(&t);
    if (local) tmv = *local;
    wchar_t buf[64];
    wcsftime(buf, 64, L"%Y-%m-%d %H:%M:%S", &tmv);
    return buf;
}

std::wstring FormatDuration(int seconds) {
    int h = seconds / 3600;
    int m = (seconds % 3600) / 60;
    int s = seconds % 60;
    std::wstringstream ss;
    if (h > 0) ss << h << L"小时";
    if (m > 0 || h > 0) ss << m << L"分";
    ss << s << L"秒";
    return ss.str();
}

void PlaySoundEffect(const wchar_t* soundName) {
    PlaySoundW(soundName, nullptr, SND_ALIAS | SND_ASYNC);
}

void PlayEmbeddedSound(int resourceId) {
    HMODULE hInst = GetModuleHandleW(nullptr);
    HRSRC hRes = FindResourceW(hInst, MAKEINTRESOURCEW(resourceId), L"WAVE");
    if (!hRes) return;
    HGLOBAL hData = LoadResource(hInst, hRes);
    if (!hData) return;
    LPVOID pData = LockResource(hData);
    if (!pData) return;
    PlaySoundW((LPCWSTR)pData, hInst, SND_MEMORY | SND_ASYNC | SND_NODEFAULT);
    UnlockResource(hData);
    FreeResource(hData);
}

struct JsonParser {
    const std::string& s;
    size_t i = 0;

    explicit JsonParser(const std::string& text) : s(text) {}
    void ws() { while (i < s.size() && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) ++i; }
    bool eat(char c) { ws(); if (i < s.size() && s[i] == c) { ++i; return true; } return false; }

    std::string str() {
        ws();
        if (i >= s.size() || s[i] != '"') return "";
        ++i;
        std::string out;
        while (i < s.size()) {
            char c = s[i++];
            if (c == '"') break;
            if (c == '\\' && i < s.size()) {
                char e = s[i++];
                if (e == '"' || e == '\\' || e == '/') out.push_back(e);
                else if (e == 'n') out.push_back('\n');
                else if (e == 'r') out.push_back('\r');
                else if (e == 't') out.push_back('\t');
                else if (e == 'u' && i + 4 <= s.size()) {
                    out += "?";
                    i += 4;
                }
            } else {
                out.push_back(c);
            }
        }
        return out;
    }

    int number() {
        ws();
        int sign = 1;
        if (i < s.size() && s[i] == '-') { sign = -1; ++i; }
        int n = 0;
        while (i < s.size() && s[i] >= '0' && s[i] <= '9') n = n * 10 + (s[i++] - '0');
        return sign * n;
    }

    void skipValue() {
        ws();
        if (i >= s.size()) return;
        if (s[i] == '"') { str(); return; }
        if (s[i] == '{') {
            eat('{');
            while (!eat('}') && i < s.size()) { str(); eat(':'); skipValue(); eat(','); }
            return;
        }
        if (s[i] == '[') {
            eat('[');
            while (!eat(']') && i < s.size()) { skipValue(); eat(','); }
            return;
        }
        while (i < s.size() && s[i] != ',' && s[i] != ']' && s[i] != '}') ++i;
    }

    std::vector<std::wstring> stringArray() {
        std::vector<std::wstring> arr;
        if (!eat('[')) return arr;
        while (!eat(']') && i < s.size()) {
            arr.push_back(Utf8ToWide(str()));
            eat(',');
        }
        return arr;
    }

    std::vector<int> intArray() {
        std::vector<int> arr;
        if (!eat('[')) return arr;
        while (!eat(']') && i < s.size()) {
            arr.push_back(number());
            eat(',');
        }
        return arr;
    }
};

bool LoadQuestionBank(const std::wstring& path, QuizMode mode, QuestionBank& bank, std::wstring& error) {
    HANDLE file = CreateFileW(path.c_str(), GENERIC_READ, FILE_SHARE_READ, nullptr, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (file == INVALID_HANDLE_VALUE) {
        error = CFG_ERR_FILE_NOT_FOUND + path;
        return false;
    }
    LARGE_INTEGER fileSize;
    if (!GetFileSizeEx(file, &fileSize) || fileSize.QuadPart > 0x7fffffff) {
        CloseHandle(file);
        error = CFG_ERR_FILE_READ + path;
        return false;
    }
    std::string text((size_t)fileSize.QuadPart, '\0');
    DWORD bytesRead = 0;
    bool readOk = text.empty() || ReadFile(file, &text[0], (DWORD)text.size(), &bytesRead, nullptr);
    CloseHandle(file);
    if (!readOk || bytesRead != text.size()) {
        error = CFG_ERR_FILE_READ + path;
        return false;
    }
    if (text.size() >= 3 && (unsigned char)text[0] == 0xEF && (unsigned char)text[1] == 0xBB && (unsigned char)text[2] == 0xBF) text.erase(0, 3);

    QuestionBank loaded;
    JsonParser p(text);
    if (!p.eat('[')) {
        error = CFG_ERR_JSON_ROOT;
        return false;
    }
    while (!p.eat(']') && p.i < text.size()) {
        if (!p.eat('{')) { error = CFG_ERR_JSON_ITEM; return false; }
        Question q;
        bool hasAnswer = false;
        bool hasFourOptions = false;
        bool hasFillAnswer = false;
        while (!p.eat('}') && p.i < text.size()) {
            std::string key = p.str();
            p.eat(':');
            if (key == "id") q.id = p.number();
            else if (key == "difficulty") { /* 难度不再参与抽题逻辑 */ }
            else if (key == "question") q.q = Utf8ToWide(p.str());
            else if (key == "options") {
                auto arr = p.stringArray();
                hasFourOptions = arr.size() == 4;
                for (int k = 0; k < 4 && k < (int)arr.size(); ++k) q.opts[k] = arr[k];
            }
            else if (key == "answer") {
                q.answers.assign(1, p.number());
                hasAnswer = true;
            }
            else if (key == "answers") {
                q.answers = p.intArray();
                hasAnswer = true;
            }
            else if (key == "fill_answer") {
                q.fillAnswer = Utf8ToWide(p.str());
                hasFillAnswer = !q.fillAnswer.empty();
            }
            else if (key == "fill_alternatives" || key == "alternatives") {
                q.fillAlts = p.stringArray();
            }
            else if (key == "explanation") q.exp = Utf8ToWide(p.str());
            else p.skipValue();
            p.eat(',');
        }
        std::sort(q.answers.begin(), q.answers.end());
        bool duplicateAnswer = std::adjacent_find(q.answers.begin(), q.answers.end()) != q.answers.end();
        bool validAnswers = hasAnswer && !q.answers.empty() && !duplicateAnswer;
        for (int answer : q.answers) validAnswers = validAnswers && answer >= 0 && answer < 4;
        bool validOptions = hasFourOptions;
        for (int k = 0; k < 4; ++k) validOptions = validOptions && !q.opts[k].empty();
        bool validCount = mode == MODE_SINGLE ? q.answers.size() == 1 : q.answers.size() >= 2;
        bool valid;
        if (mode == MODE_FILL) {
            valid = !q.q.empty() && hasFillAnswer;
        } else {
            valid = !q.q.empty() && validOptions && validAnswers && validCount;
        }
        if (!valid) {
            error = CFG_ERR_INVALID_Q + path + CFG_ERR_Q_ID + IntToWStr(q.id) + L")。";
            return false;
        }
        loaded.all.push_back(q);
        p.eat(',');
    }

    if (!loaded.ready()) {
        error = L"题库文件为空：" + path;
        return false;
    }
    if (loaded.size() < 10) {
        error = CFG_ERR_MIN_QUESTIONS + path;
        return false;
    }
    bank = loaded;
    return true;
}

void UpdateLayoutSize() {
    g_w = std::max(720, (int)(g_clientW / g_fontScale));
    g_h = std::max(560, (int)(g_clientH / g_fontScale));
}

int ScaleCoord(int v) {
    return (int)(v * g_fontScale + 0.5f);
}

void ResizeBackBuffer(HWND hwnd) {
    if (g_hdcMem) {
        DeleteObject(g_hbmMem);
        DeleteDC(g_hdcMem);
        g_hdcMem = nullptr;
        g_hbmMem = nullptr;
    }
    InvalidateRect(hwnd, nullptr, FALSE);
}

bool Hit(int mx, int my, int x, int y, int w, int h) {
    return mx >= x && mx <= x + w && my >= y && my <= y + h;
}

void AdjustFont(float delta) {
    g_fontScale += delta;
    if (g_fontScale < 1.0f) g_fontScale = 1.0f;
    if (g_fontScale > 3.0f) g_fontScale = 3.0f;
    UpdateLayoutSize();
    ResizeBackBuffer(g_hwndMain);
}

struct UIRect { int x, y, w, h; };

UIRect HomeButtonRect(int idx) {
    int cw = std::min(g_w - 80, 880);
    int cx = (g_w - cw) / 2, cy = 110;
    int btnW = 210, btnGap = 30;
    int totalBtnW = btnW * 3 + btnGap * 2;
    int btnStartX = cx + (cw - totalBtnW) / 2;
    int btnY = cy + 408;
    return {btnStartX + idx * (btnW + btnGap), btnY, btnW, 56};
}

UIRect ReturnHomeButtonRect() {
    return {g_w - 300, 14, 138, 36};
}

UIRect FillEditRect() {
    int cardX = 38, cardY = 236, cardW = g_w - 76;
    int boxY = cardY + 110;
    return {cardX + 34, boxY, cardW - 68, 56};
}

UIRect ConfirmButtonRect() {
    return {g_w / 2 - 116, g_h - 78, 232, 54};
}

std::wstring TrimString(const std::wstring& s) {
    size_t a = s.find_first_not_of(L" \t\r\n");
    size_t b = s.find_last_not_of(L" \t\r\n");
    if (a == std::wstring::npos) return L"";
    return s.substr(a, b - a + 1);
}

std::wstring LowerString(const std::wstring& s) {
    std::wstring out = s;
    for (size_t i = 0; i < out.size(); ++i) out[i] = (wchar_t)std::towlower(out[i]);
    return out;
}

bool CheckFillAnswer(const std::wstring& user, const Question& q) {
    std::wstring u = LowerString(TrimString(user));
    if (u.empty()) return false;
    if (u == LowerString(TrimString(q.fillAnswer))) return true;
    for (const auto& alt : q.fillAlts) {
        if (u == LowerString(TrimString(alt))) return true;
    }
    return false;
}

void ApplyEditFont() {
    if (!g_hwndEdit) return;
    int px = (int)(18 * g_fontScale + 0.5f);
    if (g_hfontEdit) DeleteObject(g_hfontEdit);
    g_hfontEdit = CreateFontW(-px, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE,
                              DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
                              CLEARTYPE_QUALITY, FF_DONTCARE, CFG_FONT_FAMILY);
    SendMessageW(g_hwndEdit, WM_SETFONT, (WPARAM)g_hfontEdit, TRUE);
}

void EnsureEditControl(HWND parent) {
    if (g_hwndEdit) return;
    g_hwndEdit = CreateWindowExW(WS_EX_CLIENTEDGE, L"EDIT", L"",
                                 WS_CHILD | ES_AUTOHSCROLL,
                                 0, 0, 100, 30, parent, nullptr,
                                 GetModuleHandleW(nullptr), nullptr);
    ApplyEditFont();
}

void UpdateEditForState() {
    if (!g_hwndEdit) return;
    bool shouldShow = g_state.page == PAGE_QUIZ
                      && g_state.mode == MODE_FILL
                      && !g_state.answered;
    if (shouldShow) {
        UIRect r = FillEditRect();
        SetWindowPos(g_hwndEdit, HWND_TOP,
                     ScaleCoord(r.x), ScaleCoord(r.y),
                     ScaleCoord(r.w), ScaleCoord(r.h),
                     SWP_SHOWWINDOW | SWP_NOACTIVATE);
        if (g_state.userFill.empty()) {
            SetWindowTextW(g_hwndEdit, L"");
        } else {
            SetWindowTextW(g_hwndEdit, g_state.userFill.c_str());
        }
    } else {
        ShowWindow(g_hwndEdit, SW_HIDE);
    }
}

void DestroyEditControl() {
    if (g_hwndEdit) {
        DestroyWindow(g_hwndEdit);
        g_hwndEdit = nullptr;
    }
    if (g_hfontEdit) {
        DeleteObject(g_hfontEdit);
        g_hfontEdit = nullptr;
    }
}

void ReadEditIntoState() {
    if (!g_hwndEdit) return;
    int len = GetWindowTextLengthW(g_hwndEdit);
    if (len <= 0) { g_state.userFill.clear(); return; }
    std::wstring buf(len + 1, L'\0');
    int got = GetWindowTextW(g_hwndEdit, &buf[0], len + 1);
    if (got < 0) got = 0;
    buf.resize(got);
    g_state.userFill = buf;
}

Color GdiColor(COLORREF cr) { return Color(255, GetRValue(cr), GetGValue(cr), GetBValue(cr)); }

void AddRoundRect(GraphicsPath& path, float x, float y, float w, float h, float r) {
    path.AddArc(x, y, r * 2, r * 2, 180, 90);
    path.AddArc(x + w - r * 2, y, r * 2, r * 2, 270, 90);
    path.AddArc(x + w - r * 2, y + h - r * 2, r * 2, r * 2, 0, 90);
    path.AddArc(x, y + h - r * 2, r * 2, r * 2, 90, 90);
    path.CloseFigure();
}

void FillRoundRect(Graphics& g, float x, float y, float w, float h, float r, Color fillColor) {
    GraphicsPath path;
    AddRoundRect(path, x, y, w, h, r);
    SolidBrush br(fillColor);
    g.FillPath(&br, &path);
}

void FillRoundRectBorder(Graphics& g, float x, float y, float w, float h, float r, Color fillColor, Color borderColor, float penW) {
    FillRoundRect(g, x, y, w, h, r, fillColor);
    GraphicsPath path;
    AddRoundRect(path, x, y, w, h, r);
    Pen pen(borderColor, penW);
    g.DrawPath(&pen, &path);
}

void DrawCard(Graphics& g, float x, float y, float w, float h, float r) {
    FillRoundRect(g, x + 4, y + 6, w, h, r, Color(28, 25, 45, 80));
    FillRoundRectBorder(g, x, y, w, h, r, GdiColor(CLR_PANEL), GdiColor(CLR_LINE), 1.2f);
}

Font* MakeFont(float size, int style = FontStyleRegular) {
    FontFamily ff(CFG_FONT_FAMILY);
    return new Font(&ff, size, (FontStyle)style, UnitPixel);
}

void TextCenter(Graphics& g, const std::wstring& text, Font* font, Color color, float x, float y, float w, float h) {
    RectF rect(x, y, w, h);
    StringFormat fmt;
    fmt.SetAlignment(StringAlignmentCenter);
    fmt.SetLineAlignment(StringAlignmentCenter);
    SolidBrush brush(color);
    g.DrawString(text.c_str(), -1, font, rect, &fmt, &brush);
}

void TextLeft(Graphics& g, const std::wstring& text, Font* font, Color color, float x, float y, float w, float h) {
    RectF rect(x, y, w, h);
    StringFormat fmt;
    fmt.SetTrimming(StringTrimmingEllipsisWord);
    SolidBrush brush(color);
    g.DrawString(text.c_str(), -1, font, rect, &fmt, &brush);
}

void DrawBackground(Graphics& g) {
    LinearGradientBrush bg(RectF(0, 0, (REAL)g_w, (REAL)g_h), Color(255, 240, 244, 249), Color(255, 226, 233, 243), LinearGradientModeVertical);
    g.FillRectangle(&bg, 0, 0, g_w, g_h);
    SolidBrush header(Color(255, 246, 249, 253));
    g.FillRectangle(&header, 0, 0, g_w, 84);
    SolidBrush gold(Color(255, 214, 143, 28));
    g.FillRectangle(&gold, 0, 82, g_w, 3);
}

void DrawHeader(Graphics& g, const std::wstring& rightText = L"") {
    Font* title = MakeFont(24, FontStyleBold);
    Font* sub = MakeFont(13, FontStyleBold);
    TextLeft(g, CFG_APP_TITLE, title, GdiColor(CLR_INK), 34, 14, 500, 34);
    TextLeft(g, CFG_APP_TAGLINE, sub, GdiColor(CLR_INK), 36, 52, 390, 24);
    if (!rightText.empty()) TextLeft(g, rightText, sub, GdiColor(CLR_INK), g_w - 370, 34, 330, 26);
    delete title;
    delete sub;
}

void DrawMetric(Graphics& g, int x, int y, int w, int h, const std::wstring& label, const std::wstring& value, COLORREF accent) {
    FillRoundRectBorder(g, (float)x, (float)y, (float)w, (float)h, 16, Color(255, 255, 255, 255), GdiColor(CLR_LINE), 1.2f);
    FillRoundRect(g, (float)x, (float)y, 8, (float)h, 4, GdiColor(accent));
    Font* lf = MakeFont(12, FontStyleBold);
    Font* vf = MakeFont(18, FontStyleBold);
    TextLeft(g, label, lf, GdiColor(CLR_MUTED), x + 22, y + 10, w - 32, 22);
    TextLeft(g, value, vf, GdiColor(accent), x + 22, y + 34, w - 32, 30);
    delete lf;
    delete vf;
}

void DrawButton(Graphics& g, int x, int y, int w, int h, const std::wstring& text, COLORREF c1, COLORREF c2, bool outline = false) {
    if (outline) FillRoundRectBorder(g, (float)x, (float)y, (float)w, (float)h, 12, Color(255, 255, 255, 255), GdiColor(c1), 1.5f);
    else {
        LinearGradientBrush br(RectF((REAL)x, (REAL)y, (REAL)w, (REAL)h), GdiColor(c1), GdiColor(c2), LinearGradientModeHorizontal);
        GraphicsPath path;
        AddRoundRect(path, (float)x, (float)y, (float)w, (float)h, 12);
        g.FillPath(&br, &path);
    }
    Font* f = MakeFont(15, FontStyleBold);
    TextCenter(g, text, f, outline ? GdiColor(c1) : Color(255, 255, 255, 255), (float)x, (float)y, (float)w, (float)h);
    delete f;
}

void DrawFontControls(Graphics& g) {
    Font* f = MakeFont(12, FontStyleBold);
    FillRoundRectBorder(g, (float)(g_w - 154), 16, 48, 32, 10, Color(255, 255, 255, 255), GdiColor(CLR_GOLD), 1.6f);
    FillRoundRectBorder(g, (float)(g_w - 98), 16, 48, 32, 10, Color(255, 255, 255, 255), GdiColor(CLR_GOLD), 1.6f);
    TextCenter(g, L"A-", f, GdiColor(CLR_NAVY), (float)(g_w - 154), 17, 48, 30);
    TextCenter(g, L"A+", f, GdiColor(CLR_NAVY), (float)(g_w - 98), 17, 48, 30);
    delete f;
}

void DrawHomePage(Graphics& g) {
    DrawBackground(g);
    DrawHeader(g, CFG_APP_PAGE_SUBTITLE);
    DrawFontControls(g);

    int cw = std::min(g_w - 80, 880), ch = 540;
    int cx = (g_w - cw) / 2, cy = 110;
    DrawCard(g, (float)cx, (float)cy, (float)cw, (float)ch, 24);

    Font* title = MakeFont(26, FontStyleBold);
    Font* sub = MakeFont(14);
    Font* h = MakeFont(16, FontStyleBold);
    Font* body = MakeFont(13);
    TextCenter(g, CFG_APP_CARD_TITLE, title, GdiColor(CLR_NAVY), cx, cy + 28, cw, 42);
    TextCenter(g, CFG_APP_CARD_DESC, sub, GdiColor(CLR_MUTED), cx + 30, cy + 76, cw - 60, 30);

    int bx = cx + 50, by = cy + 126, bw = cw - 100, bh = 252;
    FillRoundRect(g, (float)bx, (float)by, (float)bw, (float)bh, 18, GdiColor(CLR_SOFT));
    TextLeft(g, CFG_RULES_TITLE, h, GdiColor(CLR_NAVY), bx + 28, by + 20, 180, 28);
    for (int i = 0; i < CFG_RULES_COUNT; ++i) TextLeft(g, CFG_RULES[i], body, GdiColor(CLR_INK), bx + 30, by + 60 + i * 32, bw - 60, 27);

    int btnW = 210, btnGap = 30;
    int totalBtnW = btnW * 3 + btnGap * 2;
    int btnStartX = cx + (cw - totalBtnW) / 2;
    int btnY = cy + 408;

    DrawButton(g, btnStartX, btnY, btnW, 56, CFG_BTN_SINGLE, CLR_BLUE, RGB(30, 64, 175));
    DrawButton(g, btnStartX + (btnW + btnGap), btnY, btnW, 56, CFG_BTN_MULTIPLE, CLR_NAVY, CLR_NAVY_2);
    DrawButton(g, btnStartX + (btnW + btnGap) * 2, btnY, btnW, 56, CFG_BTN_FILL, CLR_GREEN, RGB(0, 112, 56));

    Font* countFont = MakeFont(12, FontStyleBold);
    TextCenter(g, IntToWStr(QUESTION_COUNT_SINGLE) + L" 题", countFont, GdiColor(CLR_BLUE), btnStartX, btnY + 58, btnW, 24);
    TextCenter(g, IntToWStr(QUESTION_COUNT_MULTIPLE) + L" 题", countFont, GdiColor(CLR_NAVY), btnStartX + btnW + btnGap, btnY + 58, btnW, 24);
    TextCenter(g, IntToWStr(QUESTION_COUNT_FILL) + L" 题", countFont, GdiColor(CLR_GREEN), btnStartX + (btnW + btnGap) * 2, btnY + 58, btnW, 24);
    delete countFont;
    delete title; delete sub; delete h; delete body;
}

QuestionBank& ActiveBank() {
    if (g_state.mode == MODE_MULTIPLE) return g_banks[1];
    if (g_state.mode == MODE_FILL) return g_banks[2];
    return g_banks[0];
}

std::wstring ModeName() {
    switch (g_state.mode) {
        case MODE_MULTIPLE: return L"多选题模式";
        case MODE_FILL: return L"填空题模式";
        default: return L"单选题模式";
    }
}

const Question* CurrentQuestion() {
    if (g_state.curQIdx < 0 || g_state.curQIdx >= (int)ActiveBank().all.size()) return nullptr;
    return &ActiveBank().all[g_state.curQIdx];
}

// 随机从未使用题目中选一道
int PickRandomUnused() {
    const auto& bank = ActiveBank();
    std::vector<int> available;
    for (int i = 0; i < (int)bank.all.size(); ++i) {
        if (!g_state.used[i]) available.push_back(i);
    }
    if (available.empty()) return -1;
    std::uniform_int_distribution<int> dist(0, (int)available.size() - 1);
    int idx = dist(rng);
    return available[idx];
}

bool StartQuestion() {
    int idx = PickRandomUnused();
    if (idx < 0) return false;
    g_state.curQIdx = idx;
    g_state.used[idx] = true;
    g_state.selected[0] = g_state.selected[1] = g_state.selected[2] = g_state.selected[3] = false;
    g_state.userFill.clear();
    g_state.answered = false;
    g_state.timedOut = false;
    g_state.settledSeconds = 0;
    g_state.questionStart = std::chrono::steady_clock::now();
    return true;
}

void StartQuiz(QuizMode mode) {
    g_state.mode = mode;
    g_state.resetQuiz(ActiveBank());
    if (mode == MODE_MULTIPLE) g_state.total = QUESTION_COUNT_MULTIPLE;
    else if (mode == MODE_FILL) g_state.total = QUESTION_COUNT_FILL;
    else g_state.total = QUESTION_COUNT_SINGLE;
    g_state.page = PAGE_QUIZ;
    g_state.qNum = 1;
    g_state.quizStart = std::chrono::system_clock::now();
    StartQuestion();
}

int QuestionElapsedSeconds() {
    return std::max(0, (int)std::chrono::duration_cast<std::chrono::seconds>(std::chrono::steady_clock::now() - g_state.questionStart).count());
}

int QuestionRemainingSeconds() {
    int elapsed = g_state.answered ? g_state.settledSeconds : QuestionElapsedSeconds();
    return std::max(0, QUESTION_TIME_LIMIT_SECONDS - elapsed);
}

bool HasSelection() {
    for (int i = 0; i < 4; ++i) if (g_state.selected[i]) return true;
    return false;
}

std::vector<int> SelectedAnswers() {
    std::vector<int> answers;
    for (int i = 0; i < 4; ++i) if (g_state.selected[i]) answers.push_back(i);
    return answers;
}

std::wstring AnswerLetters(const Question& question) {
    std::wstringstream ss;
    for (int i = 0; i < (int)question.answers.size(); ++i) {
        if (i) ss << L"、";
        ss << (wchar_t)(L'A' + question.answers[i]);
    }
    return ss.str();
}

void SettleCurrentQuestion(HWND hwnd, bool timeout) {
    if (g_state.answered) return;
    const Question* question = CurrentQuestion();
    if (!question) return;
    int seconds = timeout ? QUESTION_TIME_LIMIT_SECONDS : std::min(QUESTION_TIME_LIMIT_SECONDS, std::max(1, QuestionElapsedSeconds()));
    g_state.questionSeconds.push_back(seconds);
    g_state.settledSeconds = seconds;
    g_state.answered = true;
    g_state.timedOut = timeout;
    g_state.flashVisible = false;
    KillTimer(hwnd, 2);
    bool ok;
    if (g_state.mode == MODE_FILL) {
        if (!timeout) ReadEditIntoState();
        ok = !timeout && CheckFillAnswer(g_state.userFill, *question);
    } else {
        ok = !timeout && SelectedAnswers() == question->answers;
    }
    g_state.lastCorrect = ok;
    if (timeout) {
        PlayEmbeddedSound(SOUND_TIMEOUT_ID);
    } else if (ok) {
        ++g_state.correct;
        PlayEmbeddedSound(SOUND_CORRECT_ID);
    } else {
        ++g_state.wrong;
        PlayEmbeddedSound(SOUND_WRONG_ID);
    }
}

void DrawQuizPage(Graphics& g) {
    DrawBackground(g);
    int remaining = QuestionRemainingSeconds();
    int totalElapsed = (int)std::chrono::duration_cast<std::chrono::seconds>(std::chrono::system_clock::now() - g_state.quizStart).count();
    DrawHeader(g, ModeName());
    DrawFontControls(g);

    const Question* q = CurrentQuestion();
    if (!q) return;

    int topX = 38, topY = 104, topW = g_w - 76, topH = 112;
    DrawCard(g, (float)topX, (float)topY, (float)topW, (float)topH, 18);
    Font* meta = MakeFont(14, FontStyleBold);
    Font* meta2 = MakeFont(13);
    int metricW = (topW - 66) / 4;  // 4列，留出3个间隙(22px)
    bool shouldFlash = remaining <= 5 && g_state.flashVisible;
    DrawMetric(g, topX + 22, topY + 18, metricW, 70, CFG_METRIC_PROGRESS, L"第 " + IntToWStr(g_state.qNum) + L" / " + IntToWStr(g_state.total) + L" 题", CLR_BLUE);
    DrawMetric(g, topX + 34 + metricW, topY + 18, metricW, 70, CFG_METRIC_MODE, ModeName(), CLR_NAVY);
    DrawMetric(g, topX + 46 + metricW * 2, topY + 18, metricW, 70, CFG_METRIC_REMAINING, FormatDuration(remaining), shouldFlash ? CLR_RED : (remaining <= 30 ? CLR_GOLD : CLR_BLUE));
    DrawMetric(g, topX + 58 + metricW * 3, topY + 18, metricW, 70, CFG_METRIC_TOTAL_TIME, FormatDuration(totalElapsed), CLR_RED);
    FillRoundRect(g, (float)(topX + 24), (float)(topY + 96), (float)(topW - 48), 10, 5, Color(255, 219, 227, 238));
    COLORREF progressBarColor = shouldFlash ? CLR_RED : CLR_BLUE;
    FillRoundRect(g, (float)(topX + 24), (float)(topY + 96), (float)((topW - 48) * g_state.qNum / g_state.total), 10, 5, GdiColor(progressBarColor));

    int cardX = 38, cardY = 236, cardW = g_w - 76, cardH = g_h - 336;
    DrawCard(g, (float)cardX, (float)cardY, (float)cardW, (float)cardH, 22);
    Font* qFont = MakeFont(18, FontStyleBold);
    Font* optFont = MakeFont(16);
    TextLeft(g, IntToWStr(g_state.qNum) + L". " + q->q, qFont, GdiColor(CLR_INK), cardX + 34, cardY + 22, cardW - 68, 58);

    UIRect backRect = ReturnHomeButtonRect();
    DrawButton(g, backRect.x, backRect.y, backRect.w, backRect.h, CFG_BTN_RETURN_HOME, CLR_RED, RGB(160, 30, 30), true);

    if (g_state.mode == MODE_FILL) {
        TextLeft(g, CFG_HINT_FILL, meta2, GdiColor(CLR_MUTED), cardX + 34, cardY + 78, cardW - 68, 24);

        UIRect editRect = FillEditRect();
        if (g_state.answered) {
            bool ok = g_state.lastCorrect;
            Color fill = ok ? Color(255, 231, 248, 238) : Color(255, 254, 226, 226);
            Color border = ok ? GdiColor(CLR_GREEN) : GdiColor(CLR_RED);
            FillRoundRectBorder(g, (float)editRect.x, (float)editRect.y, (float)editRect.w, (float)editRect.h, 14, fill, border, 2.4f);
            std::wstring shown = g_state.userFill.empty() ? CFG_FILL_UNANSWERED : g_state.userFill;
            TextLeft(g, CFG_FILL_USER_ANSWER_LABEL + shown, optFont, ok ? GdiColor(CLR_GREEN) : GdiColor(CLR_RED), editRect.x + 18, editRect.y + 16, editRect.w - 36, 28);
        }

        if (g_state.answered) {
            int fy = editRect.y + editRect.h + 14;
            bool ok = g_state.lastCorrect;
            FillRoundRectBorder(g, (float)(cardX + 34), (float)fy, (float)(cardW - 68), 82, 14, ok ? Color(255, 231, 248, 238) : Color(255, 254, 226, 226), ok ? GdiColor(CLR_GREEN) : GdiColor(CLR_RED), 2.0f);
            std::wstring fb = ok ? CFG_FEEDBACK_FILL_CORRECT : (g_state.timedOut ? CFG_FEEDBACK_FILL_TIMEOUT : CFG_FEEDBACK_FILL_WRONG) + q->fillAnswer + CFG_FEEDBACK_SUFFIX;
            TextLeft(g, fb, meta, ok ? GdiColor(CLR_GREEN) : GdiColor(CLR_RED), cardX + 54, fy + 12, cardW - 108, 26);
            if (!q->exp.empty()) TextLeft(g, q->exp, meta2, GdiColor(CLR_INK), cardX + 54, fy + 44, cardW - 108, 30);
        }
    } else {
        TextLeft(g, g_state.mode == MODE_MULTIPLE ? CFG_HINT_MULTIPLE : CFG_HINT_SINGLE, meta2, GdiColor(CLR_MUTED), cardX + 34, cardY + 78, cardW - 68, 24);

        int optY = cardY + 110;
        for (int i = 0; i < 4; ++i) {
            int ox = cardX + 34, oy = optY + i * 64, ow = cardW - 68, oh = 52;
            bool selected = g_state.selected[i];
            bool correctAnswer = std::find(q->answers.begin(), q->answers.end(), i) != q->answers.end();
            bool showCorrect = g_state.answered && correctAnswer;
            bool showWrong = g_state.answered && selected && !correctAnswer;
            Color border = selected ? GdiColor(CLR_BLUE) : GdiColor(CLR_LINE);
            Color fill = selected ? Color(255, 224, 238, 255) : Color(255, 255, 255, 255);
            if (showWrong) { fill = Color(255, 254, 226, 226); border = GdiColor(CLR_RED); }
            if (showCorrect) { fill = Color(255, 231, 248, 238); border = GdiColor(CLR_GREEN); }
            FillRoundRectBorder(g, (float)ox, (float)oy, (float)ow, (float)oh, 14, fill, border, selected || showCorrect || showWrong ? 2.4f : 1.5f);
            wchar_t letter[2] = {(wchar_t)(L'A' + i), L'\0'};
            COLORREF letterColor = showCorrect ? CLR_GREEN : showWrong ? CLR_RED : selected ? CLR_BLUE : CLR_NAVY;
            FillRoundRect(g, (float)(ox + 14), (float)(oy + 10), 32, 32, 16, GdiColor(letterColor));
            Font* lf = MakeFont(13, FontStyleBold);
            TextCenter(g, letter, lf, Color(255, 255, 255, 255), ox + 14, oy + 10, 32, 32);
            TextLeft(g, q->opts[i].size() > 2 ? q->opts[i].substr(2) : q->opts[i], optFont, GdiColor(CLR_INK), ox + 62, oy + 12, ow - 78, 32);
            delete lf;
        }

        if (g_state.answered) {
            int fy = optY + 4 * 64 + 10;
            bool ok = g_state.lastCorrect;
            FillRoundRectBorder(g, (float)(cardX + 34), (float)fy, (float)(cardW - 68), 82, 14, ok ? Color(255, 231, 248, 238) : Color(255, 254, 226, 226), ok ? GdiColor(CLR_GREEN) : GdiColor(CLR_RED), 2.0f);
            std::wstring fb = ok ? CFG_FEEDBACK_CORRECT : (g_state.timedOut ? CFG_FEEDBACK_TIMEOUT : CFG_FEEDBACK_WRONG_PREFIX) + AnswerLetters(*q) + CFG_FEEDBACK_SUFFIX;
            TextLeft(g, fb, meta, ok ? GdiColor(CLR_GREEN) : GdiColor(CLR_RED), cardX + 54, fy + 12, cardW - 108, 26);
            if (!q->exp.empty()) TextLeft(g, q->exp, meta2, GdiColor(CLR_INK), cardX + 54, fy + 44, cardW - 108, 30);
        }
    }

    bool finalQ = g_state.answered && g_state.qNum >= g_state.total;
    COLORREF cBtn = finalQ ? CLR_GREEN : CLR_BLUE;
    COLORREF cBtn2 = finalQ ? RGB(0, 112, 56) : RGB(0, 69, 170);
    const wchar_t* btnText = finalQ ? CFG_BTN_SUBMIT : (g_state.answered ? CFG_BTN_NEXT : CFG_BTN_CONFIRM);
    UIRect confirmRect = ConfirmButtonRect();
    DrawButton(g, confirmRect.x, confirmRect.y, confirmRect.w, confirmRect.h, btnText, cBtn, cBtn2);

    delete meta; delete meta2; delete qFont; delete optFont;
}

void DrawResultPage(Graphics& g) {
    DrawBackground(g);
    DrawHeader(g, ModeName());
    DrawFontControls(g);

    int score = (int)((double)g_state.correct / g_state.total * 100.0 + 0.5);
    int totalSeconds = (int)std::chrono::duration_cast<std::chrono::seconds>(g_state.quizEnd - g_state.quizStart).count();
    int cw = std::min(g_w - 80, 760), ch = 500;
    int cx = (g_w - cw) / 2, cy = 120;
    DrawCard(g, (float)cx, (float)cy, (float)cw, (float)ch, 24);

    Font* title = MakeFont(25, FontStyleBold);
    Font* scoreFont = MakeFont(46, FontStyleBold);
    Font* stat = MakeFont(16, FontStyleBold);
    Font* body = MakeFont(13);
    TextCenter(g, CFG_RESULT_TITLE, title, GdiColor(CLR_NAVY), cx, cy + 28, cw, 40);
    TextCenter(g, IntToWStr(score), scoreFont, score >= 80 ? GdiColor(CLR_GREEN) : score >= 60 ? GdiColor(CLR_GOLD) : GdiColor(CLR_RED), cx, cy + 78, cw, 70);
    TextCenter(g, CFG_RESULT_SCORE_LABEL, body, GdiColor(CLR_MUTED), cx, cy + 142, cw, 24);

    int sy = cy + 190;
    std::wstring statsLabels[] = {CFG_METRIC_MODE, CFG_METRIC_CORRECT_TOTAL, CFG_METRIC_DURATION, CFG_METRIC_END_TIME};
    std::wstring statsValues[][2] = {
        {statsLabels[0], ModeName()},
        {statsLabels[1], IntToWStr(g_state.correct) + L" / " + IntToWStr(g_state.total)},
        {statsLabels[2], FormatDuration(totalSeconds)},
        {statsLabels[3], FormatTime(g_state.quizEnd)}
    };
    for (int i = 0; i < 4; ++i) {
        int y = sy + i * 42;
        FillRoundRect(g, (float)(cx + 54), (float)y, (float)(cw - 108), 34, 10, GdiColor(CLR_SOFT));
        TextLeft(g, statsValues[i][0], body, GdiColor(CLR_MUTED), cx + 76, y + 7, 140, 22);
        TextLeft(g, statsValues[i][1], stat, GdiColor(CLR_INK), cx + 220, y + 5, cw - 310, 24);
    }

    DrawButton(g, cx + cw / 2 - 210, cy + ch - 82, 190, 48, CFG_BTN_RESTART, CLR_BLUE, RGB(30, 64, 175));
    DrawButton(g, cx + cw / 2 + 20, cy + ch - 82, 190, 48, CFG_BTN_RETURN_HOME, CLR_NAVY, RGB(34, 70, 118), true);

    delete title; delete scoreFont; delete stat; delete body;
}

void HandleGlobalClick(int mx, int my) {
    bool changed = false;
    if (Hit(mx, my, g_w - 154, 16, 48, 32)) { AdjustFont(-0.06f); changed = true; }
    if (Hit(mx, my, g_w - 98, 16, 48, 32)) { AdjustFont(0.06f); changed = true; }
    if (changed) { ApplyEditFont(); UpdateEditForState(); }
}

void ApplyTitleBarStyle(HWND hwnd) {
    HMODULE dwm = LoadLibraryW(L"dwmapi.dll");
    if (!dwm) return;
    typedef HRESULT (WINAPI *DwmSetWindowAttributeFn)(HWND, DWORD, LPCVOID, DWORD);
    DwmSetWindowAttributeFn setAttr = (DwmSetWindowAttributeFn)GetProcAddress(dwm, "DwmSetWindowAttribute");
    if (setAttr) {
        BOOL dark = TRUE;
        COLORREF caption = RGB(13, 42, 86);
        COLORREF text = RGB(255, 255, 255);
        setAttr(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, &dark, sizeof(dark));
        setAttr(hwnd, DWMWA_CAPTION_COLOR, &caption, sizeof(caption));
        setAttr(hwnd, DWMWA_TEXT_COLOR, &text, sizeof(text));
    }
    FreeLibrary(dwm);
}

LRESULT CALLBACK WndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    switch (msg) {
    case WM_SETCURSOR: {
        DWORD dwMousePos = GetMessagePos();
        short mx = GET_X_LPARAM(dwMousePos);
        short my = GET_Y_LPARAM(dwMousePos);
        mx = (short)(mx / g_fontScale);
        my = (short)(my / g_fontScale);

        if (g_state.page == PAGE_HOME) {
            for (int i = 0; i < 3; ++i) {
                UIRect r = HomeButtonRect(i);
                if (Hit(mx, my, r.x, r.y, r.w, r.h)) {
                    SetCursor(LoadCursorW(nullptr, IDC_HAND));
                    return TRUE;
                }
            }
        } else if (g_state.page == PAGE_QUIZ) {
            UIRect backRect = ReturnHomeButtonRect();
            if (Hit(mx, my, backRect.x, backRect.y, backRect.w, backRect.h)) {
                SetCursor(LoadCursorW(nullptr, IDC_HAND));
                return TRUE;
            }
            if (g_state.mode != MODE_FILL) {
                int cardX = 38, cardY = 236, cardW = g_w - 76, optY = cardY + 110;
                for (int i = 0; i < 4; ++i) {
                    int ox = cardX + 34, oy = optY + i * 64, ow = cardW - 68, oh = 52;
                    if (!g_state.answered && Hit(mx, my, ox, oy, ow, oh)) {
                        SetCursor(LoadCursorW(nullptr, IDC_HAND));
                        return TRUE;
                    }
                }
            }
            UIRect confirmRect = ConfirmButtonRect();
            if (Hit(mx, my, confirmRect.x, confirmRect.y, confirmRect.w, confirmRect.h)) {
                SetCursor(LoadCursorW(nullptr, IDC_HAND));
                return TRUE;
            }
        } else if (g_state.page == PAGE_RESULT) {
            int cw = std::min(g_w - 80, 760), ch = 500, cx = (g_w - cw) / 2, cy = 120;
            if (Hit(mx, my, cx + cw / 2 - 210, cy + ch - 82, 190, 48)) {
                SetCursor(LoadCursorW(nullptr, IDC_HAND));
                return TRUE;
            }
            if (Hit(mx, my, cx + cw / 2 + 20, cy + ch - 82, 190, 48)) {
                SetCursor(LoadCursorW(nullptr, IDC_HAND));
                return TRUE;
            }
        }
        SetCursor(LoadCursorW(nullptr, IDC_ARROW));
        return TRUE;
    }
    case WM_CREATE: {
        g_hwndMain = hwnd;
        ApplyTitleBarStyle(hwnd);
        SetTimer(hwnd, 1, 1000, nullptr);
        EnsureEditControl(hwnd);

        // Force set window icon to override DWM dark title bar behavior
        HINSTANCE hInst = GetModuleHandleW(nullptr);
        HICON hBig = (HICON)LoadImageW(hInst, MAKEINTRESOURCEW(IDI_ICON), IMAGE_ICON, 32, 32, LR_DEFAULTSIZE);
        HICON hSmall = (HICON)LoadImageW(hInst, MAKEINTRESOURCEW(IDI_ICON), IMAGE_ICON, 16, 16, LR_DEFAULTSIZE);
        if (hBig) SendMessageW(hwnd, WM_SETICON, ICON_BIG, (LPARAM)hBig);
        if (hSmall) SendMessageW(hwnd, WM_SETICON, ICON_SMALL, (LPARAM)hSmall);

        return 0;
    }
    case WM_SIZE: {
        g_clientW = std::max(720, (int)LOWORD(lParam));
        g_clientH = std::max(560, (int)HIWORD(lParam));
        UpdateLayoutSize();
        ResizeBackBuffer(hwnd);
        UpdateEditForState();
        return 0;
    }
    case WM_MOUSEWHEEL: {
        if (GetKeyState(VK_CONTROL) & 0x8000) {
            short z = GET_WHEEL_DELTA_WPARAM(wParam);
            AdjustFont(z > 0 ? 0.06f : -0.06f);
            ApplyEditFont();
            UpdateEditForState();
            return 0;
        }
        break;
    }
    case WM_TIMER: {
        if (g_state.page == PAGE_QUIZ && !g_state.answered) {
            int remaining = QuestionRemainingSeconds();
            if (remaining <= 0) {
                SettleCurrentQuestion(hwnd, true);
                UpdateEditForState();
                SetFocus(hwnd);
            } else if (remaining <= 5) {
                // Toggle flash every 500ms using timer 1
                static int flashTick = 0;
                flashTick++;
                g_state.flashVisible = (flashTick % 2 == 0);
                InvalidateRect(hwnd, nullptr, FALSE);
            } else {
                g_state.flashVisible = false;
                InvalidateRect(hwnd, nullptr, FALSE);
            }
        }
        return 0;
    }
    case WM_LBUTTONDOWN: {
        int mx = (int)(GET_X_LPARAM(lParam) / g_fontScale);
        int my = (int)(GET_Y_LPARAM(lParam) / g_fontScale);
        HandleGlobalClick(mx, my);

        if (g_state.page == PAGE_HOME) {
            for (int i = 0; i < 3; ++i) {
                UIRect r = HomeButtonRect(i);
                if (Hit(mx, my, r.x, r.y, r.w, r.h)) {
                    StartQuiz(i == 0 ? MODE_SINGLE : (i == 1 ? MODE_MULTIPLE : MODE_FILL));
                    UpdateEditForState();
                    if (g_state.mode == MODE_FILL && g_hwndEdit) SetFocus(g_hwndEdit);
                    InvalidateRect(hwnd, nullptr, FALSE);
                    return 0;
                }
            }
        } else if (g_state.page == PAGE_QUIZ) {
            UIRect backRect = ReturnHomeButtonRect();
            if (Hit(mx, my, backRect.x, backRect.y, backRect.w, backRect.h)) {
                int rc = MessageBoxW(hwnd,
                    CFG_CONFIRM_MESSAGE,
                    CFG_CONFIRM_TITLE, MB_YESNO | MB_ICONQUESTION | MB_DEFBUTTON2);
                if (rc == IDYES) {
                    g_state.resetQuiz(ActiveBank());
                    g_state.page = PAGE_HOME;
                    UpdateEditForState();
                    SetFocus(hwnd);
                }
                InvalidateRect(hwnd, nullptr, FALSE);
                return 0;
            }
            if (!g_state.answered && QuestionRemainingSeconds() <= 0) SettleCurrentQuestion(hwnd, true);
            if (g_state.mode != MODE_FILL) {
                int cardX = 38, cardY = 236, cardW = g_w - 76, optY = cardY + 110;
                for (int i = 0; i < 4; ++i) {
                    int ox = cardX + 34, oy = optY + i * 64, ow = cardW - 68, oh = 52;
                    if (!g_state.answered && Hit(mx, my, ox, oy, ow, oh)) {
                        if (g_state.mode == MODE_SINGLE) {
                            g_state.selected[0] = g_state.selected[1] = g_state.selected[2] = g_state.selected[3] = false;
                            g_state.selected[i] = true;
                        } else {
                            g_state.selected[i] = !g_state.selected[i];
                        }
                        InvalidateRect(hwnd, nullptr, FALSE);
                        return 0;
                    }
                }
            }
            UIRect confirmRect = ConfirmButtonRect();
            if (Hit(mx, my, confirmRect.x, confirmRect.y, confirmRect.w, confirmRect.h)) {
                if (!g_state.answered) {
                    if (g_state.mode == MODE_FILL) {
                        ReadEditIntoState();
                        if (TrimString(g_state.userFill).empty()) {
                            MessageBoxW(hwnd, CFG_FILL_PROMPT, CFG_DLG_TITLE, MB_OK | MB_ICONINFORMATION);
                            return 0;
                        }
                    } else if (!HasSelection()) {
                        MessageBoxW(hwnd, CFG_NO_SELECTION, CFG_DLG_TITLE, MB_OK | MB_ICONINFORMATION);
                        return 0;
                    }
                    SettleCurrentQuestion(hwnd, false);
                    UpdateEditForState();
                    SetFocus(hwnd);
                } else if (g_state.qNum >= g_state.total) {
                    g_state.quizEnd = std::chrono::system_clock::now();
                    g_state.page = PAGE_RESULT;
                    UpdateEditForState();
                } else {
                    ++g_state.qNum;
                    if (!StartQuestion()) {
                        --g_state.qNum;
                        MessageBoxW(hwnd, CFG_ERR_NO_QUESTIONS, CFG_DLG_ERR, MB_OK | MB_ICONERROR);
                    }
                    UpdateEditForState();
                    if (g_state.mode == MODE_FILL && g_hwndEdit) SetFocus(g_hwndEdit);
                }
                InvalidateRect(hwnd, nullptr, FALSE);
            }
        } else if (g_state.page == PAGE_RESULT) {
            int cw = std::min(g_w - 80, 760), ch = 500, cx = (g_w - cw) / 2, cy = 120;
            if (Hit(mx, my, cx + cw / 2 - 210, cy + ch - 82, 190, 48)) {
                StartQuiz(g_state.mode);
                UpdateEditForState();
                if (g_state.mode == MODE_FILL && g_hwndEdit) SetFocus(g_hwndEdit);
            } else if (Hit(mx, my, cx + cw / 2 + 20, cy + ch - 82, 190, 48)) {
                g_state.resetQuiz(ActiveBank());
                g_state.page = PAGE_HOME;
                UpdateEditForState();
            }
            InvalidateRect(hwnd, nullptr, FALSE);
        }
        return 0;
    }
    case WM_PAINT: {
        PAINTSTRUCT ps;
        HDC hdc = BeginPaint(hwnd, &ps);
        RECT rc;
        GetClientRect(hwnd, &rc);
        int clientW = rc.right - rc.left, clientH = rc.bottom - rc.top;
        if (!g_hdcMem) {
            g_hdcMem = CreateCompatibleDC(hdc);
            g_hbmMem = CreateCompatibleBitmap(hdc, clientW, clientH);
            SelectObject(g_hdcMem, g_hbmMem);
        }
        Graphics memGfx(g_hdcMem);
        memGfx.SetSmoothingMode(SmoothingModeAntiAlias);
        memGfx.SetTextRenderingHint(TextRenderingHintClearTypeGridFit);
        memGfx.SetCompositingQuality(CompositingQualityHighQuality);
        memGfx.ScaleTransform(g_fontScale, g_fontScale);
        if (g_state.page == PAGE_HOME) DrawHomePage(memGfx);
        else if (g_state.page == PAGE_QUIZ) DrawQuizPage(memGfx);
        else DrawResultPage(memGfx);
        BitBlt(hdc, 0, 0, clientW, clientH, g_hdcMem, 0, 0, SRCCOPY);
        EndPaint(hwnd, &ps);
        return 0;
    }
    case WM_ERASEBKGND:
        return 1;
    case WM_DESTROY:
        KillTimer(hwnd, 1);
        DestroyEditControl();
        if (g_hdcMem) {
            DeleteObject(g_hbmMem);
            DeleteDC(g_hdcMem);
        }
        PostQuitMessage(0);
        return 0;
    }
    return DefWindowProcW(hwnd, msg, wParam, lParam);
}

int main() {
    // 初始化随机数引擎
    std::random_device rd;
    rng.seed(rd());

    HMODULE user32 = GetModuleHandleW(L"user32.dll");
    if (user32) {
        typedef BOOL (WINAPI *SetDpiAwareFn)();
        SetDpiAwareFn setDpiAware = (SetDpiAwareFn)GetProcAddress(user32, "SetProcessDPIAware");
        if (setDpiAware) setDpiAware();
    }
    std::wstring error;
    if (!LoadQuestionBank(JoinPath(ExeDir(), CFG_FILE_SINGLE), MODE_SINGLE, g_banks[0], error) ||
        !LoadQuestionBank(JoinPath(ExeDir(), CFG_FILE_MULTIPLE), MODE_MULTIPLE, g_banks[1], error) ||
        !LoadQuestionBank(JoinPath(ExeDir(), CFG_FILE_FILL), MODE_FILL, g_banks[2], error)) {
        MessageBoxW(nullptr, error.c_str(), CFG_ERR_LOAD_FAILED, MB_OK | MB_ICONERROR);
        return 1;
    }
    g_state.page = PAGE_HOME;

    GdiplusStartupInput gdiplusStartupInput;
    ULONG_PTR gdiplusToken;
    GdiplusStartup(&gdiplusToken, &gdiplusStartupInput, nullptr);

    WNDCLASSEXW wc = {};
    wc.cbSize = sizeof(WNDCLASSEXW);
    wc.lpfnWndProc = WndProc;
    wc.hInstance = GetModuleHandleW(nullptr);
    HINSTANCE hInst = GetModuleHandleW(nullptr);
    wc.hCursor = LoadCursorW(hInst, IDC_ARROW);
    wc.hIcon = (HICON)LoadImageW(hInst, MAKEINTRESOURCEW(IDI_ICON), IMAGE_ICON, 32, 32, LR_DEFAULTSIZE);
    wc.hIconSm = (HICON)LoadImageW(hInst, MAKEINTRESOURCEW(IDI_ICON), IMAGE_ICON, 16, 16, LR_DEFAULTSIZE);
    wc.hbrBackground = (HBRUSH)(COLOR_WINDOW + 1);
    wc.lpszClassName = CFG_CLASS_NAME;
    RegisterClassExW(&wc);

    HWND hwnd = CreateWindowExW(0, CFG_CLASS_NAME, CFG_APP_TITLE, WS_OVERLAPPEDWINDOW, CW_USEDEFAULT, CW_USEDEFAULT, INITIAL_WINDOW_W, INITIAL_WINDOW_H, nullptr, nullptr, GetModuleHandleW(nullptr), nullptr);
    if (!hwnd) {
        GdiplusShutdown(gdiplusToken);
        return 1;
    }

    ShowWindow(hwnd, SW_MAXIMIZE);
    UpdateWindow(hwnd);

    MSG msg;
    while (GetMessageW(&msg, nullptr, 0, 0)) {
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }

    GdiplusShutdown(gdiplusToken);
    return 0;
}