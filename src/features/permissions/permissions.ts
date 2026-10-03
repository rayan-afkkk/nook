import { requestRecordingPermissionsAsync, getRecordingPermissionsAsync } from 'expo-audio';
import { Camera } from 'expo-camera';
import * as Notifications from 'expo-notifications';
import { Linking, Platform } from 'react-native';

export type PermissionKey = 'notifications' | 'camera' | 'microphone';
export type PermissionState = 'granted' | 'undetermined' | 'denied' | 'blocked';

type Response = { granted: boolean; status: string; canAskAgain?: boolean };

function toState(r: Response): PermissionState {
  if (r.granted) return 'granted';
  if (r.status === 'undetermined') return 'undetermined';
  return r.canAskAgain === false ? 'blocked' : 'denied';
}

/** Android 13+ needs a channel before the notification permission can be requested. */
export async function ensureNotificationChannels(): Promise<void> {
  if (Platform.OS !== 'android') return;
  await Notifications.setNotificationChannelAsync('messages', {
    name: 'Messages',
    description: 'New messages from your chats',
    importance: Notifications.AndroidImportance.HIGH,
    vibrationPattern: [0, 180, 120, 180],
    lightColor: '#FF6A33',
    lockscreenVisibility: Notifications.AndroidNotificationVisibility.PRIVATE,
  });
}

export async function getPermission(key: PermissionKey): Promise<PermissionState> {
  switch (key) {
    case 'notifications':
      return toState(await Notifications.getPermissionsAsync());
    case 'camera':
      return toState(await Camera.getCameraPermissionsAsync());
    case 'microphone':
      return toState(await getRecordingPermissionsAsync());
  }
}

export async function requestPermission(key: PermissionKey): Promise<PermissionState> {
  switch (key) {
    case 'notifications':
      await ensureNotificationChannels();
      return toState(await Notifications.requestPermissionsAsync());
    case 'camera':
      return toState(await Camera.requestCameraPermissionsAsync());
    case 'microphone':
      return toState(await requestRecordingPermissionsAsync());
  }
}

export function openSystemSettings(): void {
  void Linking.openSettings();
}
