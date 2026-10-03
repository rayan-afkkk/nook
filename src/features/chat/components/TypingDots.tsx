import { useEffect } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, {
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withRepeat,
  withSequence,
  withTiming,
} from 'react-native-reanimated';

import { radius, spacing, useTheme } from '@/theme';

function Dot({ delay }: { delay: number }) {
  const { colors } = useTheme();
  const reduce = useReducedMotion();
  const y = useSharedValue(0);
  useEffect(() => {
    if (reduce) return;
    y.value = withDelay(delay, withRepeat(withSequence(withTiming(-3, { duration: 260 }), withTiming(0, { duration: 260 })), -1, false));
  }, [delay, reduce, y]);
  const style = useAnimatedStyle(() => ({ transform: [{ translateY: y.value }] }));
  return <Animated.View style={[styles.dot, { backgroundColor: colors.textMuted }, style]} />;
}

export function TypingDots() {
  const { colors } = useTheme();
  return (
    <View
      accessibilityLabel="Typing"
      style={[styles.bubble, { backgroundColor: colors.surface, borderColor: colors.border }]}
    >
      <Dot delay={0} />
      <Dot delay={140} />
      <Dot delay={280} />
    </View>
  );
}

const styles = StyleSheet.create({
  bubble: {
    flexDirection: 'row',
    gap: 4,
    alignSelf: 'flex-start',
    borderRadius: radius.card,
    borderWidth: 1,
    paddingHorizontal: spacing.md,
    paddingVertical: 14,
    marginLeft: spacing.md,
    marginTop: spacing.xs,
  },
  dot: { width: 7, height: 7, borderRadius: 4 },
});
