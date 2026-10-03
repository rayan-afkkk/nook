import Constants from 'expo-constants';

export const APP_NAME = 'NOOK';

/** OAuth "Web client ID" from Firebase Authentication > Google. Required for Google sign-in. */
export const GOOGLE_WEB_CLIENT_ID: string =
  process.env.EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID ??
  (Constants.expoConfig?.extra?.googleWebClientId as string | undefined) ??
  '';

export const LOCK_MIN_LENGTH = 6;
/** Lock the app again after this long in the background. */
export const LOCK_BACKGROUND_TIMEOUT_MS = 30_000;

export const USERNAME_MIN = 3;
export const USERNAME_MAX = 20;
