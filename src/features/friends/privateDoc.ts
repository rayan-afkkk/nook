import { arrayRemove, arrayUnion, deleteDoc, doc, onSnapshot, serverTimestamp, setDoc } from '@react-native-firebase/firestore';
import { create } from 'zustand';

import { db, metrics } from '@/lib/firebase';

/**
 * userPrivate/{uid}: only its owner (and the Worker, via its service account) can read it.
 * Holds this account's FCM device tokens and the people it has blocked.
 */
type State = { blocked: string[]; loaded: boolean };
export const usePrivate = create<State>()(() => ({ blocked: [], loaded: false }));

let stop: (() => void) | null = null;

const ref = (uid: string) => doc(db(), 'userPrivate', uid);

export function startPrivate(uid: string) {
  stop?.();
  stop = onSnapshot(ref(uid), (snap) => {
    if (!snap.metadata.fromCache) metrics.read(1, 'private doc');
    const data = (snap.data() ?? {}) as { blocked?: string[] };
    usePrivate.setState({ blocked: data.blocked ?? [], loaded: true });
  }, () => usePrivate.setState({ loaded: true }));
}

export function stopPrivate() {
  stop?.();
  stop = null;
  usePrivate.setState({ blocked: [], loaded: false });
}

export async function setBlocked(me: string, other: string, blocked: boolean) {
  await setDoc(
    ref(me),
    { blocked: blocked ? arrayUnion(other) : arrayRemove(other), updatedAt: serverTimestamp() },
    { merge: true },
  );
  metrics.write(1, blocked ? 'block' : 'unblock');
}

export async function addPushToken(me: string, token: string) {
  await setDoc(ref(me), { tokens: arrayUnion(token), updatedAt: serverTimestamp() }, { merge: true });
  metrics.write(1, 'push token');
}

export async function removePushToken(me: string, token: string) {
  await setDoc(ref(me), { tokens: arrayRemove(token), updatedAt: serverTimestamp() }, { merge: true });
  metrics.write(1, 'remove push token');
}

export async function deletePrivateDoc(me: string) {
  await deleteDoc(ref(me));
  metrics.delete(1, 'private doc');
}

export function useIsBlocked(uid: string | undefined): boolean {
  return usePrivate((s) => (uid ? s.blocked.includes(uid) : false));
}
