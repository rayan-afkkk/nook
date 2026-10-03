import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { assertFails, assertSucceeds, initializeTestEnvironment, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { get, ref, remove, serverTimestamp, set } from 'firebase/database';
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

const db = (uid: string | null) => (uid ? env.authenticatedContext(uid) : env.unauthenticatedContext()).database();

describe('presence', () => {
  it('only I can set my status; signed-in people can read it', async () => {
    await assertSucceeds(set(ref(db('alice'), 'status/alice'), { state: 'online', lastChanged: serverTimestamp() }));
    await assertFails(set(ref(db('bob'), 'status/alice'), { state: 'offline', lastChanged: serverTimestamp() }));
    await assertSucceeds(get(ref(db('bob'), 'status/alice')));
    await assertFails(get(ref(db(null), 'status/alice')));
  });

  it('rejects junk', async () => {
    await assertFails(set(ref(db('alice'), 'status/alice'), { state: 'hacking', lastChanged: 1 }));
    await assertFails(set(ref(db('alice'), 'status/alice'), { state: 'online', lastChanged: 1, extra: true }));
  });
});

describe('typing', () => {
  it('only my own typing flag, as a timestamp', async () => {
    await assertSucceeds(set(ref(db('alice'), 'typing/c1/alice'), serverTimestamp()));
    await assertSucceeds(remove(ref(db('alice'), 'typing/c1/alice')));
    await assertFails(set(ref(db('alice'), 'typing/c1/bob'), serverTimestamp()));
    await assertFails(set(ref(db('alice'), 'typing/c1/alice'), 'some text'));
  });

  it('everything else is closed', async () => {
    await assertFails(set(ref(db('alice'), 'anything'), 1));
    await assertFails(get(ref(db('alice'), '/')));
  });
});
