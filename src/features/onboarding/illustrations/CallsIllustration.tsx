import Ionicons from '@expo/vector-icons/Ionicons';
import { StyleSheet, Text, View } from 'react-native';
import Animated, { interpolate, useAnimatedStyle, type SharedValue } from 'react-native-reanimated';

import { fonts, palette } from '@/theme';

import { Float, PopIn, useLoop, type IllustrationProps } from './motion';

const FRIENDS = [
  { initial: 'A', color: palette.peach, angle: -150 },
  { initial: 'S', color: palette.mint, angle: -30 },
  { initial: 'M', color: palette.sky, angle: 40 },
  { initial: 'J', color: palette.rose, angle: 140 },
];

function Ring({ t, offset, diameter }: { t: SharedValue<number>; offset: number; diameter: number }) {
  const style = useAnimatedStyle(() => {
    const p = (t.value + offset) % 1;
    return {
      opacity: interpolate(p, [0, 0.15, 1], [0, 0.55, 0]),
      transform: [{ scale: interpolate(p, [0, 1], [1, 2.3]) }],
    };
  });
  return (
    <Animated.View
      style={[
        styles.ring,
        { width: diameter, height: diameter, borderRadius: diameter / 2, marginLeft: -diameter / 2, marginTop: -diameter / 2 },
        style,
      ]}
    />
  );
}

/** Slide 3, "Calls with your crew": pulsing call rings while friends join one by one. */
export function CallsIllustration({ active, reduceMotion, size }: IllustrationProps) {
  const t = useLoop(active, reduceMotion, 2600, 0.3);
  const center = 96;
  const orbit = size * 0.36;
  return (
    <View style={{ width: size, height: size }} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
      {active && !reduceMotion ? [0, 0.33, 0.66].map((o) => <Ring key={o} t={t} offset={o} diameter={center} />) : null}
      <View style={[styles.centerWrap, { marginLeft: -center / 2, marginTop: -center / 2 }]}>
        <PopIn active={active} reduceMotion={reduceMotion} delay={80} from={0.6}>
          <View style={[styles.avatar, { width: center, height: center, borderRadius: center / 2, backgroundColor: palette.lavender }]}>
            <Ionicons name="call" size={36} color="#1A1714" />
          </View>
        </PopIn>
      </View>
      {FRIENDS.map((f, i) => {
        const rad = (f.angle * Math.PI) / 180;
        const d = 58;
        return (
          <View
            key={f.initial}
            style={[
              styles.friend,
              { left: size / 2 + Math.cos(rad) * orbit - d / 2, top: size / 2 + Math.sin(rad) * orbit - d / 2 },
            ]}
          >
            <PopIn active={active} reduceMotion={reduceMotion} delay={500 + i * 420} from={0.2}>
              <Float active={active} reduceMotion={reduceMotion} delay={i * 200} amplitude={4} duration={2000 + i * 150}>
                <View style={[styles.avatar, { width: d, height: d, borderRadius: d / 2, backgroundColor: f.color }]}>
                  <Text style={styles.initial}>{f.initial}</Text>
                </View>
                <View style={styles.onlineDot} />
              </Float>
            </PopIn>
          </View>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  ring: { position: 'absolute', left: '50%', top: '50%', borderWidth: 1.5, borderColor: palette.cream },
  centerWrap: { position: 'absolute', left: '50%', top: '50%' },
  avatar: { alignItems: 'center', justifyContent: 'center', borderWidth: 3, borderColor: palette.black },
  friend: { position: 'absolute' },
  initial: { fontFamily: fonts.serif, fontSize: 28, color: '#1A1714' },
  onlineDot: {
    position: 'absolute',
    right: 2,
    bottom: 2,
    width: 14,
    height: 14,
    borderRadius: 7,
    backgroundColor: palette.online,
    borderWidth: 2,
    borderColor: palette.black,
  },
});
