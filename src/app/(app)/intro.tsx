import { router } from 'expo-router';

import { OnboardingScreen } from '@/features/onboarding/OnboardingScreen';

/** Account > View Onboarding: the same animated intro, returning to Account when done. */
export default function IntroReplay() {
  return <OnboardingScreen onDone={() => router.back()} />;
}
