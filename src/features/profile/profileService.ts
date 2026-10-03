import { doc, getDoc, runTransaction, serverTimestamp } from '@react-native-firebase/firestore';

import { db, metrics } from '@/lib/firebase';
import { UserFacingError } from '@/lib/errors';

import type { Profile } from './types';
import { normalizeUsername, validateUsername } from './username';

/** One document read. Callers debounce so typing doesn't burn the read quota. */
export async function isUsernameAvailable(raw: string): Promise<boolean> {
  const name = normalizeUsername(raw);
  const snap = await getDoc(doc(db(), 'usernames', name));
  metrics.read(1, 'username check');
  return !snap.exists();
}

/**
 * Atomically reserves usernames/{name} and creates users/{uid}.
 * Security rules make the pair consistent and the username permanent.
 */
export async function claimUsername(
  uid: string,
  raw: string,
  displayName: string,
  photoURL: string | null,
): Promise<void> {
  const username = normalizeUsername(raw);
  const invalid = validateUsername(username);
  if (invalid) throw new UserFacingError(invalid);
  const nameRef = doc(db(), 'usernames', username);
  const userRef = doc(db(), 'users', uid);

  await runTransaction(db(), async (tx) => {
    const [nameSnap, userSnap] = await Promise.all([tx.get(nameRef), tx.get(userRef)]);
    metrics.read(2, 'claim username');
    if (userSnap.exists()) throw new UserFacingError('You already picked a username.');
    if (nameSnap.exists()) throw new UserFacingError('That username was just taken. Try another.');
    tx.set(nameRef, { uid, createdAt: serverTimestamp() });
    tx.set(userRef, {
      uid,
      username,
      displayName: displayName.trim().slice(0, 40) || username,
      photoURL,
      createdAt: serverTimestamp(),
    });
  });
  metrics.write(2, 'claim username');
}

/** Exact-username lookup: two document reads, no listing (usernames can't be enumerated). */
export async function findUserByUsername(raw: string): Promise<Profile | null> {
  const name = normalizeUsername(raw);
  if (validateUsername(name)) return null;
  const nameSnap = await getDoc(doc(db(), 'usernames', name));
  metrics.read(1, 'find username');
  if (!nameSnap.exists()) return null;
  const uid = (nameSnap.data() as { uid?: string }).uid;
  if (!uid) return null;
  const userSnap = await getDoc(doc(db(), 'users', uid));
  metrics.read(1, 'find profile');
  return userSnap.exists() ? (userSnap.data() as Profile) : null;
}
