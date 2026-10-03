import { useLock } from '@/features/lock/lockStore';
import { usePrefs } from '@/stores/prefs';

import { useSession } from './session';

import { computeStage, type AppStage } from './stage';

export type { AppStage };

export function useAppStage(): AppStage {
  const prefsHydrated = usePrefs((s) => s.hydrated);
  const onboardingSeen = usePrefs((s) => s.onboardingSeen);
  const permissionsPrimed = usePrefs((s) => s.permissionsPrimed);
  const authStatus = useSession((s) => s.status);
  const profile = useSession((s) => s.profile);
  const record = useLock((s) => s.record);
  return computeStage({
    prefsHydrated,
    onboardingSeen,
    permissionsPrimed,
    authStatus,
    profile,
    lockConfigured: record === undefined ? undefined : record !== null,
  });
}

export const STAGE_ROUTES = {
  intro: '/welcome',
  signIn: '/sign-in',
  username: '/username',
  permissions: '/permissions',
  setLock: '/set-lock',
  app: '/chats',
} as const;
