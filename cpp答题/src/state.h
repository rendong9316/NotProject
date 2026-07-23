// ============================================================================
// state.h — QuizState 结构体定义与全局状态查询函数声明
// 管理答题程序的所有运行时状态，包括进度、选中的答案、时间等
// ============================================================================

#pragma once

#include "types.h"                                     // 引入 types.h（使用 Page, QuizMode, Question, QuestionBank 等类型）
#include <string>                                      // std::wstring（宽字符字符串）
#include <chrono>                                      // 时间相关类（system_clock, steady_clock, duration）
#include <windows.h>                                   // HWND 窗口句柄类型

// ============================================================================
// QuizState 结构体定义
// ============================================================================
struct QuizState {                                      // struct：答题会话状态——整个程序运行时最核心的数据容器
    Page page = PAGE_HOME;                              //   Page 枚举：当前显示哪个页面（初始值为主页）
    QuizMode mode = MODE_SINGLE;                        //   QuizMode 枚举：当前答题模式（默认为单选）
    int qNum = 0;                                       //   当前答到第几题了（从 1 开始计数，便于显示）
    int total = 10;                                     //   本轮答题的总题目数（不同模式有不同总数）
    int correct = 0;                                    //   累计答对的题数
    int wrong = 0;                                      //   累计答错的题数
    int selectedScore = 0;                              //   用户在风险题分值选择中点击的分值（10/20/30/40/50/60）

    bool answered = false;                              //   标记当前题是否已经提交并结算（true=已答，false=未答）
    bool lastCorrect = false;                           //   上一题的对错结果（用于反馈面板的颜色：绿=对，红=错）
    bool timedOut = false;                              //   上一题是否因为超时结束（true=超时，false=主动提交）

    std::vector<bool> used;                             //   used[i] == true 表示题库第 i 号题已被抽取过
                                                         //     数组长度等于题库大小，防止同一题出现两次

    int curQIdx = -1;                                   //   当前题目的索引（在 bank.all[] 中的位置），-1 表示没有当前题
                                                         //     curQIdx 由 StartQuestion() 赋值

    bool selected[4] = {false, false, false, false};    //   用户选中的选项：selected[0]=A选中, [1]=B选中 ...
                                                         //     单选题只能有一个 true，多选题可以有多个
    std::wstring userFill;                               //   填空题用户的输入内容（从 EDIT 控件读取）

    int settledSeconds = 0;                              //   本题实际用时（秒）。限时模式下，答题后记录到该变量
    std::chrono::system_clock::time_point quizStart;      //   本轮答题开始的时间点（系统时钟，可被用户修改影响）
    std::chrono::system_clock::time_point quizEnd;        //   本轮答题结束的时间点（用户点"提交结果"时设置）
    std::chrono::steady_clock::time_point questionStart;  //   当前题目的开始时间（单调时钟，不受系统时间调整影响）
    std::vector<int> questionSeconds;                    //   每道题的用时记录（按顺序存储的 int 数组）
    bool flashVisible = false;                           //   倒计时闪烁标志：true=可见，false=隐藏。每 1 秒翻转一次
    int fillBucket = -1;                                 //   填空题的"桶"编号（0~5，对应 6 个分值档位）
                                                         //     决定从题库哪个区段抽题

    // ---- 重置答题会话 -- 清空分数、选择、计时器，重新初始化 used 数组 ----
    void resetQuiz(const QuestionBank& bank) {          //   成员函数：重置答题会话为初始状态
                                                         //     const：保证函数内不修改 bank 参数
                                                         //     &bank：引用传递，避免拷贝整个题库（性能优化）
        qNum = 0;                                        //     重置当前题号归零
        correct = 0;                                     //     答对计数清零
        wrong = 0;                                       //     答错计数清零
        selectedScore = 0;                               //     选中的分值清零
        answered = false;                                //     清空"已作答"标记
        lastCorrect = false;                             //     清空"上次对错"标记
        timedOut = false;                                //     清空"已超时"标记
        curQIdx = -1;                                    //     清除当前题目索引
        // {} 初始化列表：将四个元素全部赋值为 false
        selected[0] = selected[1] = selected[2] = selected[3] = false;
        flashVisible = false;                            //     清空"倒计时闪烁"标记
        userFill.clear();                                //     clear() 是 std::wstring 的成员方法，清空字符串内容为空
        settledSeconds = 0;                              //     清空本题用时记录
        questionSeconds.clear();                         //     clear() 清空 vector，释放所有元素但保留容量
        used.assign(bank.all.size(), false);             //     assign() 是 vector 的成员方法：重新分配 size 个元素，全部为 false
                                                         //       此时所有题目都标记为"未被使用过"
        if (mode == MODE_FILL && fillBucket >= 0) {      //     如果是填空题且已选择了分值桶
            int base = fillBucket * 3;                   //       每个桶有 3 道题，base = 桶号 × 3
            for (int i = base; i < base + 3 && i < (int)used.size(); ++i)
                // for 循环：i 从 base 遍历到 base+2（共3次）
                //   && 是逻辑与运算符：i < base+3 且 i < used.size()，两个条件都要满足
                //   ++i 是前缀自增：i 先加 1 再用作表达式值
                used[i] = false;                         //       将这 3 道题标记回"未使用"状态
        }                                                //     } 结束 if 块
    }                                                    //     } 结束 resetQuiz 函数定义
};                                                       // } 结束 struct QuizState 定义

// Global instances (defined in state.cpp)
extern QuizState g_state;           // extern：声明全局变量 g_state，定义在 state.cpp 中
extern QuestionBank g_banks[3];     //   三个题库：g_banks[0] 单选题库, [1] 多选题库, [2] 填空题库
extern std::mt19937 rng;            //   Mersenne Twister 伪随机数生成器实例（全局共享）

// 工具函数
std::wstring Utf8ToWide(const std::string& s);          // 将 UTF-8 编码的多字节字符串转换为宽字符串
                                                         // const std::string& s：参数是 string 的常量引用（不拷贝、不修改）
                                                         // 返回：等价的 std::wstring
std::wstring IntToWStr(int n);                          // 将 int 整数转换为宽字符串
                                                         // int n：输入整数
std::wstring ExeDir();                                  // 获取当前可执行文件所在目录路径
std::wstring JoinPath(const std::wstring& dir, const std::wstring& name);
                                                         // 拼接目录和文件名，自动处理路径分隔符
std::wstring FormatTime(std::chrono::system_clock::time_point tp);
                                                         // 将 time_point 格式化为 "YYYY-MM-DD HH:MM:SS" 字符串
std::wstring FormatDuration(int seconds);               // 将秒数格式化为中文时长，如 "1小时23分45秒"
void PlaySoundEffect(const wchar_t* soundName);         // 播放 Windows 系统音效
void PlayEmbeddedSound(int resourceId);                 // 播放编译嵌入到 .exe 中的 WAVE 音频

// 题库加载
bool LoadQuestionBank(int resourceId, QuizMode mode, QuestionBank& bank, std::wstring& error);
                                                         // 从资源 ID 指定的内嵌 JSON 数据中加载题库
                                                         // bool 返回值：true=成功, false=失败
                                                         // resourceId：嵌入 .exe 的资源编号
                                                         // mode：题库对应的模式（用于校验题目数据是否合法）
                                                         // bank：输出参数（非 const 引用），加载成功后填充题目数据
                                                         // error：输出参数，失败时填入错误描述文本

// 状态查询
QuestionBank& ActiveBank();                             // 根据 g_state.mode 返回当前活跃的题库引用
                                                         // & 返回值：返回的是引用（不是拷贝），可以直接修改题库内容
std::wstring ModeName();                                // 返回当前答题模式的中文名称字符串
bool IsTimedMode();                                     // 判断当前是否为限时模式（true=单选/多选，false=填空）
bool TracksTotalTime();                                 // 判断当前是否跟踪总耗时（true=单选，false=其他模式）
const Question* CurrentQuestion();                      // 返回当前正在作答的题目指针（返回 nullptr 表示无有效题目）
int PickRandomUnused();                                 // 从未使用的题目中随机选取一个（返回 -1 表示用完）
bool StartQuestion();                                   // 开始回答下一道题（true=成功，false=无更多可用题）
void StartQuiz(QuizMode mode);                          // 启动一轮新答题
void OpenFillScoreSelect();                             // 打开填空题分值选择页面
void StartFillQuiz(int selectedScore, int scoreIdx);    // 从分值选择页确认后启动填空答题
int QuestionElapsedSeconds();                           // 计算当前题目已过去的秒数
int QuestionRemainingSeconds();                         // 计算当前题目还剩多少秒（限时模式下）
bool HasSelection();                                    // 检查用户是否至少选中了一个选项
std::vector<int> SelectedAnswers();                     // 收集所有被选中的选项索引，存入向量
std::wstring AnswerLetters(const Question& question);   // 将正确答案的数字索引转为字母字符串（"A、C"）
void SettleCurrentQuestion(HWND hwnd, bool timeout);    // 结算当前题目的对错结果
