import { useEffect } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withRepeat,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';

import { Icon, PressableScale, Text } from '@/components/ui';
import { haptics } from '@/lib/haptics';
import { motion, spacing, useTheme } from '@/theme';

type DotsProps = { length: number; filled: number; errorKey: number; busy?: boolean };

/** Row of PIN dots. Shakes whenever `errorKey` changes. */
export function PinDots({ length, filled, errorKey, busy }: DotsProps) {
  const { colors } = useTheme();
  const shake = useSharedValue(0);
  const pulse = useSharedValue(1);
  useEffect(() => {
    if (errorKey === 0) return;
    shake.value = withSequence(
      withTiming(-12, { duration: 50 }),
      withTiming(12, { duration: 50 }),
      withTiming(-8, { duration: 50 }),
      withTiming(8, { duration: 50 }),
      withSpring(0, motion.spring),
    );
  }, [errorKey, shake]);
  useEffect(() => {
    pulse.value = busy ? withRepeat(withTiming(0.35, { duration: 380 }), -1, true) : withTiming(1, { duration: 150 });
  }, [busy, pulse]);
  const style = useAnimatedStyle(() => ({ transform: [{ translateX: shake.value }], opacity: pulse.value }));
  const count = Math.max(length, filled);
  return (
    <Animated.View
      style={[styles.dots, style]}
      accessible
      accessibilityLabel={`${filled} digits entered`}
      accessibilityLiveRegion="polite"
    >
      {Array.from({ length: count }).map((_, i) => (
        <View
          key={i}
          style={[
            styles.dot,
            { borderColor: colors.textMuted },
            i < filled && { backgroundColor: colors.text, borderColor: colors.text },
          ]}
        />
      ))}
    </Animated.View>
  );
}

type PadProps = {
  onDigit: (d: string) => void;
  onDelete: () => void;
  /** Bottom-left key: biometrics on the lock screen, nothing elsewhere. */
  leftKey?: { icon: 'finger-print' | 'checkmark'; label: string; onPress: () => void } | null;
  disabled?: boolean;
};

const KEYS = ['1', '2', '3', '4', '5', '6', '7', '8', '9'];

export function PinPad({ onDigit, onDelete, leftKey, disabled }: PadProps) {
  const { colors } = useTheme();
  const key = (label: string, content: React.ReactNode, onPress: () => void, filled = true) => (
    <PressableScale
      key={label}
      accessibilityRole="button"
      accessibilityLabel={label}
      disabled={disabled}
      scaleTo={0.9}
      onPress={() => {
        haptics.tick();
        onPress();
      }}
      style={[styles.key, filled && { backgroundColor: colors.surface, borderColor: colors.border, borderWidth: 1 }]}
    >
      {content}
    </PressableScale>
  );
  return (
    <View style={styles.grid}>
      {KEYS.map((d) => key(d, <Text variant="headline">{d}</Text>, () => onDigit(d)))}
      {leftKey ? key(leftKey.label, <Icon name={leftKey.icon} size={28} />, leftKey.onPress, false) : <View style={styles.key} />}
      {key('0', <Text variant="headline">0</Text>, () => onDigit('0'))}
      {key('Delete', <Icon name="backspace-outline" size={26} />, onDelete, false)}
    </View>
  );
}

const styles = StyleSheet.create({
  dots: { flexDirection: 'row', justifyContent: 'center', gap: spacing.sm, minHeight: 20, flexWrap: 'wrap' },
  dot: { width: 14, height: 14, borderRadius: 7, borderWidth: 1.5 },
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
    rowGap: spacing.md,
    width: 3 * 76 + 2 * 28,
    alignSelf: 'center',
  },
  key: { width: 76, height: 76, borderRadius: 38, alignItems: 'center', justifyContent: 'center' },
});
