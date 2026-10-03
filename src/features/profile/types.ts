import type { Timestamp } from '@react-native-firebase/firestore';

/** users/{uid} — public profile, readable by any signed-in user. */
export type Profile = {
  uid: string;
  username: string;
  displayName: string;
  photoURL: string | null;
  createdAt: Timestamp | null;
};
