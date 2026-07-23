// state.h/cpp — 数据结构、题库加载、答题状态逻辑
#pragma once
#include "types.h"
#include <string>
#include <chrono>
#include <windows.h>

// QuizState 定义
struct QuizState {
    Page page = PAGE_HOME;
    QuizMode mode = MODE_SINGLE;
    int qNum = 0;
    int total = 10;
    int correct = 0;
    int wrong = 0;
    int selectedScore = 0;
    bool answered = false;
    bool lastCorrect = false;
    bool timedOut = false;
    std::vector<bool> used;
    int curQIdx = -1;
    bool selected[4] = {false, false, false, false};
    std::wstring userFill;
    int settledSeconds = 0;
    std::chrono::system_clock::time_point quizStart;
    std::chrono::system_clock::time_point quizEnd;
    std::chrono::steady_clock::time_point questionStart;
    std::vector<int> questionSeconds;
    bool flashVisible = false;
    int fillBucket = -1;

    void resetQuiz(const QuestionBank& bank) {
        qNum = 0;
        correct = 0;
        wrong = 0;
        selectedScore = 0;
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
        if (mode == MODE_FILL && fillBucket >= 0) {
            int base = fillBucket * 3;
            for (int i = base; i < base + 3 && i < (int)used.size(); ++i)
                used[i] = false;
        }
    }
};

// Global instances (defined in state.cpp)
extern QuizState g_state;
extern QuestionBank g_banks[3];
extern std::mt19937 rng;

// 工具函数
std::wstring Utf8ToWide(const std::string& s);
std::wstring IntToWStr(int n);
std::wstring ExeDir();
std::wstring JoinPath(const std::wstring& dir, const std::wstring& name);
std::wstring FormatTime(std::chrono::system_clock::time_point tp);
std::wstring FormatDuration(int seconds);
void PlaySoundEffect(const wchar_t* soundName);
void PlayEmbeddedSound(int resourceId);

// 题库加载
bool LoadQuestionBank(int resourceId, QuizMode mode, QuestionBank& bank, std::wstring& error);

// 状态查询
QuestionBank& ActiveBank();
std::wstring ModeName();
bool IsTimedMode();
bool TracksTotalTime();
const Question* CurrentQuestion();
int PickRandomUnused();
bool StartQuestion();
void StartQuiz(QuizMode mode);
void OpenFillScoreSelect();
void StartFillQuiz(int selectedScore, int scoreIdx);
int QuestionElapsedSeconds();
int QuestionRemainingSeconds();
bool HasSelection();
std::vector<int> SelectedAnswers();
std::wstring AnswerLetters(const Question& question);
void SettleCurrentQuestion(HWND hwnd, bool timeout);
