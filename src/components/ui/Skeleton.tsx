import { useEffect } from 'react';
import { View, type DimensionValue, type StyleProp, type ViewStyle } from 'react-native';
import Animated, {
  Easing,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';

import { radius as radii, spacing, useTheme } from '@/theme';

type Props = { width?: DimensionValue; height?: number; radius?: number; style?: StyleProp<ViewStyle> };

export function Skeleton({ width = '100%', height = 14, radius = radii.sm, style }: Props) {
  const { colors } = useTheme();
  const reduce = useReducedMotion();
  const pulse = useSharedValue(0.5);
  useEffect(() => {
    if (reduce) return;
    pulse.value = withRepeat(withTiming(1, { duration: 900, easing: Easing.inOut(Easing.ease) }), -1, true);
  }, [pulse, reduce]);
  const animated = useAnimatedStyle(() => ({ opacity: pulse.value }));
  return (
    <Animated.View
      accessibilityElementsHidden
      style={[{ width, height, borderRadius: radius, backgroundColor: colors.surfaceRaised }, animated, style]}
    />
  );
}

/** Placeholder list row (avatar + two lines) used while lists load. */
export function SkeletonRow() {
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md, paddingVertical: spacing.sm }}>
      <Skeleton width={52} height={52} radius={26} />
      <View style={{ flex: 1, gap: spacing.xs }}>
        <Skeleton width="45%" height={14} />
        <Skeleton width="75%" height={12} />
      </View>
    </View>
  );
}
