#include "state.h"
#include "types.h"
#include "config.h"
#include <windows.h>
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
#include <unordered_set>
#pragma comment(lib, "winmm.lib")

using namespace Gdiplus;

// Global random number engine
std::mt19937 rng;

// Global state
QuizState g_state;
QuestionBank g_banks[3];

// Session-level dedup for single-choice (never reset during program lifetime)
static std::unordered_set<int> g_sessionSingleAnsweredIds;

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

bool LoadQuestionBank(int resourceId, QuizMode mode, QuestionBank& bank, std::wstring& error) {
    HMODULE hInst = GetModuleHandleW(nullptr);
    HRSRC hRes = FindResourceW(hInst, MAKEINTRESOURCEW(resourceId), L"RC_DATA");
    if (!hRes) {
        error = CFG_ERR_FILE_NOT_FOUND;
        return false;
    }
    HGLOBAL hData = LoadResource(hInst, hRes);
    if (!hData) {
        error = CFG_ERR_FILE_READ;
        return false;
    }
    DWORD size = SizeofResource(hInst, hRes);
    LPVOID pData = LockResource(hData);
    if (!pData || size == 0) {
        UnlockResource(hData);
        error = CFG_ERR_FILE_READ;
        return false;
    }
    std::string text((const char*)pData, size);
    UnlockResource(hData);
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
            else if (key == "difficulty") { /* difficulty no longer used in draw logic */ }
            else if (key == "question") q.q = Utf8ToWide(p.str());
            else if (key == "options") {
                auto arr = p.stringArray();
                hasFourOptions = arr.size() == 4;
                for (int k = 0; k < 4 && k < (int)arr.size(); ++k) q.opts[k] = arr[k];
            }
            else if (key == "answer") {
                auto _arr = p.intArray();
                if (!_arr.empty()) q.answers = std::move(_arr);
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
            error = CFG_ERR_INVALID_Q + IntToWStr(q.id) + L")。";
            return false;
        }
        loaded.all.push_back(q);
        p.eat(',');
    }

    if (!loaded.ready()) {
        error = L"题库资源为空";
        return false;
    }
    if (loaded.size() < 10) {
        error = CFG_ERR_MIN_QUESTIONS;
        return false;
    }
    bank = loaded;
    return true;
}

QuestionBank& ActiveBank() {
    if (g_state.mode == MODE_MULTIPLE) return g_banks[1];
    if (g_state.mode == MODE_FILL) return g_banks[2];
    return g_banks[0];
}

std::wstring ModeName() {
    switch (g_state.mode) {
        case MODE_MULTIPLE: return L"多选题模式";
        case MODE_FILL: return L"填空/简答模式";
        default: return L"单选题模式";
    }
}

bool IsTimedMode() {
    return g_state.mode == MODE_SINGLE || g_state.mode == MODE_MULTIPLE;
}

bool TracksTotalTime() {
    return g_state.mode == MODE_SINGLE;
}

const Question* CurrentQuestion() {
    if (g_state.curQIdx < 0 || g_state.curQIdx >= (int)ActiveBank().all.size()) return nullptr;
    return &ActiveBank().all[g_state.curQIdx];
}

int PickRandomUnused() {
    const auto& bank = ActiveBank();
    int startIdx = 0, endIdx = (int)bank.all.size();
    // Fill questions constrained by bucket
    if (g_state.mode == MODE_FILL && g_state.fillBucket >= 0 && g_state.fillBucket < FILL_SCORE_OPTION_COUNT) {
        startIdx = g_state.fillBucket * 3;
        endIdx = std::min(startIdx + 3, (int)bank.all.size());
    }
    std::vector<int> available;
    for (int i = startIdx; i < endIdx; ++i) {
        if (g_state.used[i]) continue;
        // Single-choice session-level dedup
        if (g_state.mode == MODE_SINGLE && g_sessionSingleAnsweredIds.count(bank.all[i].id))
            continue;
        available.push_back(i);
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
    // Single-choice session dedup marker
    if (g_state.mode == MODE_SINGLE)
        g_sessionSingleAnsweredIds.insert(ActiveBank().all[idx].id);
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

void OpenFillScoreSelect() {
    g_state.mode = MODE_FILL;
    g_state.resetQuiz(ActiveBank());
    g_state.total = QUESTION_COUNT_FILL;
    g_state.page = PAGE_FILL_SCORE_SELECT;
}

void StartFillQuiz(int selectedScore, int scoreIdx) {
    g_state.mode = MODE_FILL;
    g_state.fillBucket = scoreIdx;
    g_state.resetQuiz(ActiveBank());
    g_state.selectedScore = selectedScore;
    g_state.total = QUESTION_COUNT_FILL;
    g_state.page = PAGE_QUIZ;
    g_state.qNum = 1;
    g_state.quizStart = std::chrono::system_clock::now();
    StartQuestion();
}

int QuestionElapsedSeconds() {
    return std::max(0, (int)std::chrono::duration_cast<std::chrono::seconds>(std::chrono::steady_clock::now() - g_state.questionStart).count());
}

int QuestionRemainingSeconds() {
    if (!IsTimedMode()) return NO_TIME_SECONDS;
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
    int seconds = NO_TIME_SECONDS;
    if (IsTimedMode()) {
        seconds = timeout ? QUESTION_TIME_LIMIT_SECONDS : std::min(QUESTION_TIME_LIMIT_SECONDS, std::max(1, QuestionElapsedSeconds()));
        g_state.questionSeconds.push_back(seconds);
    }
    g_state.settledSeconds = seconds;
    g_state.answered = true;
    g_state.timedOut = timeout;
    g_state.flashVisible = false;
    KillTimer(hwnd, 2);
    if (g_state.mode == MODE_FILL) {
        // Will be called after reading edit content in ui_control
        g_state.lastCorrect = false;
        return;
    }
    bool ok = !timeout && SelectedAnswers() == question->answers;
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
