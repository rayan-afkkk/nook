/**
 * App entry. Things that must exist before React mounts (and in headless background tasks):
 * the FCM background handler and LiveKit's WebRTC globals.
 */
import { registerGlobals } from '@livekit/react-native';
import { getMessaging, setBackgroundMessageHandler } from '@react-native-firebase/messaging';

import { handleBackgroundMessage } from './src/features/push/background';

registerGlobals();
setBackgroundMessageHandler(getMessaging(), handleBackgroundMessage);

// eslint-disable-next-line import/first
import 'expo-router/entry';
