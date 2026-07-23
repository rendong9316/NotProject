// types.h — 类型定义：枚举、数据结构、全局声明
// 不包含 windows.h，仅含基本头

#pragma once
#include <vector>
#include <string>
#include <chrono>
#include <random>

enum Page { PAGE_HOME, PAGE_FILL_SCORE_SELECT, PAGE_QUIZ, PAGE_RESULT };
enum QuizMode { MODE_SINGLE, MODE_MULTIPLE, MODE_FILL };

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
    std::vector<Question> all;
    bool ready() const { return !all.empty(); }
    int size() const { return (int)all.size(); }
};

struct UIRect { int x, y, w, h; };

// QuizState 定义在 state.h/cpp 中（需要 chrono）
