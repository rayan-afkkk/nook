import { useNetInfo } from '@react-native-community/netinfo';
import { StyleSheet } from 'react-native';
import Animated, { FadeInUp, FadeOutUp } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { radius, spacing, useTheme } from '@/theme';

import { Icon } from './Icon';
import { Text } from './Text';

export function useIsOffline(): boolean {
  const net = useNetInfo();
  return net.isConnected === false || net.isInternetReachable === false;
}

/** Floating pill shown while offline. Overlays content instead of pushing it (no layout jumps). */
export function OfflineBanner() {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const offline = useIsOffline();
  if (!offline) return null;
  return (
    <Animated.View
      entering={FadeInUp.duration(250)}
      exiting={FadeOutUp.duration(200)}
      pointerEvents="none"
      accessibilityLiveRegion="polite"
      style={[styles.pill, { top: insets.top + 6, backgroundColor: colors.info }]}
    >
      <Icon name="cloud-offline-outline" size={14} color="#1A1714" />
      <Text variant="micro" style={{ color: '#1A1714' }}>
        Offline · changes will sync when you reconnect
      </Text>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  pill: {
    position: 'absolute',
    alignSelf: 'center',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: spacing.sm,
    paddingVertical: 6,
    borderRadius: radius.pill,
    zIndex: 999,
    elevation: 10,
  },
});
