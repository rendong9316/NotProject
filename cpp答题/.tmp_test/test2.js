const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();
  
  // Capture JS errors and console messages
  page.on('console', msg => console.log(`[${msg.type()}] ${msg.text()}`));
  page.on('pageerror', err => console.log(`[PAGEERROR] ${err.message} - ${err.stack}`));
  
  await page.goto('file:///D:/Desktop/NotProjext/cpp%E7%AD%94%E9%A2%98/web/quiz.html');
  await page.waitForLoadState('networkidle');
  await new Promise(r => setTimeout(r, 3000));
  
  // Check the script tag details from browser perspective
  const scriptInfo = await page.evaluate(() => {
    const s = document.querySelector('script');
    return {
      exists: !!s,
      innerHTMLLength: s ? s.innerHTML.length : 0,
      textContentLength: s ? s.textContent.length : 0,
      tagName: s ? s.tagName : null,
      parentTag: s ? (s.parentElement ? s.parentElement.tagName : null) : null,
      first50Chars: s ? s.textContent.substring(0, 50).replace(/\n/g, '\n').replace(/\r/g, '\r') : 'none',
      last50Chars: s ? s.textContent.substring(s.textContent.length - 50).replace(/\n/g, '\n').replace(/\r/g, '\r') : 'none',
      hasDOMContentLoadedListener: false, // can't check directly, but we can test
    };
  });
  console.log('\nScript info:', JSON.stringify(scriptInfo));
  
  // Test: manually define showPage and call it
  // First let's see if ANY script variable is defined
  const globals = await page.evaluate(() => {
    // List some known global variables that should be defined by the script
    return {
      SINGLE_QUESTIONS: typeof SINGLE_QUESTIONS,
      state: typeof state,
      showPage: typeof showPage,
      startQuiz: typeof startQuiz,
      fontUp: typeof fontUp,
      buildScores: typeof buildScores,
      document_readyState: document.readyState,
      hasWindowOnLoad: typeof window.onload,
    };
  });
  console.log('\nGlobal vars:', JSON.stringify(globals));
  
  // Check if the HTML body has the script element
  const bodyCheck = await page.evaluate(() => {
    const scripts = document.querySelectorAll('script');
    return {
      totalScripts: scripts.length,
      allSrcs: Array.from(scripts).map(s => s.src || '(inline)'),
    };
  });
  console.log('\nScripts:', JSON.stringify(bodyCheck));
  
  browser.close();
})();
