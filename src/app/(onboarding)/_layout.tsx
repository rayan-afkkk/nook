import { Stack } from 'expo-router';

import { darkColors } from '@/theme';

export default function OnboardingLayout() {
  return (
    <Stack
      screenOptions={{
        headerShown: false,
        animation: 'fade',
        contentStyle: { backgroundColor: darkColors.background },
      }}
    />
  );
}
