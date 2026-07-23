const fs = require('fs');

const raw = fs.readFileSync('web/quiz.html');
const jsStart = raw.indexOf('<script>') + 8;
const jsEnd = raw.indexOf('</script>', jsStart);
const jsCode = raw.subarray(jsStart, jsEnd).toString('utf8');

// Write to temp file and test syntax
fs.writeFileSync('web/_test_syntax.js', jsCode, 'utf8');

const { execSync } = require('child_process');
try {
  const result = execSync(r'"C:\Program Files\nodejs\node.exe" --check web/_test_syntax.js', { encoding: 'utf8', stderr: 'pipe' });
  console.log('Syntax OK!');
} catch (e) {
  console.log('Syntax error:', e.stderr || e.stdout || e.message);
}

// Now try to find the EXACT position of the error by testing chunks
console.log('\n=== Chunk test ===');
for (let split = [50000, 80000, 100000, 120000, 130000, 140000, 145000, 148000, 150000].join(',').split(',').map(Number); ) {
  // We'll do this differently
}

// Let's do binary search for the error location
let lo = 100, hi = jsCode.length;
while (hi - lo > 10) {
  const mid = Math.floor((lo + hi) / 2);
  const chunk = jsCode.substring(0, mid);
  fs.writeFileSync('web/_test_syntax.js', chunk, 'utf8');
  try {
    execSync(r'"C:\Program Files\nodejs\node.exe" web/_test_syntax.js', { encoding: 'utf8', stderr: 'pipe', stdio: ['pipe', 'pipe', 'pipe'] });
    lo = mid;  // no error, problem is later
  } catch (e) {
    hi = mid;  // error in first part
  }
}

console.log(`Error is around position ${lo} - ${hi}`);
console.log(`Context around error:`);
console.log(JSON.stringify(jsCode.substring(Math.max(0, lo - 50), lo + 50)));

fs.unlinkSync('web/_test_syntax.js');
