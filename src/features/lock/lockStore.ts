import * as Crypto from 'expo-crypto';
import * as LocalAuthentication from 'expo-local-authentication';
import * as SecureStore from 'expo-secure-store';
import { create } from 'zustand';

import {
  cooldownMs,
  createRecord,
  parseRecord,
  verifySecret,
  type LockKind,
  type LockRecord,
} from './lockCrypto';

const RECORD_KEY = 'nook.lock.v1';
const ATTEMPTS_KEY = 'nook.lock.attempts.v1';

type Attempts = { failed: number; until: number };

type LockState = {
  /** null while reading SecureStore on start-up. */
  record: LockRecord | null | undefined;
  locked: boolean;
  attempts: Attempts;
  load: () => Promise<void>;
  setLock: (kind: LockKind, secret: string, biometric: boolean) => Promise<void>;
  setBiometric: (enabled: boolean) => Promise<void>;
  /** Returns true on success. Throws nothing; wrong secrets are counted. */
  unlockWithSecret: (secret: string) => Promise<boolean>;
  unlockWithBiometrics: () => Promise<boolean>;
  verify: (secret: string) => Promise<boolean>;
  lock: () => void;
  /** Used by "Forgot password" and sign-out: removes the local lock entirely. */
  clear: () => Promise<void>;
};

const storeOptions: SecureStore.SecureStoreOptions = {
  keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
};

async function saveAttempts(a: Attempts) {
  await SecureStore.setItemAsync(ATTEMPTS_KEY, JSON.stringify(a), storeOptions);
}

export const useLock = create<LockState>()((set, get) => ({
  record: undefined,
  locked: false,
  attempts: { failed: 0, until: 0 },

  load: async () => {
    const [raw, rawAttempts] = await Promise.all([
      SecureStore.getItemAsync(RECORD_KEY, storeOptions).catch(() => null),
      SecureStore.getItemAsync(ATTEMPTS_KEY, storeOptions).catch(() => null),
    ]);
    const record = parseRecord(raw);
    let attempts: Attempts = { failed: 0, until: 0 };
    try {
      if (rawAttempts) attempts = { ...attempts, ...(JSON.parse(rawAttempts) as Attempts) };
    } catch {
      // ignore corrupt counter
    }
    // Cold start: always locked when a lock exists.
    set({ record, attempts, locked: record !== null });
  },

  setLock: async (kind, secret, biometric) => {
    const record = await createRecord(kind, secret, Crypto.getRandomBytes(16), biometric);
    await SecureStore.setItemAsync(RECORD_KEY, JSON.stringify(record), storeOptions);
    await saveAttempts({ failed: 0, until: 0 });
    set({ record, locked: false, attempts: { failed: 0, until: 0 } });
  },

  setBiometric: async (enabled) => {
    const current = get().record;
    if (!current) return;
    const record = { ...current, biometric: enabled };
    await SecureStore.setItemAsync(RECORD_KEY, JSON.stringify(record), storeOptions);
    set({ record });
  },

  verify: async (secret) => {
    const record = get().record;
    if (!record) return false;
    return verifySecret(record, secret);
  },

  unlockWithSecret: async (secret) => {
    const { record, attempts } = get();
    if (!record) return true;
    if (Date.now() < attempts.until) return false;
    const ok = await verifySecret(record, secret);
    if (ok) {
      const reset = { failed: 0, until: 0 };
      set({ locked: false, attempts: reset });
      await saveAttempts(reset);
      return true;
    }
    const failed = attempts.failed + 1;
    const wait = cooldownMs(failed);
    const next = { failed, until: wait ? Date.now() + wait : 0 };
    set({ attempts: next });
    await saveAttempts(next);
    return false;
  },

  unlockWithBiometrics: async () => {
    const record = get().record;
    if (!record?.biometric) return false;
    const [hasHardware, enrolled] = await Promise.all([
      LocalAuthentication.hasHardwareAsync(),
      LocalAuthentication.isEnrolledAsync(),
    ]);
    if (!hasHardware || !enrolled) return false;
    const result = await LocalAuthentication.authenticateAsync({
      promptMessage: 'Unlock NOOK',
      cancelLabel: 'Use password',
      disableDeviceFallback: true,
      biometricsSecurityLevel: 'strong',
    });
    if (result.success) {
      const reset = { failed: 0, until: 0 };
      set({ locked: false, attempts: reset });
      await saveAttempts(reset);
    }
    return result.success;
  },

  lock: () => {
    if (get().record) set({ locked: true });
  },

  clear: async () => {
    await Promise.all([
      SecureStore.deleteItemAsync(RECORD_KEY, storeOptions).catch(() => undefined),
      SecureStore.deleteItemAsync(ATTEMPTS_KEY, storeOptions).catch(() => undefined),
    ]);
    set({ record: null, locked: false, attempts: { failed: 0, until: 0 } });
  },
}));

export async function biometricAvailability(): Promise<{ available: boolean; label: string }> {
  try {
    const [hasHardware, enrolled, types] = await Promise.all([
      LocalAuthentication.hasHardwareAsync(),
      LocalAuthentication.isEnrolledAsync(),
      LocalAuthentication.supportedAuthenticationTypesAsync(),
    ]);
    const face = types.includes(LocalAuthentication.AuthenticationType.FACIAL_RECOGNITION);
    const finger = types.includes(LocalAuthentication.AuthenticationType.FINGERPRINT);
    return {
      available: hasHardware && enrolled,
      label: finger ? 'Fingerprint' : face ? 'Face unlock' : 'Biometrics',
    };
  } catch {
    return { available: false, label: 'Biometrics' };
  }
}
