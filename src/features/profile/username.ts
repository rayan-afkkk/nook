import { USERNAME_MAX, USERNAME_MIN } from '@/constants/app';

/** Names nobody can claim (impersonation or confusion risk). */
const RESERVED = new Set([
  'admin', 'administrator', 'nook', 'nookapp', 'support', 'help', 'official', 'system', 'root',
  'moderator', 'mod', 'staff', 'team', 'security', 'null', 'undefined', 'me', 'you', 'everyone',
]);

export function normalizeUsername(input: string): string {
  return input.trim().replace(/^@+/, '').toLowerCase();
}

/** Returns an error sentence, or null when the (already normalised) name is acceptable. */
export function validateUsername(name: string): string | null {
  if (name.length < USERNAME_MIN) return `At least ${USERNAME_MIN} characters.`;
  if (name.length > USERNAME_MAX) return `At most ${USERNAME_MAX} characters.`;
  if (!/^[a-z]/.test(name)) return 'Start with a letter.';
  if (!/^[a-z0-9._]+$/.test(name)) return 'Only letters, numbers, dots and underscores.';
  if (/[._]$/.test(name)) return "Can't end with a dot or underscore.";
  if (/\.\./.test(name)) return "Can't have two dots in a row.";
  if (RESERVED.has(name)) return "That one's reserved.";
  return null;
}

/** Must stay in sync with the regex in firestore.rules. */
export const USERNAME_PATTERN = '^[a-z][a-z0-9._]{1,18}[a-z0-9]$';
