import { Stack } from 'expo-router';

import { useSession } from '@/features/auth/session';
import { AppServices } from '@/features/session/AppServices';

import { useTheme } from '@/theme';

export default function AppLayout() {
  const { colors } = useTheme();
  const uid = useSession((s) => s.user?.uid);
  return (
    <>
    {uid ? <AppServices uid={uid} /> : null}
    <Stack
      screenOptions={{
        headerShown: false,
        animation: 'slide_from_right',
        contentStyle: { backgroundColor: colors.background },
      }}
    >
      <Stack.Screen name="(tabs)" options={{ animation: 'fade' }} />
      <Stack.Screen name="intro" options={{ animation: 'fade', presentation: 'fullScreenModal' }} />
      <Stack.Screen name="call/[id]" options={{ animation: 'fade', gestureEnabled: false }} />
      <Stack.Screen name="call/incoming" options={{ animation: 'fade', gestureEnabled: false }} />
    </Stack>
    </>
  );
}
