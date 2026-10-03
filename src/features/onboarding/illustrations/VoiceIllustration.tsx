import Ionicons from '@expo/vector-icons/Ionicons';
import { StyleSheet, Text, View } from 'react-native';
import Animated, { useAnimatedStyle, type SharedValue } from 'react-native-reanimated';

import { fonts, palette } from '@/theme';

import { Float, PopIn, useLoop, type IllustrationProps } from './motion';

const BARS = 26;
/** Fixed "recorded" envelope so the waveform looks like speech, not noise. */
const ENVELOPE = Array.from({ length: BARS }, (_, i) => 0.35 + 0.65 * Math.abs(Math.sin(i * 0.9) * Math.cos(i * 0.37)));

function Bar({ i, t, maxHeight }: { i: number; t: SharedValue<number>; maxHeight: number }) {
  const env = ENVELOPE[i] ?? 0.5;
  const style = useAnimatedStyle(() => {
    const wobble = 0.55 + 0.45 * Math.abs(Math.sin(t.value * Math.PI * 4 + i * 0.55));
    const played = i / BARS < t.value;
    return {
      height: Math.max(4, maxHeight * env * wobble),
      backgroundColor: played ? palette.orange : '#5A524B',
    };
  });
  return <Animated.View style={[styles.bar, style]} />;
}

/** Slide 2, "Say it your way": a live voice-note waveform with GIF and sticker tiles bouncing in. */
export function VoiceIllustration({ active, reduceMotion, size }: IllustrationProps) {
  const t = useLoop(active, reduceMotion, 4200, 0.45);
  return (
    <View style={[styles.stage, { width: size, height: size }]} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
      <PopIn active={active} reduceMotion={reduceMotion} delay={120} from={0.7}>
        <View style={[styles.voice, { width: size * 0.92 }]}>
          <View style={styles.play}>
            <Ionicons name="play" size={18} color={palette.black} />
          </View>
          <View style={styles.wave}>
            {ENVELOPE.map((_, i) => (
              <Bar key={i} i={i} t={t} maxHeight={34} />
            ))}
          </View>
          <Text style={styles.speed}>1.5×</Text>
        </View>
      </PopIn>

      <View style={styles.tiles}>
        <PopIn active={active} reduceMotion={reduceMotion} delay={520} rotate={-8}>
          <Float active={active} reduceMotion={reduceMotion} amplitude={6} duration={2000}>
            <View style={[styles.tile, { backgroundColor: palette.lavender }]}>
              <Text style={styles.gif}>GIF</Text>
            </View>
          </Float>
        </PopIn>
        <PopIn active={active} reduceMotion={reduceMotion} delay={720} rotate={7}>
          <Float active={active} reduceMotion={reduceMotion} delay={300} amplitude={7} duration={2300}>
            <View style={[styles.tile, styles.sticker, { backgroundColor: palette.peach }]}>
              <Ionicons name="happy-outline" size={44} color="#1A1714" />
            </View>
          </Float>
        </PopIn>
        <PopIn active={active} reduceMotion={reduceMotion} delay={920} rotate={-4}>
          <Float active={active} reduceMotion={reduceMotion} delay={600} amplitude={5} duration={1900}>
            <View style={[styles.tile, styles.small, { backgroundColor: palette.mint }]}>
              <Ionicons name="image-outline" size={28} color="#1A1714" />
            </View>
          </Float>
        </PopIn>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  stage: { justifyContent: 'center', alignItems: 'center', gap: 28 },
  voice: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    backgroundColor: palette.charcoal,
    borderColor: palette.border,
    borderWidth: 1,
    borderRadius: 28,
    paddingVertical: 14,
    paddingHorizontal: 14,
  },
  play: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: palette.cream,
    alignItems: 'center',
    justifyContent: 'center',
  },
  wave: { flex: 1, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', height: 40 },
  bar: { width: 3, borderRadius: 2 },
  speed: { color: palette.muted, fontFamily: fonts.sansSemiBold, fontSize: 12 },
  tiles: { flexDirection: 'row', alignItems: 'flex-end', gap: 14 },
  tile: { width: 92, height: 92, borderRadius: 24, alignItems: 'center', justifyContent: 'center' },
  sticker: { width: 104, height: 104, borderRadius: 28 },
  small: { width: 72, height: 72, borderRadius: 20 },
  gif: { fontFamily: fonts.sansBold, fontSize: 26, color: '#1A1714', letterSpacing: 1 },
});
