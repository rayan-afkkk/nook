/**
 * Pure chat helpers (no Firebase, no React) so they can be unit tested.
 * Timestamps are handled as milliseconds.
 */
import type { Disappearing, LastMessage, MessageKind } from './types';

export const DISAPPEARING_MS: Record<Disappearing, number> = {
  off: 0,
  '24h': 24 * 60 * 60 * 1000,
  '7d': 7 * 24 * 60 * 60 * 1000,
};

export const DISAPPEARING_LABEL: Record<Disappearing, string> = { off: 'Off', '24h': '24h', '7d': '7d' };

/** Deterministic id so two people can never end up with two DMs. */
export function directChatId(a: string, b: string): string {
  const [x, y] = [a, b].sort();
  return `dm_${x}_${y}`;
}

export function otherMember(members: string[], me: string): string | undefined {
  return members.find((m) => m !== me);
}

const KIND_WORD: Record<Exclude<MessageKind, 'text' | 'system'>, string> = {
  image: 'Photo',
  file: 'File',
  voice: 'Voice note',
  gif: 'GIF',
  sticker: 'Sticker',
};

export function previewFor(kind: MessageKind, text: string | undefined, fileName?: string): string {
  if (kind === 'text' || kind === 'system') return (text ?? '').replace(/\s+/g, ' ').trim().slice(0, 120);
  if (kind === 'file' && fileName) return fileName.slice(0, 80);
  return KIND_WORD[kind];
}

export function lastMessageLine(last: LastMessage | null | undefined, mine: boolean): string {
  if (!last) return 'Say hi 👋';
  if (last.kind === 'deleted') return 'Message deleted';
  return `${mine ? 'You: ' : ''}${last.preview}`;
}

export function isUnread(lastMessageAtMs: number | null, lastReadMs: number | null, lastSenderIsMe: boolean): boolean {
  if (!lastMessageAtMs || lastSenderIsMe) return false;
  return !lastReadMs || lastMessageAtMs > lastReadMs;
}

export function isExpired(expireAtMs: number | null | undefined, nowMs: number): boolean {
  return !!expireAtMs && expireAtMs <= nowMs;
}

/** "Seen" shows under my last message when every other member has read past it. */
export function seenByAll(myLastMs: number | null, members: string[], me: string, lastRead: Record<string, number>): boolean {
  if (!myLastMs) return false;
  const others = members.filter((m) => m !== me);
  return others.length > 0 && others.every((m) => (lastRead[m] ?? 0) >= myLastMs);
}

export function sameDay(a: number, b: number): boolean {
  const x = new Date(a);
  const y = new Date(b);
  return x.getFullYear() === y.getFullYear() && x.getMonth() === y.getMonth() && x.getDate() === y.getDate();
}

export function dayLabel(ms: number, nowMs: number): string {
  if (sameDay(ms, nowMs)) return 'Today';
  if (sameDay(ms, nowMs - 86_400_000)) return 'Yesterday';
  const d = new Date(ms);
  const sameYear = d.getFullYear() === new Date(nowMs).getFullYear();
  return d.toLocaleDateString(undefined, { weekday: sameYear ? 'short' : undefined, day: 'numeric', month: 'short', year: sameYear ? undefined : 'numeric' });
}

export function timeLabel(ms: number, nowMs: number): string {
  const d = new Date(ms);
  if (sameDay(ms, nowMs)) return d.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });
  if (nowMs - ms < 6 * 86_400_000) return d.toLocaleDateString(undefined, { weekday: 'short' });
  return d.toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
}

export function durationLabel(ms: number): string {
  const total = Math.max(0, Math.round(ms / 1000));
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${m}:${s.toString().padStart(2, '0')}`;
}

/** Reaction pills: [{emoji, count, mine}] sorted by count. */
export function groupReactions(reactions: Record<string, string> | undefined, me: string) {
  const map = new Map<string, { emoji: string; count: number; mine: boolean }>();
  for (const [uid, emoji] of Object.entries(reactions ?? {})) {
    const entry = map.get(emoji) ?? { emoji, count: 0, mine: false };
    entry.count += 1;
    entry.mine ||= uid === me;
    map.set(emoji, entry);
  }
  return [...map.values()].sort((a, b) => b.count - a.count);
}

/** Resamples raw metering values (dBFS, -160..0) to `n` bars in 0..1. */
export function toWaveform(dbValues: number[], n = 40): number[] {
  if (dbValues.length === 0) return Array.from({ length: n }, () => 0.15);
  const norm = dbValues.map((db) => Math.min(1, Math.max(0.06, (db + 55) / 55)));
  const out: number[] = [];
  for (let i = 0; i < n; i += 1) {
    const start = Math.floor((i * norm.length) / n);
    const end = Math.max(start + 1, Math.floor(((i + 1) * norm.length) / n));
    const slice = norm.slice(start, end);
    out.push(Number((slice.reduce((a, b) => a + b, 0) / slice.length).toFixed(2)));
  }
  return out;
}
