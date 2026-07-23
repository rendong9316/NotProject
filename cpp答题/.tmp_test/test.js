const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();
  
  // Test file URL - need to handle Chinese chars in path
  const filePath = 'D:/Desktop/NotProjext/cpp答题/web/quiz.html';
  
  console.log('Navigating to:', filePath);
  await page.goto('file://' + filePath);
  await page.waitForLoadState('networkidle');
  await new Promise(r => setTimeout(r, 3000));
  
  const title = await page.title();
  console.log('Browser title:', title);
  
  const showPageType = await page.evaluate('() => typeof showPage');
  console.log('showPage type:', showPageType);
  
  const pages = await page.evaluate(() => 
    Array.from(document.querySelectorAll('.page')).map(p => ({
      id: p.id, display: getComputedStyle(p).display, hasOn: p.classList.contains('on')
    }))
  );
  console.log('Pages:', JSON.stringify(pages));
  
  // Check DOM directly from HTML
  const bodyHTML = await page.evaluate(() => document.body.innerHTML.substring(0, 400));
  console.log('Body HTML preview:', bodyHTML.substring(0, 200));
  
  await browser.close();
})();
