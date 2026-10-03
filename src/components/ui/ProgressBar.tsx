import { useEffect } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, { useAnimatedStyle, useSharedValue, withTiming } from 'react-native-reanimated';

import { motion, radius, useTheme } from '@/theme';

type Props = { progress: number; label: string; tone?: 'accent' | 'info' };

export function ProgressBar({ progress, label, tone = 'accent' }: Props) {
  const { colors } = useTheme();
  const clamped = Math.min(1, Math.max(0, progress));
  const value = useSharedValue(0);
  useEffect(() => {
    value.value = withTiming(clamped, { duration: motion.slow * 2 });
  }, [clamped, value]);
  const fill = useAnimatedStyle(() => ({ width: `${value.value * 100}%` }));
  return (
    <View
      accessibilityRole="progressbar"
      accessibilityLabel={label}
      accessibilityValue={{ min: 0, max: 100, now: Math.round(clamped * 100) }}
      style={[styles.track, { backgroundColor: colors.surfaceRaised }]}
    >
      <Animated.View style={[styles.fill, { backgroundColor: tone === 'accent' ? colors.accent : colors.info }, fill]} />
    </View>
  );
}

const styles = StyleSheet.create({
  track: { height: 8, borderRadius: radius.pill, overflow: 'hidden' },
  fill: { height: '100%', borderRadius: radius.pill },
});
