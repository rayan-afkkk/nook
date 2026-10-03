import { router } from 'expo-router';
import { useState } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, { FadeInDown } from 'react-native-reanimated';

import { Logo } from '@/components/brand/Logo';
import { Button, Screen, Text, toast, useIsOffline } from '@/components/ui';
import { signInWithGoogle } from '@/features/auth/authService';
import { describeError } from '@/lib/errors';
import { haptics } from '@/lib/haptics';
import { spacing } from '@/theme';

export default function SignIn() {
  const offline = useIsOffline();
  const [busy, setBusy] = useState(false);

  const onGoogle = async () => {
    setBusy(true);
    try {
      const ok = await signInWithGoogle();
      if (ok) {
        haptics.success();
        router.replace('/');
      }
    } catch (e) {
      haptics.error();
      toast.error(describeError(e, "Couldn't sign in with Google. Please try again."));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Screen edges={['top', 'bottom']}>
      <View style={styles.top}>
        <Animated.View entering={FadeInDown.duration(300)}>
          <Logo size={44} />
        </Animated.View>
      </View>
      <View style={styles.body}>
        <Animated.View entering={FadeInDown.delay(80).duration(320)}>
          <Text variant="display">Come on in.</Text>
        </Animated.View>
        <Animated.View entering={FadeInDown.delay(160).duration(320)}>
          <Text variant="bodyLarge" color="textMuted">
            Sign in with Google, then pick the username your friends will find you by.
          </Text>
        </Animated.View>
      </View>
      <Animated.View entering={FadeInDown.delay(240).duration(320)} style={styles.footer}>
        <Button
          title="Continue with Google"
          icon="logo-google"
          loading={busy}
          disabled={offline}
          onPress={() => void onGoogle()}
          accessibilityHint="Opens the Google account picker"
        />
        <Text variant="caption" color="textMuted" align="center">
          {offline ? "You're offline. Connect to sign in." : 'By continuing you agree to our '}
          {offline ? null : (
            <Text variant="captionBold" color="text" onPress={() => router.push('/privacy')} accessibilityRole="link">
              Privacy & Terms
            </Text>
          )}
          {offline ? null : '.'}
        </Text>
      </Animated.View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  top: { paddingTop: spacing.lg },
  body: { flex: 1, justifyContent: 'center', gap: spacing.md },
  footer: { gap: spacing.md, paddingBottom: spacing.lg },
});
