import { collection, getDocs, limit, onSnapshot, orderBy, query, startAfter } from '@react-native-firebase/firestore';
import { useFocusEffect } from 'expo-router';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { PAGE_SIZE } from '@/constants/app';
import { db, metrics } from '@/lib/firebase';

import { isExpired } from './model';
import type { Message } from './types';

type Snap = { id: string; data: () => unknown; metadata: { hasPendingWrites: boolean } };

function toMessage(d: Snap): Message {
  return { id: d.id, ...(d.data() as Omit<Message, 'id'>), pending: d.metadata.hasPendingWrites };
}

/**
 * Latest 30 messages live (listener attached only while the screen is focused), older pages
 * fetched on demand. Expired disappearing messages are hidden immediately on the client.
 */
export function useMessages(chatId: string) {
  const [live, setLive] = useState<Message[]>([]);
  const [older, setOlder] = useState<Message[]>([]);
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  const [error, setError] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  /** Last doc of the live page and of the most recent older page; pagination continues from the oldest. */
  const liveLast = useRef<unknown>(null);
  const olderLast = useRef<unknown>(null);
  const [now, setNow] = useState(() => Date.now());

  useFocusEffect(
    useCallback(() => {
      const q = query(collection(db(), 'chats', chatId, 'messages'), orderBy('createdAt', 'desc'), limit(PAGE_SIZE));
      const unsub = onSnapshot(
        q,
        { includeMetadataChanges: true },
        (snap) => {
          if (!snap.metadata.fromCache) {
            const changed = snap.docChanges().filter((c) => c.type !== 'removed').length;
            if (changed) metrics.read(changed, 'messages');
          }
          const docs = snap.docs as unknown as Snap[];
          setLive(docs.map(toMessage));
          liveLast.current = snap.docs[snap.docs.length - 1] ?? null;
          if (snap.docs.length < PAGE_SIZE && !olderLast.current) setHasMore(false);
          setStatus('ready');
          setError(null);
        },
        (e) => {
          setStatus('error');
          setError(e.message);
        },
      );
      return unsub;
    }, [chatId]),
  );

  const loadMore = useCallback(async () => {
    const from = olderLast.current ?? liveLast.current;
    if (loadingMore || !hasMore || !from) return;
    setLoadingMore(true);
    try {
      const q = query(
        collection(db(), 'chats', chatId, 'messages'),
        orderBy('createdAt', 'desc'),
        startAfter(from),
        limit(PAGE_SIZE),
      );
      const snap = await getDocs(q);
      if (!snap.metadata.fromCache) metrics.read(Math.max(1, snap.size), 'older messages');
      olderLast.current = snap.docs[snap.docs.length - 1] ?? olderLast.current;
      setOlder((prev) => [...prev, ...(snap.docs as unknown as Snap[]).map(toMessage)]);
      if (snap.size < PAGE_SIZE) setHasMore(false);
    } finally {
      setLoadingMore(false);
    }
  }, [chatId, hasMore, loadingMore]);

  // Re-render when the next disappearing message is due, so it vanishes on time.
  const all = useMemo(() => {
    const seen = new Set<string>();
    return [...live, ...older].filter((m) => (seen.has(m.id) ? false : (seen.add(m.id), true)));
  }, [live, older]);
  const nextExpiry = useMemo(() => {
    const times = all.map((m) => m.expireAt?.toMillis() ?? Infinity).filter((t) => t > now);
    return times.length ? Math.min(...times) : null;
  }, [all, now]);
  useEffect(() => {
    if (!nextExpiry) return;
    const t = setTimeout(() => setNow(Date.now()), Math.max(0, nextExpiry - Date.now()) + 50);
    return () => clearTimeout(t);
  }, [nextExpiry]);

  const messages = useMemo(() => all.filter((m) => !isExpired(m.expireAt?.toMillis(), now)), [all, now]);

  return { messages, status, error, hasMore, loadingMore, loadMore };
}
