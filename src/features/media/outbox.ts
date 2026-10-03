import { create } from 'zustand';

import { describeError } from '@/lib/errors';
import { haptics } from '@/lib/haptics';
import { sendMessage, newMessageId } from '@/features/chat/chatService';
import type { Chat, Media, MessageKind, ReplyRef } from '@/features/chat/types';

import { uploadToCloudinary } from './cloudinary';

/**
 * Optimistic media sending. The item shows in the chat immediately with upload progress;
 * the Firestore message is written only after Cloudinary has the file. Failed items stay
 * in the chat with a Retry button.
 */
export type OutboxItem = {
  localId: string;
  chatId: string;
  messageId: string;
  kind: Extract<MessageKind, 'image' | 'file' | 'voice' | 'sticker'>;
  uri: string;
  name: string;
  mime: string;
  size?: number;
  width?: number;
  height?: number;
  durationMs?: number;
  waveform?: number[];
  replyTo?: ReplyRef | null;
  progress: number;
  status: 'uploading' | 'failed';
  error?: string;
  createdAt: number;
};

type State = { items: OutboxItem[] };
export const useOutbox = create<State>()(() => ({ items: [] }));

const patch = (localId: string, p: Partial<OutboxItem>) =>
  useOutbox.setState((s) => ({ items: s.items.map((i) => (i.localId === localId ? { ...i, ...p } : i)) }));
const removeItem = (localId: string) => useOutbox.setState((s) => ({ items: s.items.filter((i) => i.localId !== localId) }));

const chats = new Map<string, Pick<Chat, 'id' | 'disappearing'>>();

async function run(item: OutboxItem, me: string) {
  patch(item.localId, { status: 'uploading', progress: 0, error: undefined });
  try {
    const result = await uploadToCloudinary({
      uri: item.uri,
      name: item.name,
      mime: item.mime,
      size: item.size,
      resourceType: item.kind === 'file' ? 'raw' : item.kind === 'voice' ? 'video' : 'image',
      onProgress: (p) => patch(item.localId, { progress: p }),
    });
    const media: Media = {
      url: result.url,
      publicId: result.publicId,
      resourceType: result.resourceType,
      width: result.width ?? item.width,
      height: result.height ?? item.height,
      size: result.bytes,
      name: item.kind === 'file' ? item.name : undefined,
      mime: item.mime,
      durationMs: item.durationMs ?? result.durationMs,
      waveform: item.waveform,
    };
    const chat = chats.get(item.chatId) ?? { id: item.chatId, disappearing: 'off' as const };
    await sendMessage(chat, me, { kind: item.kind, media, replyTo: item.replyTo }, item.messageId);
    removeItem(item.localId);
    haptics.light();
  } catch (e) {
    patch(item.localId, { status: 'failed', error: describeError(e, 'Upload failed.') });
  }
}

export function enqueueMedia(
  chat: Pick<Chat, 'id' | 'disappearing'>,
  me: string,
  input: Omit<OutboxItem, 'localId' | 'chatId' | 'messageId' | 'progress' | 'status' | 'createdAt'>,
) {
  chats.set(chat.id, chat);
  const item: OutboxItem = {
    ...input,
    localId: `${Date.now()}-${Math.random().toString(36).slice(2)}`,
    chatId: chat.id,
    messageId: newMessageId(chat.id),
    progress: 0,
    status: 'uploading',
    createdAt: Date.now(),
  };
  useOutbox.setState((s) => ({ items: [...s.items, item] }));
  void run(item, me);
}

export function retryOutbox(localId: string, me: string) {
  const item = useOutbox.getState().items.find((i) => i.localId === localId);
  if (item) void run(item, me);
}

export function discardOutbox(localId: string) {
  removeItem(localId);
}
