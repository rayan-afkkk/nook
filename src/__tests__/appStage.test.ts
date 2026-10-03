import { computeStage } from '@/features/auth/stage';

const base = {
  prefsHydrated: true,
  onboardingSeen: false,
  permissionsPrimed: false,
  authStatus: 'signedOut' as const,
  profile: undefined as unknown,
  lockConfigured: false as boolean | undefined,
};

describe('first-run flow', () => {
  it('waits for storage, auth and the lock record', () => {
    expect(computeStage({ ...base, prefsHydrated: false })).toBe('loading');
    expect(computeStage({ ...base, authStatus: 'loading' })).toBe('loading');
    expect(computeStage({ ...base, lockConfigured: undefined })).toBe('loading');
  });

  it('shows the intro once, then sign-in', () => {
    expect(computeStage(base)).toBe('intro');
    expect(computeStage({ ...base, onboardingSeen: true })).toBe('signIn');
  });

  it('walks a new account through username, permissions and lock', () => {
    const signedIn = { ...base, onboardingSeen: true, authStatus: 'signedIn' as const };
    expect(computeStage(signedIn)).toBe('profileLoading');
    expect(computeStage({ ...signedIn, profile: null })).toBe('username');
    expect(computeStage({ ...signedIn, profile: { username: 'ali' } })).toBe('permissions');
    expect(computeStage({ ...signedIn, profile: { username: 'ali' }, permissionsPrimed: true })).toBe('setLock');
    expect(
      computeStage({ ...signedIn, profile: { username: 'ali' }, permissionsPrimed: true, lockConfigured: true }),
    ).toBe('app');
  });

  it('a returning user who signed out skips the intro', () => {
    expect(computeStage({ ...base, onboardingSeen: true, lockConfigured: false })).toBe('signIn');
  });
});
