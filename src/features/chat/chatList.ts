import { collection, limit, onSnapshot, orderBy, query, where } from '@react-native-firebase/firestore';
import { create } from 'zustand';

import { ensureProfiles } from '@/features/profile/profiles';
import { db, metrics } from '@/lib/firebase';

import { otherMember } from './model';
import type { Chat } from './types';

type State = {
  status: 'idle' | 'loading' | 'ready' | 'error';
  chats: Chat[];
  error: string | null;
};

/** The single chat-list listener for the whole app (Spark budget: one query, served from cache on reopen). */
export const useChatList = create<State>()(() => ({ status: 'idle', chats: [], error: null }));

let stop: (() => void) | null = null;
let currentUid: string | null = null;

export function startChatList(uid: string) {
  if (stop && currentUid === uid) return;
  stopChatList();
  currentUid = uid;
  if (useChatList.getState().status === 'idle') useChatList.setState({ status: 'loading' });
  const q = query(collection(db(), 'chats'), where('members', 'array-contains', uid), orderBy('lastMessageAt', 'desc'), limit(100));
  stop = onSnapshot(
    q,
    (snap) => {
      if (!snap.metadata.fromCache) metrics.read(Math.max(1, snap.docChanges().length), 'chat list');
      const chats = snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<Chat, 'id'>) }));
      useChatList.setState({ status: 'ready', chats, error: null });
      const people = new Set<string>();
      chats.forEach((c) => (c.type === 'direct' ? people.add(otherMember(c.members, uid) ?? '') : c.members.forEach((m) => people.add(m))));
      people.delete('');
      people.delete(uid);
      ensureProfiles([...people]);
    },
    (error) => useChatList.setState({ status: 'error', error: error.message }),
  );
}

export function stopChatList() {
  stop?.();
  stop = null;
  currentUid = null;
}

export function resetChatList() {
  stopChatList();
  useChatList.setState({ status: 'idle', chats: [], error: null });
}

export function useChat(chatId: string | undefined): Chat | undefined {
  return useChatList((s) => s.chats.find((c) => c.id === chatId));
}
