import { useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, { FadeIn } from 'react-native-reanimated';

import { Logo } from '@/components/brand/Logo';
import { Button, Screen, Skeleton, Text, useIsOffline } from '@/components/ui';
import { signOut } from '@/features/auth/authService';
import { useSession } from '@/features/auth/session';
import { spacing } from '@/theme';

/** Shown while we wait for the server to confirm the profile (first sign-in on a device). */
export function ProfileLoading() {
  const offline = useIsOffline();
  const error = useSession((s) => s.profileError);
  const [slow, setSlow] = useState(false);
  useEffect(() => {
    const t = setTimeout(() => setSlow(true), 6000);
    return () => clearTimeout(t);
  }, []);
  return (
    <Screen edges={['top', 'bottom']}>
      <View style={styles.center}>
        <Logo size={64} />
        <Skeleton width={180} height={16} />
        <Skeleton width={120} height={12} />
        {offline || error || slow ? (
          <Animated.View entering={FadeIn} style={styles.help}>
            <Text variant="body" color="textMuted" align="center">
              {offline
                ? "You're offline. NOOK needs a connection the first time you sign in on this phone."
                : error
                  ? "We couldn't load your profile. Check your connection, or sign out and try again."
                  : 'Still connecting…'}
            </Text>
            {error || slow ? <Button title="Sign out" variant="secondary" onPress={() => void signOut()} /> : null}
          </Animated.View>
        ) : null}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: spacing.md },
  help: { gap: spacing.md, alignSelf: 'stretch', marginTop: spacing.xl },
});
