// ============================================================================
// types.h — 类型定义头文件
// 声明整个程序使用的核心数据结构与枚举类型
// 不包含 windows.h，仅依赖标准 C++ 库，避免头文件循环依赖
// ============================================================================

#pragma once                                           // #pragma once：预处理指令，保证本头文件只被包含一次（防止重复定义）

#include <vector>                                      // 引入 vector 容器（用于 QuestionBank::all 动态数组）
#include <string>                                      // 引入 string 类型（std::wstring 宽字符字符串）
#include <chrono>                                      // 引入 chrono 时间库（用于 QuizState 中的计时功能）
#include <random>                                      // 引入 random 库（用于 std::mt19937 随机数引擎）

enum Page {                                            // enum：定义枚举类型 Page，表示程序当前所在的页面状态
    PAGE_HOME,                                         //   PAGE_HOME = 0，主页 — 选择答题模式的界面
    PAGE_FILL_SCORE_SELECT,                            //   PAGE_FILL_SCORE_SELECT = 1，填空题的分值选择页
    PAGE_QUIZ,                                         //   PAGE_QUIZ = 2，答题页面 — 显示题目、选项、输入框
    PAGE_RESULT                                        //   PAGE_RESULT = 3，结果页 — 展示成绩和统计信息
};                                                     // } 结束 enum 定义，Page 变量只能取上述 4 个值之一

enum QuizMode {                                        // enum：定义枚举类型 QuizMode，表示当前的答题模式
    MODE_SINGLE,                                       //   MODE_SINGLE = 0，单选题模式（每题一个正确答案，限时30秒）
    MODE_MULTIPLE,                                     //   MODE_MULTIPLE = 1，多选题模式（每题多个正确答案，必须全对才得分）
    MODE_FILL                                          //   MODE_FILL = 2，填空/简答题模式（自由输入答案，不限时）
};                                                     // } 结束 enum 定义

struct Question {                                      // struct：定义结构体 Question，存储一道题目的完整数据
    int id = 0;                                        //   int：整型，题目在题库中的唯一编号；=0 是默认初始值
    int difficulty = 0;                                //   int：难度等级（0=简单, 1=中档, 2=高档），目前仅展示用
    std::wstring q;                                    //   wstring：宽字符字符串，存储题干文本（中文需要宽字符）
    std::wstring opts[4];                              //   wstring 数组：四个选项的文本内容，索引 0-3 分别对应 A、B、C、D
                                                         //     opts[0] = "A. 选项A的文字"，opts[1] = "B. 选项B的文字" ...
    std::vector<int> answers;                           //   vector<int>：动态整数数组，存储正确答案的选项索引
                                                         //     如 {0, 2} 表示第 1 和第 3 个选项（即 A 和 C）是正确答案
    std::wstring exp;                                  //   wstring：答案解析文本（答错后展示的详细解释说明）
    std::wstring fillAnswer;                            //   wstring：填空题的标准答案文本
    std::vector<std::wstring> fillAlts;                 //   vector<wstring>：填空题的替代答案列表
                                                         //     用户输入只要匹配其中任何一个，都算正确
};                                                     // } 结束 struct 定义，{ } 内声明所有成员变量

struct QuestionBank {                                  // struct：定义结构体 QuestionBank，管理一批题目
    std::vector<Question> all;                          //   vector<Question>：所有题目的容器，按加载顺序排列
                                                         //     all[i] 就是题库中第 i 道题的完整数据
    bool ready() const { return !all.empty(); }         //   const 成员函数：返回 true 表示题库已加载非空数据
                                                         //     ! 是逻辑非运算符，empty() 是 vector 的成员方法判断是否为空
    int size() const { return (int)all.size(); }        //   const 成员函数：返回题目数量
                                                         //     (int) 是将 size_t（unsigned）强制转换为 int 类型
};                                                     // } 结束 struct 定义

struct UIRect {                                        // struct：定义结构体 UIRect，描述一个矩形区域的几何位置
    int x, y;                                          //   int：左上角横坐标 x、纵坐标 y（逻辑像素，未乘以缩放比）
    int w, h;                                          //   int：宽度 w、高度 h（逻辑像素）
};                                                     // } 结束 struct 定义
                                                         // 用途：用于定义按钮、选项卡等可交互区域的位置尺寸

// ============================================================================
// QuizState 结构体的定义位于 state.h 中
// （因为需要包含 <chrono> 头文件，分离存放以避免循环依赖）
// ============================================================================
