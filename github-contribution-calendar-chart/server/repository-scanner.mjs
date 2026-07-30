import { readdir } from 'node:fs/promises';
import path from 'node:path';

const IGNORED_DIRS = new Set([
  '$recycle.bin', 'system volume information', 'node_modules', '.next', '.nuxt', '.cache',
  'dist', 'build', 'coverage', 'vendor', 'extern', 'third_party', '_deps', '.ezvcpkg',
  '.venv', 'venv', 'windowsapps'
]);

export async function findGitRepositories(
  roots,
  { maxDepth = 10, concurrency = 12, readDirectory = readdir } = {}
) {
  const found = new Set();
  const pending = roots.map(directory => ({ directory, depth: 0 }));

  while (pending.length) {
    const batch = pending.splice(-concurrency);
    const results = await Promise.all(batch.map(async item => {
      try {
        const entries = await readDirectory(item.directory, { withFileTypes: true });
        if (entries.some(entry => entry.name.toLowerCase() === '.git')) {
          found.add(item.directory);
          return [];
        }
        if (item.depth >= maxDepth) return [];
        return entries
          .filter(entry => entry.isDirectory() && !entry.isSymbolicLink() && !IGNORED_DIRS.has(entry.name.toLowerCase()))
          .map(entry => ({ directory: path.join(item.directory, entry.name), depth: item.depth + 1 }));
      } catch {
        return [];
      }
    }));
    for (const items of results) pending.push(...items);
  }

  return [...found].sort((left, right) => left.localeCompare(right, 'zh-CN'));
}
