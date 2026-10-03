import {
  directChatId,
  durationLabel,
  groupReactions,
  isExpired,
  isUnread,
  lastMessageLine,
  previewFor,
  seenByAll,
  toWaveform,
} from '@/features/chat/model';

describe('chat model', () => {
  it('DM ids are the same whoever starts the chat', () => {
    expect(directChatId('b', 'a')).toBe('dm_a_b');
    expect(directChatId('a', 'b')).toBe('dm_a_b');
  });

  it('previews never leak more than a short line', () => {
    expect(previewFor('text', '  hello\n\nworld  ')).toBe('hello world');
    expect(previewFor('text', 'x'.repeat(500))).toHaveLength(120);
    expect(previewFor('image', undefined)).toBe('Photo');
    expect(previewFor('voice', undefined)).toBe('Voice note');
    expect(previewFor('file', undefined, 'notes.pdf')).toBe('notes.pdf');
  });

  it('last message line', () => {
    expect(lastMessageLine({ senderId: 'a', kind: 'text', preview: 'hi' }, true)).toBe('You: hi');
    expect(lastMessageLine({ senderId: 'a', kind: 'deleted', preview: '' }, false)).toBe('Message deleted');
    expect(lastMessageLine(null, false)).toBe('Say hi 👋');
  });

  it('unread = newer than my lastRead and not sent by me', () => {
    expect(isUnread(200, 100, false)).toBe(true);
    expect(isUnread(100, 200, false)).toBe(false);
    expect(isUnread(200, null, false)).toBe(true);
    expect(isUnread(200, 100, true)).toBe(false);
    expect(isUnread(null, null, false)).toBe(false);
  });

  it('expiry', () => {
    expect(isExpired(100, 200)).toBe(true);
    expect(isExpired(300, 200)).toBe(false);
    expect(isExpired(null, 200)).toBe(false);
  });

  it('seen by all other members', () => {
    expect(seenByAll(100, ['me', 'a', 'b'], 'me', { a: 150, b: 100 })).toBe(true);
    expect(seenByAll(100, ['me', 'a', 'b'], 'me', { a: 150 })).toBe(false);
    expect(seenByAll(null, ['me', 'a'], 'me', { a: 150 })).toBe(false);
  });

  it('groups reactions and marks mine', () => {
    expect(groupReactions({ a: '❤️', b: '❤️', me: '😂' }, 'me')).toEqual([
      { emoji: '❤️', count: 2, mine: false },
      { emoji: '😂', count: 1, mine: true },
    ]);
  });

  it('formats durations and builds waveforms', () => {
    expect(durationLabel(65_000)).toBe('1:05');
    const w = toWaveform([-160, -40, -10, 0, -30], 5);
    expect(w).toHaveLength(5);
    w.forEach((v) => expect(v).toBeGreaterThanOrEqual(0.06));
    expect(toWaveform([], 3)).toEqual([0.15, 0.15, 0.15]);
  });
});
