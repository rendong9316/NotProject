import assert from 'node:assert/strict';
import path from 'node:path';
import test from 'node:test';
import { discoverWindowsDriveRoots, resolveScanRoots } from '../server/scan-roots.mjs';

test('discovers every accessible Windows drive letter', async () => {
  const checked = [];
  const roots = await discoverWindowsDriveRoots(async root => {
    checked.push(root);
    if (root !== 'C:\\' && root !== 'F:\\') throw new Error('Drive is unavailable');
  });

  assert.equal(checked.length, 26);
  assert.deepEqual(roots, ['C:\\', 'F:\\']);
});

test('uses configured roots when automatic discovery is disabled', async () => {
  const roots = await resolveScanRoots({ scanAllDrives: false, scanRoots: ['D:\\'] });

  assert.deepEqual(roots, [path.resolve('D:\\')]);
});
