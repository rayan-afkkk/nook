import { useEffect } from 'react';
import { AppState } from 'react-native';

import { setCallStatus } from '@/features/calls/callService';
import { nookCall } from '@/features/calls/nativeCall';
import { startChatList, stopChatList } from '@/features/chat/chatList';
import { startPrivate, stopPrivate } from '@/features/friends/privateDoc';
import { startPresence } from '@/features/presence/presence';
import { registerPush, startPushRouting } from '@/features/push/push';

/**
 * Starts the app-wide listeners once the person is fully signed in, and detaches the chat-list
 * listener while NOOK is in the background (Spark budget + battery).
 */
export function AppServices({ uid }: { uid: string }) {
  useEffect(() => {
    startChatList(uid);
    startPrivate(uid);
    startPresence(uid);
    const stopRouting = startPushRouting();
    let stopTokens: (() => void) | null = null;
    void registerPush(uid).then((off) => (stopTokens = off));

    // Calls declined from the notification while NOOK wasn't running.
    const reportDeclined = () => nookCall.consumeDeclined().forEach((id) => void setCallStatus(id, 'declined').catch(() => undefined));
    reportDeclined();

    const sub = AppState.addEventListener('change', (state) => {
      if (state === 'active') {
        startChatList(uid);
        reportDeclined();
      } else if (state === 'background') {
        stopChatList();
      }
    });
    return () => {
      sub.remove();
      stopRouting();
      stopTokens?.();
      stopChatList();
      stopPrivate();
    };
  }, [uid]);
  return null;
}
