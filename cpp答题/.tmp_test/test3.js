const { chromium } = require('playwright');
const fs = require('fs');

(async () => {
  // First let's use node to find exactly where the syntax error is
  const raw = fs.readFileSync('web/quiz.html');
  const jsStart = raw.indexOf('<script>') + 8;
  const jsEnd = raw.indexOf('</script>', jsStart);
  const jsCode = raw.subarray(jsStart, jsEnd).toString('utf8');
  
  console.log('JS code length:', jsCode.length);
  console.log('First 200:', JSON.stringify(jsCode.substring(0, 200)));
  console.log('Last 200:', JSON.stringify(jsCode.substring(jsCode.length - 200)));
  
  // Try to find the syntax error using different approaches
  // Check for unusual characters
  const badChars = [];
  for (let i = 0; i < jsCode.length; i++) {
    const c = jsCode.charCodeAt(i);
    // Check for control characters or zero-width chars
    if ((c >= 0x00 && c <= 0x08) || (c >= 0x0B && c <= 0x0C) || 
        (c >= 0x0E && c <= 0x1F) || c === 0x7F ||
        c === 0x200B || c === 0x200C || c === 0x200D || c === 0xFEFF ||
        c === 0x2060 || c === 0x00AD) {
      badChars.push({ pos: i, char: c, hex: '0x' + c.toString(16) });
    }
  }
  console.log('\nSuspicious chars found:', badChars.length);
  for (const bc of badChars.slice(0, 20)) {
    console.log(`  Pos ${bc.pos} (${bc.hex}): context="${JSON.stringify(jsCode.substring(Math.max(0,bc.pos-20), bc.pos+20))}"`);
  }
  
  // Now open in browser and check the actual script content the browser sees
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();
  
  page.on('console', msg => console.log(`[BROWSER ${msg.type()}] ${msg.text()}`));
  page.on('pageerror', err => console.log(`[PAGE ERROR] line=? ${err.message}`));
  
  await page.goto('file:///D:/Desktop/NotProjext/cpp%E7%AD%94%E9%A2%98/web/quiz.html');
  await page.waitForLoadState('networkidle');
  await new Promise(r => setTimeout(r, 2000));
  
  // In browser, get the textContent of the script tag
  const browserScriptText = await page.evaluate(() => {
    const s = document.querySelector('script');
    return {
      length: s.textContent.length,
      first100: s.textContent.substring(0, 100),
      last100: s.textContent.substring(s.textContent.length - 100),
      // Check for any weird characters
      hasBadChars: Array.from(s.textContent).some(c => c.charCodeAt(0) > 127 && c.charCodeAt(0) < 0x2000),
    };
  });
  console.log('\nBrowser script text info:', browserScriptText);
  
  // The real test: does the file have \r\n or just \n? 
  // Windows files typically have \r\n. JS shouldn't care but let's check
  const crCount = (raw.toString('utf8').match(/\r/g) || []).length;
  const lfCount = (raw.toString('utf8').match(/\n/g) || []).length;
  console.log(`\nCR count: ${crCount}, LF count: ${lfCount}, CR/LF ratio: ${crCount / lfCount.toFixed(2)}`);
  
  browser.close();
})();
