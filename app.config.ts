import type { ConfigContext, ExpoConfig } from 'expo/config';

/**
 * Dynamic Expo config.
 *
 * Secrets and per-developer files are never committed. `google-services.json`
 * is read from the path in GOOGLE_SERVICES_JSON (an EAS "file" environment
 * variable on the build server) or from the project root when building locally.
 */
const APP_NAME = 'NOOK';
const PACKAGE = 'com.nook.msgapp';
/** Printed by `npx eas-cli@latest init`. Not a secret; paste it here once. */
const EAS_PROJECT_ID = '';

export default ({ config }: ConfigContext): ExpoConfig => ({
  ...config,
  name: APP_NAME,
  slug: 'nook',
  scheme: 'nook',
  version: '0.1.0',
  orientation: 'portrait',
  icon: './assets/icon.png',
  userInterfaceStyle: 'automatic',
  backgroundColor: '#000000',
  android: {
    package: PACKAGE,
    versionCode: 1,
    googleServicesFile: process.env.GOOGLE_SERVICES_JSON ?? './google-services.json',
    adaptiveIcon: {
      backgroundColor: '#000000',
      foregroundImage: './assets/android-icon-foreground.png',
      backgroundImage: './assets/android-icon-background.png',
      monochromeImage: './assets/android-icon-monochrome.png',
    },
    predictiveBackGestureEnabled: false,
    permissions: [
      'android.permission.POST_NOTIFICATIONS',
      'android.permission.CAMERA',
      'android.permission.RECORD_AUDIO',
      'android.permission.USE_BIOMETRIC',
      'android.permission.VIBRATE',
      'android.permission.USE_FULL_SCREEN_INTENT',
      'android.permission.BLUETOOTH_CONNECT',
    ],
    blockedPermissions: ['android.permission.READ_EXTERNAL_STORAGE', 'android.permission.WRITE_EXTERNAL_STORAGE'],
  },
  ios: {
    bundleIdentifier: PACKAGE,
    supportsTablet: false,
  },
  web: {
    favicon: './assets/favicon.png',
  },
  plugins: [
    'expo-router',
    '@react-native-firebase/app',
    '@react-native-firebase/auth',
    '@react-native-firebase/messaging',
    [
      'expo-build-properties',
      {
        android: { minSdkVersion: 26 },
        ios: { useFrameworks: 'static' },
      },
    ],
    [
      'expo-splash-screen',
      {
        backgroundColor: '#000000',
        image: './assets/splash-icon.png',
        imageWidth: 96,
        dark: { backgroundColor: '#000000', image: './assets/splash-icon.png' },
      },
    ],
    [
      'expo-notifications',
      {
        icon: './assets/notification-icon.png',
        color: '#FF6A33',
      },
    ],
    [
      'expo-camera',
      {
        cameraPermission: 'NOOK uses the camera so you can take photos and join video calls.',
        microphonePermission: 'NOOK uses the microphone for voice notes and calls.',
        recordAudioAndroid: true,
      },
    ],
    [
      'expo-audio',
      { microphonePermission: 'NOOK uses the microphone for voice notes and calls.' },
    ],
    [
      'expo-local-authentication',
      { faceIDPermission: 'Unlock NOOK with Face ID.' },
    ],
    [
      'expo-image-picker',
      {
        photosPermission: 'NOOK lets you pick photos to send in your chats.',
        cameraPermission: 'NOOK uses the camera so you can take photos and join video calls.',
      },
    ],
    ['@livekit/react-native-expo-plugin', { android: { audioType: 'communication' } }],
    '@config-plugins/react-native-webrtc',
    'expo-secure-store',
    'expo-font',
    'expo-image',
    'expo-status-bar',
  ],
  experiments: {
    typedRoutes: true,
  },
  extra: {
    googleWebClientId: process.env.EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID ?? '',
    ...(EAS_PROJECT_ID ? { eas: { projectId: EAS_PROJECT_ID } } : {}),
  },
});
