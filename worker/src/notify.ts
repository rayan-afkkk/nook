/**
 * Notification wording. This is the ONLY text that goes into a push: who it's from and what kind
 * of thing it is. Message content never reaches the Worker (Firestore reads use field masks).
 */
const KIND: Record<string, string> = {
  text: 'a message',
  image: 'a photo',
  voice: 'a voice note',
  gif: 'a GIF',
  sticker: 'a sticker',
  file: 'a file',
};

export function cleanName(name: unknown, fallback: string): string {
  const s = typeof name === 'string' ? name.replace(/[\r\n\t]+/g, ' ').trim() : '';
  return (s || fallback).slice(0, 40);
}

export function notificationText(input: { chatType: 'direct' | 'group'; groupName?: unknown; senderName?: unknown; kind?: unknown }): { title: string; body: string } {
  if (input.chatType === 'group') {
    return { title: 'NOOK', body: `New message in ${cleanName(input.groupName, 'your group')}` };
  }
  const what = KIND[typeof input.kind === 'string' ? input.kind : 'text'] ?? 'a message';
  return { title: 'NOOK', body: `${cleanName(input.senderName, 'Someone')} sent you ${what}` };
}
