export interface Env {
  FIREBASE_PROJECT_ID: string;
  /** Service-account JSON (secret). Needs roles: Cloud Datastore User + Firebase Cloud Messaging API Admin. */
  FIREBASE_SERVICE_ACCOUNT: string;
  LIVEKIT_URL: string;
  LIVEKIT_API_KEY?: string;
  LIVEKIT_API_SECRET?: string;
  CLOUDINARY_CLOUD_NAME: string;
  CLOUDINARY_API_KEY?: string;
  CLOUDINARY_API_SECRET?: string;
}

export class HttpError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}
