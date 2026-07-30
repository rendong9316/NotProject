import { access } from 'node:fs/promises';
import path from 'node:path';

const WINDOWS_DRIVE_ROOTS = Array.from(
  { length: 26 },
  (_, index) => `${String.fromCharCode(65 + index)}:\\`
);

function uniqueResolvedRoots(roots) {
  return [...new Set(roots.map(root => path.resolve(root)))];
}

export async function discoverWindowsDriveRoots(canAccess = access) {
  const checks = await Promise.all(WINDOWS_DRIVE_ROOTS.map(async root => {
    try {
      await canAccess(root);
      return root;
    } catch {
      return null;
    }
  }));

  return checks.filter(Boolean);
}

export async function resolveScanRoots(
  config,
  { platform = process.platform, canAccess = access, currentDirectory = process.cwd() } = {}
) {
  const configuredRoots = Array.isArray(config.scanRoots) ? config.scanRoots : [];
  if (!config.scanAllDrives) return uniqueResolvedRoots(configuredRoots);

  const discoveredRoots = platform === 'win32'
    ? await discoverWindowsDriveRoots(canAccess)
    : [path.parse(currentDirectory).root];

  return uniqueResolvedRoots([...discoveredRoots, ...configuredRoots]);
}
