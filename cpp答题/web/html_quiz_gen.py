#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate quiz.html — 法律知识答题竞赛系统 (single-file, beautiful UI)."""
import json

def gen():
    with open('questions.json', 'r', encoding='utf-8-sig') as f:
        singles = json.load(f)
    with open('multiple_questions_from_xlsx.json', 'r', encoding='utf-8-sig') as f:
        multis = json.loads(f.read().replace('\r\n', '\n'))
    with open('fill_questions.json', 'r', encoding='utf-8-sig') as f:
        fills = json.load(f)

    def embed(obj):
        raw = json.dumps(obj, ensure_ascii=False, separators=(',',':'))
        return raw.replace('</', '<\\/')

    singles_s = embed(singles)
    multis_s  = embed(multis)
    fills_s   = embed(fills)

    rules = [
        "单选题只能选择一个答案；多选题可选择多个答案；填空题请直接输入答案。",
        "多选题须与标准答案完全一致，漏选或多选均不得分。",
        "填空/简答题提交后展示参考答案，不进行正误判定和分值计算。",
        "单选题和多选题每题限时30秒；填空/简答题不限时。",
        "单选题和多选题超时后本题锁定，请点击\"下一题\"继续作答。",
        "答题过程中可点击右上角\"返回主页\"按钮放弃本次答题，需二次确认。"
    ]

    rules_li = ""
    for r in rules:
        safe = r.replace('&','&amp;').replace('<','&lt;').replace('>','&gt;')
        rules_li += "<li>" + safe + "</li>\n"

    # ==================== CSS ====================
    css = """\
/* ===== Design System ===== */
:root {
  /* Colors */
  --clr-bg-start: #0b1a2e;
  --clr-bg-end: #0f2644;
  --clr-panel: rgba(255,255,255,0.97);
  --clr-card: rgba(255,255,255,0.95);
  --clr-border: rgba(255,255,255,0.12);
  --clr-border-strong: rgba(255,255,255,0.22);
  --clr-text: #1c2e47;
  --clr-text-secondary: #5a7a9e;
  --clr-text-light: #a0bcd8;
  --clr-primary: #1565c0;
  --clr-primary-dark: #0d47a1;
  --clr-primary-light: rgba(21,101,192,0.12);
  --clr-gold: #d4a017;
  --clr-gold-light: rgba(212,160,23,0.15);
  --clr-success: #2e7d32;
  --clr-success-light: rgba(46,125,50,0.12);
  --clr-danger: #c62828;
  --clr-danger-light: rgba(198,40,40,0.12);
  --clr-warning: #ef6c00;
  --clr-timer-flash: #e53935;

  /* Typography */
  --font-body: "Noto Serif SC", "Source Han Serif SC", "SimSun", serif;
  --font-display: "Noto Sans SC", "Source Han Sans SC", "Microsoft YaHei", sans-serif;
  --font-mono: "JetBrains Mono", "Fira Code", monospace;

  /* Spacing */
  --radius-sm: 6px;
  --radius-md: 12px;
  --radius-lg: 20px;
  --radius-xl: 28px;

  /* Shadows */
  --shadow-sm: 0 2px 8px rgba(0,0,0,0.08);
  --shadow-md: 0 4px 20px rgba(0,0,0,0.12);
  --shadow-lg: 0 8px 40px rgba(0,0,0,0.18);
  --shadow-glow: 0 0 30px rgba(212,160,23,0.15);

  /* Transitions */
  --transition-fast: 0.15s cubic-bezier(0.4, 0, 0.2, 1);
  --transition-base: 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  --transition-slow: 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

@import url('https://fonts.googleapis.com/css2?family=Noto+Sans+SC:wght@300;400;500;700;900&family=Noto+Serif+SC:wght@400;600;700&display=swap');

* { margin: 0; padding: 0; box-sizing: border-box; }

html, body {
  width: 100%; height: 100%;
  overflow: hidden;
  font-family: var(--font-body);
  color: var(--clr-text);
  background: linear-gradient(160deg, var(--clr-bg-start) 0%, var(--clr-bg-end) 40%, #132d50 100%);
  user-select: none;
  -webkit-font-smoothing: antialiased;
}

body {
  display: flex;
  flex-direction: column;
  position: relative;
}

/* Background texture overlay */
body::before {
  content: '';
  position: fixed;
  inset: 0;
  background:
    radial-gradient(ellipse at 20% 20%, rgba(21,101,192,0.08) 0%, transparent 60%),
    radial-gradient(ellipse at 80% 80%, rgba(212,160,23,0.06) 0%, transparent 60%),
    url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='60' height='60'%3E%3Cfilter id='n'%3E%3CfeTurbulence baseFrequency='.65' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)' opacity='.03'/%3E%3C/svg%3E");
  pointer-events: none;
  z-index: 0;
}

/* ===== Header Bar ===== */
.hdr-bar {
  position: relative;
  z-index: 10;
  height: 88px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 48px;
  background: linear-gradient(180deg, rgba(11,26,46,0.95) 0%, rgba(11,26,46,0.7) 100%);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--clr-border);
  flex-shrink: 0;
}

.hdr-bar .logo-area {
  display: flex;
  align-items: center;
  gap: 20px;
}

.hdr-bar .logo-icon {
  width: 48px;
  height: 48px;
  background: linear-gradient(135deg, var(--clr-gold), #f0c040);
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 900;
  color: #0b1a2e;
  box-shadow: 0 2px 12px rgba(212,160,23,0.3);
}

.hdr-bar .logo-text h1 {
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 2px;
}

.hdr-bar .logo-text .version {
  font-size: 11px;
  color: var(--clr-text-light);
  letter-spacing: 1px;
  margin-top: 2px;
}

.hdr-bar .center-info {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  font-family: var(--font-display);
  font-size: 14px;
  font-weight: 500;
  color: var(--clr-gold);
  letter-spacing: 3px;
}

.hdr-bar .hdr-controls {
  display: flex;
  align-items: center;
  gap: 8px;
}

.font-btn {
  width: 36px;
  height: 32px;
  border: 1px solid var(--clr-border-strong);
  border-radius: var(--radius-sm);
  background: rgba(255,255,255,0.06);
  color: var(--clr-text-light);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--transition-fast);
}

.font-btn:hover {
  background: rgba(255,255,255,0.12);
  color: #fff;
  border-color: var(--clr-gold);
}

.home-btn {
  height: 32px;
  padding: 0 18px;
  border: 1px solid rgba(198,40,40,0.5);
  border-radius: var(--radius-sm);
  background: rgba(198,40,40,0.1);
  color: #ef5350;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--transition-fast);
  letter-spacing: 1px;
}

.home-btn:hover {
  background: rgba(198,40,40,0.2);
  border-color: #ef5350;
}

/* ===== Pages ===== */
.pg {
  position: relative;
  z-index: 1;
  display: none !important;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
}

.pg.on {
  display: flex !important;
}

/* ===== HOME PAGE ===== */
.hp {
  align-items: center;
  justify-content: center;
  padding: 40px 60px;
}

.home-container {
  width: 100%;
  max-width: 960px;
  text-align: center;
}

.home-hero {
  margin-bottom: 48px;
}

.home-hero h2 {
  font-family: var(--font-display);
  font-size: 36px;
  font-weight: 900;
  color: #fff;
  letter-spacing: 4px;
  text-shadow: 0 2px 20px rgba(0,0,0,0.3);
  margin-bottom: 16px;
}

.home-hero .tagline {
  font-size: 15px;
  color: var(--clr-text-light);
  letter-spacing: 2px;
}

.home-card {
  background: var(--clr-panel);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-lg), var(--shadow-glow);
  padding: 48px 56px 40px;
  text-align: left;
  position: relative;
  overflow: hidden;
}

.home-card::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 4px;
  background: linear-gradient(90deg, var(--clr-primary), var(--clr-gold), var(--clr-success));
}

.home-card h3 {
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 700;
  color: var(--clr-primary-dark);
  margin-bottom: 24px;
  letter-spacing: 2px;
}

.rules-list {
  list-style: none;
  margin-bottom: 36px;
}

.rules-list li {
  font-size: 14px;
  line-height: 1.8;
  color: var(--clr-text);
  padding: 8px 0;
  border-bottom: 1px dashed rgba(0,0,0,0.06);
  padding-left: 20px;
  position: relative;
}

.rules-list li:last-child { border-bottom: none; }

.rules-list li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 8px;
  height: 8px;
  background: var(--clr-gold);
  border-radius: 50%;
}

.mode-buttons {
  display: flex;
  gap: 24px;
  justify-content: center;
}

.mode-btn {
  flex: 1;
  max-width: 260px;
  padding: 28px 20px 20px;
  border: none;
  border-radius: var(--radius-lg);
  color: #fff;
  font-family: var(--font-display);
  font-size: 17px;
  font-weight: 700;
  cursor: pointer;
  transition: all var(--transition-base);
  position: relative;
  overflow: hidden;
  letter-spacing: 2px;
}

.mode-btn::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(255,255,255,0.15) 0%, transparent 50%);
  pointer-events: none;
}

.mode-btn:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 25px rgba(0,0,0,0.25);
}

.mode-btn:active {
  transform: translateY(-1px);
}

.mode-btn.blue {
  background: linear-gradient(135deg, #1565c0, #0d47a1);
}

.mode-btn.navy {
  background: linear-gradient(135deg, #1a3a5c, #0f2440);
}

.mode-btn.green {
  background: linear-gradient(135deg, #2e7d32, #1b5e20);
}

.mode-count {
  display: block;
  margin-top: 10px;
  font-size: 13px;
  font-weight: 400;
  opacity: 0.85;
  letter-spacing: 1px;
}

/* ===== SCORE SELECT PAGE ===== */
.score-page {
  align-items: center;
  justify-content: center;
  padding: 60px;
}

.score-container {
  width: 100%;
  max-width: 780px;
  text-align: center;
}

.score-title-block {
  margin-bottom: 40px;
}

.score-title-block h2 {
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 3px;
  text-shadow: 0 2px 12px rgba(0,0,0,0.2);
}

.score-title-block p {
  font-size: 14px;
  color: var(--clr-text-light);
  margin-top: 10px;
  letter-spacing: 1px;
}

.score-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-bottom: 40px;
}

.score-item {
  background: var(--clr-card);
  border-radius: var(--radius-lg);
  padding: 24px 16px;
  cursor: pointer;
  transition: all var(--transition-base);
  border: 2px solid transparent;
  position: relative;
  overflow: hidden;
}

.score-item::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, rgba(46,125,50,0.08), rgba(46,125,50,0.02));
  opacity: 0;
  transition: opacity var(--transition-base);
}

.score-item:hover::before { opacity: 1; }

.score-item:hover {
  transform: translateY(-4px);
  border-color: var(--clr-success);
  box-shadow: 0 8px 30px rgba(46,125,50,0.2);
}

.score-value {
  font-family: var(--font-display);
  font-size: 32px;
  font-weight: 900;
  color: var(--clr-success);
  position: relative;
  z-index: 1;
}

.score-label {
  font-size: 13px;
  color: var(--clr-text-secondary);
  margin-top: 6px;
  position: relative;
  z-index: 1;
}

.back-home-btn {
  height: 40px;
  padding: 0 28px;
  border: 1.5px solid rgba(198,40,40,0.4);
  border-radius: var(--radius-md);
  background: transparent;
  color: #ef5350;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--transition-fast);
  letter-spacing: 1px;
}

.back-home-btn:hover {
  background: var(--clr-danger-light);
  border-color: #ef5350;
}

/* ===== QUIZ PAGE ===== */
.quiz-page {
  flex-direction: column;
}

/* Metrics row */
.metrics-row {
  display: flex;
  gap: 14px;
  padding: 20px 40px 0;
  margin-bottom: 0;
  flex-shrink: 0;
}

.metric-card {
  flex: 1;
  background: var(--clr-card);
  border-radius: var(--radius-md);
  padding: 16px 20px;
  position: relative;
  overflow: hidden;
  box-shadow: var(--shadow-sm);
  transition: all var(--transition-base);
}

.metric-card::before {
  content: '';
  position: absolute;
  left: 0; top: 0; bottom: 0;
  width: 4px;
  border-radius: 4px 0 0 4px;
}

.metric-card.m-progress::before { background: var(--clr-primary); }
.metric-card.m-mode::before { background: var(--clr-text-secondary); }
.metric-card.m-timer::before { background: var(--clr-warning); }
.metric-card.m-total::before { background: var(--clr-danger); }

.metric-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--clr-text-secondary);
  letter-spacing: 2px;
  text-transform: uppercase;
}

.metric-value {
  font-family: var(--font-display);
  font-size: 16px;
  font-weight: 700;
  color: var(--clr-text);
  margin-top: 4px;
  letter-spacing: 1px;
}

.metric-card.m-timer .metric-value { color: var(--clr-warning); }
.metric-card.m-timer.flash .metric-value { color: var(--clr-timer-flash); }
.metric-card.m-total .metric-value { color: var(--clr-danger); }

/* Progress bar */
.progress-track {
  margin: 16px 40px 0;
  height: 6px;
  background: rgba(255,255,255,0.1);
  border-radius: 3px;
  overflow: hidden;
  flex-shrink: 0;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--clr-primary), var(--clr-gold));
  border-radius: 3px;
  transition: width 0.5s ease;
  width: 0%;
}

/* Question area */
.question-area {
  flex: 1;
  padding: 20px 40px 16px;
  overflow-y: auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.question-card {
  background: var(--clr-panel);
  border-radius: var(--radius-lg);
  padding: 32px 36px;
  box-shadow: var(--shadow-md);
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  min-height: 0;
}

.question-number {
  font-family: var(--font-display);
  font-size: 14px;
  font-weight: 700;
  color: var(--clr-primary);
  letter-spacing: 2px;
  margin-bottom: 12px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.question-number::before {
  content: '';
  display: inline-block;
  width: 8px;
  height: 8px;
  background: var(--clr-primary);
  border-radius: 50%;
}

.question-text {
  font-size: 20px;
  font-weight: 700;
  line-height: 1.6;
  color: var(--clr-text);
  white-space: pre-wrap;
  word-break: break-word;
  margin-bottom: 8px;
}

.question-hint {
  font-size: 13px;
  color: var(--clr-text-secondary);
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid rgba(0,0,0,0.06);
  white-space: pre-wrap;
}

/* Option items */
.options-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex: 1;
}

.option-item {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 16px 18px;
  border: 2px solid rgba(0,0,0,0.08);
  border-radius: var(--radius-md);
  cursor: default;
  transition: all var(--transition-fast);
  background: #fafbfd;
  white-space: pre-wrap;
  word-break: break-word;
}

.option-item.clickable { cursor: pointer; }

.option-item.clickable:hover {
  border-color: var(--clr-primary);
  background: var(--clr-primary-light);
  transform: translateX(4px);
}

.option-item.selected {
  border-color: var(--clr-primary);
  background: var(--clr-primary-light);
  box-shadow: 0 2px 12px rgba(21,101,192,0.1);
}

.option-item.correct-answer {
  border-color: var(--clr-success);
  background: var(--clr-success-light);
  box-shadow: 0 2px 12px rgba(46,125,50,0.1);
}

.option-item.wrong-answer {
  border-color: var(--clr-danger);
  background: var(--clr-danger-light);
  box-shadow: 0 2px 12px rgba(198,40,40,0.1);
}

.option-marker {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-display);
  font-size: 14px;
  font-weight: 700;
  flex-shrink: 0;
  transition: all var(--transition-fast);
  color: #fff;
}

.option-marker.default { background: var(--clr-text-secondary); }
.option-marker.chosen { background: var(--clr-primary); }
.option-marker.is-correct { background: var(--clr-success); }
.option-marker.is-wrong { background: var(--clr-danger); }

.option-text {
  font-size: 16px;
  line-height: 1.6;
  flex: 1;
  padding-top: 4px;
  white-space: pre-wrap;
  word-break: break-word;
}

/* Feedback */
.feedback-box {
  margin-top: 24px;
  padding: 20px 24px;
  border-radius: var(--radius-md);
  border-left: 4px solid;
}

.feedback-box.correct {
  border-color: var(--clr-success);
  background: linear-gradient(135deg, rgba(46,125,50,0.06), rgba(46,125,50,0.02));
}

.feedback-box.incorrect {
  border-color: var(--clr-danger);
  background: linear-gradient(135deg, rgba(198,40,40,0.06), rgba(198,40,40,0.02));
}

.feedback-text {
  font-size: 16px;
  font-weight: 700;
  margin-bottom: 6px;
}

.feedback-box.correct .feedback-text { color: var(--clr-success); }
.feedback-box.incorrect .feedback-text { color: var(--clr-danger); }

.explanation-text {
  font-size: 13px;
  color: var(--clr-text);
  line-height: 1.7;
  white-space: pre-wrap;
}

/* Fill input */
.fill-input-area {
  margin-top: 8px;
  margin-bottom: 24px;
  padding-bottom: 24px;
  border-bottom: 1px solid rgba(0,0,0,0.06);
}

.fill-input {
  width: 100%;
  height: 56px;
  border: 2px solid rgba(0,0,0,0.1);
  border-radius: var(--radius-md);
  padding: 0 20px;
  font-size: 18px;
  font-family: var(--font-body);
  outline: none;
  transition: all var(--transition-fast);
  background: #fafbfd;
  white-space: pre-wrap;
}

.fill-input:focus {
  border-color: var(--clr-primary);
  background: #fff;
  box-shadow: 0 2px 12px rgba(21,101,192,0.1);
}

.fill-display {
  width: 100%;
  min-height: 56px;
  border: 2px solid rgba(0,0,0,0.1);
  border-radius: var(--radius-md);
  padding: 0 20px;
  font-size: 18px;
  background: #f4f6f8;
  display: flex;
  align-items: center;
  white-space: pre-wrap;
  color: var(--clr-text);
}

.reference-box {
  margin-top: 20px;
  padding: 20px 24px;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, rgba(212,160,23,0.08), rgba(212,160,23,0.02));
  border: 1px solid rgba(212,160,23,0.2);
}

.ref-label {
  font-size: 14px;
  font-weight: 700;
  color: #8d6e00;
  margin-bottom: 8px;
  letter-spacing: 2px;
}

.ref-text {
  font-size: 14px;
  color: var(--clr-text);
  line-height: 1.8;
  white-space: pre-wrap;
}

/* Bottom confirm button */
.confirm-bar {
  display: flex;
  justify-content: center;
  padding: 12px 40px 24px;
  flex-shrink: 0;
}

.confirm-btn {
  width: 280px;
  height: 52px;
  border: none;
  border-radius: var(--radius-md);
  color: #fff;
  font-family: var(--font-display);
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
  transition: all var(--transition-base);
  letter-spacing: 4px;
  position: relative;
  overflow: hidden;
}

.confirm-btn.primary {
  background: linear-gradient(135deg, var(--clr-primary), var(--clr-primary-dark));
  box-shadow: 0 4px 15px rgba(21,101,192,0.3);
}

.confirm-btn.primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(21,101,192,0.4);
}

.confirm-btn.success {
  background: linear-gradient(135deg, var(--clr-success), #1b5e20);
  box-shadow: 0 4px 15px rgba(46,125,50,0.3);
}

.confirm-btn.success:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(46,125,50,0.4);
}

/* ===== RESULT PAGE ===== */
.result-page {
  align-items: center;
  justify-content: center;
  padding: 60px;
}

.result-container {
  width: 100%;
  max-width: 800px;
  text-align: center;
}

.result-card {
  background: var(--clr-panel);
  border-radius: var(--radius-xl);
  padding: 56px 64px 48px;
  box-shadow: var(--shadow-lg);
  position: relative;
  overflow: hidden;
}

.result-card::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 4px;
  background: linear-gradient(90deg, var(--clr-primary), var(--clr-gold), var(--clr-success));
}

.result-heading {
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 700;
  color: var(--clr-text);
  letter-spacing: 4px;
  margin-bottom: 32px;
}

.result-score-block {
  margin-bottom: 40px;
}

.result-score {
  font-family: var(--font-display);
  font-size: 72px;
  font-weight: 900;
  line-height: 1;
}

.result-score.good { color: var(--clr-success); }
.result-score.mid { color: var(--clr-gold); }
.result-score.bad { color: var(--clr-danger); }
.result-score.fill { color: var(--clr-success); }

.result-score-unit {
  font-size: 24px;
  font-weight: 600;
}

.result-score-label {
  font-size: 14px;
  color: var(--clr-text-secondary);
  margin-top: 8px;
  letter-spacing: 2px;
}

.result-stats {
  width: 100%;
  margin-bottom: 40px;
}

.stat-row {
  display: flex;
  align-items: center;
  padding: 14px 24px;
  background: #f7f8fa;
  border-radius: var(--radius-md);
  margin-bottom: 8px;
}

.stat-key {
  font-size: 13px;
  color: var(--clr-text-secondary);
  width: 140px;
  text-align: right;
  margin-right: 20px;
  letter-spacing: 1px;
  flex-shrink: 0;
}

.stat-val {
  font-size: 16px;
  font-weight: 600;
  color: var(--clr-text);
  flex: 1;
  text-align: left;
}

.result-actions {
  display: flex;
  gap: 20px;
  justify-content: center;
}

.action-btn {
  height: 46px;
  padding: 0 36px;
  border: none;
  border-radius: var(--radius-md);
  font-family: var(--font-display);
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  transition: all var(--transition-base);
  letter-spacing: 3px;
}

.action-btn.restart {
  color: #fff;
  background: linear-gradient(135deg, var(--clr-primary), var(--clr-primary-dark));
  box-shadow: 0 4px 12px rgba(21,101,192,0.3);
}

.action-btn.restart:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(21,101,192,0.4);
}

.action-btn.fill-restart {
  color: #fff;
  background: linear-gradient(135deg, var(--clr-success), #1b5e20);
  box-shadow: 0 4px 12px rgba(46,125,50,0.3);
}

.action-btn.fill-restart:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(46,125,50,0.4);
}

.action-btn.home {
  background: transparent;
  border: 1.5px solid rgba(0,0,0,0.15);
  color: var(--clr-text-secondary);
}

.action-btn.home:hover {
  border-color: var(--clr-text);
  color: var(--clr-text);
  background: rgba(0,0,0,0.03);
}

/* ===== Flash animation ===== */
@keyframes timer-flash {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}
.timer-flash .metric-value {
  animation: timer-flash 0.5s infinite;
  color: var(--clr-timer-flash) !important;
}

/* ===== Scrollbar ===== */
::-webkit-scrollbar { width: 6px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb { background: rgba(0,0,0,0.15); border-radius: 3px; }
::-webkit-scrollbar-thumb:hover { background: rgba(0,0,0,0.25); }

/* ===== Responsive ===== */
@media (max-width: 1024px) {
  .home-card { padding: 32px 36px 28px; }
  .mode-buttons { flex-direction: column; gap: 12px; align-items: stretch; }
  .mode-btn { max-width: none; }
  .metrics-row { padding: 16px 20px 0; gap: 8px; }
  .question-area { padding: 16px 20px 12px; }
  .confirm-bar { padding: 12px 20px 20px; }
}
"""

    # ==================== HTML ====================
    html_parts = []

    html_parts.append("""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>法律知识答题竞赛系统</title>
<style>
""" + css + """
</style>
</head>
<body>

<!-- ========== HEADER BAR ========== -->
<div class="hdr-bar">
  <div class="logo-area">
    <div class="logo-icon">律</div>
    <div class="logo-text">
      <h1>法律知识答题竞赛系统</h1>
      <span class="version">V1.0.0</span>
    </div>
  </div>
  <div id="quiz_mode_label" class="center-info"></div>
  <div class="hdr-controls">
    <button class="font-btn" onclick="fontDown()">A-</button>
    <button class="font-btn" onclick="fontUp()">A+</button>
  </div>
</div>

<!-- ========== HOME PAGE ========== -->
<div id="page-home" class="pg hp">
  <div class="home-container">
    <div class="home-hero">
      <h2>法律知识答题竞赛</h2>
      <p class="tagline">系统随机抽题，不同模式采用独立的答题规则</p>
    </div>
    <div class="home-card">
      <h3>答题规则</h3>
      <ul class="rules-list">
""")
    html_parts.append(rules_li)
    html_parts.append("""      </ul>
      <div class="mode-buttons">
        <button class="mode-btn blue" onclick="startQuiz('single')">
          必答题
          <span class="mode-count">3 题 · 每题30秒限时</span>
        </button>
        <button class="mode-btn navy" onclick="startQuiz('multiple')">
          抢答题
          <span class="mode-count">10 题 · 每题30秒限时</span>
        </button>
        <button class="mode-btn green" onclick="openScoreSelect()">
          风险题
          <span class="mode-count">2 题 · 不限时 · 多选值</span>
        </button>
      </div>
    </div>
  </div>
</div>

<!-- ========== SCORE SELECT PAGE ========== -->
<div id="page-score" class="pg score-page">
  <div class="score-container">
    <div class="score-title-block">
      <h2>请选择本次答题分值</h2>
      <p>选择分值后进入填空/简答题</p>
    </div>
    <div class="score-grid" id="score_grid"></div>
    <button class="back-home-btn" onclick="goHome()">返 回 主 页</button>
  </div>
</div>

<!-- ========== QUIZ PAGE ========== -->
<div id="page-quiz" class="pg quiz-page">
  <div class="metrics-row">
    <div class="metric-card m-progress">
      <div class="metric-label">当前进度</div>
      <div class="metric-value" id="mv_progress">第 1 / 3 题</div>
    </div>
    <div class="metric-card m-mode">
      <div class="metric-label">答题模式</div>
      <div class="metric-value" id="mv_mode">单选题模式</div>
    </div>
    <div class="metric-card m-timer" id="m_timer" style="display:none;">
      <div class="metric-label">本题剩余</div>
      <div class="metric-value" id="mv_timer">30秒</div>
    </div>
    <div class="metric-card m-total" id="m_total" style="display:none;">
      <div class="metric-label">总用时</div>
      <div class="metric-value" id="mv_total">0秒</div>
    </div>
  </div>
  <div class="progress-track">
    <div class="progress-fill" id="progress_fill"></div>
  </div>
  <div class="question-area">
    <div class="question-card" id="question_card">
      <!-- Question rendered by JS -->
    </div>
  </div>
  <div class="confirm-bar">
    <button class="confirm-btn primary" id="confirm_btn" onclick="confirmBtn()"></button>
  </div>
</div>

<!-- ========== RESULT PAGE ========== -->
<div id="page-result" class="pg result-page">
  <div class="result-container">
    <div class="result-card">
      <div class="result-heading">答 题 结 果</div>
      <div class="result-score-block">
        <span class="result-score gn" id="result_score">100</span><span class="result-score-unit"></span>
        <div class="result-score-label" id="result_label">总 分</div>
      </div>
      <div class="result-stats" id="result_stats"></div>
      <div class="result-actions">
        <button class="action-btn restart" id="restart_btn" onclick="restartQuiz()">再 次 答 题</button>
        <button class="action-btn home" onclick="goHome()">返 回 主 页</button>
      </div>
    </div>
  </div>
</div>

</body>
""")

    # ==================== JAVASCRIPT ====================
    js = """
<script>
// ===== Question Data =====
var SINGLE_QUESTIONS = {singles};
var MULTIPLE_QUESTIONS = {multis};
var FILL_QUESTIONS = {fills};

var QUESTION_TIME_LIMIT = 30;
var SCORE_OPTIONS = [10, 20, 30, 40, 50, 60];

// ===== State =====
var state = {{
  page: 'home', mode: null, qNum: 0, total: 0,
  correct: 0, wrong: 0, selectedScore: 0,
  answered: false, lastCorrect: false, timedOut: false,
  used: [], curQIdx: -1, selected: [false,false,false,false],
  userFill: '', settledSeconds: 0, quizStart: null, quizEnd: null,
  questionStart: null, flashVisible: false, fillBucket: -1,
  bank: null, questionSeconds: []
}};
var fontScale = 2.0;
var singleAnsweredIds = {{}};
var timerInterval = null;
var homeBtn = null;

// ===== Helpers =====
function arrEq(a,b){{
  if(a.length!==b.length) return false;
  var sa=a.slice().sort(), sb=b.slice().sort();
  for(var i=0;i<sa.length;i++) if(sa[i]!==sb[i]) return false;
  return true;
}}
function selAns(){{
  var r=[];
  for(var i=0;i<state.selected.length;i++) if(state.selected[i]) r.push(i);
  return r;
}}
function esc(s){{
  return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}}
function getBank(mode){{
  if(mode==='multiple') return MULTIPLE_QUESTIONS.slice();
  if(mode==='fill') return FILL_QUESTIONS.slice();
  return SINGLE_QUESTIONS.slice();
}}
function resetQuiz(bank){{
  state.qNum=0; state.correct=0; state.wrong=0; state.selectedScore=0;
  state.answered=false; state.lastCorrect=false; state.timedOut=false;
  state.curQIdx=-1; state.selected=[false,false,false,false];
  state.flashVisible=false; state.userFill=''; state.settledSeconds=0;
  state.questionSeconds=[];
  state.used=new Array(bank.length).fill(false);
  if(state.mode==='fill' && state.fillBucket>=0){{
    var base=state.fillBucket*3;
    for(var i=base;i<base+3&&i<bank.length;i++) state.used[i]=false;
  }}
}}
function pickUnused(){{
  var bank=state.bank;
  var s=0, e=bank.length;
  if(state.mode==='fill' && state.fillBucket>=0 && state.fillBucket<SCORE_OPTIONS.length){{
    s=state.fillBucket*3; e=Math.min(s+3,bank.length);
  }}
  var avail=[];
  for(var i=s;i<e;i++){{
    if(state.used[i]) continue;
    if(state.mode==='single' && singleAnsweredIds[bank[i].id]) continue;
    avail.push(i);
  }}
  if(!avail.length) return -1;
  return avail[Math.floor(Math.random()*avail.length)];
}}
function startQuestion(){{
  var idx=pickUnused();
  if(idx<0) return false;
  state.curQIdx=idx;
  state.used[idx]=true;
  if(state.mode==='single') singleAnsweredIds[state.bank[idx].id]=true;
  state.selected=[false,false,false,false];
  state.userFill=''; state.answered=false; state.timedOut=false;
  state.settledSeconds=0; state.questionStart=Date.now();
  renderQuestion();
  return true;
}}
function isTimedMode(){{ return state.mode==='single'||state.mode==='multiple'; }}
function tracksTotalTime(){{ return state.mode==='single'; }}
function modeName(){{
  if(state.mode==='multiple') return '多选题模式';
  if(state.mode==='fill') return '填空/简答模式';
  return '单选题模式';
}}
function curQuestion(){{
  if(state.curQIdx<0||!state.bank) return null;
  return state.bank[state.curQIdx];
}}
function remainSec(){{
  if(!isTimedMode()) return 0;
  var el=state.answered?state.settledSeconds:Math.floor((Date.now()-state.questionStart)/1000);
  return Math.max(0, QUESTION_TIME_LIMIT-el);
}}
function fmtDur(sec){{
  var h=Math.floor(sec/3600), m=Math.floor((sec%3600)/60), s=sec%60, r='';
  if(h>0) r+=h+'小时';
  if(m>0||h>0) r+=m+'分';
  r+=s+'秒';
  return r;
}}
function ansLtrs(ans){{
  var parts=[];
  for(var i=0;i<ans.length;i++) parts.push(String.fromCharCode(65+ans[i]));
  return parts.join('\\u3001');
}}

// ===== Page Navigation =====
function showPage(id){{
  document.querySelectorAll('.pg').forEach(function(p){{ p.classList.remove('on'); }});
  var el = document.getElementById('page-'+id);
  if(el) el.classList.add('on');
  state.page = id;
}}
function startQuiz(mode){{
  state.mode=mode; state.bank=getBank(mode);
  resetQuiz(state.bank);
  state.total=(mode==='multiple'?10:(mode==='fill'?2:3));
  state.quizStart=Date.now();
  showPage('quiz'); updateMetrics(); state.qNum=1; startQuestion();
}}
function openScoreSelect(){{
  state.mode='fill'; state.bank=getBank('fill');
  resetQuiz(state.bank); state.total=2;
  showPage('score');
}}
function goHome(){{
  stopTimer();
  if(homeBtn && homeBtn.parentNode) homeBtn.parentNode.removeChild(homeBtn);
  state.page='home'; state.mode=null; state.bank=null;
  for(var k in singleAnsweredIds) delete singleAnsweredIds[k];
  showPage('home');
}}
function restartQuiz(){{
  if(state.mode==='fill') openScoreSelect();
  else startQuiz(state.mode);
}}

// ===== Timer =====
function startTimer(){{
  stopTimer();
  timerInterval=setInterval(tick,1000);
}}
function stopTimer(){{
  if(timerInterval){{clearInterval(timerInterval);timerInterval=null;}}
}}
function tick(){{
  if(state.page!=='quiz'||state.answered||!isTimedMode()) return;
  var rm=remainSec();
  if(rm<=0){{settle(true);return;}}
  updateMetrics();
  if(rm<=5){{
    var te=document.getElementById('m_timer');
    if(te) te.classList.add('timer-flash');
  }}else{{
    var te=document.getElementById('m_timer');
    if(te) te.classList.remove('timer-flash');
  }}
}}

// ===== Metrics =====
function updateMetrics(){{
  document.getElementById('quiz_mode_label').textContent=modeName();
  document.getElementById('mv_progress').textContent='第 '+state.qNum+' / '+state.total+' 题';
  document.getElementById('mv_mode').textContent=modeName();
  var te=document.getElementById('m_timer');
  var te2=document.getElementById('m_total');
  if(isTimedMode()){{
    te.style.display='';
    document.getElementById('mv_timer').textContent=fmtDur(remainSec());
  }}else{{te.style.display='none';}}
  if(tracksTotalTime()){{
    te2.style.display='';
    document.getElementById('mv_total').textContent=fmtDur(Math.floor((Date.now()-state.quizStart)/1000));
  }}else{{te2.style.display='none';}}
  document.getElementById('progress_fill').style.width=((state.qNum/state.total)*100)+'%';
}}

// ===== Return Home Button (in quiz) =====
function addReturnBtn(){{
  var hdr=document.querySelector('#page-quiz .hdr-bar');
  if(!hdr) return;
  homeBtn=document.createElement('button');
  homeBtn.className='home-btn';
  homeBtn.textContent='返回主页';
  homeBtn.onclick=function(){{
    if(confirm('确定要放弃本次答题并返回主页吗？\\n本次答题记录将被丢弃，无法恢复。')){{
      goHome();
    }}
  }};
  hdr.appendChild(homeBtn);
}}

// ===== Rendering =====
function renderQuestion(){{
  var q=curQuestion();
  if(!q) return;
  var card=document.getElementById('question_card');
  var btn=document.getElementById('confirm_btn');
  var finalQ=state.answered&&state.qNum>=state.total;
  if(final){{btn.className='confirm-btn success';btn.textContent='提 交 结 果';}}
  else if(state.answered){{btn.className='confirm-btn primary';btn.textContent='下 一 题';}}
  else if(state.mode==='fill'){{btn.className='confirm-btn primary';btn.textContent='提 交 答 案';}}
  else{{btn.className='confirm-btn primary';btn.textContent='确 认 答 案';}}

  if(state.mode==='fill') renderFill(card,q);
  else renderChoice(card,q);
}}

function renderChoice(card, q){{
  var letters=['A','B','C','D'];
  var h='<div class="question-number">第'+state.qNum+'题</div>';
  h+='<div class="question-text">'+q.question+'</div>';
  if(!state.answered){{
    h+='<div class="question-hint">'+(state.mode==='multiple'?'请选择所有正确答案后确认':'请选择一个正确答案后确认')+'</div>';
  }}
  h+='<div class="options-group">';
  for(var i=0;i<4;i++){{
    if(!q.options[i]||!q.options[i].trim()) continue;
    var c='option-item', cc='option-marker default';
    var sel=state.selected[i];
    var isOk=q.answers.indexOf(i)>=0;
    if(state.answered){{
      if(isOk){{c+=' correct-answer';cc='option-marker is-correct';}}
      else if(sel){{c+=' wrong-answer';cc='option-marker is-wrong';}}
    }} else if(sel){{c+=' selected';cc='option-marker chosen';}}
    c+=' clickable';
    h+='<div class="'+c+'" data-idx="'+i+'" onclick="optClick('+i+')">';
    h+='<div class="'+cc+'">'+letters[i]+'</div>';
    h+='<div class="option-text">'+esc(q.options[i])+'</div>';
    h+='</div>';
  }}
  h+='</div>';

  if(state.answered){{
    var fbClass=state.lastCorrect?'feedback-box correct':'feedback-box incorrect';
    var fbText;
    if(state.lastCorrect) fbText='回答正确。';
    else if(state.timedOut) fbText='答题超时，本题按错误结算。';
    else fbText='回答错误，正确答案：'+ansLtrs(q.answers)+'。';
    h+='<div class="'+fbClass+'"><div class="feedback-text">'+fbText+'</div>';
    if(q.explanation) h+='<div class="explanation-text">'+esc(q.explanation)+'</div>';
    h+='</div>';
  }}
  card.innerHTML=h;
}}

function renderFill(card, q){{
  var h='<div class="question-number">第'+state.qNum+'题</div>';
  h+='<div class="question-text">'+q.question+'</div>';
  h+='<div class="question-hint">请在下方输入框中填写答案，提交后显示参考答案</div>';
  if(!state.answered){{
    h+='<div class="fill-input-area"><input type="text" class="fill-input" id="fill_input" placeholder="请输入您的答案" onkeydown="if(event.key===`Enter`)confirmBtn()" /></div>';
  }} else {{
    var display=state.userFill||'(未作答)';
    h+='<div class="fill-display">'+esc(display)+'</div>';
  }}
  if(state.answered){{
    var ref=q.fill_answer;
    if(q.fill_alternatives&&q.fill_alternatives.length) ref+=' / '+q.fill_alternatives.join(' / ');
    h+='<div class="reference-box"><div class="ref-label">参 考 答 案</div><div class="ref-text">'+esc(ref)+'</div></div>';
  }}
  card.innerHTML=h;
  if(!state.answered){{
    setTimeout(function(){{
      var inp=document.getElementById('fill_input');
      if(inp) inp.focus();
    }},50);
  }}
}}

// ===== Interactions =====
function optClick(idx){{
  if(state.answered) return;
  if(state.mode==='single'){{
    state.selected=[false,false,false,false];
    state.selected[idx]=true;
  }} else {{
    state.selected[idx]=!state.selected[idx];
  }}
  renderQuestion();
}}

function confirmBtn(){{
  if(!state.answered){{
    if(state.mode==='fill'){{
      var inp=document.getElementById('fill_input');
      state.userFill=inp?inp.value.trim():'';
      if(!state.userFill){{ alert('请先在输入框中填写答案。');return; }}
    }} else if(!selAns().length){{ alert('请先选择答案。');return; }}
    settle(false);
  }} else if(state.qNum>=state.total){{
    state.quizEnd=Date.now(); showResults();
  }} else {{
    state.qNum++;
    if(!startQuestion()){{
      state.qNum--;
      alert('本次启动下单选题已全部抽完，请重新启动程序。');
    }}
    updateMetrics();
  }}
}}

function settle(timeout){{
  if(state.answered) return;
  var q=curQuestion();
  if(!q) return;
  var sec=0;
  if(isTimedMode()){{
    sec=timeout?QUESTION_TIME_LIMIT:Math.min(QUESTION_TIME_LIMIT,Math.max(1,Math.floor((Date.now()-state.questionStart)/1000)));
    state.questionSeconds.push(sec);
  }}
  state.settledSeconds=sec;
  state.answered=true;
  state.timedOut=timeout;
  state.flashVisible=false;

  if(state.mode==='fill'){{
    state.lastCorrect=false;
    renderQuestion();
    return;
  }}
  var ok=!timeout && arrEq(selAns(),q.answers);
  state.lastCorrect=ok;
  if(timeout) playSound('timeout');
  else if(ok){{state.correct++;playSound('correct');}}
  else{{state.wrong++;playSound('wrong');}}
  renderQuestion();
}}

function playSound(type){{
  try{{
    var map={{correct:'sound_correct.wav',wrong:'sound_wrong.wav',timeout:'sound_timeout.wav'}};
    if(map[type]){{var a=new Audio(map[type]);a.play().catch(function(){{}});}}
  }}catch(e){{}}
}}

// ===== Results =====
function showResults(){{
  stopTimer();
  var fm=state.mode==='fill';
  var sc=fm?state.selectedScore:Math.round((state.correct/state.total)*100);
  showPage('result');

  var se=document.getElementById('result_score');
  se.textContent=sc+(fm?'分':'');
  se.className='result-score '+(fm?'fill':(sc>=80?'good':sc>=60?'mid':'bad'));
  document.getElementById('result_label').textContent=fm?'选 择 分 值':'总 分';
  document.getElementById('quiz_mode_label').textContent='';

  var sh='';
  if(fm){{
    sh+='<div class="stat-row"><span class="stat-key">答题模式</span><span class="stat-val">'+modeName()+'</span></div>';
  }} else {{
    sh+='<div class="stat-row"><span class="stat-key">答题模式</span><span class="stat-val">'+modeName()+'</span></div>';
    sh+='<div class="stat-row"><span class="stat-key">答对 / 总题数</span><span class="stat-val">'+state.correct+' / '+state.total+'</span></div>';
    if(tracksTotalTime()){{
      var ts=Math.floor((state.quizEnd-state.quizStart)/1000);
      sh+='<div class="stat-row"><span class="stat-key">全程用时</span><span class="stat-val">'+fmtDur(ts)+'</span></div>';
    }}
    var et=new Date(state.quizEnd);
    var pad=function(n){{return n<10?'0'+n:''+n;}};
    var ts2=et.getFullYear()+'-'+pad(et.getMonth()+1)+'-'+pad(et.getDate())+' '+pad(et.getHours())+':'+pad(et.getMinutes())+':'+pad(et.getSeconds());
    sh+='<div class="stat-row"><span class="stat-key">答题结束</span><span class="stat-val">'+ts2+'</span></div>';
  }}
  document.getElementById('result_stats').innerHTML=sh;

  var rb=document.getElementById('restart_btn');
  if(fm){{rb.className='action-btn fill-restart';rb.onclick=function(){{openScoreSelect();}};}}
  else{{rb.className='action-btn restart';rb.onclick=restartQuiz;}}
}}

// ===== Score Select =====
function buildScores(){{
  var h='';
  for(var i=0;i<SCORE_OPTIONS.length;i++){{
    h+='<div class="score-item" onclick="selScore('+SCORE_OPTIONS[i]+','+i+')">';
    h+='<div class="score-value">'+SCORE_OPTIONS[i]+'</div>';
    h+='<div class="score-label">分</div>';
    h+='</div>';
  }}
  return h;
}}
function selScore(score,idx){{
  state.mode='fill';state.fillBucket=idx;state.selectedScore=score;
  state.bank=getBank('fill');resetQuiz(state.bank);state.total=2;
  state.quizStart=Date.now();showPage('quiz');updateMetrics();state.qNum=1;startQuestion();
}}

// ===== Font Size =====
function fontUp(){{ adjust(0.06); }}
function fontDown(){{ adjust(-0.06); }}
function adjust(d){{
  fontScale+=d;
  if(fontScale<1)fontScale=1;
  if(fontScale>3)fontScale=3;
  document.body.style.transform='scale('+fontScale+')';
  document.body.style.transformOrigin='top center';
  document.body.style.height=(100/fontScale)+'%';
  document.body.style.width=(100/fontScale)+'%';
}}

// ===== Init =====
document.addEventListener('DOMContentLoaded', function(){{
  document.getElementById('score_grid').innerHTML=buildScores();
  addReturnBtn();
  startTimer();
}});
</script>
</body>
</html>
"""

    # Insert embedded data into JS template
    js = js.replace('{singles}', singles_s)
    js = js.replace('{multis}', multis_s)
    js = js.replace('{fills}', fills_s)

    # Fix the typo: "final" -> "finalQ" in renderQuestion
    js = js.replace("if(final){{", "if(finalQ){{")

    html_parts.append(js)
    html_parts.append('')

    return ''.join(html_parts)


if __name__ == '__main__':
    output = gen()
    with open('quiz.html', 'w', encoding='utf-8') as f:
        f.write(output)
    print('Generated quiz.html (' + str(len(output)) + ' bytes)')
