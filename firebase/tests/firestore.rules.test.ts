import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  serverTimestamp,
  setDoc,
  updateDoc,
  writeBatch,
  type Firestore,
} from 'firebase/firestore';
import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';

let env: RulesTestEnvironment;

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-nook',
    firestore: { rules: readFileSync(resolve(__dirname, '../firestore.rules'), 'utf8') },
  });
});

afterAll(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
});

const db = (uid: string | null): Firestore =>
  (uid ? env.authenticatedContext(uid) : env.unauthenticatedContext()).firestore() as unknown as Firestore;

/** The same two writes the app makes in profileService.claimUsername. */
function claim(fs: Firestore, uid: string, username: string, overrides: Record<string, unknown> = {}) {
  const batch = writeBatch(fs);
  batch.set(doc(fs, 'usernames', username), { uid, createdAt: serverTimestamp() });
  batch.set(doc(fs, 'users', uid), {
    uid,
    username,
    displayName: 'Ali',
    photoURL: 'https://example.com/a.png',
    createdAt: serverTimestamp(),
    ...overrides,
  });
  return batch.commit();
}

async function seed(uid: string, username: string) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const fs = ctx.firestore();
    await setDoc(doc(fs as unknown as Firestore, 'usernames', username), { uid, createdAt: new Date() });
    await setDoc(doc(fs as unknown as Firestore, 'users', uid), {
      uid,
      username,
      displayName: username,
      photoURL: null,
      createdAt: new Date(),
    });
  });
}

describe('username claim', () => {
  it('lets a new user claim a free username with their profile', async () => {
    await assertSucceeds(claim(db('alice'), 'alice', 'ali'));
  });

  it('rejects signed-out users', async () => {
    await assertFails(claim(db(null), 'alice', 'ali'));
  });

  it('rejects claiming for someone else', async () => {
    await assertFails(claim(db('mallory'), 'alice', 'ali'));
  });

  it('cannot steal a username that is already taken', async () => {
    await seed('alice', 'ali');
    await assertFails(claim(db('mallory'), 'mallory', 'ali'));
    await assertFails(setDoc(doc(db('mallory'), 'usernames', 'ali'), { uid: 'mallory', createdAt: serverTimestamp() }));
  });

  it('cannot reserve a username without creating the matching profile', async () => {
    await assertFails(setDoc(doc(db('alice'), 'usernames', 'ali'), { uid: 'alice', createdAt: serverTimestamp() }));
  });

  it('cannot create a profile pointing at a username owned by someone else', async () => {
    await seed('bob', 'bobby');
    await assertFails(
      setDoc(doc(db('alice'), 'users', 'alice'), {
        uid: 'alice',
        username: 'bobby',
        displayName: 'Alice',
        photoURL: null,
        createdAt: serverTimestamp(),
      }),
    );
  });

  it('allows only one username per account', async () => {
    await seed('alice', 'ali');
    await assertFails(claim(db('alice'), 'alice', 'ali2'));
  });

  it.each(['Ali', 'ab', '1abc', 'a__', 'ali.', 'a..b', 'admin', 'nook', 'way_too_long_username_x', 'al i'])(
    'rejects invalid username %j',
    async (name) => {
      await assertFails(claim(db('alice'), 'alice', name));
    },
  );

  it('rejects extra fields and bad display names', async () => {
    await assertFails(claim(db('alice'), 'alice', 'ali', { isAdmin: true }));
    await assertFails(claim(db('alice'), 'alice', 'ali', { displayName: '' }));
    await assertFails(claim(db('alice'), 'alice', 'ali', { displayName: 'x'.repeat(41) }));
    await assertFails(claim(db('alice'), 'alice', 'ali', { photoURL: 'javascript:alert(1)' }));
  });

  it('requires a server timestamp', async () => {
    await assertFails(claim(db('alice'), 'alice', 'ali', { createdAt: new Date(0) }));
  });
});

describe('profiles', () => {
  beforeEach(async () => {
    await seed('alice', 'ali');
  });

  it('signed-in users can look up one profile and one username', async () => {
    await assertSucceeds(getDoc(doc(db('bob'), 'users', 'alice')));
    await assertSucceeds(getDoc(doc(db('bob'), 'usernames', 'ali')));
  });

  it('signed-out users can read nothing', async () => {
    await assertFails(getDoc(doc(db(null), 'users', 'alice')));
    await assertFails(getDoc(doc(db(null), 'usernames', 'ali')));
  });

  it('nobody can list users or usernames', async () => {
    await assertFails(getDocs(collection(db('bob'), 'users')));
    await assertFails(getDocs(collection(db('bob'), 'usernames')));
  });

  it('owner can change display name and photo', async () => {
    await assertSucceeds(updateDoc(doc(db('alice'), 'users', 'alice'), { displayName: 'Alice B', photoURL: null }));
  });

  it('username is permanent', async () => {
    await assertFails(updateDoc(doc(db('alice'), 'users', 'alice'), { username: 'alice' }));
    await assertFails(updateDoc(doc(db('alice'), 'usernames', 'ali'), { uid: 'alice' }));
  });

  it('others cannot edit or delete a profile or username', async () => {
    await assertFails(updateDoc(doc(db('bob'), 'users', 'alice'), { displayName: 'pwned' }));
    await assertFails(deleteDoc(doc(db('bob'), 'users', 'alice')));
    await assertFails(deleteDoc(doc(db('bob'), 'usernames', 'ali')));
  });

  it('owner cannot release the username while keeping the profile', async () => {
    await assertFails(deleteDoc(doc(db('alice'), 'usernames', 'ali')));
  });

  it('account deletion removes profile and username together', async () => {
    const fs = db('alice');
    const batch = writeBatch(fs);
    batch.delete(doc(fs, 'users', 'alice'));
    batch.delete(doc(fs, 'usernames', 'ali'));
    await assertSucceeds(batch.commit());
  });
});

describe('everything else', () => {
  it('is denied by default', async () => {
    await assertFails(setDoc(doc(db('alice'), 'random', 'x'), { a: 1 }));
    await assertFails(getDoc(doc(db('alice'), 'random', 'x')));
  });
});
