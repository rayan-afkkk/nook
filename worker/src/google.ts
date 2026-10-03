import { HttpError } from './env';
import { signRS256 } from './crypto';

type ServiceAccount = { client_email: string; private_key: string; private_key_id?: string; token_uri?: string };

const SCOPES = ['https://www.googleapis.com/auth/datastore', 'https://www.googleapis.com/auth/firebase.messaging'].join(' ');

let cached: { token: string; expires: number; email: string } | null = null;

export function parseServiceAccount(raw: string | undefined): ServiceAccount {
  if (!raw) throw new HttpError(500, 'FIREBASE_SERVICE_ACCOUNT secret is not set');
  try {
    const sa = JSON.parse(raw) as ServiceAccount;
    if (!sa.client_email || !sa.private_key) throw new Error('missing fields');
    return sa;
  } catch {
    throw new HttpError(500, 'FIREBASE_SERVICE_ACCOUNT is not valid service-account JSON');
  }
}

/** OAuth access token for Firestore + FCM, from the service account (JWT bearer grant). Cached ~1h. */
export async function googleAccessToken(rawServiceAccount: string | undefined): Promise<string> {
  const sa = parseServiceAccount(rawServiceAccount);
  const now = Math.floor(Date.now() / 1000);
  if (cached && cached.email === sa.client_email && cached.expires > now + 60) return cached.token;
  const tokenUri = sa.token_uri ?? 'https://oauth2.googleapis.com/token';
  const assertion = await signRS256(
    { iss: sa.client_email, scope: SCOPES, aud: tokenUri, iat: now, exp: now + 3600 },
    sa.private_key,
    sa.private_key_id,
  );
  const res = await fetch(tokenUri, {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion }),
  });
  if (!res.ok) throw new HttpError(502, `Google auth failed (${res.status})`);
  const body = (await res.json()) as { access_token: string; expires_in: number };
  cached = { token: body.access_token, expires: now + body.expires_in, email: sa.client_email };
  return body.access_token;
}
