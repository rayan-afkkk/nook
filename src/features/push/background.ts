import type { RemoteMessage } from '@react-native-firebase/messaging';

import { nookCall } from '@/features/calls/nativeCall';

/**
 * Runs headless when NOOK is in the background or killed (Android). Message notifications are
 * drawn by the system from the FCM payload; calls arrive as high-priority data messages and are
 * turned into a full-screen ringing notification here.
 */
export async function handleBackgroundMessage(msg: RemoteMessage): Promise<void> {
  const data = (msg.data ?? {}) as Record<string, string | undefined>;
  if (data.type === 'call' && data.callId) {
    nookCall.showIncoming(data.callId, data.callerName || 'NOOK', data.video === '1');
  } else if (data.type === 'call_cancel' && data.callId) {
    nookCall.dismiss(data.callId);
  }
}
