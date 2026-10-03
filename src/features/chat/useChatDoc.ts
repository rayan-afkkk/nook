import { doc, onSnapshot } from '@react-native-firebase/firestore';
import { useEffect, useState } from 'react';

import { db, metrics } from '@/lib/firebase';

import { useChat } from './chatList';
import type { Chat } from './types';

/**
 * A chat from the shared chat-list listener; falls back to its own listener only when the
 * chat isn't in the list yet (e.g. opened from a notification before the list loaded).
 */
export function useChatDoc(chatId: string): { chat: Chat | undefined; missing: boolean } {
  const fromList = useChat(chatId);
  const [own, setOwn] = useState<Chat | undefined>(undefined);
  const [missing, setMissing] = useState(false);
  const needOwn = !fromList;
  useEffect(() => {
    if (!needOwn) return;
    return onSnapshot(
      doc(db(), 'chats', chatId),
      (snap) => {
        if (!snap.metadata.fromCache) metrics.read(1, 'chat doc');
        if (snap.exists()) {
          setOwn({ id: snap.id, ...(snap.data() as Omit<Chat, 'id'>) });
          setMissing(false);
        } else if (!snap.metadata.fromCache) {
          setMissing(true);
        }
      },
      () => setMissing(true),
    );
  }, [chatId, needOwn]);
  return { chat: fromList ?? own, missing: !fromList && missing };
}
