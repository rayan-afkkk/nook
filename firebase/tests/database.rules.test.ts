import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { assertFails, initializeTestEnvironment, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { ref, set, get } from 'firebase/database';
import { afterAll, beforeAll, describe, it } from 'vitest';

let env: RulesTestEnvironment;

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-nook',
    database: { rules: readFileSync(resolve(__dirname, '../database.rules.json'), 'utf8') },
  });
});

afterAll(async () => {
  await env.cleanup();
});

describe('realtime database (phase 1: locked down)', () => {
  it('denies all reads and writes until presence ships', async () => {
    const db = env.authenticatedContext('alice').database();
    await assertFails(set(ref(db, 'presence/alice'), { online: true }));
    await assertFails(get(ref(db, 'presence/alice')));
  });
});
