import { execFile, spawn } from 'node:child_process';
import { createHash } from 'node:crypto';
import readline from 'node:readline';
import { promisify } from 'node:util';

const execFileAsync = promisify(execFile);

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

export function repositoryId(repositoryPath) {
  return Buffer.from(repositoryPath.toLowerCase()).toString('base64url');
}

export function contributionFilterSignature(config) {
  const authors = [...(config.authors || [])].map(author => author.toLowerCase()).sort();
  return sha256(JSON.stringify({ authors, includeAllAuthors: Boolean(config.includeAllAuthors) }));
}

export async function readRefsFingerprint(repositoryPath) {
  const { stdout } = await execFileAsync('git', [
    '-C', repositoryPath, 'for-each-ref', '--sort=refname',
    '--format=%(refname)%00%(objectname)%00%(*objectname)'
  ], { windowsHide: true, maxBuffer: 4 * 1024 * 1024 });

  return sha256(stdout);
}

export async function collectContributionDays(repositoryPath, config) {
  const authors = new Set((config.authors || []).map(author => author.toLowerCase()));
  const includeAllAuthors = Boolean(config.includeAllAuthors) || !authors.size;
  const days = new Map();
  const child = spawn('git', [
    '-C', repositoryPath, 'log', '--all', '--date=format:%Y-%m-%d',
    '--pretty=format:%ad%x09%ae%x09%an'
  ], { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
  let stderr = '';

  child.stderr.setEncoding('utf8');
  child.stderr.on('data', chunk => {
    if (stderr.length < 4096) stderr += chunk;
  });

  const completion = new Promise((resolve, reject) => {
    child.once('error', reject);
    child.once('close', code => {
      if (code === 0) resolve();
      else reject(new Error(stderr.trim() || `git log exited with code ${code}`));
    });
  });

  const lines = readline.createInterface({ input: child.stdout, crlfDelay: Infinity });
  for await (const line of lines) {
    if (!line) continue;
    const [date, email = '', name = ''] = line.split('\t');
    const included = includeAllAuthors || authors.has(email.toLowerCase()) || authors.has(name.toLowerCase());
    if (included) days.set(date, (days.get(date) || 0) + 1);
  }
  await completion;

  return Object.fromEntries([...days].sort(([left], [right]) => left.localeCompare(right)));
}
