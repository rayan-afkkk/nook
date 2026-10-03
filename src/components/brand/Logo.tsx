import { useEffect } from 'react';
import Animated, {
  Easing,
  useAnimatedProps,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import Svg, { Circle, Path } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { palette } from '@/theme';

const AnimatedPath = Animated.createAnimatedComponent(Path);
const AnimatedCircle = Animated.createAnimatedComponent(Circle);

/** The NOOK mark: an arched alcove that doubles as a speech bubble, with a warm light inside. */
export const LOGO_PATH = 'M30 112 V56 A30 30 0 0 1 90 56 V100 H48 Z';
const PATH_LENGTH = 262;

type Props = {
  size?: number;
  /** Draws the outline in, then pops the light. */
  animated?: boolean;
  stroke?: string;
  onDrawn?: () => void;
};

export function Logo({ size = 96, animated = false, stroke = palette.cream, onDrawn }: Props) {
  const draw = useSharedValue(animated ? 0 : 1);
  const dot = useSharedValue(animated ? 0 : 1);

  useEffect(() => {
    if (!animated) return;
    draw.value = withTiming(1, { duration: 900, easing: Easing.bezier(0.65, 0, 0.35, 1) });
    dot.value = withDelay(
      750,
      withSpring(1, { damping: 9, stiffness: 200 }, (finished) => {
        if (finished && onDrawn) scheduleOnRN(onDrawn);
      }),
    );
  }, [animated, draw, dot, onDrawn]);

  const pathProps = useAnimatedProps(() => ({ strokeDashoffset: PATH_LENGTH * (1 - draw.value) }));
  const dotProps = useAnimatedProps(() => ({ r: 8 * dot.value }));

  return (
    <Svg width={size} height={size} viewBox="18 16 84 104" accessibilityRole="image" accessibilityLabel="NOOK">
      <AnimatedPath
        d={LOGO_PATH}
        fill="none"
        stroke={stroke}
        strokeWidth={8}
        strokeLinejoin="round"
        strokeLinecap="round"
        strokeDasharray={[PATH_LENGTH, PATH_LENGTH]}
        animatedProps={pathProps}
      />
      <AnimatedCircle cx={60} cy={66} fill={palette.orange} animatedProps={dotProps} />
    </Svg>
  );
}
