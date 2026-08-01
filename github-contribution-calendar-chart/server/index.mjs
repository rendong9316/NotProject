import { createReadStream } from 'node:fs';
import { access, readFile, stat } from 'node:fs/promises';
import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { RepositoryService } from './repository-service.mjs';

const PROJECT_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const DIST_ROOT = path.join(PROJECT_ROOT, 'dist');
const config = JSON.parse(await readFile(path.join(PROJECT_ROOT, 'config.json'), 'utf8'));
const API_ONLY = process.argv.includes('--api-only');
const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8', '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml', '.png': 'image/png', '.ico': 'image/x-icon'
};

const repositoryService = new RepositoryService({ config, projectRoot: PROJECT_ROOT });

function sendJson(response, status, payload) {
  response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
  response.end(JSON.stringify(payload));
}

function mapApiError(message) {
  if (message.includes('EACCES') || message.includes('permission')) return '磁盘权限不足，请检查扫描目录的访问权限';
  if (message.includes('git') || message.includes('git log')) return 'Git 命令执行失败，请确认 Git 已正确安装';
  if (message.includes('ENOENT') || message.includes('No such file')) return '部分仓库路径已不存在，请在刷新后重新扫描';
  if (message.includes('maxBuffer')) return '仓库数据量过大，缓存超出限制，建议缩小扫描范围';
  return message;
}

async function handleApi(request, response, url) {
  if (url.pathname === '/api/health') {
    const snapshot = await repositoryService.listRepositories({ initialize: false });
    sendJson(response, 200, { ok: true, ...snapshot });
    return true;
  }
  if (url.pathname === '/api/repos') {
    sendJson(response, 200, await repositoryService.listRepositories());
    return true;
  }
  if (url.pathname === '/api/refresh' && request.method === 'POST') {
    sendJson(response, 200, await repositoryService.refreshKnownRepositories());
    return true;
  }
  if (url.pathname === '/api/discover' && request.method === 'POST') {
    sendJson(response, 200, await repositoryService.discoverRepositories());
    return true;
  }
  if (url.pathname === '/api/contributions') {
    const year = Number(url.searchParams.get('year')) || new Date().getFullYear();
    const requested = new Set((url.searchParams.get('repos') || '').split(',').filter(Boolean));
    sendJson(response, 200, await repositoryService.readContributions(requested, year));
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
    sendJson(response, 500, { error: mapApiError(error.message) });
  }
});

server.listen(config.port, '127.0.0.1', () => {
  console.log(`Local Contributions: http://localhost:${config.port}`);
});
