import { Redirect } from 'expo-router';

import { STAGE_ROUTES, useAppStage } from '@/features/auth/useAppStage';
import { ProfileLoading } from '@/features/profile/ProfileLoading';

/** Single entry point: sends the person to wherever they are in the first-run flow. */
export default function Index() {
  const stage = useAppStage();
  if (stage === 'loading' || stage === 'profileLoading') return <ProfileLoading />;
  return <Redirect href={STAGE_ROUTES[stage]} />;
}
