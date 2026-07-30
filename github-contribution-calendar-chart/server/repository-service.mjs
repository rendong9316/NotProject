import path from 'node:path';
import { createEmptyIndex, JsonCacheStore, CACHE_VERSION } from './cache-store.mjs';
import {
  collectContributionDays,
  contributionFilterSignature,
  readRefsFingerprint,
  repositoryId
} from './git-repository.mjs';
import { findGitRepositories } from './repository-scanner.mjs';
import { resolveScanRoots } from './scan-roots.mjs';

async function mapLimit(items, limit, mapper) {
  const results = new Array(items.length);
  let nextIndex = 0;
  const workers = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (nextIndex < items.length) {
      const index = nextIndex++;
      results[index] = await mapper(items[index], index);
    }
  });
  await Promise.all(workers);
  return results;
}

function publicRepository(repository) {
  return {
    id: repository.id,
    name: repository.name,
    path: repository.path,
    available: repository.available,
    updatedAt: repository.updatedAt,
    error: repository.error || null
  };
}

export class RepositoryService {
  constructor({ config, projectRoot, store, dependencies = {} }) {
    this.config = config;
    this.store = store || new JsonCacheStore(path.resolve(projectRoot, config.cacheDirectory || 'data'));
    this.resolveRoots = dependencies.resolveScanRoots || resolveScanRoots;
    this.findRepositories = dependencies.findGitRepositories || findGitRepositories;
    this.readFingerprint = dependencies.readRefsFingerprint || readRefsFingerprint;
    this.collectDays = dependencies.collectContributionDays || collectContributionDays;
    this.indexPromise = null;
    this.operation = null;
  }

  async listRepositories({ initialize = true } = {}) {
    const index = await this.#loadIndex();
    if (initialize && !index.lastDiscoveryAt) await this.discoverRepositories();
    return this.#snapshot(await this.#loadIndex());
  }

  async refreshKnownRepositories() {
    const index = await this.#loadIndex();
    if (!index.lastDiscoveryAt) return this.discoverRepositories();
    return this.#runOperation('refresh', async () => {
      const startedAt = Date.now();
      const updates = await mapLimit(index.repositories, this.config.gitConcurrency || 6, repository => (
        this.#updateRepository(repository.path, repository)
      ));
      const repositories = updates
        .filter(result => result.status !== 'unavailable' || result.repository.updatedAt)
        .map(result => result.repository);
      const nextIndex = {
        ...index,
        lastRefreshAt: new Date().toISOString(),
        repositories
      };
      await this.#saveIndex(nextIndex);
      return {
        ...this.#snapshot(nextIndex),
        operation: this.#operationResult('refresh', updates, startedAt)
      };
    });
  }

  async discoverRepositories() {
    return this.#runOperation('discover', async () => {
      const startedAt = Date.now();
      const index = await this.#loadIndex();
      const scanRoots = await this.resolveRoots(this.config);
      const paths = await this.findRepositories(scanRoots, {
        maxDepth: this.config.maxScanDepth,
        concurrency: this.config.scanConcurrency || 12
      });
      const existingById = new Map(index.repositories.map(repository => [repository.id, repository]));
      const updates = await mapLimit(paths, this.config.gitConcurrency || 6, repositoryPath => {
        const id = repositoryId(repositoryPath);
        return this.#updateRepository(repositoryPath, existingById.get(id));
      });
      const discoveredIds = new Set(updates.map(result => result.repository.id));
      const missing = index.repositories
        .filter(repository => !discoveredIds.has(repository.id))
        .filter(repository => repository.updatedAt)
        .map(repository => ({
          repository: { ...repository, available: false, error: '仓库路径当前不可用' },
          status: 'unavailable'
        }));
      const completedAt = new Date().toISOString();
      const nextIndex = {
        version: CACHE_VERSION,
        scanRoots,
        lastDiscoveryAt: completedAt,
        lastRefreshAt: completedAt,
        repositories: [...updates, ...missing]
          .map(result => result.repository)
          .sort((left, right) => left.path.localeCompare(right.path, 'zh-CN'))
      };
      await this.#saveIndex(nextIndex);
      return {
        ...this.#snapshot(nextIndex),
        operation: this.#operationResult('discover', [...updates, ...missing], startedAt)
      };
    });
  }

  async readContributions(repositoryIds, year) {
    const index = await this.#loadIndex();
    const requested = new Set(repositoryIds);
    const repositories = index.repositories.filter(repository => requested.has(repository.id));
    const cachedRepositories = await mapLimit(repositories, 12, async repository => ({
      repository,
      data: await this.store.loadRepository(repository.id)
    }));
    const dayCounts = new Map();
    const prefix = `${year}-`;
    const repoStats = [];

    for (const { repository, data } of cachedRepositories) {
      let count = 0;
      for (const [date, value] of Object.entries(data?.days || {})) {
        if (!date.startsWith(prefix)) continue;
        dayCounts.set(date, (dayCounts.get(date) || 0) + value);
        count += value;
      }
      if (count) repoStats.push({ id: repository.id, name: repository.name, count });
    }

    const days = [...dayCounts]
      .map(([date, count]) => ({ date, count }))
      .sort((left, right) => left.date.localeCompare(right.date));
    repoStats.sort((left, right) => right.count - left.count);
    return { year, days, total: days.reduce((sum, day) => sum + day.count, 0), repoStats };
  }

  getActiveOperation() {
    return this.operation?.kind || null;
  }

  async #loadIndex() {
    if (!this.indexPromise) {
      this.indexPromise = this.store.loadIndex().then(index => index || createEmptyIndex());
    }
    return this.indexPromise;
  }

  async #saveIndex(index) {
    await this.store.saveIndex(index);
    this.indexPromise = Promise.resolve(index);
  }

  async #updateRepository(repositoryPath, existing) {
    const id = repositoryId(repositoryPath);
    const now = new Date().toISOString();
    const filterSignature = contributionFilterSignature(this.config);
    try {
      const fingerprint = await this.readFingerprint(repositoryPath);
      const cacheExists = existing ? await this.store.hasRepository(id) : false;
      if (existing?.fingerprint === fingerprint && existing.filterSignature === filterSignature && cacheExists) {
        return {
          repository: { ...existing, available: true, error: null, checkedAt: now },
          status: 'unchanged'
        };
      }

      const days = await this.collectDays(repositoryPath, this.config);
      await this.store.saveRepository(id, {
        version: CACHE_VERSION,
        id,
        path: repositoryPath,
        updatedAt: now,
        days
      });
      return {
        repository: {
          id,
          name: path.basename(repositoryPath),
          path: repositoryPath,
          fingerprint,
          filterSignature,
          available: true,
          checkedAt: now,
          updatedAt: now,
          error: null
        },
        status: existing ? 'updated' : 'added'
      };
    } catch (error) {
      return {
        repository: existing
          ? { ...existing, available: false, checkedAt: now, error: error.message }
          : {
              id,
              name: path.basename(repositoryPath),
              path: repositoryPath,
              fingerprint: null,
              filterSignature,
              available: false,
              checkedAt: now,
              updatedAt: null,
              error: error.message
            },
        status: 'unavailable'
      };
    }
  }

  #runOperation(kind, work) {
    if (this.operation) return this.operation.promise;
    const promise = work().finally(() => {
      if (this.operation?.promise === promise) this.operation = null;
    });
    this.operation = { kind, promise };
    return promise;
  }

  #snapshot(index) {
    return {
      repositories: index.repositories.map(publicRepository),
      scanRoots: index.scanRoots,
      lastDiscoveryAt: index.lastDiscoveryAt,
      lastRefreshAt: index.lastRefreshAt,
      activeOperation: this.getActiveOperation()
    };
  }

  #operationResult(type, results, startedAt) {
    const count = status => results.filter(result => result.status === status).length;
    return {
      type,
      checked: results.length,
      added: count('added'),
      updated: count('updated'),
      unchanged: count('unchanged'),
      unavailable: count('unavailable'),
      durationMs: Date.now() - startedAt
    };
  }
}
