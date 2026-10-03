import Constants from 'expo-constants';

export const APP_NAME = 'NOOK';

/** OAuth "Web client ID" from Firebase Authentication > Google. Required for Google sign-in. */
export const GOOGLE_WEB_CLIENT_ID: string =
  process.env.EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID ||
  (Constants.expoConfig?.extra?.googleWebClientId as string | undefined) ||
  // nook-39914 Web client (client_type 3 in google-services.json). Public, not a secret.
  '904247852572-h6mqoud4uoqm1rvjqjfgc35qqdg4s1as.apps.googleusercontent.com';

export const LOCK_MIN_LENGTH = 6;
/** Lock the app again after this long in the background. */
export const LOCK_BACKGROUND_TIMEOUT_MS = 30_000;

export const USERNAME_MIN = 3;
export const USERNAME_MAX = 20;

/* ---------- Services configured per deployment (see README > Setup) ---------- */

/** Cloudflare Worker base URL, e.g. https://nook-worker.<you>.workers.dev (no trailing slash). */
export const WORKER_URL: string = (process.env.EXPO_PUBLIC_WORKER_URL ?? '').replace(/\/+$/, '');
/** Cloudinary cloud name and the UNSIGNED upload preset (restricted to formats, size and folder). */
export const CLOUDINARY_CLOUD_NAME: string = process.env.EXPO_PUBLIC_CLOUDINARY_CLOUD_NAME ?? '';
export const CLOUDINARY_UPLOAD_PRESET: string = process.env.EXPO_PUBLIC_CLOUDINARY_UPLOAD_PRESET ?? '';
/** Giphy SDK/API key (a public client key by design). */
export const GIPHY_API_KEY: string = process.env.EXPO_PUBLIC_GIPHY_API_KEY ?? '';

export const MAX_FILE_BYTES = 10 * 1024 * 1024;
export const PAGE_SIZE = 30;
export const MAX_GROUP_MEMBERS = 32;
export const CALL_RING_TIMEOUT_MS = 40_000;
