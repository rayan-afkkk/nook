import { HttpError } from './env';
import { b64urlDecode, decodeJson } from './crypto';

/** Google's public keys for Firebase ID tokens, as a JWK set. */
const JWKS_URL = 'https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com';

type Jwk = JsonWebKey & { kid: string };
type Claims = { aud: string; iss: string; sub: string; exp: number; iat: number; auth_time?: number; name?: string };

let cache: { keys: Jwk[]; expires: number } | null = null;

export type KeyFetcher = () => Promise<{ keys: Jwk[]; maxAgeSeconds: number }>;

export const fetchGoogleKeys: KeyFetcher = async () => {
  const res = await fetch(JWKS_URL);
  if (!res.ok) throw new HttpError(503, 'Could not load Google signing keys');
  const maxAge = Number(/max-age=(\d+)/.exec(res.headers.get('cache-control') ?? '')?.[1] ?? 3600);
  const body = (await res.json()) as { keys: Jwk[] };
  return { keys: body.keys, maxAgeSeconds: maxAge };
};

async function keysFor(fetcher: KeyFetcher, now: number): Promise<Jwk[]> {
  if (cache && cache.expires > now) return cache.keys;
  const { keys, maxAgeSeconds } = await fetcher();
  cache = { keys, expires: now + maxAgeSeconds * 1000 };
  return keys;
}

export function resetKeyCache() {
  cache = null;
}

/**
 * Verifies a Firebase Auth ID token (RS256, Google's rotating keys) and returns the user id.
 * Checks signature, audience, issuer, expiry and issued-at, per Firebase's documented rules.
 */
export async function verifyIdToken(token: string, projectId: string, fetcher: KeyFetcher = fetchGoogleKeys, nowMs = Date.now()): Promise<{ uid: string; name?: string }> {
  const parts = token.split('.');
  if (parts.length !== 3) throw new HttpError(401, 'Malformed token');
  const [h, p, s] = parts as [string, string, string];
  const header = decodeJson<{ alg?: string; kid?: string }>(h);
  if (header.alg !== 'RS256' || !header.kid) throw new HttpError(401, 'Bad token header');
  const jwk = (await keysFor(fetcher, nowMs)).find((k) => k.kid === header.kid);
  if (!jwk) {
    resetKeyCache();
    throw new HttpError(401, 'Unknown signing key');
  }
  const key = await crypto.subtle.importKey('jwk', jwk, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['verify']);
  const ok = await crypto.subtle.verify('RSASSA-PKCS1-v1_5', key, b64urlDecode(s), new TextEncoder().encode(`${h}.${p}`));
  if (!ok) throw new HttpError(401, 'Bad signature');
  const claims = decodeJson<Claims>(p);
  const now = Math.floor(nowMs / 1000);
  if (claims.aud !== projectId) throw new HttpError(401, 'Wrong audience');
  if (claims.iss !== `https://securetoken.google.com/${projectId}`) throw new HttpError(401, 'Wrong issuer');
  if (!claims.sub || typeof claims.sub !== 'string') throw new HttpError(401, 'No subject');
  if (claims.exp <= now) throw new HttpError(401, 'Token expired');
  if (claims.iat > now + 300) throw new HttpError(401, 'Token from the future');
  if (claims.auth_time && claims.auth_time > now + 300) throw new HttpError(401, 'Bad auth time');
  return { uid: claims.sub, name: claims.name };
}
