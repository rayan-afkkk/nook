import { useEffect } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, {
  Easing,
  interpolate,
  interpolateColor,
  Extrapolation,
  useAnimatedStyle,
  useSharedValue,
  withDelay,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import Svg, { Defs, LinearGradient, RadialGradient, Rect, Stop } from 'react-native-svg';

import { PressableScale } from '@/components/ui';
import { haptics } from '@/lib/haptics';
import { darkColors, fonts, radius, typography } from '@/theme';

/* ---------- Headline: words reveal with a staggered fade-up ---------- */

function Word({ word, index, active, reduceMotion }: { word: string; index: number; active: boolean; reduceMotion: boolean }) {
  const p = useSharedValue(reduceMotion ? 1 : 0);
  useEffect(() => {
    if (reduceMotion) {
      p.value = 1;
    } else if (active) {
      p.value = withDelay(120 + index * 80, withTiming(1, { duration: 420, easing: Easing.out(Easing.cubic) }));
    } else {
      p.value = withTiming(0, { duration: 120 });
    }
  }, [active, reduceMotion, index, p]);
  const style = useAnimatedStyle(() => ({
    opacity: p.value,
    transform: [{ translateY: (1 - p.value) * 18 }],
  }));
  return <Animated.Text style={[styles.word, style]}>{word} </Animated.Text>;
}

export function Headline({ text, active, reduceMotion }: { text: string; active: boolean; reduceMotion: boolean }) {
  return (
    <View style={styles.headline} accessible accessibilityRole="header" accessibilityLabel={text}>
      {text.split(' ').map((w, i) => (
        <Word key={`${w}-${i}`} word={w} index={i} active={active} reduceMotion={reduceMotion} />
      ))}
    </View>
  );
}

/* ---------- Progress pill that morphs between dots ---------- */

function Dot({ i, scrollX, width }: { i: number; scrollX: SharedValue<number>; width: number }) {
  const style = useAnimatedStyle(() => {
    const pos = scrollX.value / Math.max(1, width);
    const d = Math.min(1, Math.abs(pos - i));
    return {
      width: interpolate(d, [0, 1], [28, 8], Extrapolation.CLAMP),
      backgroundColor: interpolateColor(d, [0, 1], [darkColors.text, '#4A443E']),
    };
  });
  return <Animated.View style={[styles.dot, style]} />;
}

export function ProgressPill({ count, scrollX, width }: { count: number; scrollX: SharedValue<number>; width: number }) {
  return (
    <View style={styles.dots} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
      {Array.from({ length: count }).map((_, i) => (
        <Dot key={i} i={i} scrollX={scrollX} width={width} />
      ))}
    </View>
  );
}

/* ---------- Background glow that shifts tint per slide ---------- */

function GlowLayer({ tint, i, scrollX, width, size }: { tint: string; i: number; scrollX: SharedValue<number>; width: number; size: number }) {
  const style = useAnimatedStyle(() => {
    const d = Math.abs(scrollX.value / Math.max(1, width) - i);
    return { opacity: interpolate(d, [0, 1], [1, 0], Extrapolation.CLAMP) };
  });
  const id = `glow-${i}`;
  return (
    <Animated.View style={[StyleSheet.absoluteFill, style]}>
      <Svg width={size} height={size}>
        <Defs>
          <RadialGradient id={id} cx="50%" cy="50%" r="50%">
            <Stop offset="0" stopColor={tint} stopOpacity={0.42} />
            <Stop offset="0.55" stopColor={tint} stopOpacity={0.12} />
            <Stop offset="1" stopColor={tint} stopOpacity={0} />
          </RadialGradient>
        </Defs>
        <Rect width={size} height={size} fill={`url(#${id})`} />
      </Svg>
    </Animated.View>
  );
}

export function Glow({ tints, scrollX, width, top }: { tints: readonly string[]; scrollX: SharedValue<number>; width: number; top: number }) {
  const size = width * 1.5;
  return (
    <View
      pointerEvents="none"
      style={[styles.glow, { width: size, height: size, top, left: (width - size) / 2 }]}
    >
      {tints.map((t, i) => (
        <GlowLayer key={i} tint={t} i={i} scrollX={scrollX} width={width} size={size} />
      ))}
    </View>
  );
}

/* ---------- Cream pill button that shimmers once ---------- */

export function ShimmerButton({ title, onPress, shimmer }: { title: string; onPress: () => void; shimmer: boolean }) {
  const sweep = useSharedValue(-1);
  const buttonWidth = useSharedValue(0);
  useEffect(() => {
    if (shimmer) {
      sweep.value = -1;
      sweep.value = withDelay(350, withTiming(1.4, { duration: 1100, easing: Easing.inOut(Easing.quad) }));
    }
  }, [shimmer, sweep]);
  const shine = useAnimatedStyle(() => ({
    transform: [{ translateX: sweep.value * buttonWidth.value }, { skewX: '-20deg' }],
  }));
  return (
    <PressableScale
      accessibilityRole="button"
      accessibilityLabel={title}
      onPress={() => {
        haptics.light();
        onPress();
      }}
      onLayout={(e) => {
        buttonWidth.value = e.nativeEvent.layout.width;
      }}
      style={styles.button}
    >
      <Animated.Text style={styles.buttonText}>{title}</Animated.Text>
      <Animated.View pointerEvents="none" style={[styles.shine, shine]}>
        <Svg width="100%" height="100%">
          <Defs>
            <LinearGradient id="shine" x1="0" y1="0" x2="1" y2="0">
              <Stop offset="0" stopColor="#FFFFFF" stopOpacity={0} />
              <Stop offset="0.5" stopColor="#FFFFFF" stopOpacity={0.85} />
              <Stop offset="1" stopColor="#FFFFFF" stopOpacity={0} />
            </LinearGradient>
          </Defs>
          <Rect width="100%" height="100%" fill="url(#shine)" />
        </Svg>
      </Animated.View>
    </PressableScale>
  );
}

const styles = StyleSheet.create({
  headline: { flexDirection: 'row', flexWrap: 'wrap' },
  word: { ...typography.display, color: darkColors.text },
  dots: { flexDirection: 'row', gap: 6, alignItems: 'center', height: 8 },
  dot: { height: 8, borderRadius: 4 },
  glow: { position: 'absolute' },
  button: {
    height: 58,
    borderRadius: radius.pill,
    backgroundColor: darkColors.primary,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
    alignSelf: 'stretch',
  },
  buttonText: { fontFamily: fonts.sansSemiBold, fontSize: 16, color: darkColors.onPrimary },
  shine: { position: 'absolute', top: 0, bottom: 0, left: 0, width: 90, marginLeft: -90 },
});

