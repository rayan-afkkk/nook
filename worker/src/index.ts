import { destroyAsset } from './cloudinary';
import { HttpError, type Env } from './env';
import { sendFcm } from './fcm';
import { verifyIdToken } from './firebaseAuth';
import { Firestore, type Doc } from './firestore';
import { googleAccessToken } from './google';
import { liveKitToken } from './livekit';
import { cleanName, notificationText } from './notify';

/**
 * NOOK Worker. Every endpoint:
 *   1. verifies the caller's Firebase ID token,
 *   2. checks they belong to the chat/call they're acting on,
 *   3. reads only the fields it needs (never message text), and logs nothing about content.
 */

const json = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { 'content-type': 'application/json', 'cache-control': 'no-store' } });

const ID = /^[A-Za-z0-9_-]{1,128}$/;

function str(v: unknown, field: string): string {
  if (typeof v !== 'string' || !ID.test(v)) throw new HttpError(400, `Invalid ${field}`);
  return v;
}

const asList = (v: unknown): string[] => (Array.isArray(v) ? v.filter((x): x is string => typeof x === 'string') : []);

type Ctx = { env: Env; uid: string; fs: Firestore; accessToken: string };

async function pushToUser(ctx: Ctx, recipient: string, privateDoc: Doc | null | undefined, build: (token: string) => Record<string, unknown>) {
  const tokens = asList(privateDoc?.tokens);
  const dead: string[] = [];
  let sent = 0;
  await Promise.all(
    tokens.map(async (token) => {
      const result = await sendFcm(ctx.env.FIREBASE_PROJECT_ID, ctx.accessToken, build(token));
      if (result === 'ok') sent += 1;
      else if (result === 'invalid-token') dead.push(token);
    }),
  );
  if (dead.length) await ctx.fs.commit([ctx.fs.arrayRemoveWrite(`userPrivate/${recipient}`, 'tokens', dead)]);
  return sent;
}

/** POST /notify { chatId, messageId } — "Ali sent you a photo" to everyone else in the chat. */
async function notify(ctx: Ctx, body: Record<string, unknown>) {
  const chatId = str(body.chatId, 'chatId');
  const messageId = str(body.messageId, 'messageId');
  const [chat, message, sender] = await Promise.all([
    ctx.fs.get(`chats/${chatId}`, ['members', 'mutedBy', 'type', 'name']),
    ctx.fs.get(`chats/${chatId}/messages/${messageId}`, ['senderId', 'kind']),
    ctx.fs.get(`users/${ctx.uid}`, ['displayName']),
  ]);
  const members = asList(chat?.members);
  if (!chat || !members.includes(ctx.uid)) throw new HttpError(403, 'Not a member of this chat');
  if (!message || message.senderId !== ctx.uid) throw new HttpError(403, 'Not your message');

  const muted = new Set(asList(chat.mutedBy));
  const recipients = members.filter((m) => m !== ctx.uid && !muted.has(m));
  const privates = await ctx.fs.batchGet(recipients.map((r) => `userPrivate/${r}`), ['tokens', 'blocked']);
  const { title, body: text } = notificationText({
    chatType: chat.type === 'group' ? 'group' : 'direct',
    groupName: chat.name,
    senderName: sender?.displayName,
    kind: message.kind,
  });

  let sent = 0;
  for (const r of recipients) {
    const priv = privates.get(`userPrivate/${r}`);
    if (asList(priv?.blocked).includes(ctx.uid)) continue;
    sent += await pushToUser(ctx, r, priv, (token) => ({
      token,
      notification: { title, body: text },
      data: { type: 'message', chatId },
      android: {
        priority: 'HIGH',
        collapse_key: chatId,
        notification: { channel_id: 'messages', tag: chatId, color: '#FF6A33', icon: 'notification_icon' },
      },
    }));
  }
  return { sent };
}

/** POST /messages/delete { chatId, messageIds } — sender deletes, or anyone cleans up expired ones. */
async function deleteMessages(ctx: Ctx, body: Record<string, unknown>) {
  const chatId = str(body.chatId, 'chatId');
  const ids = Array.isArray(body.messageIds) ? body.messageIds.slice(0, 50).map((m) => str(m, 'messageId')) : [];
  if (!ids.length) throw new HttpError(400, 'No messages');
  const chat = await ctx.fs.get(`chats/${chatId}`, ['members']);
  const isMember = asList(chat?.members).includes(ctx.uid);
  const docs = await ctx.fs.batchGet(ids.map((id) => `chats/${chatId}/messages/${id}`), ['senderId', 'expireAt', 'media.publicId', 'media.resourceType']);
  const creds =
    ctx.env.CLOUDINARY_CLOUD_NAME && ctx.env.CLOUDINARY_API_KEY && ctx.env.CLOUDINARY_API_SECRET
      ? { cloudName: ctx.env.CLOUDINARY_CLOUD_NAME, apiKey: ctx.env.CLOUDINARY_API_KEY, apiSecret: ctx.env.CLOUDINARY_API_SECRET }
      : null;
  const now = Date.now();
  const writes: unknown[] = [];
  for (const id of ids) {
    const path = `chats/${chatId}/messages/${id}`;
    const m = docs.get(path);
    if (!m) continue;
    const mine = m.senderId === ctx.uid;
    const expired = typeof m.expireAt === 'number' && m.expireAt <= now && isMember;
    if (!mine && !expired) throw new HttpError(403, 'You can only delete your own messages');
    const media = (m.media ?? {}) as { publicId?: unknown; resourceType?: unknown };
    if (creds && typeof media.publicId === 'string') {
      const type = media.resourceType === 'video' || media.resourceType === 'raw' ? media.resourceType : 'image';
      await destroyAsset(creds, media.publicId, type);
    }
    writes.push(ctx.fs.deleteWrite(path));
  }
  await ctx.fs.commit(writes);
  return { deleted: writes.length };
}

async function loadCall(ctx: Ctx, callId: string) {
  const call = await ctx.fs.get(`calls/${callId}`, ['members', 'status', 'callerId', 'calleeId', 'chatId', 'video']);
  if (!call || !asList(call.members).includes(ctx.uid)) throw new HttpError(403, 'Not part of this call');
  return call;
}

/** POST /call/token { callId } — LiveKit token for room = call id. */
async function callToken(ctx: Ctx, body: Record<string, unknown>) {
  const callId = str(body.callId, 'callId');
  const call = await loadCall(ctx, callId);
  if (call.status !== 'ringing' && call.status !== 'answered') throw new HttpError(409, 'This call has ended');
  if (!ctx.env.LIVEKIT_URL || !ctx.env.LIVEKIT_API_KEY || !ctx.env.LIVEKIT_API_SECRET) throw new HttpError(503, 'Calls are not set up on the server yet');
  const me = await ctx.fs.get(`users/${ctx.uid}`, ['displayName']);
  const token = await liveKitToken(ctx.env.LIVEKIT_API_KEY, ctx.env.LIVEKIT_API_SECRET, {
    identity: ctx.uid,
    name: cleanName(me?.displayName, 'NOOK'),
    room: callId,
  });
  return { token, url: ctx.env.LIVEKIT_URL };
}

/** POST /call/invite { callId } — high-priority data push that rings the other phone. */
async function callInvite(ctx: Ctx, body: Record<string, unknown>) {
  const callId = str(body.callId, 'callId');
  const call = await loadCall(ctx, callId);
  if (call.callerId !== ctx.uid || call.status !== 'ringing') throw new HttpError(409, 'Not ringing');
  const callee = String(call.calleeId);
  const [priv, me] = await Promise.all([ctx.fs.get(`userPrivate/${callee}`, ['tokens', 'blocked']), ctx.fs.get(`users/${ctx.uid}`, ['displayName'])]);
  if (asList(priv?.blocked).includes(ctx.uid)) return { sent: 0 };
  const sent = await pushToUser(ctx, callee, priv, (token) => ({
    token,
    data: { type: 'call', callId, chatId: String(call.chatId), video: call.video ? '1' : '0', callerName: cleanName(me?.displayName, 'Someone') },
    android: { priority: 'HIGH', ttl: '40s' },
  }));
  return { sent };
}

/** POST /call/cancel { callId } — stops the other phone ringing. */
async function callCancel(ctx: Ctx, body: Record<string, unknown>) {
  const callId = str(body.callId, 'callId');
  const call = await loadCall(ctx, callId);
  if (call.status === 'ringing' || call.status === 'answered') throw new HttpError(409, 'Call is still active');
  const other = asList(call.members).find((m) => m !== ctx.uid);
  if (!other) return { sent: 0 };
  const priv = await ctx.fs.get(`userPrivate/${other}`, ['tokens']);
  const sent = await pushToUser(ctx, other, priv, (token) => ({
    token,
    data: { type: 'call_cancel', callId },
    android: { priority: 'HIGH', ttl: '60s' },
  }));
  return { sent };
}

const ROUTES: Record<string, (ctx: Ctx, body: Record<string, unknown>) => Promise<unknown>> = {
  '/notify': notify,
  '/messages/delete': deleteMessages,
  '/call/token': callToken,
  '/call/invite': callInvite,
  '/call/cancel': callCancel,
};

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const { pathname } = new URL(request.url);
    if (request.method === 'GET' && pathname === '/') return json({ ok: true, service: 'nook-worker' });
    const handler = ROUTES[pathname];
    if (!handler) return json({ error: 'Not found' }, 404);
    if (request.method !== 'POST') return json({ error: 'Method not allowed' }, 405);
    try {
      const auth = request.headers.get('authorization') ?? '';
      const idToken = auth.startsWith('Bearer ') ? auth.slice(7) : '';
      if (!idToken) throw new HttpError(401, 'Missing token');
      const { uid } = await verifyIdToken(idToken, env.FIREBASE_PROJECT_ID);
      const body = (await request.json().catch(() => null)) as Record<string, unknown> | null;
      if (!body || typeof body !== 'object') throw new HttpError(400, 'Invalid JSON');
      const accessToken = await googleAccessToken(env.FIREBASE_SERVICE_ACCOUNT);
      const ctx: Ctx = { env, uid, fs: new Firestore(env.FIREBASE_PROJECT_ID, accessToken), accessToken };
      return json(await handler(ctx, body));
    } catch (e) {
      if (e instanceof HttpError) return json({ error: e.message }, e.status);
      // Log only the error type, never request data.
      console.error('nook-worker error', pathname, e instanceof Error ? e.name : 'unknown');
      return json({ error: 'Server error' }, 500);
    }
  },
} satisfies ExportedHandler<Env>;
