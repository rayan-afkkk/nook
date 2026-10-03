import { useEffect } from 'react';
import { StyleSheet } from 'react-native';
import Animated, { FadeInUp, FadeOutUp } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { create } from 'zustand';

import { radius, spacing, useTheme } from '@/theme';

import { Icon } from './Icon';
import { Text } from './Text';

type Tone = 'neutral' | 'error' | 'success';
type ToastState = { id: number; message: string | null; tone: Tone };

const useToastStore = create<ToastState>()(() => ({ id: 0, message: null, tone: 'neutral' }));

export const toast = {
  show: (message: string, tone: Tone = 'neutral') =>
    useToastStore.setState((s) => ({ id: s.id + 1, message, tone })),
  error: (message: string) => toast.show(message, 'error'),
  success: (message: string) => toast.show(message, 'success'),
};

/** One toast at a time, floating above content so nothing below jumps. */
export function ToastHost() {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const { id, message, tone } = useToastStore();

  useEffect(() => {
    if (!message) return;
    const t = setTimeout(() => useToastStore.setState({ message: null }), 3200);
    return () => clearTimeout(t);
  }, [id, message]);

  if (!message) return null;
  return (
    <Animated.View
      key={id}
      entering={FadeInUp.springify().damping(18)}
      exiting={FadeOutUp.duration(200)}
      accessibilityLiveRegion="assertive"
      accessibilityRole="alert"
      pointerEvents="none"
      style={[styles.toast, { top: insets.top + spacing.xs, backgroundColor: colors.surfaceRaised, borderColor: colors.border }]}
    >
      <Icon
        name={tone === 'error' ? 'alert-circle-outline' : tone === 'success' ? 'checkmark-circle-outline' : 'information-circle-outline'}
        size={20}
        color={tone === 'error' ? 'danger' : tone === 'success' ? colors.online : 'text'}
      />
      <Text variant="caption" style={styles.text}>
        {message}
      </Text>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  toast: {
    position: 'absolute',
    left: spacing.md,
    right: spacing.md,
    borderRadius: radius.md,
    borderWidth: 1,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.xs,
    zIndex: 1000,
    elevation: 12,
  },
  text: { flex: 1 },
});
