import { requireOptionalNativeModule } from 'expo-modules-core';
import { Platform } from 'react-native';

type NookCallNative = {
  showIncomingCall(callId: string, callerName: string, video: boolean): void;
  dismissIncomingCall(callId: string): void;
  startRingtone(): void;
  stopRingtone(): void;
  setShowWhenLocked(show: boolean): void;
  canUseFullScreenIntent(): boolean;
  openFullScreenIntentSettings(): void;
  consumeDeclinedCalls(): string[];
};

/** modules/nook-call (Android). Absent in tests and on other platforms: every call becomes a no-op. */
const native = Platform.OS === 'android' ? requireOptionalNativeModule<NookCallNative>('NookCall') : null;

const safe = <T>(fn: () => T, fallback: T): T => {
  try {
    return native ? fn() : fallback;
  } catch {
    return fallback;
  }
};

export const nookCall = {
  available: !!native,
  showIncoming: (callId: string, callerName: string, video: boolean) => safe(() => native!.showIncomingCall(callId, callerName, video), undefined),
  dismiss: (callId: string) => safe(() => native!.dismissIncomingCall(callId), undefined),
  startRingtone: () => safe(() => native!.startRingtone(), undefined),
  stopRingtone: () => safe(() => native!.stopRingtone(), undefined),
  setShowWhenLocked: (show: boolean) => safe(() => native!.setShowWhenLocked(show), undefined),
  canUseFullScreenIntent: () => safe(() => native!.canUseFullScreenIntent(), true),
  openFullScreenIntentSettings: () => safe(() => native!.openFullScreenIntentSettings(), undefined),
  consumeDeclined: () => safe(() => native!.consumeDeclinedCalls(), [] as string[]),
};
