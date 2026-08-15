#pragma once

#include "types.h"

#include <windows.h>
#include <atomic>
#include <cstdint>
#include <deque>
#include <string>

struct ChatMessage {
    enum Kind { User, Assistant, System, Tool };
    Kind kind = System;
    std::wstring text;
};

struct AgentSession {
    std::wstring id = L"main";
    std::wstring currentTurnId;
    std::wstring currentDayDate;
    std::deque<ChatMessage> history;
    std::wstring lastError;
    std::uint64_t contentRevision = 0;
    bool active = false;
};

extern AgentSession g_agentSession;
extern std::atomic<bool> g_agentBusy;
extern HWND g_hwndAgentInput;

void dbg(const char* format, ...);
bool AgentStartProcess();
void AgentStopProcess();
bool AgentProcessIsRunning();
bool AgentIsReady();
void AgentStart(const std::wstring& dayDate = L"");
void AgentStop();
bool AgentSend(const std::wstring& message);
void AgentCancel();
bool AgentIsBusy();
void AgentHandleResult(LPARAM eventPointer);
void AgentNewConversation();
void AgentClearConversation();
void AgentShowConversationMenu(HWND owner, int screenX, int screenY);
void AgentRefreshInputFont();
bool AgentInputHitTest(int x, int y, const RECT& panelRect);
bool AgentSendBtnHitTest(int x, int y, const RECT& panelRect);
bool AgentConversationBtnHitTest(int x, int y, const RECT& panelRect);
bool AgentNewBtnHitTest(int x, int y, const RECT& panelRect);
bool AgentClearBtnHitTest(int x, int y, const RECT& panelRect);
void AgentInputChanged(HWND editHwnd);
