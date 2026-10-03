/** Pure first-run state machine (no React, no native modules) so it can be unit tested. */
export type AppStage =
  | 'loading'
  | 'intro'
  | 'signIn'
  | 'profileLoading'
  | 'username'
  | 'permissions'
  | 'setLock'
  | 'app';

type Inputs = {
  prefsHydrated: boolean;
  onboardingSeen: boolean;
  permissionsPrimed: boolean;
  authStatus: 'loading' | 'signedOut' | 'signedIn';
  profile: unknown;
  lockConfigured: boolean | undefined;
};

export function computeStage(i: Inputs): AppStage {
  if (!i.prefsHydrated || i.authStatus === 'loading' || i.lockConfigured === undefined) return 'loading';
  if (i.authStatus === 'signedOut') return i.onboardingSeen ? 'signIn' : 'intro';
  if (i.profile === undefined) return 'profileLoading';
  if (i.profile === null) return 'username';
  if (!i.permissionsPrimed) return 'permissions';
  if (!i.lockConfigured) return 'setLock';
  return 'app';
}

