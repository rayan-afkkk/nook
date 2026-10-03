import { useMemo } from 'react';

import { useChatList } from '@/features/chat/chatList';

/** Everyone I share a chat with, most recent first (derived from the chat list; no extra reads). */
export function useFriends(me: string): string[] {
  const chats = useChatList((s) => s.chats);
  return useMemo(() => {
    const seen = new Set<string>();
    const out: string[] = [];
    for (const c of chats) {
      for (const m of c.members) {
        if (m !== me && !seen.has(m)) {
          seen.add(m);
          out.push(m);
        }
      }
    }
    return out;
  }, [chats, me]);
}
