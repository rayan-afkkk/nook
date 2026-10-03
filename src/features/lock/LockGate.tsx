import { useEffect, useRef } from 'react';
import { AppState } from 'react-native';

import { LOCK_BACKGROUND_TIMEOUT_MS } from '@/constants/app';

import { LockScreen } from './LockScreen';
import { useLock } from './lockStore';

/** Locks on cold start (see lockStore.load) and after 30s in the background. */
export function LockGate() {
  const locked = useLock((s) => s.locked);
  const backgroundedAt = useRef<number | null>(null);

  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => {
      if (state === 'background') {
        backgroundedAt.current = Date.now();
      } else if (state === 'active') {
        const since = backgroundedAt.current;
        backgroundedAt.current = null;
        if (since !== null && Date.now() - since >= LOCK_BACKGROUND_TIMEOUT_MS) {
          useLock.getState().lock();
        }
      }
    });
    return () => sub.remove();
  }, []);

  return locked ? <LockScreen /> : null;
}
