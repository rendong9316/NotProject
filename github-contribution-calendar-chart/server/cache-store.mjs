import { mkdir, readFile, rename, writeFile } from 'node:fs/promises';
import path from 'node:path';

export const CACHE_VERSION = 1;

export function createEmptyIndex() {
  return {
    version: CACHE_VERSION,
    scanRoots: [],
    lastDiscoveryAt: null,
    lastRefreshAt: null,
    repositories: []
  };
}

async function readJson(filePath) {
  try {
    return JSON.parse(await readFile(filePath, 'utf8'));
  } catch (error) {
    if (error.code === 'ENOENT') return null;
    if (error instanceof SyntaxError) {
      const corruptPath = `${filePath}.corrupt-${Date.now()}`;
      await rename(filePath, corruptPath).catch(() => {});
      return null;
    }
    throw error;
  }
}

export class JsonCacheStore {
  constructor(cacheDirectory) {
    this.cacheDirectory = cacheDirectory;
    this.indexPath = path.join(cacheDirectory, 'index.json');
    this.repositoriesDirectory = path.join(cacheDirectory, 'repositories');
  }

  async loadIndex() {
    const index = await readJson(this.indexPath);
    return index?.version === CACHE_VERSION ? index : null;
  }

  async saveIndex(index) {
    await this.#writeJson(this.indexPath, index);
  }

  async loadRepository(repositoryId) {
    const data = await readJson(this.#repositoryPath(repositoryId));
    return data?.version === CACHE_VERSION ? data : null;
  }

  async saveRepository(repositoryId, data) {
    await this.#writeJson(this.#repositoryPath(repositoryId), data);
  }

  async hasRepository(repositoryId) {
    return Boolean(await this.loadRepository(repositoryId));
  }

  #repositoryPath(repositoryId) {
    return path.join(this.repositoriesDirectory, `${repositoryId}.json`);
  }

  async #writeJson(filePath, value) {
    await mkdir(path.dirname(filePath), { recursive: true });
    const temporaryPath = `${filePath}.${process.pid}.${Date.now()}.tmp`;
    await writeFile(temporaryPath, `${JSON.stringify(value, null, 2)}\n`, 'utf8');
    await rename(temporaryPath, filePath);
  }
}
