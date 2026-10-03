import { pbkdf2Async } from '@noble/hashes/pbkdf2.js';
import { sha256 } from '@noble/hashes/sha2.js';
import { bytesToHex, hexToBytes, utf8ToBytes } from '@noble/hashes/utils.js';

import { LOCK_MIN_LENGTH } from '@/constants/app';

export type LockKind = 'pin' | 'password';

/** What is kept on the device. The secret itself is never stored. */
export type LockRecord = {
  v: 1;
  kind: LockKind;
  /** hex, 16 random bytes */
  salt: string;
  /** hex, PBKDF2-HMAC-SHA256 output */
  hash: string;
  iterations: number;
  biometric: boolean;
  /** PIN only: lets the lock screen submit as soon as enough digits are typed. */
  pinLength?: number;
};

/**
 * PBKDF2 work factor. Pure-JS on Hermes, so this is tuned to ~0.3-0.6s on a
 * mid-range phone. The record stores its own count so it can be raised later.
 */
export const LOCK_ITERATIONS = 60_000;

export function validateSecret(kind: LockKind, secret: string): string | null {
  if (secret.length < LOCK_MIN_LENGTH) {
    return `Use at least ${LOCK_MIN_LENGTH} ${kind === 'pin' ? 'digits' : 'characters'}.`;
  }
  if (kind === 'pin' && !/^\d+$/.test(secret)) return 'A PIN can only contain numbers.';
  if (kind === 'pin' && /^(\d)\1+$/.test(secret)) return 'Avoid repeating the same digit.';
  if (kind === 'pin' && ('0123456789'.includes(secret) || '9876543210'.includes(secret))) {
    return 'Avoid simple sequences like 123456.';
  }
  if (secret.length > 64) return 'Keep it under 64 characters.';
  return null;
}

export async function deriveHash(secret: string, saltHex: string, iterations: number): Promise<string> {
  const key = await pbkdf2Async(sha256, utf8ToBytes(secret), hexToBytes(saltHex), {
    c: iterations,
    dkLen: 32,
    asyncTick: 16,
  });
  return bytesToHex(key);
}

export async function createRecord(
  kind: LockKind,
  secret: string,
  saltBytes: Uint8Array,
  biometric: boolean,
  iterations = LOCK_ITERATIONS,
): Promise<LockRecord> {
  const salt = bytesToHex(saltBytes);
  const hash = await deriveHash(secret, salt, iterations);
  return { v: 1, kind, salt, hash, iterations, biometric, ...(kind === 'pin' ? { pinLength: secret.length } : {}) };
}

/** Constant-time string comparison of two hex digests. */
export function safeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i += 1) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

export async function verifySecret(record: LockRecord, secret: string): Promise<boolean> {
  const hash = await deriveHash(secret, record.salt, record.iterations);
  return safeEqual(hash, record.hash);
}

export function parseRecord(raw: string | null): LockRecord | null {
  if (!raw) return null;
  try {
    const r = JSON.parse(raw) as Partial<LockRecord>;
    if (
      r.v === 1 &&
      (r.kind === 'pin' || r.kind === 'password') &&
      typeof r.salt === 'string' &&
      typeof r.hash === 'string' &&
      typeof r.iterations === 'number'
    ) {
      return {
        v: 1,
        kind: r.kind,
        salt: r.salt,
        hash: r.hash,
        iterations: r.iterations,
        biometric: !!r.biometric,
        ...(typeof r.pinLength === 'number' ? { pinLength: r.pinLength } : {}),
      };
    }
  } catch {
    // fall through
  }
  return null;
}

/** Wrong-attempt throttling: free tries, then a growing cooldown (30s, 60s, 120s ... max 15 min). */
export const FREE_ATTEMPTS = 5;
export function cooldownMs(failedAttempts: number): number {
  if (failedAttempts < FREE_ATTEMPTS) return 0;
  const steps = failedAttempts - FREE_ATTEMPTS;
  return Math.min(30_000 * 2 ** steps, 15 * 60_000);
}
