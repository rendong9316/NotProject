/* quiz.exe - 鸡东司法局法律知识答题竞赛系统 (Win32 + GDI+)
 * 编译: g++ -o quiz.exe quiz.cpp -lgdi32 -lgdiplus -DUNICODE -D_UNICODE -mwindows
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
#include <iomanip>
#include <fstream>
#include <chrono>
#include <ctime>
#include <cwctype>
#include <cstdio>

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

struct Question {
    int id = 0;
    int difficulty = 0;
    std::wstring q;
    std::wstring opts[4];
    int ans = 0;
    std::wstring exp;
};

struct QuestionBank {
    std::vector<Question> groups[3];

    const std::vector<Question>& pool(int diff) const { return groups[diff]; }
    std::vector<Question>& pool(int diff) { return groups[diff]; }
    bool ready() const { return !groups[0].empty() && !groups[1].empty() && !groups[2].empty(); }
};

enum Page { PAGE_LOGIN, PAGE_HOME, PAGE_QUIZ, PAGE_RESULT };

struct QuizState {
    Page page = PAGE_LOGIN;
    std::wstring playerName;
    int curDiff = 0;
    int qNum = 0;
    int total = 20;
    int correct = 0;
    int wrong = 0;
    int easyC = 0, easyT = 0, medC = 0, medT = 0, hardC = 0, hardT = 0;
    bool answered = false;
    bool lastCorrect = false;
    bool resultSaved = false;
    int consecWrong[3] = {0, 0, 0};
    std::vector<bool> used[3];
    int curQIdx = -1;
    int selOpt = -1;
    std::chrono::system_clock::time_point quizStart;
    std::chrono::system_clock::time_point quizEnd;
    std::chrono::steady_clock::time_point questionStart;
    std::vector<int> questionSeconds;

    void resetForLogin(const QuestionBank& bank) {
        page = PAGE_LOGIN;
        playerName.clear();
        resetQuiz(bank);
    }

    void resetQuiz(const QuestionBank& bank) {
        curDiff = 0;
        qNum = 0;
        correct = 0;
        wrong = 0;
        easyC = easyT = medC = medT = hardC = hardT = 0;
        answered = false;
        lastCorrect = false;
        resultSaved = false;
        consecWrong[0] = consecWrong[1] = consecWrong[2] = 0;
        curQIdx = -1;
        selOpt = -1;
        questionSeconds.clear();
        for (int i = 0; i < 3; ++i) used[i].assign(bank.pool(i).size(), false);
    }
};

QuestionBank g_bank;
QuizState g_state;

HWND g_hwndMain = nullptr;
HWND g_hwndNameEdit = nullptr;
int g_clientW = 2440, g_clientH = 1760;
int g_w = 2440, g_h = 1760;
HDC g_hdcMem = nullptr;
HBITMAP g_hbmMem = nullptr;
float g_fontScale = 2.0f;

const wchar_t* CLASS_NAME = L"JusticeQuizAppClass";
const wchar_t* WINDOW_TITLE = L"司法局法律知识答题竞赛系统";
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

std::wstring Utf8ToWide(const std::string& s) {
    if (s.empty()) return L"";
    int len = MultiByteToWideChar(CP_UTF8, 0, s.data(), (int)s.size(), nullptr, 0);
    std::wstring out(len, L'\0');
    MultiByteToWideChar(CP_UTF8, 0, s.data(), (int)s.size(), &out[0], len);
    return out;
}

std::string WideToUtf8(const std::wstring& s) {
    if (s.empty()) return "";
    int len = WideCharToMultiByte(CP_UTF8, 0, s.data(), (int)s.size(), nullptr, 0, nullptr, nullptr);
    std::string out(len, '\0');
    WideCharToMultiByte(CP_UTF8, 0, s.data(), (int)s.size(), &out[0], len, nullptr, nullptr);
    return out;
}

std::wstring IntToWStr(int n) {
    wchar_t buf[64];
    swprintf(buf, L"%d", n);
    return std::wstring(buf);
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

std::wstring HtmlEscape(const std::wstring& s) {
    std::wstring out;
    for (wchar_t c : s) {
        if (c == L'&') out += L"&amp;";
        else if (c == L'<') out += L"&lt;";
        else if (c == L'>') out += L"&gt;";
        else if (c == L'\"') out += L"&quot;";
        else out += c;
    }
    return out;
}

std::wstring ExcelCell(const std::wstring& text) {
    return L"<td style='mso-number-format:\"\\@\";'>" + HtmlEscape(text) + L"</td>";
}

std::wstring Trim(const std::wstring& s) {
    size_t a = 0, b = s.size();
    while (a < b && iswspace(s[a])) ++a;
    while (b > a && iswspace(s[b - 1])) --b;
    return s.substr(a, b - a);
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
};

int DifficultyFromText(const std::wstring& d) {
    if (d == L"medium" || d == L"中档" || d == L"中等") return 1;
    if (d == L"hard" || d == L"高档" || d == L"困难") return 2;
    return 0;
}

bool LoadQuestionBank(const std::wstring& path, QuestionBank& bank, std::wstring& error) {
    FILE* file = _wfopen(path.c_str(), L"rb");
    if (!file) {
        error = L"未找到题库文件：" + path;
        return false;
    }
    fseek(file, 0, SEEK_END);
    long size = ftell(file);
    fseek(file, 0, SEEK_SET);
    std::string text;
    if (size > 0) {
        text.resize(size);
        fread(&text[0], 1, size, file);
    }
    fclose(file);
    if (text.size() >= 3 && (unsigned char)text[0] == 0xEF && (unsigned char)text[1] == 0xBB && (unsigned char)text[2] == 0xBF) text.erase(0, 3);

    QuestionBank loaded;
    JsonParser p(text);
    if (!p.eat('[')) {
        error = L"题库 JSON 格式错误：根节点应为数组。";
        return false;
    }
    while (!p.eat(']') && p.i < text.size()) {
        if (!p.eat('{')) { error = L"题库 JSON 格式错误：题目项应为对象。"; return false; }
        Question q;
        while (!p.eat('}') && p.i < text.size()) {
            std::string key = p.str();
            p.eat(':');
            if (key == "id") q.id = p.number();
            else if (key == "difficulty") q.difficulty = DifficultyFromText(Utf8ToWide(p.str()));
            else if (key == "question") q.q = Utf8ToWide(p.str());
            else if (key == "options") {
                auto arr = p.stringArray();
                for (int k = 0; k < 4 && k < (int)arr.size(); ++k) q.opts[k] = arr[k];
            }
            else if (key == "answer") q.ans = p.number();
            else if (key == "explanation") q.exp = Utf8ToWide(p.str());
            else p.skipValue();
            p.eat(',');
        }
        if (!q.q.empty() && q.ans >= 0 && q.ans < 4) loaded.pool(q.difficulty).push_back(q);
        p.eat(',');
    }

    if (!loaded.ready()) {
        error = L"题库至少需要包含 easy、medium、hard 三类题目。";
        return false;
    }
    bank = loaded;
    return true;
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
    FontFamily ff(L"Microsoft YaHei");
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
    TextLeft(g, L"司法局法律知识答题竞赛系统", title, GdiColor(CLR_INK), 34, 14, 500, 34);
    TextLeft(g, L"题库导入 · 自动计时 · 成绩留痕", sub, GdiColor(CLR_INK), 36, 52, 390, 24);
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

void DrawLoginPage(Graphics& g) {
    DrawBackground(g);
    DrawHeader(g);
    DrawFontControls(g);

    int cw = std::min(g_w - 80, 660), ch = 430;
    int cx = (g_w - cw) / 2, cy = 150;
    DrawCard(g, (float)cx, (float)cy, (float)cw, (float)ch, 24);

    Font* badge = MakeFont(12, FontStyleBold);
    Font* title = MakeFont(27, FontStyleBold);
    Font* body = MakeFont(14);
    TextCenter(g, L"JIDONG JUSTICE BUREAU", badge, GdiColor(CLR_GOLD), cx, cy + 34, cw, 24);
    TextCenter(g, L"参赛人员登录", title, GdiColor(CLR_NAVY), cx, cy + 70, cw, 44);
    TextCenter(g, L"请输入答题者姓名，提交后成绩会自动汇总到 Excel。", body, GdiColor(CLR_MUTED), cx + 40, cy + 124, cw - 80, 32);

    Font* label = MakeFont(14, FontStyleBold);
    TextLeft(g, L"答题者姓名", label, GdiColor(CLR_INK), cx + 82, cy + 190, 180, 26);
    FillRoundRectBorder(g, (float)(cx + 80), (float)(cy + 224), (float)(cw - 160), 52, 14, GdiColor(CLR_SOFT), GdiColor(CLR_BLUE), 1.8f);
    DrawButton(g, cx + (cw - 240) / 2, cy + 314, 240, 58, L"进入答题", CLR_NAVY, CLR_NAVY_2);

    delete badge; delete title; delete body; delete label;
}

void DrawHomePage(Graphics& g) {
    DrawBackground(g);
    DrawHeader(g, L"答题者：" + g_state.playerName);
    DrawFontControls(g);

    int cw = std::min(g_w - 80, 760), ch = 470;
    int cx = (g_w - cw) / 2, cy = 120;
    DrawCard(g, (float)cx, (float)cy, (float)cw, (float)ch, 24);

    Font* title = MakeFont(26, FontStyleBold);
    Font* sub = MakeFont(14);
    Font* h = MakeFont(16, FontStyleBold);
    Font* body = MakeFont(13);
    TextCenter(g, L"竞赛说明", title, GdiColor(CLR_NAVY), cx, cy + 30, cw, 42);
    TextCenter(g, L"共20题，系统从简单题开始，根据正确率动态提升或降低难度。", sub, GdiColor(CLR_MUTED), cx + 30, cy + 78, cw - 60, 30);

    int bx = cx + 50, by = cy + 130, bw = cw - 100, bh = 210;
    FillRoundRect(g, (float)bx, (float)by, (float)bw, (float)bh, 18, GdiColor(CLR_SOFT));
    TextLeft(g, L"规则与记录", h, GdiColor(CLR_NAVY), bx + 28, by + 22, 180, 28);
    std::wstring lines[] = {
        L"1. 每道题选择答案后点击“下一题”确认，系统记录每题用时。",
        L"2. 答对一题可进入更高难度；同难度连续答错三题会降级。",
        L"3. 完成后点击提交成绩，将在程序同目录汇总到 Excel 表格。",
        L"4. 右上角 A- / A+ 可缩放整体界面，也可使用 Ctrl + 鼠标滚轮。"
    };
    for (int i = 0; i < 4; ++i) TextLeft(g, lines[i], body, GdiColor(CLR_INK), bx + 30, by + 66 + i * 34, bw - 60, 28);

    DrawButton(g, cx + (cw - 220) / 2, cy + 374, 220, 54, L"开始竞赛", CLR_BLUE, RGB(30, 64, 175));
    delete title; delete sub; delete h; delete body;
}

const Question* CurrentQuestion() {
    if (g_state.curDiff < 0 || g_state.curDiff > 2 || g_state.curQIdx < 0) return nullptr;
    const auto& pool = g_bank.pool(g_state.curDiff);
    if (g_state.curQIdx >= (int)pool.size()) return nullptr;
    return &pool[g_state.curQIdx];
}

bool HasUnused(int diff) {
    for (bool used : g_state.used[diff]) if (!used) return true;
    return false;
}

int PickQuestion(int diff) {
    for (int i = 0; i < (int)g_state.used[diff].size(); ++i) if (!g_state.used[diff][i]) return i;
    g_state.used[diff].assign(g_bank.pool(diff).size(), false);
    return 0;
}

int NextDifficulty() {
    int cur = g_state.curDiff;
    if (g_state.lastCorrect) {
        if (cur < 2 && HasUnused(cur + 1)) ++cur;
    } else {
        if (g_state.consecWrong[cur] >= 3) {
            g_state.consecWrong[cur] = 0;
            if (cur > 0) --cur;
        }
        while (cur > 0 && !HasUnused(cur)) --cur;
        if (cur == 0 && !HasUnused(0)) g_state.used[0].assign(g_bank.pool(0).size(), false);
    }
    return cur;
}

void CountDifficultyTotal(int diff) {
    if (diff == 0) ++g_state.easyT;
    else if (diff == 1) ++g_state.medT;
    else ++g_state.hardT;
}

void CountDifficultyCorrect(int diff) {
    if (diff == 0) ++g_state.easyC;
    else if (diff == 1) ++g_state.medC;
    else ++g_state.hardC;
}

void StartQuestion(int diff) {
    int idx = PickQuestion(diff);
    g_state.curDiff = diff;
    g_state.curQIdx = idx;
    g_state.used[diff][idx] = true;
    g_state.selOpt = -1;
    g_state.answered = false;
    g_state.questionStart = std::chrono::steady_clock::now();
    CountDifficultyTotal(diff);
}

void StartQuiz() {
    g_state.resetQuiz(g_bank);
    g_state.page = PAGE_QUIZ;
    g_state.qNum = 1;
    g_state.quizStart = std::chrono::system_clock::now();
    StartQuestion(0);
}

void SaveResultIfNeeded(HWND hwnd) {
    if (g_state.resultSaved) return;
    g_state.quizEnd = std::chrono::system_clock::now();
    int score = (int)((double)g_state.correct / g_state.total * 100.0 + 0.5);
    int totalSeconds = (int)std::chrono::duration_cast<std::chrono::seconds>(g_state.quizEnd - g_state.quizStart).count();
    std::wstring path = JoinPath(ExeDir(), L"答题结果汇总.csv");

    std::wstringstream times;
    for (int i = 0; i < (int)g_state.questionSeconds.size(); ++i) {
        if (i) times << L", ";
        times << L"第" << (i + 1) << L"题" << g_state.questionSeconds[i] << L"秒";
    }

    std::wstringstream row;
    row << g_state.playerName << ","
        << score << ","
        << g_state.correct << ","
        << g_state.wrong << ","
        << FormatTime(g_state.quizStart) << ","
        << FormatTime(g_state.quizEnd) << ","
        << FormatDuration(totalSeconds) << ","
        << g_state.easyC << "/" << g_state.easyT << ","
        << g_state.medC << "/" << g_state.medT << ","
        << g_state.hardC << "/" << g_state.hardT << ","
        << times.str();

    std::wstring header = L"答题者姓名,得分,答对题数,答错题数,答题开始时间,答题结束时间,全程所用时间,简单题,中档题,高档题,每题用时\r\n";

    std::wstring content;
    FILE* in = _wfopen(path.c_str(), L"rb");
    if (in) {
        fseek(in, 0, SEEK_END);
        long size = ftell(in);
        fseek(in, 0, SEEK_SET);
        std::string bytes;
        if (size > 0) {
            bytes.resize(size);
            fread(&bytes[0], 1, size, in);
        }
        fclose(in);
        if (bytes.size() >= 3 && (unsigned char)bytes[0] == 0xEF && (unsigned char)bytes[1] == 0xBB && (unsigned char)bytes[2] == 0xBF) bytes.erase(0, 3);
        content = Utf8ToWide(bytes);
        if (!content.empty() && content.back() != L'\r' && content.back() != L'\n') content += L"\r\n";
        content += row.str() + L"\r\n";
    } else {
        content = header + row.str() + L"\r\n";
    }

    FILE* out = _wfopen(path.c_str(), L"wb");
    if (!out) {
        g_state.resultSaved = true;
        MessageBoxW(hwnd, L"Excel 结果文件保存失败，请检查程序目录写入权限，或先关闭已打开的成绩表。", L"保存失败", MB_OK | MB_ICONWARNING);
        return;
    }
    std::string bom = "\xEF\xBB\xBF";
    std::string body = WideToUtf8(content);
    fwrite(bom.data(), 1, bom.size(), out);
    fwrite(body.data(), 1, body.size(), out);
    fclose(out);
    g_state.resultSaved = true;
}

void DrawQuizPage(Graphics& g) {
    DrawBackground(g);
    int questionElapsed = (int)std::chrono::duration_cast<std::chrono::seconds>(std::chrono::steady_clock::now() - g_state.questionStart).count();
    int totalElapsed = (int)std::chrono::duration_cast<std::chrono::seconds>(std::chrono::system_clock::now() - g_state.quizStart).count();
    DrawHeader(g, L"答题者：" + g_state.playerName);
    DrawFontControls(g);

    const wchar_t* diffNames[] = {L"简单题", L"中档题", L"高档题"};
    COLORREF diffColors[] = {CLR_GREEN, CLR_GOLD, CLR_RED};
    const Question* q = CurrentQuestion();
    if (!q) return;

    int topX = 38, topY = 104, topW = g_w - 76, topH = 112;
    DrawCard(g, (float)topX, (float)topY, (float)topW, (float)topH, 18);
    Font* meta = MakeFont(14, FontStyleBold);
    Font* meta2 = MakeFont(13);
    int metricW = std::max(150, (topW - 92) / 4);
    DrawMetric(g, topX + 22, topY + 18, metricW, 70, L"当前进度", L"第 " + IntToWStr(g_state.qNum) + L" / " + IntToWStr(g_state.total) + L" 题", CLR_BLUE);
    DrawMetric(g, topX + 34 + metricW, topY + 18, metricW, 70, L"当前难度", diffNames[g_state.curDiff], diffColors[g_state.curDiff]);
    DrawMetric(g, topX + 46 + metricW * 2, topY + 18, metricW, 70, L"本题用时", FormatDuration(questionElapsed), CLR_GOLD);
    DrawMetric(g, topX + 58 + metricW * 3, topY + 18, topW - 80 - metricW * 3, 70, L"总用时", FormatDuration(totalElapsed), CLR_RED);
    FillRoundRect(g, (float)(topX + 24), (float)(topY + 96), (float)(topW - 48), 10, 5, Color(255, 219, 227, 238));
    FillRoundRect(g, (float)(topX + 24), (float)(topY + 96), (float)((topW - 48) * g_state.qNum / g_state.total), 10, 5, GdiColor(CLR_BLUE));

    int cardX = 38, cardY = 236, cardW = g_w - 76, cardH = g_h - 336;
    DrawCard(g, (float)cardX, (float)cardY, (float)cardW, (float)cardH, 22);
    Font* qFont = MakeFont(18, FontStyleBold);
    Font* optFont = MakeFont(16);
    TextLeft(g, IntToWStr(g_state.qNum) + L". " + q->q, qFont, GdiColor(CLR_INK), cardX + 34, cardY + 28, cardW - 68, 66);

    int optY = cardY + 110;
    for (int i = 0; i < 4; ++i) {
        int ox = cardX + 34, oy = optY + i * 64, ow = cardW - 68, oh = 52;
        bool selected = i == g_state.selOpt;
        bool showCorrect = g_state.answered && i == q->ans;
        Color border = selected ? GdiColor(CLR_BLUE) : GdiColor(CLR_LINE);
        Color fill = selected ? Color(255, 224, 238, 255) : Color(255, 255, 255, 255);
        if (showCorrect) { fill = Color(255, 231, 248, 238); border = GdiColor(CLR_GREEN); }
        FillRoundRectBorder(g, (float)ox, (float)oy, (float)ow, (float)oh, 14, fill, border, selected || showCorrect ? 2.4f : 1.5f);
        wchar_t letter[4]; swprintf(letter, L"%c", (wchar_t)(L'A' + i));
        FillRoundRect(g, (float)(ox + 14), (float)(oy + 10), 32, 32, 16, selected ? GdiColor(CLR_BLUE) : GdiColor(CLR_NAVY));
        Font* lf = MakeFont(13, FontStyleBold);
        TextCenter(g, letter, lf, Color(255, 255, 255, 255), ox + 14, oy + 10, 32, 32);
        TextLeft(g, q->opts[i].size() > 2 ? q->opts[i].substr(2) : q->opts[i], optFont, GdiColor(CLR_INK), ox + 62, oy + 12, ow - 78, 32);
        delete lf;
    }

    if (g_state.answered) {
        int fy = optY + 4 * 64 + 10;
        bool ok = g_state.lastCorrect;
        FillRoundRectBorder(g, (float)(cardX + 34), (float)fy, (float)(cardW - 68), 82, 14, ok ? Color(255, 231, 248, 238) : Color(255, 254, 226, 226), ok ? GdiColor(CLR_GREEN) : GdiColor(CLR_RED), 2.0f);
        std::wstring fb = ok ? L"回答正确。" : L"回答错误，正确答案：" + q->opts[q->ans] + L"。";
        TextLeft(g, fb, meta, ok ? GdiColor(CLR_GREEN) : GdiColor(CLR_RED), cardX + 54, fy + 12, cardW - 108, 26);
        TextLeft(g, q->exp, meta2, GdiColor(CLR_INK), cardX + 54, fy + 44, cardW - 108, 30);
    }

    DrawButton(g, g_w / 2 - 116, g_h - 78, 232, 54, g_state.answered && g_state.qNum >= g_state.total ? L"提交成绩" : (g_state.answered ? L"下一题" : L"确认答案"), g_state.answered && g_state.qNum >= g_state.total ? CLR_GREEN : CLR_BLUE, g_state.answered && g_state.qNum >= g_state.total ? RGB(0, 112, 56) : RGB(0, 69, 170));

    delete meta; delete meta2; delete qFont; delete optFont;
}

void DrawResultPage(Graphics& g) {
    DrawBackground(g);
    DrawHeader(g, L"答题者：" + g_state.playerName);
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
    TextCenter(g, L"成绩已提交", title, GdiColor(CLR_NAVY), cx, cy + 28, cw, 40);
    TextCenter(g, IntToWStr(score), scoreFont, score >= 80 ? GdiColor(CLR_GREEN) : score >= 60 ? GdiColor(CLR_GOLD) : GdiColor(CLR_RED), cx, cy + 78, cw, 70);
    TextCenter(g, L"总分", body, GdiColor(CLR_MUTED), cx, cy + 142, cw, 24);

    int sy = cy + 190;
    std::wstring stats[][2] = {
        {L"答对 / 总题数", IntToWStr(g_state.correct) + L" / " + IntToWStr(g_state.total)},
        {L"答题开始", FormatTime(g_state.quizStart)},
        {L"答题结束", FormatTime(g_state.quizEnd)},
        {L"全程用时", FormatDuration(totalSeconds)},
        {L"难度统计", L"简单 " + IntToWStr(g_state.easyC) + L"/" + IntToWStr(g_state.easyT) + L"    中档 " + IntToWStr(g_state.medC) + L"/" + IntToWStr(g_state.medT) + L"    高档 " + IntToWStr(g_state.hardC) + L"/" + IntToWStr(g_state.hardT)}
    };
    for (int i = 0; i < 5; ++i) {
        int y = sy + i * 42;
        FillRoundRect(g, (float)(cx + 54), (float)y, (float)(cw - 108), 34, 10, GdiColor(CLR_SOFT));
        TextLeft(g, stats[i][0], body, GdiColor(CLR_MUTED), cx + 76, y + 7, 140, 22);
        TextLeft(g, stats[i][1], stat, GdiColor(CLR_INK), cx + 220, y + 5, cw - 310, 24);
    }

    DrawButton(g, cx + cw / 2 - 210, cy + ch - 82, 190, 48, L"再次答题", CLR_BLUE, RGB(30, 64, 175));
    DrawButton(g, cx + cw / 2 + 20, cy + ch - 82, 190, 48, L"返回登录", CLR_NAVY, RGB(34, 70, 118), true);

    delete title; delete scoreFont; delete stat; delete body;
}

void UpdateLayoutSize() {
    g_w = std::max(720, (int)(g_clientW / g_fontScale));
    g_h = std::max(560, (int)(g_clientH / g_fontScale));
}

int ScaleCoord(int v) {
    return (int)(v * g_fontScale + 0.5f);
}

void UpdateNameEditVisibility() {
    if (!g_hwndNameEdit) return;
    ShowWindow(g_hwndNameEdit, g_state.page == PAGE_LOGIN ? SW_SHOW : SW_HIDE);
    if (g_state.page == PAGE_LOGIN) {
        int cw = std::min(g_w - 80, 660), cy = 150, cx = (g_w - cw) / 2;
        MoveWindow(g_hwndNameEdit, ScaleCoord(cx + 96), ScaleCoord(cy + 232), ScaleCoord(cw - 192), ScaleCoord(34), TRUE);
        SetFocus(g_hwndNameEdit);
    }
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
    UpdateNameEditVisibility();
    ResizeBackBuffer(g_hwndMain);
}

void HandleGlobalClick(int mx, int my) {
    if (Hit(mx, my, g_w - 154, 16, 48, 32)) AdjustFont(-0.06f);
    if (Hit(mx, my, g_w - 98, 16, 48, 32)) AdjustFont(0.06f);
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
        HWND hwndChild = (HWND)wParam;
        DWORD dwMousePos = GetMessagePos();
        short mx = GET_X_LPARAM(dwMousePos);
        short my = GET_Y_LPARAM(dwMousePos);
        mx = (short)(mx / g_fontScale);
        my = (short)(my / g_fontScale);

        if (g_state.page == PAGE_LOGIN) {
            int cw = std::min(g_w - 80, 660), cy = 150, cx = (g_w - cw) / 2;
            if (Hit(mx, my, cx + (cw - 240) / 2, cy + 314, 240, 58)) {
                SetCursor(LoadCursorW(nullptr, IDC_HAND));
                return TRUE;
            }
        } else if (g_state.page == PAGE_HOME) {
            int cw = std::min(g_w - 80, 760), cy = 120, cx = (g_w - cw) / 2;
            if (Hit(mx, my, cx + (cw - 220) / 2, cy + 374, 220, 54)) {
                SetCursor(LoadCursorW(nullptr, IDC_HAND));
                return TRUE;
            }
        } else if (g_state.page == PAGE_QUIZ) {
            int cardX = 38, cardY = 236, cardW = g_w - 76, optY = cardY + 110;
            for (int i = 0; i < 4; ++i) {
                int ox = cardX + 34, oy = optY + i * 64, ow = cardW - 68, oh = 52;
                if (!g_state.answered && Hit(mx, my, ox, oy, ow, oh)) {
                    SetCursor(LoadCursorW(nullptr, IDC_HAND));
                    return TRUE;
                }
            }
            if (Hit(mx, my, g_w / 2 - 116, g_h - 78, 232, 54)) {
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
        g_hwndNameEdit = CreateWindowExW(0, L"EDIT", L"", WS_CHILD | WS_VISIBLE | ES_AUTOHSCROLL, 0, 0, 100, 28, hwnd, (HMENU)101, GetModuleHandleW(nullptr), nullptr);
        HFONT font = CreateFontW(20, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE, DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY, DEFAULT_PITCH, L"Microsoft YaHei");
        SendMessageW(g_hwndNameEdit, WM_SETFONT, (WPARAM)font, TRUE);
        UpdateNameEditVisibility();
        SetTimer(hwnd, 1, 1000, nullptr);

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
        UpdateNameEditVisibility();
        ResizeBackBuffer(hwnd);
        return 0;
    }
    case WM_MOUSEWHEEL: {
        if (GetKeyState(VK_CONTROL) & 0x8000) {
            short z = GET_WHEEL_DELTA_WPARAM(wParam);
            AdjustFont(z > 0 ? 0.06f : -0.06f);
            return 0;
        }
        break;
    }
    case WM_TIMER: {
        if (g_state.page == PAGE_QUIZ && !g_state.answered) InvalidateRect(hwnd, nullptr, FALSE);
        return 0;
    }
    case WM_LBUTTONDOWN: {
        int mx = (int)(GET_X_LPARAM(lParam) / g_fontScale);
        int my = (int)(GET_Y_LPARAM(lParam) / g_fontScale);
        HandleGlobalClick(mx, my);

        if (g_state.page == PAGE_LOGIN) {
            int cw = std::min(g_w - 80, 660), cy = 150, cx = (g_w - cw) / 2;
            if (Hit(mx, my, cx + (cw - 240) / 2, cy + 314, 240, 58)) {
                wchar_t buf[128];
                GetWindowTextW(g_hwndNameEdit, buf, 128);
                g_state.playerName = Trim(buf);
                if (g_state.playerName.empty()) {
                    MessageBoxW(hwnd, L"请输入答题者姓名。", L"提示", MB_OK | MB_ICONINFORMATION);
                    return 0;
                }
                g_state.page = PAGE_HOME;
                UpdateNameEditVisibility();
                InvalidateRect(hwnd, nullptr, FALSE);
            }
        } else if (g_state.page == PAGE_HOME) {
            int cw = std::min(g_w - 80, 760), cy = 120, cx = (g_w - cw) / 2;
            if (Hit(mx, my, cx + (cw - 220) / 2, cy + 374, 220, 54)) StartQuiz();
            InvalidateRect(hwnd, nullptr, FALSE);
        } else if (g_state.page == PAGE_QUIZ) {
            const Question* q = CurrentQuestion();
            int cardX = 38, cardY = 236, cardW = g_w - 76, optY = cardY + 110;
            for (int i = 0; i < 4; ++i) {
                int ox = cardX + 34, oy = optY + i * 64, ow = cardW - 68, oh = 52;
                if (!g_state.answered && Hit(mx, my, ox, oy, ow, oh)) {
                    g_state.selOpt = i;
                    InvalidateRect(hwnd, nullptr, FALSE);
                    return 0;
                }
            }
            if (Hit(mx, my, g_w / 2 - 116, g_h - 78, 232, 54)) {
                if (!g_state.answered) {
                    if (g_state.selOpt < 0) {
                        MessageBoxW(hwnd, L"请先选择答案。", L"提示", MB_OK | MB_ICONINFORMATION);
                        return 0;
                    }
                    int seconds = std::max(1, (int)std::chrono::duration_cast<std::chrono::seconds>(std::chrono::steady_clock::now() - g_state.questionStart).count());
                    g_state.questionSeconds.push_back(seconds);
                    bool ok = q && g_state.selOpt == q->ans;
                    g_state.answered = true;
                    g_state.lastCorrect = ok;
                    if (ok) { ++g_state.correct; CountDifficultyCorrect(g_state.curDiff); g_state.consecWrong[g_state.curDiff] = 0; }
                    else { ++g_state.wrong; ++g_state.consecWrong[g_state.curDiff]; }
                } else if (g_state.qNum >= g_state.total) {
                    g_state.quizEnd = std::chrono::system_clock::now();
                    SaveResultIfNeeded(hwnd);
                    g_state.page = PAGE_RESULT;
                } else {
                    ++g_state.qNum;
                    StartQuestion(NextDifficulty());
                }
                InvalidateRect(hwnd, nullptr, FALSE);
            }
        } else if (g_state.page == PAGE_RESULT) {
            int cw = std::min(g_w - 80, 760), ch = 500, cx = (g_w - cw) / 2, cy = 120;
            if (Hit(mx, my, cx + cw / 2 - 210, cy + ch - 82, 190, 48)) {
                StartQuiz();
            } else if (Hit(mx, my, cx + cw / 2 + 20, cy + ch - 82, 190, 48)) {
                g_state.resetQuiz(g_bank);
                g_state.page = PAGE_LOGIN;
                SetWindowTextW(g_hwndNameEdit, L"");
                UpdateNameEditVisibility();
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
        if (g_state.page == PAGE_LOGIN) DrawLoginPage(memGfx);
        else if (g_state.page == PAGE_HOME) DrawHomePage(memGfx);
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
    HMODULE user32 = GetModuleHandleW(L"user32.dll");
    if (user32) {
        typedef BOOL (WINAPI *SetDpiAwareFn)();
        SetDpiAwareFn setDpiAware = (SetDpiAwareFn)GetProcAddress(user32, "SetProcessDPIAware");
        if (setDpiAware) setDpiAware();
    }
    std::wstring error;
    if (!LoadQuestionBank(JoinPath(ExeDir(), L"questions.json"), g_bank, error)) {
        MessageBoxW(nullptr, error.c_str(), L"题库加载失败", MB_OK | MB_ICONERROR);
        return 1;
    }
    g_state.resetForLogin(g_bank);

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
    wc.lpszClassName = CLASS_NAME;
    RegisterClassExW(&wc);

    HWND hwnd = CreateWindowExW(0, CLASS_NAME, WINDOW_TITLE, WS_OVERLAPPEDWINDOW, CW_USEDEFAULT, CW_USEDEFAULT, INITIAL_WINDOW_W, INITIAL_WINDOW_H, nullptr, nullptr, GetModuleHandleW(nullptr), nullptr);
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
