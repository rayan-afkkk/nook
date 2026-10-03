import { getApp } from '@react-native-firebase/app';
import { getAuth } from '@react-native-firebase/auth';
import { initializeFirestore, type Firestore } from '@react-native-firebase/firestore';

let firestore: Firestore | null = null;

/**
 * The single Firestore instance. Offline persistence is ON so reopening a screen
 * is served from the on-device cache instead of re-reading from the server
 * (this is what keeps us inside the Spark plan's 50k reads/day).
 */
export function db(): Firestore {
  if (!firestore) {
    firestore = initializeFirestore(getApp(), {
      persistence: true,
      cacheSizeBytes: 100 * 1024 * 1024,
      ignoreUndefinedProperties: true,
      serverTimestampBehavior: 'estimate',
    });
  }
  return firestore;
}

export const auth = () => getAuth();

export { metrics, useFirestoreMetrics } from './metrics';
