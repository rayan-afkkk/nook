import {
  collection,
  doc,
  limit,
  onSnapshot,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
} from '@react-native-firebase/firestore';
import { useFocusEffect } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';

import { db, metrics } from '@/lib/firebase';
import { callWorker, callWorkerInBackground } from '@/lib/worker';

import type { Call, CallStatus } from './types';

const callRef = (id: string) => doc(db(), 'calls', id);

/** Creates the call record and asks the Worker to ring the other person (high-priority push). */
export async function startCall(chatId: string, me: string, other: string, video: boolean): Promise<string> {
  const ref = doc(collection(db(), 'calls'));
  await setDoc(ref, {
    chatId,
    members: [me, other].sort(),
    callerId: me,
    calleeId: other,
    video,
    status: 'ringing',
    startedAt: serverTimestamp(),
  });
  metrics.write(1, 'start call');
  callWorkerInBackground('/call/invite', { callId: ref.id });
  return ref.id;
}

export async function setCallStatus(callId: string, status: CallStatus) {
  const patch: Record<string, unknown> = { status };
  if (status === 'answered') patch.answeredAt = serverTimestamp();
  if (status === 'ended' || status === 'declined' || status === 'missed' || status === 'cancelled') patch.endedAt = serverTimestamp();
  await updateDoc(callRef(callId), patch);
  metrics.write(1, `call ${status}`);
  // Stop the other phone ringing.
  if (status === 'cancelled' || status === 'missed' || status === 'declined') {
    callWorkerInBackground('/call/cancel', { callId });
  }
}

export async function fetchCallToken(callId: string): Promise<{ token: string; url: string }> {
  return callWorker<{ token: string; url: string }>('/call/token', { callId });
}

export function useCall(callId: string | undefined) {
  const [call, setCall] = useState<Call | null | undefined>(undefined);
  useEffect(() => {
    if (!callId) return;
    return onSnapshot(
      callRef(callId),
      (snap) => {
        if (!snap.metadata.fromCache) metrics.read(1, 'call');
        setCall(snap.exists() ? { id: snap.id, ...(snap.data() as Omit<Call, 'id'>) } : null);
      },
      () => setCall(null),
    );
  }, [callId]);
  return call;
}

/** Call history, listened to only while the Calls tab is focused. */
export function useCallHistory(me: string) {
  const [calls, setCalls] = useState<Call[]>([]);
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  useFocusEffect(
    useCallback(() => {
      const q = query(collection(db(), 'calls'), where('members', 'array-contains', me), orderBy('startedAt', 'desc'), limit(50));
      return onSnapshot(
        q,
        (snap) => {
          if (!snap.metadata.fromCache) metrics.read(Math.max(1, snap.docChanges().length), 'call history');
          setCalls(snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<Call, 'id'>) })));
          setStatus('ready');
        },
        () => setStatus('error'),
      );
    }, [me]),
  );
  return { calls, status };
}
