import { useEffect, type PropsWithChildren } from 'react';
import type { StyleProp, ViewStyle } from 'react-native';
import Animated, {
  cancelAnimation,
  Easing,
  useAnimatedStyle,
  useSharedValue,
  withDelay,
  withRepeat,
  withSequence,
  withSpring,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';

import { motion } from '@/theme';

export type IllustrationProps = { active: boolean; reduceMotion: boolean; size: number };

/**
 * Pops its child in with a springy scale when `active` turns on, and resets when it turns off
 * so the entrance replays each time the slide is revisited. Static under reduce motion.
 */
export function PopIn({
  active,
  reduceMotion,
  delay = 0,
  from = 0.4,
  rotate = 0,
  style,
  children,
}: PropsWithChildren<{
  active: boolean;
  reduceMotion: boolean;
  delay?: number;
  from?: number;
  rotate?: number;
  style?: StyleProp<ViewStyle>;
}>) {
  const p = useSharedValue(reduceMotion ? 1 : 0);
  useEffect(() => {
    if (reduceMotion) {
      p.value = 1;
      return;
    }
    if (active) {
      p.value = withDelay(delay, withSpring(1, motion.springBouncy));
    } else {
      cancelAnimation(p);
      p.value = 0;
    }
  }, [active, reduceMotion, delay, p]);
  const animated = useAnimatedStyle(() => ({
    opacity: Math.min(1, p.value * 1.6),
    transform: [
      { translateY: (1 - p.value) * 18 },
      { scale: from + (1 - from) * p.value },
      { rotate: `${rotate * p.value}deg` },
    ],
  }));
  return <Animated.View style={[style, animated]}>{children}</Animated.View>;
}

/** Gentle endless bob, phase-shifted by `delay`. */
export function Float({
  active,
  reduceMotion,
  delay = 0,
  amplitude = 5,
  duration = 1800,
  style,
  children,
}: PropsWithChildren<{
  active: boolean;
  reduceMotion: boolean;
  delay?: number;
  amplitude?: number;
  duration?: number;
  style?: StyleProp<ViewStyle>;
}>) {
  const y = useSharedValue(0);
  useEffect(() => {
    if (!active || reduceMotion) {
      cancelAnimation(y);
      y.value = 0;
      return;
    }
    const ease = Easing.inOut(Easing.sin);
    y.value = withDelay(
      delay,
      withRepeat(
        withSequence(
          withTiming(-amplitude, { duration: duration / 2, easing: ease }),
          withTiming(amplitude, { duration: duration / 2, easing: ease }),
        ),
        -1,
        true,
      ),
    );
    return () => cancelAnimation(y);
  }, [active, reduceMotion, delay, amplitude, duration, y]);
  const animated = useAnimatedStyle(() => ({ transform: [{ translateY: y.value }] }));
  return <Animated.View style={[style, animated]}>{children}</Animated.View>;
}

/** A 0→1 clock that repeats while active. Everything in a looping scene is derived from it on the UI thread. */
export function useLoop(active: boolean, reduceMotion: boolean, duration: number, restValue = 1): SharedValue<number> {
  const t = useSharedValue(reduceMotion ? restValue : 0);
  useEffect(() => {
    if (reduceMotion) {
      t.value = restValue;
      return;
    }
    if (!active) {
      cancelAnimation(t);
      t.value = 0;
      return;
    }
    t.value = 0;
    t.value = withRepeat(withTiming(1, { duration, easing: Easing.linear }), -1, false);
    return () => cancelAnimation(t);
  }, [active, reduceMotion, duration, restValue, t]);
  return t;
}
