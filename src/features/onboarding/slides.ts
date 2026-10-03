import { onboardingTints } from '@/theme';

export type SlideId = 'private' | 'voice' | 'calls' | 'lock';

export const SLIDES: { id: SlideId; title: string; body: string; tint: string }[] = [
  {
    id: 'private',
    title: 'Private by design',
    body: 'A quiet corner for you and your crew. Only the people in a chat can open it.',
    tint: onboardingTints[0],
  },
  {
    id: 'voice',
    title: 'Say it your way',
    body: 'Voice notes, photos, GIFs and stickers your group makes together.',
    tint: onboardingTints[1],
  },
  {
    id: 'calls',
    title: 'Calls with your crew',
    body: 'Jump on a voice or video call in a tap. Friends join right from the notification.',
    tint: onboardingTints[2],
  },
  {
    id: 'lock',
    title: 'Yours to lock',
    body: 'Lock NOOK with your own PIN or fingerprint, and let messages disappear when you want.',
    tint: onboardingTints[3],
  },
];
