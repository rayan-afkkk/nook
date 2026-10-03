import {
  getDatabase,
  goOffline,
  goOnline,
  onDisconnect,
  onValue,
  ref,
  remove,
  serverTimestamp,
  set,
} from '@react-native-firebase/database';
import { useEffect, useState } from 'react';
import { AppState } from 'react-native';

/**
 * Presence and typing live in the Realtime Database (free, no Firestore quota):
 *   status/{uid}            { state: 'online' | 'offline', lastChanged }
 *   typing/{chatId}/{uid}   server timestamp while typing
 * onDisconnect() marks people offline even if the app is killed. The connection is closed while
 * NOOK is in the background, which keeps us far below Spark's 100 simultaneous connections.
 */

let cleanup: (() => void) | null = null;

export function startPresence(uid: string) {
  cleanup?.();
  const database = getDatabase();
  const statusRef = ref(database, `status/${uid}`);
  const offline = { state: 'offline', lastChanged: serverTimestamp() };
  const online = { state: 'online', lastChanged: serverTimestamp() };

  const unsubConnected = onValue(ref(database, '.info/connected'), (snap) => {
    if (snap.val() !== true) return;
    onDisconnect(statusRef)
      .set(offline)
      .then(() => set(statusRef, online))
      .catch(() => undefined);
  });

  const appSub = AppState.addEventListener('change', (state) => {
    if (state === 'active') {
      goOnline(database);
    } else if (state === 'background') {
      set(statusRef, offline)
        .catch(() => undefined)
        .finally(() => goOffline(database));
    }
  });

  cleanup = () => {
    unsubConnected();
    appSub.remove();
  };
}

export async function stopPresence(uid: string) {
  cleanup?.();
  cleanup = null;
  try {
    await set(ref(getDatabase(), `status/${uid}`), { state: 'offline', lastChanged: serverTimestamp() });
  } catch {
    // signed out already
  }
}

export type Presence = { online: boolean; lastChanged: number | null };

/** Live online status for one person (RTDB listener, detached on unmount). */
export function usePresence(uid: string | undefined): Presence {
  const [presence, setPresence] = useState<Presence>({ online: false, lastChanged: null });
  useEffect(() => {
    if (!uid) return;
    const unsub = onValue(
      ref(getDatabase(), `status/${uid}`),
      (snap) => {
        const v = snap.val() as { state?: string; lastChanged?: number } | null;
        setPresence({ online: v?.state === 'online', lastChanged: v?.lastChanged ?? null });
      },
      () => setPresence({ online: false, lastChanged: null }),
    );
    return unsub;
  }, [uid]);
  return presence;
}

/* ---------------- typing ---------------- */

const TYPING_TTL_MS = 6000;
const TYPING_THROTTLE_MS = 3000;
let lastTypingWrite = 0;
let typingChat: string | null = null;

/** Throttled: at most one write every 3 seconds while typing. */
export function setTyping(chatId: string, uid: string, typing: boolean) {
  const r = ref(getDatabase(), `typing/${chatId}/${uid}`);
  if (!typing) {
    if (typingChat === chatId) {
      typingChat = null;
      lastTypingWrite = 0;
      remove(r).catch(() => undefined);
    }
    return;
  }
  const now = Date.now();
  if (typingChat === chatId && now - lastTypingWrite < TYPING_THROTTLE_MS) return;
  lastTypingWrite = now;
  typingChat = chatId;
  onDisconnect(r).remove().catch(() => undefined);
  set(r, serverTimestamp()).catch(() => undefined);
}

/** uids currently typing in a chat (excluding me). */
export function useTyping(chatId: string, me: string): string[] {
  const [typing, setTypingUids] = useState<string[]>([]);
  useEffect(() => {
    let entries: Record<string, number> = {};
    const compute = () => {
      const now = Date.now();
      const next = Object.entries(entries)
        .filter(([uid, at]) => uid !== me && now - at < TYPING_TTL_MS)
        .map(([uid]) => uid)
        .sort();
      setTypingUids((prev) => (prev.join() === next.join() ? prev : next));
    };
    const unsub = onValue(
      ref(getDatabase(), `typing/${chatId}`),
      (snap) => {
        entries = (snap.val() as Record<string, number> | null) ?? {};
        compute();
      },
      () => {
        entries = {};
        compute();
      },
    );
    // Expire stale entries even when no new event arrives.
    const timer = setInterval(compute, 2000);
    return () => {
      unsub();
      clearInterval(timer);
    };
  }, [chatId, me]);
  return typing;
}
