import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';

import { decodeJson } from '../src/crypto';
import { classifyFcmError } from '../src/fcm';
import { resetKeyCache, verifyIdToken } from '../src/firebaseAuth';
import { decodeFields } from '../src/firestore';
import { liveKitToken } from '../src/livekit';
import { notificationText } from '../src/notify';
import worker from '../src/index';

import { makeIdToken, makeRsaKey } from './helpers';

const PROJECT = 'nook-test';
let key: Awaited<ReturnType<typeof makeRsaKey>>;
const now = () => Math.floor(Date.now() / 1000);
const good = (sub = 'alice') => ({ aud: PROJECT, iss: `https://securetoken.google.com/${PROJECT}`, sub, iat: now() - 10, exp: now() + 3600, auth_time: now() - 10 });

beforeAll(async () => {
  key = await makeRsaKey();
});

afterEach(() => {
  vi.unstubAllGlobals();
  resetKeyCache();
});

describe('Firebase ID token verification', () => {
  const fetcher = async () => ({ keys: [key.jwk as JsonWebKey & { kid: string }], maxAgeSeconds: 3600 });

  it('accepts a valid token', async () => {
    const t = await makeIdToken(key.pem, key.kid, good());
    await expect(verifyIdToken(t, PROJECT, fetcher)).resolves.toMatchObject({ uid: 'alice' });
  });

  it.each([
    ['wrong audience', { aud: 'other' }],
    ['wrong issuer', { iss: 'https://evil.example.com' }],
    ['expired', { exp: now() - 1 }],
    ['no subject', { sub: '' }],
  ])('rejects %s', async (_label, override) => {
    const t = await makeIdToken(key.pem, key.kid, { ...good(), ...override });
    await expect(verifyIdToken(t, PROJECT, fetcher)).rejects.toThrow();
  });

  it('rejects a token signed by another key', async () => {
    const other = await makeRsaKey('test-kid');
    const t = await makeIdToken(other.pem, key.kid, good());
    await expect(verifyIdToken(t, PROJECT, fetcher)).rejects.toThrow(/signature/i);
  });

  it('rejects garbage', async () => {
    await expect(verifyIdToken('a.b', PROJECT, fetcher)).rejects.toThrow();
  });
});

describe('notification wording never includes content', () => {
  it('direct messages say who and what kind', () => {
    expect(notificationText({ chatType: 'direct', senderName: 'Ali', kind: 'image' }).body).toBe('Ali sent you a photo');
    expect(notificationText({ chatType: 'direct', senderName: 'Ali', kind: 'text' }).body).toBe('Ali sent you a message');
    expect(notificationText({ chatType: 'direct', senderName: 'Ali', kind: 'voice' }).body).toBe('Ali sent you a voice note');
  });
  it('groups only name the group', () => {
    expect(notificationText({ chatType: 'group', groupName: 'The Boys', senderName: 'Ali', kind: 'text' }).body).toBe('New message in The Boys');
  });
  it('sanitises names', () => {
    expect(notificationText({ chatType: 'direct', senderName: 'A\nB', kind: 'x' }).body).toBe('A B sent you a message');
    expect(notificationText({ chatType: 'direct', senderName: '', kind: 'gif' }).body).toBe('Someone sent you a GIF');
  });
});

describe('helpers', () => {
  it('LiveKit token grants exactly one room', async () => {
    const t = await liveKitToken('APIkey', 'secret', { identity: 'alice', room: 'call1', nowSeconds: 1000 });
    const claims = decodeJson<{ iss: string; sub: string; video: { room: string; roomJoin: boolean }; exp: number }>(t.split('.')[1]!);
    expect(claims).toMatchObject({ iss: 'APIkey', sub: 'alice', video: { room: 'call1', roomJoin: true } });
    expect(claims.exp).toBe(1000 + 7200);
  });

  it('classifies dead FCM tokens', () => {
    expect(classifyFcmError(404, '{"error":{"status":"NOT_FOUND","details":[{"errorCode":"UNREGISTERED"}]}}')).toBe('invalid-token');
    expect(classifyFcmError(400, '{"error":{"message":"The registration token is not a valid FCM registration token","status":"INVALID_ARGUMENT"}}')).toBe('invalid-token');
    expect(classifyFcmError(500, 'oops')).toBe('error');
  });

  it('decodes Firestore REST values', () => {
    expect(
      decodeFields({
        a: { stringValue: 'x' },
        b: { integerValue: '3' },
        c: { arrayValue: { values: [{ stringValue: 'u1' }] } },
        d: { mapValue: { fields: { e: { booleanValue: true } } } },
        t: { timestampValue: '2026-01-01T00:00:00Z' },
        n: { nullValue: null },
      }),
    ).toEqual({ a: 'x', b: 3, c: ['u1'], d: { e: true }, t: Date.parse('2026-01-01T00:00:00Z'), n: null });
  });
});

describe('POST /notify end to end (Google APIs mocked)', () => {
  it('pushes "Ali sent you a photo" to the other member, skips muted/blocked, removes dead tokens, never sends text', async () => {
    const sa = await makeRsaKey('sa');
    const env = {
      FIREBASE_PROJECT_ID: PROJECT,
      FIREBASE_SERVICE_ACCOUNT: JSON.stringify({ client_email: 'w@nook.iam.gserviceaccount.com', private_key: sa.pem }),
      LIVEKIT_URL: '',
      CLOUDINARY_CLOUD_NAME: '',
    };
    const str = (s: string) => ({ stringValue: s });
    const arr = (xs: string[]) => ({ arrayValue: { values: xs.map(str) } });
    const fcmBodies: string[] = [];
    const commits: string[] = [];
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      const body = typeof init?.body === 'string' ? init.body : '';
      if (url.includes('securetoken@system')) return new Response(JSON.stringify({ keys: [key.jwk] }), { headers: { 'cache-control': 'max-age=60' } });
      if (url.includes('oauth2.googleapis.com/token')) return Response.json({ access_token: 'g-token', expires_in: 3600 });
      if (url.includes('/documents/chats/dm1/messages/m1?')) {
        expect(url).toContain('mask.fieldPaths=senderId');
        expect(url).not.toContain('text');
        return Response.json({ fields: { senderId: str('alice'), kind: str('image') } });
      }
      if (url.includes('/documents/chats/dm1?')) return Response.json({ fields: { members: arr(['alice', 'bob', 'carol', 'dave']), mutedBy: arr(['carol']), type: str('group'), name: str('The Boys') } });
      if (url.includes('/documents/users/alice?')) return Response.json({ fields: { displayName: str('Ali') } });
      if (url.endsWith(':batchGet')) {
        const root = `projects/${PROJECT}/databases/(default)/documents`;
        return Response.json([
          { found: { name: `${root}/userPrivate/bob`, fields: { tokens: arr(['bob-live', 'bob-dead']), blocked: arr([]) } } },
          { found: { name: `${root}/userPrivate/dave`, fields: { tokens: arr(['dave-1']), blocked: arr(['alice']) } } },
        ]);
      }
      if (url.includes('fcm.googleapis.com')) {
        fcmBodies.push(body);
        return body.includes('bob-dead') ? new Response('{"error":{"details":[{"errorCode":"UNREGISTERED"}]}}', { status: 404 }) : Response.json({ name: 'ok' });
      }
      if (url.endsWith(':commit')) {
        commits.push(body);
        return Response.json({});
      }
      throw new Error(`unexpected fetch ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);

    const idToken = await makeIdToken(key.pem, key.kid, good('alice'));
    const res = await worker.fetch(
      new Request('https://w/notify', { method: 'POST', headers: { authorization: `Bearer ${idToken}` }, body: JSON.stringify({ chatId: 'dm1', messageId: 'm1' }) }),
      env,
    );
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ sent: 1 });
    // carol muted, dave blocked alice: only bob's two tokens were tried.
    expect(fcmBodies).toHaveLength(2);
    for (const b of fcmBodies) {
      expect(b).toContain('New message in The Boys');
      expect(b).not.toContain('secret message text');
    }
    expect(commits[0]).toContain('removeAllFromArray');
    expect(commits[0]).toContain('bob-dead');
  });

  it('rejects people outside the chat and missing tokens', async () => {
    const env = { FIREBASE_PROJECT_ID: PROJECT, FIREBASE_SERVICE_ACCOUNT: '', LIVEKIT_URL: '', CLOUDINARY_CLOUD_NAME: '' };
    const res = await worker.fetch(new Request('https://w/notify', { method: 'POST', body: '{}' }), env);
    expect(res.status).toBe(401);
    const notFound = await worker.fetch(new Request('https://w/nope', { method: 'POST' }), env);
    expect(notFound.status).toBe(404);
  });
});
