import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { assertFails, assertSucceeds, initializeTestEnvironment, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import {
  arrayRemove,
  arrayUnion,
  collection,
  collectionGroup,
  deleteDoc,
  deleteField,
  doc,
  getDoc,
  getDocs,
  limit,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  Timestamp,
  updateDoc,
  where,
  writeBatch,
  type Firestore,
} from 'firebase/firestore';
import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';

let env: RulesTestEnvironment;
const A = 'alice';
const B = 'bob';
const C = 'carol';
const M = 'mallory';
const DM = `dm_${A}_${B}`;

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-nook',
    firestore: { rules: readFileSync(resolve(__dirname, '../firestore.rules'), 'utf8') },
  });
});
afterAll(async () => env.cleanup());

const db = (uid: string | null): Firestore =>
  (uid ? env.authenticatedContext(uid) : env.unauthenticatedContext()).firestore() as unknown as Firestore;

async function admin(fn: (fs: Firestore) => Promise<void>) {
  await env.withSecurityRulesDisabled(async (ctx) => fn(ctx.firestore() as unknown as Firestore));
}

const past = () => Timestamp.fromMillis(Date.now() - 60_000);

beforeEach(async () => {
  await env.clearFirestore();
  await admin(async (fs) => {
    for (const u of [A, B, C, M]) {
      await setDoc(doc(fs, 'users', u), { uid: u, username: u, displayName: u, photoURL: null, createdAt: new Date() });
    }
  });
});

function newDm(creator: string, members = [A, B], id = DM, extra: Record<string, unknown> = {}) {
  return setDoc(doc(db(creator), 'chats', id), {
    type: 'direct',
    members,
    createdBy: creator,
    createdAt: serverTimestamp(),
    lastMessage: null,
    lastMessageAt: serverTimestamp(),
    lastRead: { [creator]: serverTimestamp() },
    mutedBy: [],
    disappearing: 'off',
    ...extra,
  });
}

async function seedChats() {
  await admin(async (fs) => {
    await setDoc(doc(fs, 'chats', DM), {
      type: 'direct', members: [A, B], createdBy: A, createdAt: new Date(), lastMessage: null, lastMessageAt: new Date(),
      lastRead: {}, mutedBy: [], disappearing: 'off',
    });
    await setDoc(doc(fs, 'chats', 'g1'), {
      type: 'group', name: 'The Boys', photoURL: null, members: [A, B, C], createdBy: A, createdAt: new Date(),
      lastMessage: null, lastMessageAt: new Date(), lastRead: {}, mutedBy: [], disappearing: 'off',
    });
    await setDoc(doc(fs, 'chats', DM, 'messages', 'm1'), { senderId: B, kind: 'text', text: 'yo', reactions: {}, createdAt: new Date() });
    await setDoc(doc(fs, 'chats', DM, 'messages', 'old'), { senderId: B, kind: 'text', text: 'poof', reactions: {}, createdAt: new Date(), expireAt: past() });
  });
}

function send(uid: string, chatId: string, id: string, msg: Record<string, unknown> = {}) {
  const fs = db(uid);
  const batch = writeBatch(fs);
  const data: Record<string, unknown> = { senderId: uid, kind: 'text', text: 'hello', reactions: {}, createdAt: serverTimestamp(), ...msg };
  Object.keys(data).forEach((k) => data[k] === undefined && delete data[k]);
  batch.set(doc(fs, 'chats', chatId, 'messages', id), data);
  batch.update(doc(fs, 'chats', chatId), {
    lastMessage: { senderId: uid, kind: msg.kind ?? 'text', preview: 'hello' },
    lastMessageAt: serverTimestamp(),
    [`lastRead.${uid}`]: serverTimestamp(),
  });
  return batch.commit();
}

describe('direct chats', () => {
  it('either person can look up and create their DM, once', async () => {
    await assertSucceeds(getDoc(doc(db(A), 'chats', DM)));
    await assertSucceeds(newDm(A));
    await assertFails(newDm(B)); // exists now: that would be an update
  });

  it('nobody else can create or peek at it', async () => {
    await assertFails(getDoc(doc(db(M), 'chats', DM)));
    await assertFails(newDm(M));
  });

  it('id must match the sorted members', async () => {
    await assertFails(newDm(A, [B, A]));
    await assertFails(newDm(A, [A, B], `dm_${A}_${C}`));
    await assertFails(newDm(A, [A, 'ghost'], `dm_${A}_ghost`)); // user doesn't exist
  });
});

describe('groups', () => {
  beforeEach(seedChats);

  it('creates a group with valid fields', async () => {
    await assertSucceeds(
      setDoc(doc(db(A), 'chats', 'g2'), {
        type: 'group', name: 'Crew', photoURL: null, members: [A, B], createdBy: A, createdAt: serverTimestamp(),
        lastMessage: { senderId: A, kind: 'system', preview: 'Group created' }, lastMessageAt: serverTimestamp(),
        lastRead: { [A]: serverTimestamp() }, mutedBy: [], disappearing: '24h',
      }),
    );
  });

  it('members can rename, add people, and leave; outsiders cannot', async () => {
    await assertSucceeds(updateDoc(doc(db(B), 'chats', 'g1'), { name: 'The Crew' }));
    await assertSucceeds(updateDoc(doc(db(B), 'chats', 'g1'), { members: arrayUnion(M) }));
    await assertSucceeds(updateDoc(doc(db(C), 'chats', 'g1'), { members: arrayRemove(C) }));
    await assertFails(updateDoc(doc(db(C), 'chats', 'g1'), { name: 'x' })); // C left
  });

  it('cannot kick someone else out', async () => {
    await assertFails(updateDoc(doc(db(A), 'chats', 'g1'), { members: arrayRemove(B) }));
  });

  it('outsiders cannot read or join', async () => {
    await assertFails(getDoc(doc(db(M), 'chats', 'g1')));
    await assertFails(updateDoc(doc(db(M), 'chats', 'g1'), { members: arrayUnion(M) }));
  });

  it('chat list query only returns my chats', async () => {
    await assertSucceeds(getDocs(query(collection(db(A), 'chats'), where('members', 'array-contains', A), orderBy('lastMessageAt', 'desc'), limit(50))));
    await assertFails(getDocs(query(collection(db(A), 'chats'), orderBy('lastMessageAt', 'desc'))));
  });

  it('mute, read receipts and timer only touch my own entries', async () => {
    await assertSucceeds(updateDoc(doc(db(A), 'chats', 'g1'), { mutedBy: arrayUnion(A) }));
    await assertFails(updateDoc(doc(db(A), 'chats', 'g1'), { mutedBy: arrayUnion(B) }));
    await assertSucceeds(updateDoc(doc(db(A), 'chats', 'g1'), { [`lastRead.${A}`]: serverTimestamp() }));
    await assertFails(updateDoc(doc(db(A), 'chats', 'g1'), { [`lastRead.${B}`]: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(db(A), 'chats', 'g1'), { disappearing: '7d' }));
    await assertFails(updateDoc(doc(db(A), 'chats', 'g1'), { disappearing: '1h' }));
  });

  it('cannot delete a chat', async () => {
    await assertFails(deleteDoc(doc(db(A), 'chats', 'g1')));
  });
});

describe('messages', () => {
  beforeEach(seedChats);

  it('members send text with the chat preview in one batch', async () => {
    await assertSucceeds(send(A, DM, 'n1'));
    await assertSucceeds(send(C, 'g1', 'n2'));
  });

  it('outsiders cannot send or read', async () => {
    await assertFails(send(M, DM, 'n1'));
    await assertFails(getDoc(doc(db(M), 'chats', DM, 'messages', 'm1')));
    await assertFails(getDocs(collection(db(M), 'chats', DM, 'messages')));
  });

  it('cannot impersonate a sender or fake the time', async () => {
    const fs = db(A);
    await assertFails(setDoc(doc(fs, 'chats', DM, 'messages', 'x'), { senderId: B, kind: 'text', text: 'hi', reactions: {}, createdAt: serverTimestamp() }));
    await assertFails(setDoc(doc(fs, 'chats', DM, 'messages', 'x'), { senderId: A, kind: 'text', text: 'hi', reactions: {}, createdAt: new Date(0) }));
  });

  it('validates media and expiry', async () => {
    await assertSucceeds(send(A, DM, 'img', { kind: 'image', text: undefined, media: { url: 'https://res.cloudinary.com/x/image/upload/a.jpg', publicId: 'nook/a', width: 10, height: 10 } }));
    await assertSucceeds(send(A, DM, 'gif', { kind: 'gif', text: undefined, media: { url: 'https://media2.giphy.com/media/abc/200w.webp' } }));
    await assertFails(send(A, DM, 'evil', { kind: 'image', text: undefined, media: { url: 'https://evil.example.com/a.jpg' } }));
    await assertFails(send(A, DM, 'big', { kind: 'file', text: undefined, media: { url: 'https://res.cloudinary.com/x/raw/upload/a.zip', size: 20 * 1024 * 1024 } }));
    await assertSucceeds(send(A, DM, 'ttl', { expireAt: Timestamp.fromMillis(Date.now() + 86_400_000) }));
    await assertFails(send(A, DM, 'ttl2', { expireAt: Timestamp.fromMillis(Date.now() + 30 * 86_400_000) }));
  });

  it('reactions: each member sets or clears only their own', async () => {
    await assertSucceeds(updateDoc(doc(db(A), 'chats', DM, 'messages', 'm1'), { [`reactions.${A}`]: '❤️' }));
    await assertSucceeds(updateDoc(doc(db(A), 'chats', DM, 'messages', 'm1'), { [`reactions.${A}`]: deleteField() }));
    await assertFails(updateDoc(doc(db(A), 'chats', DM, 'messages', 'm1'), { [`reactions.${B}`]: '❤️' }));
    await assertFails(updateDoc(doc(db(A), 'chats', DM, 'messages', 'm1'), { text: 'edited' }));
  });

  it('only the sender deletes, except expired messages which any member may clean up', async () => {
    await assertFails(deleteDoc(doc(db(A), 'chats', DM, 'messages', 'm1')));
    await assertSucceeds(deleteDoc(doc(db(B), 'chats', DM, 'messages', 'm1')));
    await assertSucceeds(deleteDoc(doc(db(A), 'chats', DM, 'messages', 'old')));
  });

  it('expired-message scan is allowed for members', async () => {
    await assertSucceeds(getDocs(query(collection(db(A), 'chats', DM, 'messages'), where('expireAt', '<=', Timestamp.now()), limit(25))));
  });

  it('account deletion can find only my own messages across chats', async () => {
    await assertSucceeds(getDocs(query(collectionGroup(db(B), 'messages'), where('senderId', '==', B), limit(200))));
    await assertFails(getDocs(query(collectionGroup(db(M), 'messages'), where('senderId', '==', B))));
  });
});

describe('calls', () => {
  beforeEach(seedChats);

  const ring = (uid: string, callee: string, extra: Record<string, unknown> = {}) =>
    setDoc(doc(db(uid), 'calls', 'c1'), {
      chatId: DM, members: [uid, callee].sort(), callerId: uid, calleeId: callee, video: false, status: 'ringing', startedAt: serverTimestamp(), ...extra,
    });

  it('a member can ring the other member of a chat', async () => {
    await assertSucceeds(ring(A, B));
  });

  it('cannot ring someone outside the chat or fake the caller', async () => {
    await assertFails(ring(A, C));
    await assertFails(ring(M, A));
    await assertFails(ring(A, B, { callerId: B }));
  });

  it('only the callee answers; either can end; outsiders can’t touch it', async () => {
    await ring(A, B);
    await assertFails(updateDoc(doc(db(A), 'calls', 'c1'), { status: 'answered', answeredAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(db(B), 'calls', 'c1'), { status: 'answered', answeredAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(db(A), 'calls', 'c1'), { status: 'ended', endedAt: serverTimestamp() }));
    await assertFails(getDoc(doc(db(M), 'calls', 'c1')));
  });

  it('history query only shows my calls', async () => {
    await ring(A, B);
    await assertSucceeds(getDocs(query(collection(db(B), 'calls'), where('members', 'array-contains', B), orderBy('startedAt', 'desc'))));
  });
});

describe('private data and sticker packs', () => {
  it('push tokens and block list are owner-only', async () => {
    await assertSucceeds(setDoc(doc(db(A), 'userPrivate', A), { tokens: arrayUnion('t1'), blocked: [], updatedAt: serverTimestamp() }, { merge: true }));
    await assertFails(getDoc(doc(db(B), 'userPrivate', A)));
    await assertFails(setDoc(doc(db(B), 'userPrivate', A), { tokens: ['evil'] }));
    await assertFails(setDoc(doc(db(A), 'userPrivate', A), { isAdmin: true }, { merge: true }));
  });

  it('anyone can make a pack and add stickers; only the creator renames or deletes', async () => {
    const s = (by: string) => ({ url: 'https://res.cloudinary.com/x/image/upload/s.png', publicId: `s-${by}`, addedBy: by });
    await assertSucceeds(setDoc(doc(db(A), 'stickerPacks', 'p1'), { name: 'Inside jokes', createdBy: A, createdAt: serverTimestamp(), stickers: [s(A)] }));
    await assertSucceeds(updateDoc(doc(db(B), 'stickerPacks', 'p1'), { stickers: arrayUnion(s(B)) }));
    await assertFails(updateDoc(doc(db(B), 'stickerPacks', 'p1'), { stickers: [] }));
    await assertFails(updateDoc(doc(db(B), 'stickerPacks', 'p1'), { name: 'mine now' }));
    await assertSucceeds(updateDoc(doc(db(A), 'stickerPacks', 'p1'), { name: 'Jokes' }));
    await assertFails(deleteDoc(doc(db(B), 'stickerPacks', 'p1')));
    await assertSucceeds(getDocs(collection(db(C), 'stickerPacks')));
    await assertFails(getDocs(collection(db(null), 'stickerPacks')));
  });
});
