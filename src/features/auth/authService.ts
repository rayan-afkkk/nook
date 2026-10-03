import {
  GoogleAuthProvider,
  deleteUser,
  reauthenticateWithCredential,
  signInWithCredential,
  signOut as firebaseSignOut,
} from '@react-native-firebase/auth';
import { collectionGroup, doc, getDocs, limit, query, where, writeBatch } from '@react-native-firebase/firestore';
import {
  GoogleSignin,
  isCancelledResponse,
  isErrorWithCode,
  statusCodes,
} from '@react-native-google-signin/google-signin';

import { GOOGLE_WEB_CLIENT_ID } from '@/constants/app';
import { resetChatList } from '@/features/chat/chatList';
import { deletePrivateDoc, stopPrivate } from '@/features/friends/privateDoc';
import { useLock } from '@/features/lock/lockStore';
import { stopPresence } from '@/features/presence/presence';
import { resetProfiles } from '@/features/profile/profiles';
import { unregisterPush } from '@/features/push/push';
import { auth, db, metrics } from '@/lib/firebase';
import { callWorker, workerConfigured } from '@/lib/worker';
import { UserFacingError } from '@/lib/errors';
import { usePrefs } from '@/stores/prefs';

let configured = false;
function configure() {
  if (configured) return;
  if (!GOOGLE_WEB_CLIENT_ID) {
    throw new UserFacingError(
      'Google sign-in is not set up yet: EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID is missing from this build.',
    );
  }
  GoogleSignin.configure({ webClientId: GOOGLE_WEB_CLIENT_ID });
  configured = true;
}

/** Shows the Google account picker and returns a Firebase credential, or null if cancelled. */
async function googleCredential() {
  configure();
  try {
    await GoogleSignin.hasPlayServices({ showPlayServicesUpdateDialog: true });
    const response = await GoogleSignin.signIn();
    if (isCancelledResponse(response)) return null;
    const idToken = response.data.idToken;
    if (!idToken) throw new UserFacingError('Google did not return an ID token. Check the Web client ID.');
    return GoogleAuthProvider.credential(idToken);
  } catch (error) {
    if (isErrorWithCode(error)) {
      if (error.code === statusCodes.SIGN_IN_CANCELLED) return null;
      if (error.code === statusCodes.IN_PROGRESS) return null;
      if (error.code === statusCodes.PLAY_SERVICES_NOT_AVAILABLE) {
        throw new UserFacingError('Google Play services is missing or out of date on this phone.');
      }
      if (String(error.code) === '10' || /DEVELOPER_ERROR/i.test(error.message)) {
        throw new UserFacingError(
          "Google sign-in isn't configured for this build (developer error 10). The app's SHA-1 must be added in Firebase.",
        );
      }
    }
    throw error;
  }
}

/** Returns true when signed in, false when the user backed out of the picker. */
export async function signInWithGoogle(): Promise<boolean> {
  const credential = await googleCredential();
  if (!credential) return false;
  await signInWithCredential(auth(), credential);
  return true;
}

/**
 * Signs out of Firebase and Google and clears this device's app lock
 * (this is also the "forgot password" path).
 */
async function teardown(uid: string | undefined) {
  if (uid) {
    await unregisterPush(uid);
    await stopPresence(uid);
  }
  resetChatList();
  stopPrivate();
  resetProfiles();
}

export async function signOut(): Promise<void> {
  await teardown(auth().currentUser?.uid);
  await useLock.getState().clear();
  usePrefs.getState().resetForSignOut();
  try {
    configure();
    await GoogleSignin.signOut();
  } catch {
    // Not signed in with Google on this device; nothing to clear.
  }
  await firebaseSignOut(auth());
}

/**
 * Deletes this account: my messages (and their media), push tokens, profile, username
 * reservation and the Firebase Auth user. Re-authenticates with Google first because
 * Firebase requires a recent sign-in.
 */
export async function deleteAccount(username: string | null): Promise<boolean> {
  const user = auth().currentUser;
  if (!user) return false;
  const credential = await googleCredential();
  if (!credential) return false;
  await reauthenticateWithCredential(user, credential);

  await deleteMyMessages(user.uid);
  await teardown(user.uid);
  await deletePrivateDoc(user.uid).catch(() => undefined);

  const batch = writeBatch(db());
  batch.delete(doc(db(), 'users', user.uid));
  if (username) batch.delete(doc(db(), 'usernames', username));
  await batch.commit();
  metrics.delete(username ? 2 : 1, 'deleteAccount');

  await deleteUser(user);
  await useLock.getState().clear();
  usePrefs.getState().resetForSignOut();
  try {
    await GoogleSignin.signOut();
  } catch {
    // ignore
  }
  return true;
}

/** Deletes every message I sent, in pages. Media goes through the Worker so files are removed too. */
async function deleteMyMessages(uid: string) {
  for (let round = 0; round < 50; round += 1) {
    const snap = await getDocs(query(collectionGroup(db(), 'messages'), where('senderId', '==', uid), limit(200)));
    metrics.read(Math.max(1, snap.size), 'my messages');
    if (snap.empty) return;
    const plain = writeBatch(db());
    let plainCount = 0;
    const media = new Map<string, string[]>();
    snap.docs.forEach((d) => {
      const chatId = d.ref.parent.parent?.id;
      const data = d.data() as { media?: { publicId?: string } };
      if (chatId && data.media?.publicId && workerConfigured()) {
        media.set(chatId, [...(media.get(chatId) ?? []), d.id]);
      } else {
        plain.delete(d.ref);
        plainCount += 1;
      }
    });
    for (const [chatId, ids] of media) {
      await callWorker('/messages/delete', { chatId, messageIds: ids });
    }
    if (plainCount) {
      await plain.commit();
      metrics.delete(plainCount, 'delete my messages');
    }
  }
}
