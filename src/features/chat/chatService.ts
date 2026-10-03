import {
  arrayRemove,
  arrayUnion,
  collection,
  deleteDoc,
  deleteField,
  doc,
  getDocs,
  limit,
  query,
  runTransaction,
  serverTimestamp,
  Timestamp,
  updateDoc,
  where,
  writeBatch,
} from '@react-native-firebase/firestore';

import { MAX_GROUP_MEMBERS } from '@/constants/app';
import { UserFacingError } from '@/lib/errors';
import { db, metrics } from '@/lib/firebase';
import { callWorker, callWorkerInBackground, workerConfigured } from '@/lib/worker';

import { DISAPPEARING_MS, directChatId, previewFor } from './model';
import type { Chat, Disappearing, Media, Message, MessageKind, ReplyRef } from './types';

const chatRef = (id: string) => doc(db(), 'chats', id);
const messagesCol = (chatId: string) => collection(db(), 'chats', chatId, 'messages');

/** Opens (or creates) the one DM between two people. Returns the chat id. */
export async function openDirectChat(me: string, other: string, disappearing: Disappearing): Promise<string> {
  if (me === other) throw new UserFacingError("You can't message yourself.");
  const id = directChatId(me, other);
  const ref = chatRef(id);
  await runTransaction(db(), async (tx) => {
    const snap = await tx.get(ref);
    metrics.read(1, 'open DM');
    if (snap.exists()) return;
    tx.set(ref, {
      type: 'direct',
      members: [me, other].sort(),
      createdBy: me,
      createdAt: serverTimestamp(),
      lastMessage: null,
      lastMessageAt: serverTimestamp(),
      lastRead: { [me]: serverTimestamp() },
      mutedBy: [],
      disappearing,
    });
    metrics.write(1, 'create DM');
  });
  return id;
}

export async function createGroup(me: string, name: string, memberUids: string[], disappearing: Disappearing): Promise<string> {
  const clean = name.trim();
  if (!clean) throw new UserFacingError('Give the group a name.');
  const members = [...new Set([me, ...memberUids])];
  if (members.length < 2) throw new UserFacingError('Add at least one friend.');
  if (members.length > MAX_GROUP_MEMBERS) throw new UserFacingError(`Groups can have up to ${MAX_GROUP_MEMBERS} people.`);
  const ref = doc(collection(db(), 'chats'));
  const batch = writeBatch(db());
  batch.set(ref, {
    type: 'group',
    name: clean.slice(0, 40),
    photoURL: null,
    members,
    createdBy: me,
    createdAt: serverTimestamp(),
    lastMessage: { senderId: me, kind: 'system', preview: `Group created` },
    lastMessageAt: serverTimestamp(),
    lastRead: { [me]: serverTimestamp() },
    mutedBy: [],
    disappearing,
  });
  await batch.commit();
  metrics.write(1, 'create group');
  return ref.id;
}

export type Draft = {
  kind: MessageKind;
  text?: string;
  media?: Media;
  replyTo?: ReplyRef | null;
  forwarded?: boolean;
};

/** Pre-allocates a message id (used by the media outbox so retries don't duplicate). */
export function newMessageId(chatId: string): string {
  return doc(messagesCol(chatId)).id;
}

/**
 * Writes the message and the chat's "last message" in one batch. Firestore shows it instantly
 * (latency compensation) and queues it while offline. Push is requested once the server has it.
 */
export async function sendMessage(chat: Pick<Chat, 'id' | 'disappearing'>, me: string, draft: Draft, messageId?: string): Promise<string> {
  const id = messageId ?? newMessageId(chat.id);
  const ttl = DISAPPEARING_MS[chat.disappearing ?? 'off'];
  const message: Record<string, unknown> = {
    senderId: me,
    kind: draft.kind,
    createdAt: serverTimestamp(),
    reactions: {},
  };
  if (draft.text) message.text = draft.text.slice(0, 4000);
  if (draft.media) message.media = draft.media;
  if (draft.replyTo) message.replyTo = draft.replyTo;
  if (draft.forwarded) message.forwarded = true;
  if (ttl) message.expireAt = Timestamp.fromMillis(Date.now() + ttl);

  const batch = writeBatch(db());
  batch.set(doc(messagesCol(chat.id), id), message);
  batch.update(chatRef(chat.id), {
    lastMessage: { senderId: me, kind: draft.kind, preview: previewFor(draft.kind, draft.text, draft.media?.name) },
    lastMessageAt: serverTimestamp(),
    [`lastRead.${me}`]: serverTimestamp(),
  });
  const committed = batch.commit();
  metrics.write(2, 'send message');
  void committed.then(() => callWorkerInBackground('/notify', { chatId: chat.id, messageId: id })).catch(() => undefined);
  await committed;
  return id;
}

export async function setReaction(chatId: string, messageId: string, me: string, emoji: string | null) {
  await updateDoc(doc(messagesCol(chatId), messageId), { [`reactions.${me}`]: emoji ?? deleteField() });
  metrics.write(1, 'reaction');
}

/** Deletes for everyone. Media messages go through the Worker so the Cloudinary file is removed too. */
export async function deleteMessages(chat: Pick<Chat, 'id' | 'lastMessage'>, me: string, messages: Message[], latestId?: string) {
  const withMedia = messages.filter((m) => m.media?.publicId);
  const plain = messages.filter((m) => !m.media?.publicId);
  if (withMedia.length) {
    if (workerConfigured()) {
      await callWorker('/messages/delete', { chatId: chat.id, messageIds: withMedia.map((m) => m.id) });
    } else {
      plain.push(...withMedia);
    }
  }
  if (plain.length) {
    const batch = writeBatch(db());
    plain.forEach((m) => batch.delete(doc(messagesCol(chat.id), m.id)));
    await batch.commit();
    metrics.delete(plain.length, 'delete messages');
  }
  if (latestId && messages.some((m) => m.id === latestId)) {
    await updateDoc(chatRef(chat.id), { lastMessage: { senderId: me, kind: 'deleted', preview: '' } }).catch(() => undefined);
  }
}

export async function deleteMessage(chat: Pick<Chat, 'id' | 'lastMessage'>, me: string, message: Message, latestId?: string) {
  if (message.pending && !message.createdAt) {
    await deleteDoc(doc(messagesCol(chat.id), message.id));
    return;
  }
  await deleteMessages(chat, me, [message], latestId);
}

/** One small write when a chat is opened or scrolled to the bottom (read receipts + unread badges). */
export async function markRead(chatId: string, me: string) {
  await updateDoc(chatRef(chatId), { [`lastRead.${me}`]: serverTimestamp() });
  metrics.write(1, 'mark read');
}

export async function setDisappearing(chatId: string, value: Disappearing) {
  await updateDoc(chatRef(chatId), { disappearing: value });
  metrics.write(1, 'disappearing');
}

export async function setMuted(chatId: string, me: string, muted: boolean) {
  await updateDoc(chatRef(chatId), { mutedBy: muted ? arrayUnion(me) : arrayRemove(me) });
  metrics.write(1, 'mute');
}

export async function renameGroup(chatId: string, name: string) {
  const clean = name.trim();
  if (!clean) throw new UserFacingError('Give the group a name.');
  await updateDoc(chatRef(chatId), { name: clean.slice(0, 40) });
  metrics.write(1, 'rename group');
}

export async function addMembers(chat: Chat, uids: string[]) {
  const next = [...new Set([...chat.members, ...uids])];
  if (next.length > MAX_GROUP_MEMBERS) throw new UserFacingError(`Groups can have up to ${MAX_GROUP_MEMBERS} people.`);
  await updateDoc(chatRef(chat.id), { members: arrayUnion(...uids) });
  metrics.write(1, 'add members');
}

export async function leaveGroup(chatId: string, me: string) {
  await updateDoc(chatRef(chatId), { members: arrayRemove(me) });
  metrics.write(1, 'leave group');
}

/**
 * Disappearing messages: any member's app deletes a small batch of expired messages when the chat
 * opens (no Cloud Functions or TTL needed). Media ones go through the Worker to remove the file.
 */
export async function cleanupExpired(chat: Pick<Chat, 'id' | 'lastMessage'>, me: string): Promise<number> {
  const snap = await getDocs(query(messagesCol(chat.id), where('expireAt', '<=', Timestamp.now()), limit(25)));
  if (!snap.metadata.fromCache) metrics.read(Math.max(1, snap.size), 'expired scan');
  if (snap.empty) return 0;
  const expired = snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<Message, 'id'>) }));
  await deleteMessages(chat, me, expired);
  return expired.length;
}
