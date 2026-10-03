import { onAuthStateChanged, type User } from '@react-native-firebase/auth';
import { doc, onSnapshot } from '@react-native-firebase/firestore';
import { create } from 'zustand';

import { auth, db, metrics } from '@/lib/firebase';
import type { Profile } from '@/features/profile/types';

export type AuthStatus = 'loading' | 'signedOut' | 'signedIn';

type SessionState = {
  status: AuthStatus;
  user: Pick<User, 'uid' | 'displayName' | 'email' | 'photoURL'> | null;
  /**
   * undefined = not known yet (loading or offline with an empty cache),
   * null = signed in but no username claimed yet.
   */
  profile: Profile | null | undefined;
  profileError: string | null;
};

export const useSession = create<SessionState>()(() => ({
  status: 'loading',
  user: null,
  profile: undefined,
  profileError: null,
}));

let stopProfile: (() => void) | null = null;
let started = false;

/**
 * Starts the one auth listener and the one profile listener for the whole app.
 * Called once from the root layout.
 */
export function startSession(): () => void {
  if (started) return () => undefined;
  started = true;
  const stopAuth = onAuthStateChanged(auth(), (user) => {
    stopProfile?.();
    stopProfile = null;
    if (!user) {
      useSession.setState({ status: 'signedOut', user: null, profile: undefined, profileError: null });
      return;
    }
    useSession.setState({
      status: 'signedIn',
      user: { uid: user.uid, displayName: user.displayName, email: user.email, photoURL: user.photoURL },
      profile: undefined,
      profileError: null,
    });
    stopProfile = onSnapshot(
      doc(db(), 'users', user.uid),
      { includeMetadataChanges: true },
      (snap) => {
        if (!snap.metadata.fromCache) metrics.read(1, 'profile');
        if (snap.exists()) {
          useSession.setState({ profile: snap.data() as Profile, profileError: null });
        } else if (!snap.metadata.fromCache) {
          // Only trust "no profile" when the server said so; an empty offline cache is not proof.
          useSession.setState({ profile: null, profileError: null });
        }
      },
      (error) => {
        useSession.setState({ profileError: error.message });
      },
    );
  });
  return () => {
    stopAuth();
    stopProfile?.();
    started = false;
  };
}
