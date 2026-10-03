import { StyleSheet, View } from 'react-native';
import Animated, { Extrapolation, interpolate, useAnimatedStyle, type SharedValue } from 'react-native-reanimated';

import { palette } from '@/theme';

import { PopIn, useLoop, type IllustrationProps } from './motion';

/** Loop timeline (fractions of one cycle). */
const SNAP = [0.24, 0.31, 0.35] as const;
const REOPEN = [0.9, 1] as const;
const dissolveStart = (i: number) => 0.44 + i * 0.12;
const DISSOLVE_LEN = 0.16;

const MESSAGES = [
  { left: 0.02, top: 0.08, width: 0.4, color: palette.sky },
  { left: 0.6, top: 0.22, width: 0.36, color: palette.rose },
  { left: 0.04, top: 0.7, width: 0.34, color: palette.mint },
];

const PARTICLES = [
  { dx: -14, dy: -26, s: 6 },
  { dx: 8, dy: -34, s: 5 },
  { dx: 22, dy: -20, s: 4 },
  { dx: -4, dy: -42, s: 4 },
  { dx: 30, dy: -36, s: 3 },
  { dx: -24, dy: -14, s: 3 },
];

function Message({ t, i, size }: { t: SharedValue<number>; i: number; size: number }) {
  const m = MESSAGES[i]!;
  const start = dissolveStart(i);
  const bubble = useAnimatedStyle(() => {
    const fadeIn = interpolate(t.value, [0, 0.12], [0, 1], Extrapolation.CLAMP);
    const p = interpolate(t.value, [start, start + DISSOLVE_LEN], [0, 1], Extrapolation.CLAMP);
    return {
      opacity: fadeIn * (1 - p),
      transform: [{ translateY: -12 * p }, { scale: 1 - 0.18 * p }],
    };
  });
  return (
    <View style={[styles.messageWrap, { left: size * m.left, top: size * m.top, width: size * m.width }]}>
      <Animated.View style={[styles.message, { backgroundColor: m.color }, bubble]}>
        <View style={[styles.line, { width: '85%' }]} />
        <View style={[styles.line, { width: '55%' }]} />
      </Animated.View>
      {PARTICLES.map((pt, k) => (
        <Particle key={k} t={t} start={start} {...pt} color={m.color} />
      ))}
    </View>
  );
}

function Particle({
  t,
  start,
  dx,
  dy,
  s,
  color,
}: {
  t: SharedValue<number>;
  start: number;
  dx: number;
  dy: number;
  s: number;
  color: string;
}) {
  const style = useAnimatedStyle(() => {
    const p = interpolate(t.value, [start + 0.02, start + DISSOLVE_LEN + 0.08], [0, 1], Extrapolation.CLAMP);
    return {
      opacity: p > 0 && p < 1 ? Math.sin(p * Math.PI) : 0,
      transform: [{ translateX: dx * p }, { translateY: dy * p }, { scale: 1 - 0.5 * p }],
    };
  });
  return <Animated.View style={[styles.particle, { width: s, height: s, borderRadius: s / 2, backgroundColor: color }, style]} />;
}

/** Slide 4, "Yours to lock": the lock snaps shut, then messages dissolve like disappearing messages. */
export function LockIllustration({ active, reduceMotion, size }: IllustrationProps) {
  const t = useLoop(active, reduceMotion, 4400, 0.4);
  const shackle = useAnimatedStyle(() => {
    const closing = interpolate(t.value, [0, SNAP[0], SNAP[1], SNAP[2]], [-26, -26, 4, 0], Extrapolation.CLAMP);
    const reopen = interpolate(t.value, [REOPEN[0], REOPEN[1]], [0, -26], Extrapolation.CLAMP);
    const y = t.value >= REOPEN[0] ? reopen : closing;
    return { transform: [{ translateY: y }] };
  });
  const body = useAnimatedStyle(() => {
    const bump = interpolate(t.value, [SNAP[1], SNAP[1] + 0.03, SNAP[2] + 0.03], [1, 0.95, 1], Extrapolation.CLAMP);
    return { transform: [{ scale: bump }] };
  });
  const keyhole = useAnimatedStyle(() => ({
    opacity: interpolate(t.value, [SNAP[1], SNAP[2]], [0.45, 1], Extrapolation.CLAMP),
  }));

  return (
    <View style={{ width: size, height: size }} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
      {MESSAGES.map((_, i) => (
        <Message key={i} t={t} i={i} size={size} />
      ))}
      <View style={styles.lockWrap}>
        <PopIn active={active} reduceMotion={reduceMotion} delay={60} from={0.7}>
          <View style={styles.lock}>
            <Animated.View style={[styles.shackle, shackle]} />
            <Animated.View style={[styles.body, body]}>
              <Animated.View style={[styles.keyhole, keyhole]}>
                <View style={styles.keyDot} />
                <View style={styles.keyStem} />
              </Animated.View>
            </Animated.View>
          </View>
        </PopIn>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  messageWrap: { position: 'absolute', alignItems: 'center' },
  message: { alignSelf: 'stretch', borderRadius: 18, paddingHorizontal: 14, paddingVertical: 12, gap: 7 },
  line: { height: 7, borderRadius: 4, backgroundColor: 'rgba(26,23,20,0.22)' },
  particle: { position: 'absolute', top: 18 },
  lockWrap: { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, alignItems: 'center', justifyContent: 'center' },
  lock: { width: 132, height: 170, alignItems: 'center', justifyContent: 'flex-end' },
  shackle: {
    position: 'absolute',
    top: 8,
    width: 82,
    height: 96,
    borderWidth: 13,
    borderBottomWidth: 0,
    borderColor: palette.cream,
    borderTopLeftRadius: 41,
    borderTopRightRadius: 41,
  },
  body: {
    width: 132,
    height: 104,
    borderRadius: 26,
    backgroundColor: palette.cream,
    alignItems: 'center',
    justifyContent: 'center',
  },
  keyhole: { alignItems: 'center' },
  keyDot: { width: 20, height: 20, borderRadius: 10, backgroundColor: palette.orange },
  keyStem: { width: 8, height: 18, borderRadius: 4, marginTop: -4, backgroundColor: palette.orange },
});
