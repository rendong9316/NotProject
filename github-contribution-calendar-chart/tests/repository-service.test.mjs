import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { mkdtemp, mkdir, readdir, rm, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import process from 'node:process';
import test from 'node:test';
import { setTimeout as delay } from 'node:timers/promises';
import { promisify } from 'node:util';
import { JsonCacheStore } from '../server/cache-store.mjs';
import { RepositoryService } from '../server/repository-service.mjs';

const execFileAsync = promisify(execFile);

async function git(repositoryPath, args, options = {}) {
  return execFileAsync('git', ['-C', repositoryPath, ...args], {
    windowsHide: true,
    ...options
  });
}

async function commit(repositoryPath, fileName, content, date) {
  await writeFile(path.join(repositoryPath, fileName), content, 'utf8');
  await git(repositoryPath, ['add', fileName]);
  await git(repositoryPath, ['commit', '-m', content], {
    env: { ...process.env, GIT_AUTHOR_DATE: date, GIT_COMMITTER_DATE: date }
  });
}

async function createFixture() {
  const root = await mkdtemp(path.join(os.tmpdir(), 'local-contributions-'));
  const repositoryPath = path.join(root, 'project');
  await mkdir(repositoryPath);
  await git(repositoryPath, ['init', '--quiet']);
  await git(repositoryPath, ['config', 'user.name', 'Test User']);
  await git(repositoryPath, ['config', 'user.email', 'test@example.com']);
  await commit(repositoryPath, 'first.txt', 'first', '2025-01-02T12:00:00+08:00');
  const config = {
    scanAllDrives: false,
    scanRoots: [root],
    cacheDirectory: 'cache',
    includeAllAuthors: true,
    authors: [],
    maxScanDepth: 3,
    scanConcurrency: 2,
    gitConcurrency: 2
  };
  return { root, repositoryPath, config };
}

test('persists contributions and incrementally refreshes changed repositories', async t => {
  const fixture = await createFixture();
  t.after(() => rm(fixture.root, { recursive: true, force: true }));
  const service = new RepositoryService({ config: fixture.config, projectRoot: fixture.root });

  const initial = await service.listRepositories();
  assert.equal(initial.repositories.length, 1);
  assert.ok(initial.lastDiscoveryAt);
  assert.equal((await service.readContributions([initial.repositories[0].id], 2025)).total, 1);

  const restarted = new RepositoryService({
    config: fixture.config,
    projectRoot: fixture.root,
    dependencies: {
      resolveScanRoots: async () => { throw new Error('restart must not scan drives'); },
      findGitRepositories: async () => { throw new Error('restart must not scan directories'); }
    }
  });
  assert.equal((await restarted.listRepositories()).repositories.length, 1);

  const unchanged = await restarted.refreshKnownRepositories();
  assert.equal(unchanged.operation.unchanged, 1);
  assert.equal(unchanged.operation.updated, 0);

  await commit(fixture.repositoryPath, 'second.txt', 'second', '2025-02-03T12:00:00+08:00');
  const updated = await restarted.refreshKnownRepositories();
  assert.equal(updated.operation.updated, 1);
  assert.equal((await restarted.readContributions([initial.repositories[0].id], 2025)).total, 2);

  await git(fixture.repositoryPath, ['reset', '--hard', 'HEAD~1']);
  const rewritten = await restarted.refreshKnownRepositories();
  assert.equal(rewritten.operation.updated, 1);
  assert.equal((await restarted.readContributions([initial.repositories[0].id], 2025)).total, 1);
});

test('shares an in-progress refresh between concurrent requests', async t => {
  const fixture = await createFixture();
  t.after(() => rm(fixture.root, { recursive: true, force: true }));
  const service = new RepositoryService({ config: fixture.config, projectRoot: fixture.root });
  await service.listRepositories();
  const originalReadFingerprint = service.readFingerprint;
  let fingerprintChecks = 0;
  service.readFingerprint = async repositoryPath => {
    fingerprintChecks += 1;
    await delay(30);
    return originalReadFingerprint(repositoryPath);
  };

  const [first, second] = await Promise.all([
    service.refreshKnownRepositories(),
    service.refreshKnownRepositories()
  ]);

  assert.equal(fingerprintChecks, 1);
  assert.deepEqual(first.operation, second.operation);
});

test('isolates malformed JSON cache files', async t => {
  const root = await mkdtemp(path.join(os.tmpdir(), 'local-contributions-cache-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  const store = new JsonCacheStore(root);
  await mkdir(root, { recursive: true });
  await writeFile(path.join(root, 'index.json'), '{broken', 'utf8');

  assert.equal(await store.loadIndex(), null);
  const files = await readdir(root);
  assert.ok(files.some(file => file.startsWith('index.json.corrupt-')));
});
