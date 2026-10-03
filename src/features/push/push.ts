import {
  deleteToken,
  getInitialNotification,
  getMessaging,
  getToken,
  onMessage,
  onNotificationOpenedApp,
  onTokenRefresh,
  type RemoteMessage,
} from '@react-native-firebase/messaging';
import { router } from 'expo-router';

import { toast } from '@/components/ui';
import { nookCall } from '@/features/calls/nativeCall';
import { getActiveChat } from '@/features/chat/activeChat';
import { addPushToken, removePushToken } from '@/features/friends/privateDoc';
import { ensureNotificationChannels } from '@/features/permissions/permissions';

/**
 * Push payloads never contain message text. Data keys:
 *   type: 'message' | 'call' | 'call_cancel', chatId, callId, video ('1' | '0'), callerName
 */
type Data = { type?: string; chatId?: string; callId?: string; video?: string; callerName?: string };

let token: string | null = null;

export async function registerPush(uid: string): Promise<() => void> {
  const messaging = getMessaging();
  await ensureNotificationChannels().catch(() => undefined);
  try {
    token = await getToken(messaging);
    await addPushToken(uid, token);
  } catch {
    // No Play services / offline: try again next launch.
  }
  return onTokenRefresh(messaging, (t) => {
    token = t;
    void addPushToken(uid, t).catch(() => undefined);
  });
}

/** Called before sign-out / account deletion so this phone stops receiving pushes. */
export async function unregisterPush(uid: string) {
  const t = token;
  token = null;
  if (t) await removePushToken(uid, t).catch(() => undefined);
  await deleteToken(getMessaging()).catch(() => undefined);
}

function openFromData(data: Data | undefined) {
  if (!data) return;
  if (data.type === 'message' && data.chatId) router.push({ pathname: '/chat/[id]', params: { id: data.chatId } });
  if (data.type === 'call' && data.callId) router.push({ pathname: '/call/incoming', params: { callId: data.callId } });
}

/** Foreground pushes, taps on notifications and the notification that cold-started the app. */
export function startPushRouting(): () => void {
  const messaging = getMessaging();
  const offMessage = onMessage(messaging, (msg: RemoteMessage) => {
    const data = msg.data as Data | undefined;
    if (data?.type === 'call' && data.callId) {
      router.push({ pathname: '/call/incoming', params: { callId: data.callId } });
      return;
    }
    if (data?.type === 'call_cancel' && data.callId) {
      nookCall.dismiss(data.callId);
      return;
    }
    if (data?.type === 'message' && data.chatId && data.chatId !== getActiveChat() && msg.notification?.body) {
      toast.show(msg.notification.body);
    }
  });
  const offOpened = onNotificationOpenedApp(messaging, (msg) => openFromData(msg.data as Data | undefined));
  void getInitialNotification(messaging).then((msg) => openFromData(msg?.data as Data | undefined));
  return () => {
    offMessage();
    offOpened();
  };
}
