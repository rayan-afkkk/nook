/** Turns any thrown value into a short sentence that is safe to show to the user. */
export function describeError(error: unknown, fallback = 'Something went wrong. Please try again.'): string {
  const code = typeof error === 'object' && error && 'code' in error ? String((error as { code: unknown }).code) : '';
  switch (code) {
    case 'firestore/unavailable':
    case 'auth/network-request-failed':
      return "You're offline. Check your connection and try again.";
    case 'firestore/permission-denied':
      return "You don't have permission to do that.";
    case 'auth/requires-recent-login':
      return 'For your security, sign in again and retry.';
    case 'auth/user-disabled':
      return 'This account has been disabled.';
    default:
      break;
  }
  if (error instanceof Error && error.message && error.message.length < 140 && !/\[.*\]/.test(error.message)) {
    return error.message;
  }
  return fallback;
}

export class UserFacingError extends Error {}
