import { doc, getDoc, getDocFromCache } from '@react-native-firebase/firestore';
import { useEffect } from 'react';
import { create } from 'zustand';

import { db, metrics } from '@/lib/firebase';

import type { Profile } from './types';

type State = { byId: Record<string, Profile | null | undefined> };

/**
 * Other people's profiles, fetched once per app session (cache first, then one server read)
 * so lists of chats don't re-read profiles on every render or reopen.
 */
export const useProfiles = create<State>()(() => ({ byId: {} }));

const inflight = new Set<string>();
const refreshed = new Set<string>();

async function load(uid: string) {
  if (inflight.has(uid) || refreshed.has(uid)) return;
  inflight.add(uid);
  const ref = doc(db(), 'users', uid);
  try {
    try {
      const cached = await getDocFromCache(ref);
      if (cached.exists()) useProfiles.setState((s) => ({ byId: { ...s.byId, [uid]: cached.data() as Profile } }));
    } catch {
      // not cached yet
    }
    const fresh = await getDoc(ref);
    if (!fresh.metadata.fromCache) metrics.read(1, 'profile');
    refreshed.add(uid);
    useProfiles.setState((s) => ({ byId: { ...s.byId, [uid]: fresh.exists() ? (fresh.data() as Profile) : null } }));
  } catch {
    // offline: keep whatever the cache gave us
  } finally {
    inflight.delete(uid);
  }
}

export function ensureProfiles(uids: string[]) {
  for (const uid of uids) void load(uid);
}

export function useProfile(uid: string | undefined): Profile | null | undefined {
  const profile = useProfiles((s) => (uid ? s.byId[uid] : undefined));
  useEffect(() => {
    if (uid) void load(uid);
  }, [uid]);
  return profile;
}

export function displayNameOf(p: Profile | null | undefined, fallback = 'Someone'): string {
  return p?.displayName ?? (p === null ? 'Deleted account' : fallback);
}

export function resetProfiles() {
  refreshed.clear();
  useProfiles.setState({ byId: {} });
}
