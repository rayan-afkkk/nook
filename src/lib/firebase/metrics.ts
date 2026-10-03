import { create } from 'zustand';

/**
 * Development-only counter of Firestore operations, shown on the Debug screen.
 * Counts are estimates of what Firebase bills: documents delivered from the server
 * (not from the offline cache) count as reads.
 */
type MetricsState = {
  reads: number;
  writes: number;
  deletes: number;
  since: number;
  log: { at: number; kind: 'read' | 'write' | 'delete'; count: number; label: string }[];
  reset: () => void;
};

export const useFirestoreMetrics = create<MetricsState>()((set) => ({
  reads: 0,
  writes: 0,
  deletes: 0,
  since: Date.now(),
  log: [],
  reset: () => set({ reads: 0, writes: 0, deletes: 0, since: Date.now(), log: [] }),
}));

function bump(kind: 'read' | 'write' | 'delete', count: number, label: string) {
  if (!__DEV__ || count <= 0) return;
  useFirestoreMetrics.setState((s) => ({
    reads: s.reads + (kind === 'read' ? count : 0),
    writes: s.writes + (kind === 'write' ? count : 0),
    deletes: s.deletes + (kind === 'delete' ? count : 0),
    log: [{ at: Date.now(), kind, count, label }, ...s.log].slice(0, 100),
  }));
}

export const metrics = {
  read: (count: number, label: string) => bump('read', count, label),
  write: (count: number, label: string) => bump('write', count, label),
  delete: (count: number, label: string) => bump('delete', count, label),
};
