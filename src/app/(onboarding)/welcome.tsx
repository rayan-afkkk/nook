import { router } from 'expo-router';

import { OnboardingScreen } from '@/features/onboarding/OnboardingScreen';
import { usePrefs } from '@/stores/prefs';

export default function Welcome() {
  return (
    <OnboardingScreen
      onDone={() => {
        usePrefs.getState().setOnboardingSeen(true);
        router.replace('/sign-in');
      }}
    />
  );
}
