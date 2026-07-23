#include "ui_draw.h"
#include "state.h"
#include "config.h"
#include <algorithm>
#include <cmath>

// Global layout values (set from main thread)
int g_clientW = 2440, g_clientH = 1760;
int g_w = 2440, g_h = 1760;
HDC g_hdcMem = nullptr;
HBITMAP g_hbmMem = nullptr;
float g_fontScale = 2.0f;

UIRect g_choiceOptionRects[CHOICE_OPTION_COUNT];
int g_choiceOptionRectCount = 0;

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

void TextWrap(Graphics& g, const std::wstring& text, Font* font, Color color,
              float x, float y, float w, float h) {
    RectF rect(x, y, w, h);
    StringFormat fmt;
    fmt.SetFormatFlags(StringFormatFlagsLineLimit);
    fmt.SetTrimming(StringTrimmingNone);
    fmt.SetLineAlignment(StringAlignmentCenter);
    SolidBrush brush(color);
    g.DrawString(text.c_str(), -1, font, rect, &fmt, &brush);
}

int MeasureWrappedTextHeight(Graphics& g, const std::wstring& text,
                             Font* font, float width) {
    StringFormat fmt;
    fmt.SetFormatFlags(StringFormatFlagsLineLimit);
    fmt.SetTrimming(StringTrimmingNone);
    RectF layout(0.0f, 0.0f, width, (REAL)QUESTION_MEASURE_HEIGHT);
    RectF bounds;
    Status status = g.MeasureString(text.c_str(), -1, font, layout, &fmt, &bounds);
    if (status != Ok) return 0;
    return (int)std::ceil(bounds.Height);
}

Font* MakeFittingQuestionFont(Graphics& g, const std::wstring& text,
                              float width, float height) {
    StringFormat fmt;
    fmt.SetFormatFlags(StringFormatFlagsLineLimit);
    RectF layout(0.0f, 0.0f, width, (REAL)QUESTION_MEASURE_HEIGHT);
    for (int fontSize = QUESTION_FONT_MAX_SIZE;
         fontSize >= QUESTION_FONT_MIN_SIZE;
         fontSize -= QUESTION_FONT_STEP) {
        Font* font = MakeFont((float)fontSize, FontStyleBold);
        RectF bounds;
        Status status = g.MeasureString(text.c_str(), -1, font, layout, &fmt, &bounds);
        if (status == Ok && bounds.Height <= height) return font;
        delete font;
    }
    return MakeFont((float)QUESTION_FONT_MIN_SIZE, FontStyleBold);
}

void DrawBackground(Graphics& g) {
    LinearGradientBrush bg(RectF(0, 0, (REAL)g_w, (REAL)g_h), Color(255, 240, 244, 249), Color(255, 226, 233, 243), LinearGradientModeVertical);
    g.FillRectangle(&bg, 0, 0, g_w, g_h);
    SolidBrush header(Color(255, 246, 249, 253));
    g.FillRectangle(&header, 0, 0, g_w, 84);
    SolidBrush gold(Color(255, 214, 143, 28));
    g.FillRectangle(&gold, 0, 82, g_w, 3);
}

void DrawHeader(Graphics& g, const std::wstring& rightText) {
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

void DrawButton(Graphics& g, int x, int y, int w, int h, const std::wstring& text, COLORREF c1, COLORREF c2, bool outline) {
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

void DrawFillScoreSelectPage(Graphics& g) {
    DrawBackground(g);
    DrawHeader(g, CFG_SCORE_SELECT_TITLE);
    DrawFontControls(g);

    int cardW = std::min(g_w - PAGE_HORIZONTAL_MARGIN, SCORE_SELECT_CARD_WIDTH);
    int cardX = (g_w - cardW) / UI_CENTER_DIVISOR;
    DrawCard(g, (float)cardX, (float)SCORE_SELECT_CARD_Y, (float)cardW,
             (float)SCORE_SELECT_CARD_HEIGHT, SCORE_SELECT_CARD_RADIUS);

    Font* title = MakeFont(SCORE_SELECT_TITLE_FONT_SIZE, FontStyleBold);
    Font* hint = MakeFont(SCORE_SELECT_HINT_FONT_SIZE);
    TextCenter(g, CFG_SCORE_SELECT_TITLE, title, GdiColor(CLR_NAVY), cardX,
               SCORE_SELECT_CARD_Y + SCORE_SELECT_TITLE_Y, cardW, SCORE_SELECT_TITLE_HEIGHT);
    TextCenter(g, CFG_SCORE_SELECT_HINT, hint, GdiColor(CLR_MUTED), cardX,
               SCORE_SELECT_CARD_Y + SCORE_SELECT_HINT_Y, cardW, SCORE_SELECT_HINT_HEIGHT);

    for (int i = NO_SCORE; i < FILL_SCORE_OPTION_COUNT; ++i) {
        UIRect button = FillScoreButtonRect(i);
        std::wstring label = IntToWStr(FILL_SCORE_OPTIONS[i]) + CFG_SCORE_SUFFIX;
        DrawButton(g, button.x, button.y, button.w, button.h, label,
                   CLR_GREEN, CLR_SCORE_BUTTON_SECONDARY);
    }

    UIRect backRect = ReturnHomeButtonRect();
    DrawButton(g, backRect.x, backRect.y, backRect.w, backRect.h,
               CFG_BTN_RETURN_HOME, CLR_RED, CLR_BACK_BUTTON_SECONDARY, true);
    delete title;
    delete hint;
}

UIRect HomeButtonRect(int idx) {
    int cw = std::min(g_w - 80, 880);
    int cx = (g_w - cw) / 2, cy = 110;
    int btnW = 210, btnGap = 30;
    int totalBtnW = btnW * 3 + btnGap * 2;
    int btnStartX = cx + (cw - totalBtnW) / 2;
    int btnY = cy + 408;
    return {btnStartX + idx * (btnW + btnGap), btnY, btnW, 56};
}

UIRect FillScoreButtonRect(int idx) {
    int cardW = std::min(g_w - PAGE_HORIZONTAL_MARGIN, SCORE_SELECT_CARD_WIDTH);
    int cardX = (g_w - cardW) / UI_CENTER_DIVISOR;
    int totalButtonW = SCORE_SELECT_BUTTON_WIDTH * SCORE_SELECT_COLUMNS
                     + SCORE_SELECT_BUTTON_GAP * (SCORE_SELECT_COLUMNS - 1);
    int startX = cardX + (cardW - totalButtonW) / UI_CENTER_DIVISOR;
    int column = idx % SCORE_SELECT_COLUMNS;
    int row = idx / SCORE_SELECT_COLUMNS;
    return {
        startX + column * (SCORE_SELECT_BUTTON_WIDTH + SCORE_SELECT_BUTTON_GAP),
        SCORE_SELECT_CARD_Y + SCORE_SELECT_BUTTON_Y + row * SCORE_SELECT_BUTTON_ROW_STEP,
        SCORE_SELECT_BUTTON_WIDTH,
        SCORE_SELECT_BUTTON_HEIGHT
    };
}

UIRect ReturnHomeButtonRect() {
    return {g_w - 300, 14, 138, 36};
}

UIRect FillEditRect() {
    int cardX = 38, cardY = 236, cardW = g_w - 76;
    int boxY = cardY + CHOICE_OPTION_START_OFFSET_Y;
    return {cardX + 34, boxY, cardW - 68, 56};
}

UIRect ConfirmButtonRect() {
    return {g_w / 2 - 116, g_h - 78, 232, 54};
}

void DrawQuizPage(Graphics& g) {
    DrawBackground(g);
    int remaining = QuestionRemainingSeconds();
    int totalElapsed = NO_TIME_SECONDS;
    if (TracksTotalTime()) {
        totalElapsed = (int)std::chrono::duration_cast<std::chrono::seconds>(std::chrono::system_clock::now() - g_state.quizStart).count();
    }
    DrawHeader(g, ModeName());
    DrawFontControls(g);

    const Question* q = CurrentQuestion();
    if (!q) return;

    int topX = 38, topY = 104, topW = g_w - 76, topH = 112;
    DrawCard(g, (float)topX, (float)topY, (float)topW, (float)topH, 18);
    Font* meta = MakeFont(14, FontStyleBold);
    Font* meta2 = MakeFont(13);
    int metricCount = TracksTotalTime() ? QUIZ_METRIC_COUNT_TIMED_WITH_TOTAL
                    : (IsTimedMode() ? QUIZ_METRIC_COUNT_TIMED_NO_TOTAL : QUIZ_METRIC_COUNT_UNTIMED);
    int metricW = (topW - QUIZ_METRIC_SIDE_PADDING * UI_CENTER_DIVISOR
                  - QUIZ_METRIC_GAP * (metricCount - 1)) / metricCount;
    bool shouldFlash = IsTimedMode() && remaining <= 5 && g_state.flashVisible;
    int metricX = topX + QUIZ_METRIC_SIDE_PADDING;
    DrawMetric(g, metricX, topY + 18, metricW, 70, CFG_METRIC_PROGRESS,
               L"第 " + IntToWStr(g_state.qNum) + L" / " + IntToWStr(g_state.total) + L" 题", CLR_BLUE);
    metricX += metricW + QUIZ_METRIC_GAP;
    DrawMetric(g, metricX, topY + 18, metricW, 70, CFG_METRIC_MODE, ModeName(), CLR_NAVY);
    if (IsTimedMode()) {
        metricX += metricW + QUIZ_METRIC_GAP;
        DrawMetric(g, metricX, topY + 18, metricW, 70, CFG_METRIC_REMAINING,
                   FormatDuration(remaining), shouldFlash ? CLR_RED : CLR_GOLD);
    }
    if (TracksTotalTime()) {
        metricX += metricW + QUIZ_METRIC_GAP;
        DrawMetric(g, metricX, topY + 18, metricW, 70, CFG_METRIC_TOTAL_TIME,
                   FormatDuration(totalElapsed), CLR_RED);
    }
    FillRoundRect(g, (float)(topX + 24), (float)(topY + 96), (float)(topW - 48), 10, 5, Color(255, 219, 227, 238));
    COLORREF progressBarColor = shouldFlash ? CLR_RED : CLR_BLUE;
    FillRoundRect(g, (float)(topX + 24), (float)(topY + 96), (float)((topW - 48) * g_state.qNum / g_state.total), 10, 5, GdiColor(progressBarColor));

    int cardX = 38, cardY = 236, cardW = g_w - 76, cardH = g_h - 336;
    DrawCard(g, (float)cardX, (float)cardY, (float)cardW, (float)cardH, 22);
    std::wstring questionText = IntToWStr(g_state.qNum) + L". " + q->q + L" (题库第" + IntToWStr(q->id) + L"题)";
    Font* qFont = nullptr;
    Font* optFont = nullptr;
    int questionHeight = QUESTION_TEXT_HEIGHT;
    int hintOffsetY = QUESTION_HINT_OFFSET_Y;
    int optionStartY = CHOICE_OPTION_START_OFFSET_Y;
    int optionHeights[CHOICE_OPTION_COUNT] = {};

    if (g_state.mode == MODE_FILL) {
        qFont = MakeFittingQuestionFont(
            g, questionText, (float)(cardW - QUESTION_TEXT_WIDTH_INSET),
            (float)QUESTION_TEXT_HEIGHT);
        optFont = MakeFont(CHOICE_OPTION_FONT_MAX_SIZE);
        g_choiceOptionRectCount = 0;
    } else {
        int feedbackReserve = g_state.answered
                            ? CHOICE_FEEDBACK_HEIGHT + CHOICE_FEEDBACK_GAP_Y
                            : NO_TIME_SECONDS;
        int availableHeight = cardH - QUESTION_TEXT_OFFSET_Y
                            - CHOICE_DYNAMIC_BOTTOM_PADDING - feedbackReserve;
        int optionTextWidth = cardW - CHOICE_OPTION_WIDTH_INSET
                            - CHOICE_OPTION_TEXT_WIDTH_INSET;

        for (int shrink = NO_SCORE; shrink <= CHOICE_FONT_SHRINK_STEPS; ++shrink) {
            int questionFontSize = std::max(
                QUESTION_FONT_MIN_SIZE, QUESTION_FONT_MAX_SIZE - shrink);
            int optionFontSize = std::max(
                CHOICE_OPTION_FONT_MIN_SIZE, CHOICE_OPTION_FONT_MAX_SIZE - shrink);
            Font* candidateQuestionFont = MakeFont(questionFontSize, FontStyleBold);
            Font* candidateOptionFont = MakeFont(optionFontSize);
            int candidateQuestionHeight = std::max(
                CHOICE_DYNAMIC_QUESTION_MIN_HEIGHT,
                MeasureWrappedTextHeight(
                    g, questionText, candidateQuestionFont,
                    (float)(cardW - QUESTION_TEXT_WIDTH_INSET))
                    + CHOICE_DYNAMIC_QUESTION_PADDING);
            int requiredHeight = candidateQuestionHeight
                               + CHOICE_DYNAMIC_QUESTION_HINT_GAP
                               + QUESTION_HINT_HEIGHT
                               + CHOICE_DYNAMIC_HINT_OPTION_GAP;
            int candidateOptionHeights[CHOICE_OPTION_COUNT] = {};
            for (int i = NO_SCORE; i < CHOICE_OPTION_COUNT; ++i) {
                candidateOptionHeights[i] = std::max(
                    CHOICE_DYNAMIC_OPTION_MIN_HEIGHT,
                    MeasureWrappedTextHeight(
                        g, q->opts[i], candidateOptionFont, (float)optionTextWidth)
                        + CHOICE_DYNAMIC_OPTION_PADDING);
                requiredHeight += candidateOptionHeights[i];
                if (i + 1 < CHOICE_OPTION_COUNT) requiredHeight += CHOICE_DYNAMIC_OPTION_GAP;
            }

            bool smallestFonts = questionFontSize == QUESTION_FONT_MIN_SIZE
                              && optionFontSize == CHOICE_OPTION_FONT_MIN_SIZE;
            if (requiredHeight <= availableHeight || smallestFonts) {
                qFont = candidateQuestionFont;
                optFont = candidateOptionFont;
                questionHeight = candidateQuestionHeight;
                for (int i = NO_SCORE; i < CHOICE_OPTION_COUNT; ++i) {
                    optionHeights[i] = candidateOptionHeights[i];
                }
                break;
            }
            delete candidateQuestionFont;
            delete candidateOptionFont;
        }
        hintOffsetY = QUESTION_TEXT_OFFSET_Y + questionHeight
                    + CHOICE_DYNAMIC_QUESTION_HINT_GAP;
        optionStartY = hintOffsetY + QUESTION_HINT_HEIGHT
                     + CHOICE_DYNAMIC_HINT_OPTION_GAP;
    }

    TextWrap(g, questionText, qFont, GdiColor(CLR_INK),
             cardX + QUESTION_TEXT_OFFSET_X,
             cardY + QUESTION_TEXT_OFFSET_Y,
             cardW - QUESTION_TEXT_WIDTH_INSET,
             questionHeight);

    UIRect backRect = ReturnHomeButtonRect();
    DrawButton(g, backRect.x, backRect.y, backRect.w, backRect.h, CFG_BTN_RETURN_HOME, CLR_RED, RGB(160, 30, 30), true);

    if (g_state.mode == MODE_FILL) {
        TextLeft(g, CFG_HINT_FILL, meta2, GdiColor(CLR_MUTED),
                 cardX + QUESTION_TEXT_OFFSET_X,
                 cardY + QUESTION_HINT_OFFSET_Y,
                 cardW - QUESTION_TEXT_WIDTH_INSET,
                 QUESTION_HINT_HEIGHT);

        UIRect editRect = FillEditRect();
        if (g_state.answered) {
            Color fill = Color(255, 244, 248, 253);
            Color border = GdiColor(CLR_LINE);
            FillRoundRectBorder(g, (float)editRect.x, (float)editRect.y,
                                (float)editRect.w, (float)editRect.h, 14, fill, border, 1.5f);
            std::wstring shown = g_state.userFill.empty() ? CFG_FILL_UNANSWERED : g_state.userFill;
            TextLeft(g, CFG_FILL_USER_ANSWER_LABEL + shown, optFont, GdiColor(CLR_INK),
                     editRect.x + 18, editRect.y + 16, editRect.w - 36, 28);
        }

        if (g_state.answered) {
            int fy = editRect.y + editRect.h + 14;
            FillRoundRectBorder(g, (float)(cardX + 34), (float)fy,
                                (float)(cardW - 68), 98, 14,
                                Color(255, 244, 248, 253), GdiColor(CLR_LINE), 1.5f);
            TextLeft(g, CFG_FEEDBACK_FILL_REFERRAL, meta, GdiColor(CLR_NAVY),
                     cardX + 54, fy + 10, cardW - 108, 24);
            std::wstring reference = q->fillAnswer;
            for (const auto& alt : q->fillAlts) reference += L" / " + alt;
            TextWrap(g, reference, meta2, GdiColor(CLR_INK),
                     cardX + 54, fy + 36, cardW - 108, 52);
        }
    } else {
        TextLeft(g, g_state.mode == MODE_MULTIPLE ? CFG_HINT_MULTIPLE : CFG_HINT_SINGLE,
                 meta2, GdiColor(CLR_MUTED),
                 cardX + QUESTION_TEXT_OFFSET_X,
                 cardY + hintOffsetY,
                 cardW - QUESTION_TEXT_WIDTH_INSET,
                 QUESTION_HINT_HEIGHT);

        int optY = cardY + optionStartY;
        int nextOptionY = optY;
        g_choiceOptionRectCount = CHOICE_OPTION_COUNT;
        for (int i = 0; i < CHOICE_OPTION_COUNT; ++i) {
            int ox = cardX + CHOICE_OPTION_LEFT_INSET;
            int oy = nextOptionY;
            int ow = cardW - CHOICE_OPTION_WIDTH_INSET;
            int oh = optionHeights[i];
            g_choiceOptionRects[i] = {ox, oy, ow, oh};
            nextOptionY += oh + CHOICE_DYNAMIC_OPTION_GAP;
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
            int letterY = oy + (oh - 32) / UI_CENTER_DIVISOR;
            FillRoundRect(g, (float)(ox + 14), (float)letterY, 32, 32, 16, GdiColor(letterColor));
            Font* lf = MakeFont(13, FontStyleBold);
            TextCenter(g, letter, lf, Color(255, 255, 255, 255), ox + 14, letterY, 32, 32);
            TextWrap(g, q->opts[i], optFont, GdiColor(CLR_INK),
                     ox + CHOICE_OPTION_TEXT_OFFSET_X,
                     oy + CHOICE_DYNAMIC_OPTION_PADDING / UI_CENTER_DIVISOR,
                     ow - CHOICE_OPTION_TEXT_WIDTH_INSET,
                     oh - CHOICE_DYNAMIC_OPTION_PADDING);
            delete lf;
        }

        if (g_state.answered) {
            const UIRect& lastOption = g_choiceOptionRects[CHOICE_OPTION_COUNT - 1];
            int fy = lastOption.y + lastOption.h + CHOICE_FEEDBACK_GAP_Y;
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

    bool fillMode = g_state.mode == MODE_FILL;
    int score = fillMode ? g_state.selectedScore
                         : (int)((double)g_state.correct / g_state.total * 100.0 + 0.5);
    int totalSeconds = NO_TIME_SECONDS;
    if (TracksTotalTime()) {
        totalSeconds = (int)std::chrono::duration_cast<std::chrono::seconds>(g_state.quizEnd - g_state.quizStart).count();
    }
    int cw = std::min(g_w - 80, 760), ch = 500;
    int cx = (g_w - cw) / 2, cy = 120;
    DrawCard(g, (float)cx, (float)cy, (float)cw, (float)ch, 24);

    Font* title = MakeFont(25, FontStyleBold);
    Font* scoreFont = MakeFont(46, FontStyleBold);
    Font* stat = MakeFont(16, FontStyleBold);
    Font* body = MakeFont(13);
    TextCenter(g, CFG_RESULT_TITLE, title, GdiColor(CLR_NAVY), cx, cy + 28, cw, 40);
    std::wstring scoreText = IntToWStr(score) + (fillMode ? CFG_SCORE_SUFFIX : L"");
    Color scoreColor = fillMode ? GdiColor(CLR_GREEN)
                                : (score >= 80 ? GdiColor(CLR_GREEN)
                                               : score >= 60 ? GdiColor(CLR_GOLD) : GdiColor(CLR_RED));
    TextCenter(g, scoreText, scoreFont, scoreColor, cx, cy + 78, cw, 70);
    TextCenter(g, fillMode ? CFG_METRIC_SELECTED_SCORE : CFG_RESULT_SCORE_LABEL,
               body, GdiColor(CLR_MUTED), cx, cy + 142, cw, 24);

    int sy = cy + 190;
    if (fillMode) {
        FillRoundRect(g, (float)(cx + 54), (float)sy, (float)(cw - 108), 34, 10, GdiColor(CLR_SOFT));
        TextLeft(g, CFG_METRIC_MODE, body, GdiColor(CLR_MUTED), cx + 76, sy + 7, 140, 22);
        TextLeft(g, ModeName(), stat, GdiColor(CLR_INK), cx + 220, sy + 5, cw - 310, 24);
    } else {
        int row = 0;
        auto drawStat = [&](const std::wstring& label, const std::wstring& value) {
            int y = sy + row * 42;
            FillRoundRect(g, (float)(cx + 54), (float)y, (float)(cw - 108), 34, 10, GdiColor(CLR_SOFT));
            TextLeft(g, label, body, GdiColor(CLR_MUTED), cx + 76, y + 7, 140, 22);
            TextLeft(g, value, stat, GdiColor(CLR_INK), cx + 220, y + 5, cw - 310, 24);
            ++row;
        };
        drawStat(CFG_METRIC_MODE, ModeName());
        drawStat(CFG_METRIC_CORRECT_TOTAL,
                 IntToWStr(g_state.correct) + L" / " + IntToWStr(g_state.total));
        if (TracksTotalTime()) drawStat(CFG_METRIC_DURATION, FormatDuration(totalSeconds));
        drawStat(CFG_METRIC_END_TIME, FormatTime(g_state.quizEnd));
    }

    DrawButton(g, cx + cw / 2 - 210, cy + ch - 82, 190, 48, CFG_BTN_RESTART, CLR_BLUE, RGB(30, 64, 175));
    DrawButton(g, cx + cw / 2 + 20, cy + ch - 82, 190, 48, CFG_BTN_RETURN_HOME, CLR_NAVY, RGB(34, 70, 118), true);

    delete title; delete scoreFont; delete stat; delete body;
}
