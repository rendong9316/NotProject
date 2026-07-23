// ============================================================================
// state.cpp — 数据结构、题库加载、答题状态逻辑实现
// 包含：字符串工具函数、JSON 解析器、题库资源加载与校验、
//      随机抽题算法、答题结算逻辑
// ============================================================================

#include "state.h"                                     // 包含 state.h：全局状态 g_state、Q&A 结构体声明
#include "types.h"                                     // 包含 types.h：Page/QuizMode enum、QuestionBank
#include "config.h"                                    // 包含 config.h：CFG_* 常量、RES_ID_*、FILL_SCORE_OPTIONS
#include <windows.h>                                   // Windows API
#include <gdiplus.h>                                   // GDI+
#include <string>                                      // std::wstring
#include <vector>                                      // std::vector
#include <algorithm>                                   // std::sort, std::find, std::adjacent_find, std::max, std::min
#include <sstream>                                     // std::wstringstream（宽字符字符串流）
#include <chrono>                                      // 时间库：system_clock, steady_clock, duration
#include <ctime>                                       // localtime, wcsftime
#include <cwctype>                                     // wctype（宽字符类型判断）
#include <cwchar>                                      // wcsftime
#include <cstdio>                                      // FILE 等 C 标准库
#include <mmsystem.h>                                  // PlaySound API
#include <random>                                      // mt19937, uniform_int_distribution
#include <unordered_set>                               // unordered_set（单选去重集合）
#pragma comment(lib, "winmm.lib")                     // 链接 winmm.lib（PlaySound）

using namespace Gdiplus;                              // 使用 Gdiplus 命名空间：简化 GDI+ 调用

// Global random number engine                         // Mersenne Twister 19937 伪随机数生成器
std::mt19937 rng;                                     //   在 main() 中用系统熵源初始化，用于所有随机抽题操作

// Global state                                        // 三个题库实例和答题会话状态
QuizState g_state;                                    //   当前答题会话的唯一状态对象
QuestionBank g_banks[3];                              //   [0]=单选题库, [1]=多选题库, [2]=填空题库

// Session-level dedup for single-choice               // 会话级去重：记录本次启动已答过的单选题 ID
static std::unordered_set<int> g_sessionSingleAnsweredIds; // 程序生命周期内永不重置，确保单选模式题目不重复

/** UTF-8 字符串转 Windows 宽字符串（UTF-16 wstring） */
std::wstring Utf8ToWide(const std::string& s) {
                                                         // C++ 标准库的多字节→宽字符转换函数
                                                         // CP_UTF8：编码为 UTF-8
    if (s.empty()) return L"";                          // 空字符串直接返回空宽字符串
                                                         // L"" 是宽字符字符串字面量（每个字符占 2 字节）
    int len = MultiByteToWideChar(CP_UTF8, 0, s.data(), (int)s.size(), nullptr, 0);
                                                         // 第一步：获取需要多少 wchar_t 空间
                                                         // MultiByteToWideChar：Windows API
                                                         // s.data()：源字符串的 char* 指针
                                                         // (int)s.size()：转换为 int 长度（size_t 到 int 的安全转换）
    std::wstring out(len, L'\0');                       // 分配输出缓冲区，初始为空字符填充
    MultiByteToWideChar(CP_UTF8, 0, s.data(), (int)s.size(), &out[0], len);
                                                         // 第二步：实际执行转换
                                                         // &out[0]：wstring 底层 char 数组的地址
    return out;                                         // 返回转换后的宽字符串
}                                                     // } 结束 Utf8ToWide 函数

/** int 转宽字符串 */
std::wstring IntToWStr(int n) {
    std::wstringstream ss;                              // 创建宽字符字符串流
    ss << n;                                            // 将整数 n 写入流中
                                                         // << 运算符将 int 自动格式化为字符串
    return ss.str();                                    // 提取流中的字符串内容并返回
                                                         // str() 是 wstringstream 的成员函数
}                                                     // } 结束 IntToWStr 函数

/** 获取可执行文件所在目录 */
std::wstring ExeDir() {
    wchar_t path[MAX_PATH];                             // MAX_PATH：Windows 定义的最大路径长度（通常 260）
    GetModuleFileNameW(nullptr, path, MAX_PATH);       // 获取当前模块（.exe）的完整路径
                                                         // nullptr：获取当前进程的主模块
    std::wstring p(path);                               // 将 char* 转为 wstring
    size_t pos = p.find_last_of(L"\\/");               // find_last_of：从后往前查找最后一个 '/' 或 '\'
                                                         // size_t：unsigned 整型，find 失败时返回 npos
    return pos == std::wstring::npos ? L"." : p.substr(0, pos);
                                                         // ?: 三元运算符：如果 pos == npos（没找到分隔符），返回 "."（当前目录）
                                                         // else 返回 substr(0, pos)：从开头到最后一个斜杠的子串（去掉文件名）
}                                                     // } 结束 ExeDir 函数

/** 拼接目录和文件名，自动处理分隔符 */
std::wstring JoinPath(const std::wstring& dir, const std::wstring& name) {
    if (dir.empty()) return name;                      // 空目录就直接返回名字
    wchar_t last = dir.back();                          // back() 返回 string 最后一个字符
    if (last == L'\\' || last == L'/') return dir + name; // 目录已有分隔符，直接拼接（+ 运算符连接两个 wstring）
                                                         // == 是相等比较运算符
    return dir + L"\\" + name;                         // 否则手动添加反斜杠分隔符
}                                                     // } 结束 JoinPath 函数

/** 将 time_point 格式化为 "YYYY-MM-DD HH:MM:SS" 字符串 */
std::wstring FormatTime(std::chrono::system_clock::time_point tp) {
    std::time_t t = std::chrono::system_clock::to_time_t(tp);
                                                         // to_time_t：将 C++11 time_point 转为 C time_t
                                                         // system_clock：系统时钟，可被用户修改（不受此影响）
    std::tm tmv{};                                     // {} 聚合初始化，将所有 tm 字段置零
    std::tm* local = std::localtime(&t);               // localtime：转为本地时区时间
    if (local) tmv = *local;                           // 安全解引用指针（localtime 可能返回 nullptr）
                                                         // *local：解引用指针获取 tm 结构体的值
    wchar_t buf[64];                                   // 缓冲区大小 64 个宽字符
    wcsftime(buf, 64, L"%Y-%m-%d %H:%M:%S", &tmv);    // wcsftime：格式化时间为宽字符串
                                                         // %Y=%d=%M=%S：年-月-日 时:分:秒
                                                         // &tmv：传入 tm 结构体地址
    return buf;                                        // 返回格式化后的宽字符串
}                                                     // } 结束 FormatTime 函数

/** 将秒数格式化为中文时长，如 "1小时23分45秒" 或 "5分30秒" 或 "12秒" */
std::wstring FormatDuration(int seconds) {
    int h = seconds / 3600;                            // / 是整数除法，3600 秒 = 1 小时
    int m = (seconds % 3600) / 60;                     // % 取模运算求余数，再除以 60 得到分钟
    int s = seconds % 60;                              // % 60 得到剩余秒数
    std::wstringstream ss;                             // 创建字符串流用于拼接
    if (h > 0) ss << h << L"小时";                     // 如果有小时就拼接 "X小时"
    if (m > 0 || h > 0) ss << m << L"分";              // 有分钟或有小时才显示分钟
    ss << s << L"秒";                                  // 秒数始终显示
    return ss.str();                                   // 提取拼接结果
}                                                     // } 结束 FormatDuration 函数

/** 播放系统音效（通过别名，如 "@MB.Asterisk"） */
void PlaySoundEffect(const wchar_t* soundName) {
    PlaySoundW(soundName, nullptr, SND_ALIAS | SND_ASYNC);
                                                         // PlaySoundW：Windows API 播放声音
                                                         // SND_ALIAS：通过系统别名播放（Windows 预设音效）
                                                         // SND_ASYNC：异步播放（不阻塞当前线程）
                                                         // | 是按位或运算符，组合多个标志位
}                                                     // } 结束 PlaySoundEffect 函数

/** 播放编译嵌入的资源中的 WAVE 音频（通过 resourceId 定位） */
void PlayEmbeddedSound(int resourceId) {
    HMODULE hInst = GetModuleHandleW(nullptr);         // 获取当前模块句柄（用于定位内嵌资源）
    HRSRC hRes = FindResourceW(hInst, MAKEINTRESOURCEW(resourceId), L"WAVE");
                                                         // FindResourceW：查找内嵌资源
                                                         // MAKEINTRESOURCEW(resourceId)：将 int 资源 ID 转为资源名指针
                                                         // L"WAVE"：资源类型为 WAVE 音频
    if (!hRes) return;                                 // 资源不存在则返回
    HGLOBAL hData = LoadResource(hInst, hRes);        // LoadResource：将资源加载到全局内存
    if (!hData) return;                                // 加载失败则返回
    LPVOID pData = LockResource(hData);               // LockResource：锁定资源获取数据指针
    if (!pData) return;                                // 锁定失败则返回
    PlaySoundW((LPCWSTR)pData, hInst, SND_MEMORY | SND_ASYNC | SND_NODEFAULT);
                                                         // LPCWSTR：long pointer to constant wide string（指向宽字符串的长指针）
                                                         // (LPCWSTR)pData：强制转换为 LPCWSTR 类型
                                                         // SND_MEMORY：从内存播放（而非从文件）
                                                         // SND_NODEFAULT：找不到声音时静默（不播放默认音效）
    UnlockResource(hData);                             // 解锁资源（减少引用计数）
    FreeResource(hData);                               // 释放资源（释放全局内存块）
}                                                     // } 结束 PlayEmbeddedSound 函数

// ============================================================================
// 轻量 JSON 解析器
// 极简实现，仅支持题目 JSON 所需的子集：数组、对象、字符串、数字
// 不使用外部 JSON 库以减少编译体积和依赖
// ============================================================================
struct JsonParser {                                    // struct：轻量 JSON 解析器
    const std::string& s;                              //   待解析的原始 JSON 字符串（const 引用，不拷贝）
    size_t i = 0;                                      //   当前读取位置（字符偏移量，从 0 开始）

    explicit JsonParser(const std::string& text) : s(text) {}
                                                         // 构造函数：接收原始 JSON 文本
                                                         // explicit：防止隐式类型转换
                                                         // : s(text) 是成员初始化列表语法
    void ws() { while (i < s.size() && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) ++i; }
                                                         // ws() = skip whitespace（跳过空白字符）
                                                         // ++i：前缀自增运算符（先加 1 再取值）
    bool eat(char c) { ws(); if (i < s.size() && s[i] == c) { ++i; return true; } return false; }
                                                         // eat(c)：尝试消耗掉字符 c
                                                         // 先跳过空白，然后检查当前字符是否匹配
                                                         // 匹配则 i++ 前进一位，返回 true；否则返回 false
    // 读取一个双引号包裹的 JSON 字符串值，自动处理转义字符
    std::string str() {
        ws();                                           // 先跳过空白
        if (i >= s.size() || s[i] != '"') return "";   // 不是字符串开头（无'"'）则报错返回空
                                                         // != 是不等于运算符
        ++i;                                             // 跳过开始的 '"'
        std::string out;                                // 创建输出字符串
        while (i < s.size()) {                          // 逐字符循环直到结束
            char c = s[i++];                            // 读取当前字符并前移指针（i++ 后缀自增）
                                                         // i++：先用作表达式值再加 1
            if (c == '"') break;                        // 遇到闭合引号，结束字符串读取
                                                         // break：跳出 while 循环
            if (c == '\\' && i < s.size()) {           // 遇到转义字符 '\'
                char e = s[i++];                        // 读取转义后的字符
                if (e == '"' || e == '\\' || e == '/') out.push_back(e); // push_back：将字符追加到字符串末尾
                else if (e == 'n') out.push_back('\n'); // \n：转义为换行符
                else if (e == 'r') out.push_back('\r'); // \r：回车符
                else if (e == 't') out.push_back('\t'); // \t：制表符
                else if (e == 'u' && i + 4 <= s.size()) {
                    out += "?";                          // +=：字符串追加操作
                                                         // \uXXXX：简略处理为 "?"
                    i += 4;                              // 跳过四个十六进制字符
                }
            } else {
                out.push_back(c);                       // 普通字符直接追加
            }
        }
        return out;                                     // 返回解析出的字符串
    }

    /** 读取一个整数（可选负号），跳过前导空白 */
    int number() {
        ws();                                           // 跳过空白
        int sign = 1;                                   // sign = +1 表示正数
        if (i < s.size() && s[i] == '-') { sign = -1; ++i; }
                                                         // 如果有负号，sign=-1 并跳过 '-'
        int n = 0;
        while (i < s.size() && s[i] >= '0' && s[i] <= '9') // 连续读取数字字符（ASCII '0'~'9'）
                                                         // >= 大于等于, <= 小于等于
            n = n * 10 + (s[i++] - '0');                // n = n*10 + 新数字（s[i]-'0' 将 char 转为 int）
                                                         // -= 算术减法，char 会自动提升为 int
        return sign * n;                                // 返回带符号的整数
    }

    /** 跳过当前 JSON 值（不解析内容），用于遇到未知键时快速前进 */
    void skipValue() {
        ws();
        if (i >= s.size()) return;
        if (s[i] == '"') { str(); return; }             // 如果是字符串，直接读走
        if (s[i] == '{') {                               // 如果是嵌套对象，递归跳过
            eat('{');                                    // eat('{') 消耗掉 '{'
            while (!eat('}') && i < s.size()) { str(); eat(':'); skipValue(); eat(','); }
                                                         // !eat('}')：循环直到碰到 '}' 或出错
                                                         // !：逻辑非运算符
            return;
        }
        if (s[i] == '[') {                               // 如果是嵌套数组，逐个值跳过
            eat('[');
            while (!eat(']') && i < s.size()) { skipValue(); eat(','); }
            return;
        }
        // 跳过数字、布尔、null 等无结构的内容：一直走到逗号、括号或结束
        while (i < s.size() && s[i] != ',' && s[i] != ']' && s[i] != '}') ++i;
                                                         // 连续跳过所有不是分隔符的字符
    }

    /** 读取一个 JSON 字符串数组，返回 wstring 向量 */
    std::vector<std::wstring> stringArray() {
        std::vector<std::wstring> arr;                  // 创建空的 vector 容器
        if (!eat('[')) return arr;                      // 如果不是 '[' 开头（没有 '['），返回空向量
                                                         // 如果是空数组 [] 则返回空 vec
        while (!eat(']') && i < s.size()) {             // 循环读取直到遇到 ']' 或结束
            arr.push_back(Utf8ToWide(str()));           // push_back：将转换后的宽字符串追加到数组
            eat(',');                                    // 跳过元素间的逗号
        }
        return arr;                                     // 返回解析结果
    }

    /** 读取一个 JSON 整数数组，返回 int 向量 */
    std::vector<int> intArray() {
        std::vector<int> arr;
        if (!eat('[')) return arr;
        while (!eat(']') && i < s.size()) {
            arr.push_back(number());
            eat(',');
        }
        return arr;
    }
};                                                     // } 结束 JsonParser 结构体定义

// ============================================================================
// 题库加载核心函数
// 从可执行文件的内嵌资源中读取 JSON 文本，解析并校验为 QuestionBank
// @param resourceId 资源 ID（如 RES_ID_SINGLE = 2001）
// @param mode       此题库对应的答题模式（用于校验答案数量是否符合规则）
// @param bank       输出参数，加载成功后填充题库数据
// @param error      输出参数，失败时填入错误描述
// @return true 表示加载成功
// ============================================================================
bool LoadQuestionBank(int resourceId, QuizMode mode, QuestionBank& bank, std::wstring& error) {
                                                         // QuizMode mode：题型的枚举值
    HMODULE hInst = GetModuleHandleW(nullptr);          // 获取当前模块句柄

    // 步骤1：通过资源 ID 查找 RC_DATA 类型的内嵌资源（编译时嵌入的 JSON 文本）
    HRSRC hRes = FindResourceW(hInst, MAKEINTRESOURCEW(resourceId), L"RC_DATA");
                                                         // MAKEINTRESOURCEW：将 int 转为资源名指针
    if (!hRes) { error = CFG_ERR_FILE_NOT_FOUND; return false; }
                                                         // 找不到资源：标记错误，返回失败
    // 步骤2：加载资源到全局内存
    HGLOBAL hData = LoadResource(hInst, hRes);
    if (!hData) { error = CFG_ERR_FILE_READ; return false; }
                                                         // 加载资源失败
    // 步骤3：获取资源大小和指针
    DWORD size = SizeofResource(hInst, hRes);           // SizeofResource：查询资源的大小（字节数）
    LPVOID pData = LockResource(hData);                 // LockResource：获取资源数据的内存指针
    if (!pData || size == 0) {                          // 如果锁定失败或资源大小为 0
        UnlockResource(hData);                          // 需要释放之前的锁定
        error = CFG_ERR_FILE_READ;                      // 标记读取错误
        return false;                                   // 返回失败
    }                                                   // } 结束 if(!pData || size==0)

    // 步骤4：将资源数据转为 C++ 字符串
    std::string text((const char*)pData, size);         // 从内存指针构造 string，指定长度为 size 字节
                                                         // (const char*)：强制类型转换 LPVOID → const char*
    UnlockResource(hData);                              // 用完数据后解锁资源内存块

    // 步骤5：去除 BOM（Byte Order Mark）前缀 EF BB BF，如果有的话
    if (text.size() >= 3 && (unsigned char)text[0] == 0xEF &&
        (unsigned char)text[1] == 0xBB && (unsigned char)text[2] == 0xBF)
                                                         // unsigned char：无符号字符类型（避免符号扩展问题）
        text.erase(0, 3);                               // erase(pos, count)：删除从 pos 开始的 count 个字符

    // 步骤6：用 JsonParser 逐字段解析 JSON 数组中的每个题目对象
    QuestionBank loaded;                                // 创建新的题库容器准备存储解析结果
    JsonParser p(text);                                 // 构造 JSON 解析器实例，传入原始文本
    if (!p.eat('[')) { error = CFG_ERR_JSON_ROOT; return false; }
                                                         // 解析开始前必须是一个 '[' 字符（JSON 数组）

    while (!p.eat(']') && p.i < text.size()) {         // 循环解析直到遇到 ']' 或文本结束
                                                         // !p.eat(']')：只要没到数组结尾就继续循环
        if (!p.eat('{')) { error = CFG_ERR_JSON_ITEM; return false; }
                                                         // 每个元素必须是以 '{' 开始的对象
        Question q;                                     // 新题目实例
        bool hasAnswer = false;                         // 标记是否有答案字段
        bool hasFourOptions = false;                    // 标记是否有四个有效选项
        bool hasFillAnswer = false;                     // 标记是否有填空题答案

        // 遍历对象的所有键值对
        while (!p.eat('}') && p.i < text.size()) {    // 循环直到遇到 '}' 或文本结束
            std::string key = p.str();                  // 读取键名（JSON 对象的 key 总是双引号包裹的字符串）
            p.eat(':');                                 // 跳过冒号分隔符
            if (key == "id") q.id = p.number();        // number() 解析整数字段值
            else if (key == "difficulty") { /* difficulty 不再用于绘制逻辑 */ }
                                                         // else if：互斥条件分支（key 等于字符串时执行）
            else if (key == "question") q.q = Utf8ToWide(p.str());
                                                         // Utf8ToWide(p.str())：把 JSON 里的 UTF-8 字符串转为宽字符
            else if (key == "options") {               // options 字段：提取为数组
                auto arr = p.stringArray();             // stringArray() 返回 std::vector<wstring>
                                                         // auto：C++11 自动类型推断（arr 的类型是 stringArray() 的返回值）
                hasFourOptions = arr.size() == 4;       // size() 返回数组大小，== 比较相等
                for (int k = 0; k < 4 && k < (int)arr.size(); ++k) q.opts[k] = arr[k];
                                                         // for 循环：最多复制 4 个选项到 q.opts[0..3]
                                                         // &&：逻辑与，两个条件都要满足
            }
            else if (key == "answer") {                // answer 字段（单选题答案）
                auto _arr = p.intArray();               // 读取整数数组
                if (!_arr.empty()) q.answers = std::move(_arr);
                                                         // .empty()：检查向量是否为空
                                                         // std::move(_arr)：转移所有权（避免深层拷贝）
                hasAnswer = true;
            }
            else if (key == "answers") {               // answers 字段（通用答案字段）
                q.answers = p.intArray();               // 直接赋值（q.answers 是 vector<int>）
                hasAnswer = true;
            }
            else if (key == "fill_answer") {           // fill_answer 字段（填空题答案）
                q.fillAnswer = Utf8ToWide(p.str());     // UTF-8 → 宽字符
                hasFillAnswer = !q.fillAnswer.empty();  // !q.fillAnswer.empty() 判断非空
                                                         // .empty() 返回 bool：true=空, false=非空
            }
            else if (key == "fill_alternatives" || key == "alternatives") {
                                                         // ||：逻辑或运算符（两个键名都合法）
                q.fillAlts = p.stringArray();           // 读取备选答案数组
            }
            else if (key == "explanation") q.exp = Utf8ToWide(p.str());
                                                         // explanation：答案解析
            else p.skipValue();                         // 未知字段：跳过其值，不报错
            p.eat(',');                                 // 跳过键值对之间的逗号（最后一对不需要，但 eat 会安全处理）
        }                                               // } 结束 while(遍历键值对)

        std::sort(q.answers.begin(), q.answers.end()); // sort：排序（从小到大）
                                                         // begin()：指向第一个元素的迭代器
                                                         // end()：指向最后一个元素之后的"尾后"迭代器
        bool duplicateAnswer = std::adjacent_find(q.answers.begin(), q.answers.end()) != q.answers.end();
                                                         // adjacent_find：查找相邻相同元素（用于检测重复答案）
                                                         // != 不等于运算符：如果找到了就不等于 end()
        bool validAnswers = hasAnswer && !q.answers.empty() && !duplicateAnswer;
                                                         // ! 逻辑非，&& 逻辑与
        for (int answer : q.answers)                   // range-based for 循环（C++11）
                                                         // for(type var : container)：对每个元素执行一次循环
            validAnswers = validAnswers && answer >= 0 && answer < 4;
                                                         // 每个答案必须在 0-3 范围内
        bool validOptions = hasFourOptions;             // 初始值为 true（如果根本没有 options 就是 false）
        for (int k = 0; k < 4; ++k) validOptions = validOptions && !q.opts[k].empty();
                                                         // 循环检查四个选项都不为空
        bool validCount = mode == MODE_SINGLE ? q.answers.size() == 1 : q.answers.size() >= 2;
                                                         // ?: 三元运算符
        bool valid;
        if (mode == MODE_FILL) {                        // 填空题只需要题干 + 答案（无选项要求）
            valid = !q.q.empty() && hasFillAnswer;
        } else {                                        // 选择题需要题干 + 四个选项 + 合法答案 + 正确数量的答案
            valid = !q.q.empty() && validOptions && validAnswers && validCount;
        }                                               // } 结束 if(mode==FILL)
        if (!valid) {                                   // !valid：valid 为 false 时进入
            error = CFG_ERR_INVALID_Q + IntToWStr(q.id) + L")。";
                                                         // + 运算符连接宽字符串
            return false;                               // 返回失败
        }                                               // } 结束 if(!valid)
        loaded.all.push_back(q);                        // push_back：将校验通过的题目加入题库
        p.eat(',');                                     // 跳过题目之间的逗号
    }                                                   // } 结束 while(解析数组)

    if (!loaded.ready()) {                              // ready()：判断题库是否为空
        error = L"题库资源为空";
        return false;
    }                                                   // } 结束 if(!loaded.ready())
    // 每个题库至少需要 10 道有效题目（防止抽题时很快就全部用完）
    if (loaded.size() < 10) {                           // < 小于运算符
        error = CFG_ERR_MIN_QUESTIONS;
        return false;
    }                                                   // } 结束 if(size < 10)
    bank = loaded;                                      // 赋值给输出参数（bank = loaded）
                                                         // =：赋值运算符（std::vector 支持整体拷贝）
    return true;                                        // 加载成功
}                                                     // } 结束 LoadQuestionBank 函数

// 根据当前答题模式返回活跃使用的题库引用
QuestionBank& ActiveBank() {
                                                         // & 返回值引用：调用方直接操作返回的题库
    if (g_state.mode == MODE_MULTIPLE) return g_banks[1]; // ==：相等比较运算符
    if (g_state.mode == MODE_FILL) return g_banks[2];    // 返回对应索引的题库引用
    return g_banks[0];                                   // 默认返回单选题库（MODE_SINGLE）
}                                                     // } 结束 ActiveBank

// 返回模式的中文显示名称
std::wstring ModeName() {
    switch (g_state.mode) {                            // switch：根据枚举值选择分支
                                                         // break：跳出 switch（防止 fall-through）
        case MODE_MULTIPLE: return L"多选题模式";       // case 后接常量和冒号
        case MODE_FILL:     return L"填空/简答模式";
        default:            return L"单选题模式";       // default：其他所有情况
    }                                                   // } 结束 switch
}                                                     // } 结束 ModeName

// 判断当前是否为限时模式（单选和多选题每题限时30秒，填空题不限时）
bool IsTimedMode() {
    return g_state.mode == MODE_SINGLE || g_state.mode == MODE_MULTIPLE;
                                                         // == 比较运算符, || 逻辑或
}                                                     // } 结束 IsTimedMode

// 判断当前模式是否累计总用时（只有单选题在结果页显示总用时统计）
bool TracksTotalTime() {
    return g_state.mode == MODE_SINGLE;                // 只有单选模式跟踪总用时
}                                                     // } 结束 TracksTotalTime

// 返回当前正在作答的题目指针
const Question* CurrentQuestion() {
                                                         // const Question*：指向常量的指针
    if (g_state.curQIdx < 0 || g_state.curQIdx >= (int)ActiveBank().all.size()) return nullptr;
                                                         // < 小于, >= 大于等于, || 逻辑或, nullptr C++11 空指针
                                                         // ActiveBank().all.size()：获取题库大小（. 成员访问）
                                                         // (int)：显式类型转换
    return &ActiveBank().all[g_state.curQIdx];          // &：取地址运算符（&all[idx] 返回 Question*）
}                                                     // } 结束 CurrentQuestion

/**
 * 从未使用的题目中随机选取一道题。
 * 考虑不同模式的特殊约束：
 * - 填空题：从分值桶对应的 3 道题中选
 * - 单选题：跳过本次会话中已经答过的题
 * @return 选中题目在 bank.all[] 中的索引，全部已用完返回 -1
 */
int PickRandomUnused() {
    const auto& bank = ActiveBank();                    // auto 类型推导：推断 bank 为 const QuestionBank&
                                                         // const &：常量引用（只读，不拷贝）
    int startIdx = 0, endIdx = (int)bank.all.size();    // 初始化范围：默认[0, size)

    // 填空题受分值桶限制：从对应区间的 3 道题中选
    if (g_state.mode == MODE_FILL && g_state.fillBucket >= 0 && g_state.fillBucket < FILL_SCORE_OPTION_COUNT) {
                                                         // && 逻辑与
        startIdx = g_state.fillBucket * 3;               // * 乘法：每个桶有 3 道题
        endIdx = std::min(startIdx + 3, (int)bank.all.size());
                                                         // std::min(a, b)：返回 a 和 b 中较小者
                                                         // 确保 endIdx 不超过题库边界
    }                                                   // } 结束 if(FILL mode bucket)

    // 收集所有可用的（未使用的）题目索引
    std::vector<int> available;                         // 创建空的 vector<int> 动态数组
    for (int i = startIdx; i < endIdx; ++i) {           // 从 startIdx 到 endIdx-1 遍历
        if (g_state.used[i]) continue;                  // continue：跳过本次循环的剩余部分，直接进入下一轮
                                                         // used[i] 为 true 说明这题已经被答过了
        // 单选题模式：跳过本次启动以来已经答过的题目（会话级去重）
        if (g_state.mode == MODE_SINGLE && g_sessionSingleAnsweredIds.count(bank.all[i].id))
                                                         // count(key)：unordered_set 的成员函数，key 存在返回 1，否则 0
            continue;
        available.push_back(i);                         // push_back：将可用索引追加到数组末尾
    }                                                   // } 结束 for 遍历可用题目
    if (available.empty()) return -1;                   // 如果数组为空，说明所有题都用完了
                                                         // .empty()：vector 成员函数
    // 在可用题目中等概率随机选一个
    std::uniform_int_distribution<int> dist(0, (int)available.size() - 1);
                                                         // uniform_int_distribution：均匀分布随机数引擎
                                                         // 构造参数 (min, max)：随机数范围
                                                         // rng()：调用 mt19937 引擎的 operator() 生成下一个随机数
    int idx = dist(rng);                                // dist(rng)：在 [0, size-1] 中等概率随机取一个
    return available[idx];                              // 返回对应索引处题目的题目 in the bank.all[] 索引
}                                                     // } 结束 PickRandomUnused

/**
 * 开始回答下一道新题
 * 流程：随机抽题 → 标记已用 → 重置用户选择 → 记录开始时间
 * @return true 表示抽到了新题，false 表示没有更多可用题目了
 */
bool StartQuestion() {
    int idx = PickRandomUnused();                       // 先从 PickRandomUnused 获取题目索引
    if (idx < 0) return false;                          // 如果 idx < 0 返回 false（无可用题）
                                                         // < 小于运算符
    g_state.curQIdx = idx;                              // 设置当前题目索引（在 all[] 中的位置）
    g_state.used[idx] = true;                           // 将该题标记为已使用（used 数组中对应位置设为 true）

    // 单选题模式：记录本题 ID 到会话去重集合
    if (g_state.mode == MODE_SINGLE)                    // 检查是否处于单选题模式
        g_sessionSingleAnsweredIds.insert(ActiveBank().all[idx].id);
                                                         // insert：unordered_set 的成员函数，将元素插入集合
                                                         // 如果已存在则不做任何事（set 自动去重）
    // 重置用户选择状态：四个选项全部未选中
    g_state.selected[0] = g_state.selected[1] = g_state.selected[2] = g_state.selected[3] = false;
                                                         // 链式赋值
    g_state.userFill.clear();                           // clear()：清空 userFill 字符串
    g_state.answered = false;                           // 标记未作答
    g_state.timedOut = false;                           // 标记未超时
    g_state.settledSeconds = 0;                         // 清除上次用时
    g_state.flashVisible = false;                       // 清除倒计时闪烁标志
    // 记录本题开始的时间戳（单调时钟，不受系统时间调整影响）
    g_state.questionStart = std::chrono::steady_clock::now();
                                                         // steady_clock::now()：获取单调递增的系统时钟
    return true;                                        // 一切就绪，返回 true
}                                                     // } 结束 StartQuestion

/** 启动一轮新的答题（从主页的按钮触发） */
void StartQuiz(QuizMode mode) {
    g_state.mode = mode;                                // 设置答题模式
    g_state.resetQuiz(ActiveBank());                   // resetQuiz() 重置 used 数组和分数计数器
                                                         // ActiveBank() 获取当前题库引用
    // 根据模式分配不同的总题数
    if (mode == MODE_MULTIPLE) g_state.total = QUESTION_COUNT_MULTIPLE;
                                                         // 条件赋值
    else if (mode == MODE_FILL) g_state.total = QUESTION_COUNT_FILL;
    else g_state.total = QUESTION_COUNT_SINGLE;          // else：兜底，即 MODE_SINGLE

    g_state.page = PAGE_QUIZ;                          // 切换到答题页
    g_state.qNum = 1;                                  // 从第 1 题开始
    g_state.quizStart = std::chrono::system_clock::now(); // 记录答题开始时间
    StartQuestion();                                   // 抽取第一道题
}                                                     // } 结束 StartQuiz

/** 打开填空题的分值选择页面 */
void OpenFillScoreSelect() {
    g_state.mode = MODE_FILL;                          // 设置为填空题模式
    g_state.resetQuiz(ActiveBank());                   // 重置状态
    g_state.total = QUESTION_COUNT_FILL;               // 设总数为 2 题
    g_state.page = PAGE_FILL_SCORE_SELECT;             // 切换到分值选择页
}                                                     // } 结束 OpenFillScoreSelect

/** 从分值选择页确认后启动填空答题 */
void StartFillQuiz(int selectedScore, int scoreIdx) {
    g_state.mode = MODE_FILL;                          // 设置模式
    g_state.fillBucket = scoreIdx;                     // 记录分值桶，用于限制抽题范围
    g_state.resetQuiz(ActiveBank());
    g_state.selectedScore = selectedScore;             // 保存用户选择的分值（10/20/.../60）
    g_state.total = QUESTION_COUNT_FILL;
    g_state.page = PAGE_QUIZ;                          // 进入答题页
    g_state.qNum = 1;
    g_state.quizStart = std::chrono::system_clock::now();
    StartQuestion();
}                                                     // } 结束 StartFillQuiz

/** 计算当前题目已过去的秒数（单调时钟，不受系统时间调整影响） */
int QuestionElapsedSeconds() {
                                                         // std::chrono 库提供跨平台时间测量能力
                                                         // steady_clock::now()：获取当前单调递增的时刻
                                                         // steady_clock 是单调时钟，不会回跳
    // steady_clock::now()：获取当前单调时钟时刻
    // -: 两个 time_point 相减得到 duration（时间段）类型
    // g_state.questionStart：本题开始的时间戳
    // .count()：duration 的成员函数，返回刻度数（此处单位为秒）
    // std::max(a, b)：返回 a 和 b 中的较大值
    // max(0, ...)：确保返回结果不小于 0（防止开始时间晚于当前时间的异常情况）
    return std::max(0, (int)std::chrono::duration_cast<std::chrono::seconds>(
        std::chrono::steady_clock::now() - g_state.questionStart).count());
}                                                     // } 结束 QuestionElapsedSeconds

/** 计算当前题目剩余秒数 */
int QuestionRemainingSeconds() {
    if (!IsTimedMode()) return NO_TIME_SECONDS;        // 填空不限时，返回 0 标记
                                                         // !IsTimedMode()：如果当前不是限时模式（即填空模式）
                                                         // NO_TIME_SECONDS 是配置常量，通常设为 0 表示"不限时"
    // 已作答：用 settledSeconds（不含超时情况）；否则：实时计算
    int elapsed = g_state.answered ? g_state.settledSeconds : QuestionElapsedSeconds();
                                                         // ?: 三元运算符
                                                         // g_state.answered：如果题目已作答
                                                         // g_state.settledSeconds：返回结算时的用时（固定值，不再变化）
                                                         // QuestionElapsedSeconds()：返回实时计算的已过秒数（随时间递增）
    return std::max(0, QUESTION_TIME_LIMIT_SECONDS - elapsed);
                                                         // QUESTION_TIME_LIMIT_SECONDS：配置常量，如 30 秒（每题限时）
                                                         // -: 减法运算符，用总时限减去已过时间得到剩余时间
                                                         // max(0, ...)：确保不低于 0（防止计时溢出）
}                                                     // } 结束 QuestionRemainingSeconds

/** 检查用户是否选中了至少一个选项 */
bool HasSelection() {                     // HasSelection：检查用户是否至少勾选了一个选项
    for (int i = 0; i < 4; ++i)         // for 循环：i 从 0 递增到 3（对应 A/B/C/D 四个选项）
        if (g_state.selected[i]) return true;
        // g_state.selected[i]：第 i 个选项的选中状态（true=已勾选, false=未勾选）
        // return true：只要发现有一个选项被选中，立即返回 true（短路优化）
    return false;                 // 循环走完 4 次都没找到选中的选项，返回 false
}                                                     // } 结束 HasSelection

/** 返回用户选中的所有选项索引，按升序排列 */
std::vector<int> SelectedAnswers() {    // SelectedAnswers：收集用户选择的所有答案索引
    std::vector<int> answers;         // 声明空的结果向量
    for (int i = 0; i < 4; ++i)       // 遍历 4 个选项（A/B/C/D）
        if (g_state.selected[i]) answers.push_back(i);
        // push_back(i)：如果第 i 个选项被选中，将 i 追加到 answers 向量
        // 由于 i 从小到大遍历，结果天然有序：如 [0, 2] 表示选了 A 和 C
    return answers;                   // 返回包含所有选中答案索引的向量
}                                                     // } 结束 SelectedAnswers

/**
 * 将正确答案的数字索引转为字母字符串
 * 如 {0, 2} 转换为 "A、C"
 */
std::wstring AnswerLetters(const Question& question) {
                                                         // const Question& question：常量引用，只读不修改，& 避免拷贝
    std::wstringstream ss;          // 创建宽字符流用于拼接字母字符串
    for (int i = 0; i < (int)question.answers.size(); ++i) {
        // i：遍历 answers 向量的索引
        // (int)question.answers.size()：将 size_t 转为 int 进行比较
        if (i) ss << L"、";           // i > 0 时在前一个答案字母后面添加顿号 "、"（中文分隔符）
        // if(i)：当 i 为非零值时条件为真，等价于 if(i > 0)，利用 C++ 中非零即真的规则
        ss << (wchar_t)(L'A' + question.answers[i]);
        // L'A'：宽字符'A'，Unicode/ASCII 码为 65
        // + question.answers[i]：加上答案索引（0=A, 1=B, 2=C, 3=D）
        // 例如 answers[0]=2 时，L'A' + 2 = L'C'
        // (wchar_t)：强制类型转换，将计算结果转为宽字符类型
        // 最后用 << 运算符将字符输出到流 ss 中
    }
    return ss.str();                // 提取流中累积的内容并返回（如 "A、C"）
}                                                     // } 结束 AnswerLetters

/**
 * 结算当前题目的对错
 * 这是答题的核心逻辑：
 * 1. 防止重复结算（answered 为 true 时直接返回）
 * 2. 如果是超时（timeout=true），不比对答案，直接判错
 * 3. 比较用户选择和正确答案是否完全一致
 * 4. 更新正确/错误计数
 * 5. 播放对应的音效
 * 6. 记录每道题的用时
 */
void SettleCurrentQuestion(HWND hwnd, bool timeout) {
    // HWND hwnd：Windows 窗口句柄，用于 KillTimer 关闭倒计时定时器
    // bool timeout：是否因超时而结算（true=倒计时归零，false=用户主动提交）
    if (g_state.answered) return;   // 如果已经结算过，直接返回（双重检查防重复）
    // answered == true：已被结算过一次了，不再重复执行
    const Question* question = CurrentQuestion();
    // 获取当前题目指针，currentQuestion() 返回 &ActiveBank().all[curQIdx]
    if (!question) return;          // 如果指针为空（题目索引无效），直接返回不再继续

    // 记录本题用时
    int seconds = NO_TIME_SECONDS;  // 初始化秒数为"不限时"标记值
    // NO_TIME_SECONDS 通常是 0，在填空模式中使用
    if (IsTimedMode()) {            // 检查是否为限时模式（单选/多选）
        seconds = timeout ? QUESTION_TIME_LIMIT_SECONDS
        // ternary operator：timeout 为真时取 QUESTION_TIME_LIMIT_SECONDS（超时计满时限）
        // timeout 为假时取下面的 min(..., max(1, QuestionElapsedSeconds()))
                          : std::min(QUESTION_TIME_LIMIT_SECONDS, std::max(1, QuestionElapsedSeconds()));
        // std::max(1, ...)：确保至少 1 秒（用户瞬间提交不应记为 0 秒）
        // std::min(A, B)：取 A 和 B 中较小者，确保不超过时限
        // QUESTION_TIME_LIMIT_SECONDS：上限，如 30
        g_state.questionSeconds.push_back(seconds);
        // push_back：将本题用时追加到所有题目用时的历史列表中
        // questionSeconds：vector<int> 类型的历史记录
    }
    g_state.settledSeconds = seconds;       // 记录结算时的用时秒数
    g_state.answered = true;                // 标记题目已结算，后续点击无效
    g_state.timedOut = timeout;             // 保存是否超时的状态
    g_state.flashVisible = false;     // 停止倒计时闪烁效果
    // 不再调用 KillTimer —— WM_TIMER case 里的 !g_state.answered 检查会自动跳过已答完的题目
    // 保留定时器持续运行，保证所有题目的倒计时都正常刷新

    if (g_state.mode == MODE_FILL) {
        // 填空题不做对错判定，仅记录未作答，由 ui_control 读取编辑框后设置 lastCorrect
        g_state.lastCorrect = false;    // 填空题的正确答案判定交给 UI 层的文本比较逻辑
        return;                         // 填空题结算到此为止，不进入下面的选择题判分逻辑
    }

    // 选择题：比对选中和标准答案
    bool ok = !timeout && SelectedAnswers() == question->answers;
    // !timeout：超时不算对错比对（timeout 为真时整个表达式必为 false）
    // &&：逻辑与，前提是不超时
    // SelectedAnswers()：调用函数获取用户选的索引向量（如 [0, 2]）
    // ==：vector 的等于运算符，逐个元素比较两个向量是否完全相同（内容和顺序都要一样）
    // question->answers：-> 箭头运算符（结构体指针的成员访问）
    // 等价于 (*question).answers，因为 question 是指针
    // 将用户选择和标准答案逐项比对
    g_state.lastCorrect = ok;       // 记录本题的对错结果（供结果页展示）
    if (timeout) {                  // 超时：不修改 correct/wrong 计数，只播放音效
        PlayEmbeddedSound(SOUND_TIMEOUT_ID);
        // SOUND_TIMEOUT_ID：超时音效的资源 ID
    } else if (ok) {                // 正确：正确计数加 1，播放正确音效
        ++g_state.correct;          // ++ 前置自增运算符：先加 1 再使用
        // g_state.correct：正确答题计数字段
        PlayEmbeddedSound(SOUND_CORRECT_ID);
    } else {                        // 错误：错误计数加 1，播放错误音效
        ++g_state.wrong;            // g_state.wrong：错误答题计数字段
        PlayEmbeddedSound(SOUND_WRONG_ID);
    }
}                                                     // } 结束 SettleCurrentQuestion 函数
