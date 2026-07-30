import { execFile } from 'node:child_process';
import { createReadStream } from 'node:fs';
import { access, readFile, readdir, stat } from 'node:fs/promises';
import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';

const execFileAsync = promisify(execFile);
const PROJECT_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const DIST_ROOT = path.join(PROJECT_ROOT, 'dist');
const config = JSON.parse(await readFile(path.join(PROJECT_ROOT, 'config.json'), 'utf8'));
const API_ONLY = process.argv.includes('--api-only');
const IGNORED_DIRS = new Set([
  '$RECYCLE.BIN', 'System Volume Information', 'node_modules', '.next', '.nuxt', '.cache',
  'dist', 'build', 'coverage', 'vendor', 'extern', 'third_party', '_deps', '.ezvcpkg',
  '.venv', 'venv', 'WindowsApps'
]);
const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8', '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml', '.png': 'image/png', '.ico': 'image/x-icon'
};

let repositoryCache = null;

function repositoryId(repositoryPath) {
  return Buffer.from(repositoryPath.toLowerCase()).toString('base64url');
}

async function scanRepositories() {
  const found = new Set();
  let frontier = config.scanRoots.map(root => ({ directory: path.resolve(root), depth: 0 }));

  while (frontier.length) {
    const next = [];
    for (let offset = 0; offset < frontier.length; offset += 40) {
      const batch = frontier.slice(offset, offset + 40);
      const results = await Promise.all(batch.map(async item => {
        try {
          const entries = await readdir(item.directory, { withFileTypes: true });
          if (entries.some(entry => entry.name === '.git')) {
            found.add(item.directory);
            return [];
          }
          if (item.depth >= config.maxScanDepth) return [];
          return entries
            .filter(entry => entry.isDirectory() && !entry.isSymbolicLink() && !IGNORED_DIRS.has(entry.name))
            .map(entry => ({ directory: path.join(item.directory, entry.name), depth: item.depth + 1 }));
        } catch {
          return [];
        }
      }));
      results.forEach(items => next.push(...items));
    }
    frontier = next;
  }

  repositoryCache = [...found]
    .sort((left, right) => left.localeCompare(right, 'zh-CN'))
    .map(repositoryPath => ({ id: repositoryId(repositoryPath), name: path.basename(repositoryPath), path: repositoryPath }));
  return repositoryCache;
}

async function getRepositories(refresh = false) {
  if (!repositoryCache || refresh) return scanRepositories();
  return repositoryCache;
}

async function readCommits(repository, year) {
  const since = `${year}-01-01T00:00:00`;
  const until = `${year + 1}-01-01T00:00:00`;
  try {
    const { stdout } = await execFileAsync('git', [
      '-C', repository.path, 'log', '--all', `--since=${since}`, `--until=${until}`,
      '--date=format:%Y-%m-%d', '--pretty=format:%ad%x09%ae%x09%an'
    ], { windowsHide: true, maxBuffer: 20 * 1024 * 1024 });
    const authors = new Set((config.authors || []).map(author => author.toLowerCase()));
    return stdout.split(/\r?\n/).filter(Boolean).flatMap(line => {
      const [date, email = '', name = ''] = line.split('\t');
      const included = config.includeAllAuthors || !authors.size || authors.has(email.toLowerCase()) || authors.has(name.toLowerCase());
      return included ? [date] : [];
    });
  } catch {
    return [];
  }
}

function sendJson(response, status, payload) {
  response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
  response.end(JSON.stringify(payload));
}

async function handleApi(request, response, url) {
  if (url.pathname === '/api/health') {
    sendJson(response, 200, { ok: true, scanRoots: config.scanRoots });
    return true;
  }
  if (url.pathname === '/api/repos') {
    const repositories = await getRepositories(url.searchParams.get('refresh') === '1');
    sendJson(response, 200, { repositories, scanRoots: config.scanRoots });
    return true;
  }
  if (url.pathname === '/api/contributions') {
    const year = Number(url.searchParams.get('year')) || new Date().getFullYear();
    const requested = new Set((url.searchParams.get('repos') || '').split(',').filter(Boolean));
    const repositories = (await getRepositories()).filter(repo => requested.has(repo.id));
    const commitLists = await Promise.all(repositories.map(repo => readCommits(repo, year)));
    const dayCounts = new Map();
    const repoStats = repositories.map((repo, index) => {
      for (const date of commitLists[index]) dayCounts.set(date, (dayCounts.get(date) || 0) + 1);
      return { id: repo.id, name: repo.name, count: commitLists[index].length };
    }).filter(repo => repo.count > 0).sort((left, right) => right.count - left.count);
    const days = [...dayCounts].map(([date, count]) => ({ date, count })).sort((left, right) => left.date.localeCompare(right.date));
    sendJson(response, 200, { year, days, total: days.reduce((sum, day) => sum + day.count, 0), repoStats });
    return true;
  }
  return false;
}

async function serveStatic(response, pathname) {
  if (API_ONLY) {
    sendJson(response, 404, { error: 'API-only development server' });
    return;
  }
  const requested = pathname === '/' ? 'index.html' : decodeURIComponent(pathname.slice(1));
  let filePath = path.resolve(DIST_ROOT, requested);
  if (!filePath.startsWith(DIST_ROOT)) {
    response.writeHead(403).end('Forbidden');
    return;
  }
  try {
    if (!(await stat(filePath)).isFile()) throw new Error('Not a file');
  } catch {
    filePath = path.join(DIST_ROOT, 'index.html');
  }
  try {
    await access(filePath);
    response.writeHead(200, { 'Content-Type': MIME_TYPES[path.extname(filePath)] || 'application/octet-stream' });
    createReadStream(filePath).pipe(response);
  } catch {
    response.writeHead(503, { 'Content-Type': 'text/plain; charset=utf-8' });
    response.end('应用尚未构建，请先运行 npm run build。');
  }
}

const server = http.createServer(async (request, response) => {
  const url = new URL(request.url, `http://${request.headers.host || 'localhost'}`);
  try {
    if (url.pathname.startsWith('/api/') && await handleApi(request, response, url)) return;
    await serveStatic(response, url.pathname);
  } catch (error) {
    sendJson(response, 500, { error: error.message });
  }
});

server.listen(config.port, '0.0.0.0', () => {
  console.log(`Local Contributions: http://localhost:${config.port}`);
});
