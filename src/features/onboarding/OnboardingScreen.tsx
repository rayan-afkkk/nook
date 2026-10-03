import { NavigationBar } from 'expo-navigation-bar';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, {
  Easing,
  useAnimatedReaction,
  useAnimatedRef,
  useAnimatedScrollHandler,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { Logo } from '@/components/brand/Logo';
import { haptics } from '@/lib/haptics';
import { darkColors, fonts, spacing, typography } from '@/theme';

import { Glow, Headline, ProgressPill, ShimmerButton } from './components';
import { BubblesIllustration } from './illustrations/BubblesIllustration';
import { CallsIllustration } from './illustrations/CallsIllustration';
import { LockIllustration } from './illustrations/LockIllustration';
import { VoiceIllustration } from './illustrations/VoiceIllustration';
import type { IllustrationProps } from './illustrations/motion';
import { SLIDES, type SlideId } from './slides';

const ART: Record<SlideId, (p: IllustrationProps) => React.JSX.Element> = {
  private: BubblesIllustration,
  voice: VoiceIllustration,
  calls: CallsIllustration,
  lock: LockIllustration,
};

/** Illustration travels at this fraction of the page speed (text moves at full speed). */
const PARALLAX = 0.5;

type PageProps = {
  index: number;
  scrollX: SharedValue<number>;
  width: number;
  artSize: number;
  active: boolean;
  reduceMotion: boolean;
};

function Page({ index, scrollX, width, artSize, active, reduceMotion }: PageProps) {
  const slide = SLIDES[index]!;
  const Art = ART[slide.id];

  // Reduce motion: hold each page still and crossfade instead of sliding.
  const page = useAnimatedStyle(() => {
    if (!reduceMotion) return {};
    const offset = scrollX.value - index * width;
    return { opacity: 1 - Math.min(1, Math.abs(offset) / width), transform: [{ translateX: offset }] };
  });
  const art = useAnimatedStyle(() => {
    if (reduceMotion) return {};
    const offset = scrollX.value - index * width;
    const d = Math.min(1, Math.abs(offset) / width);
    return { transform: [{ translateX: offset * PARALLAX }, { scale: 1 - 0.08 * d }], opacity: 1 - 0.6 * d };
  });

  return (
    <Animated.View style={[{ width }, styles.page, page]} accessibilityElementsHidden={!active} importantForAccessibility={active ? 'auto' : 'no-hide-descendants'}>
      <Animated.View style={[styles.art, art]}>
        <Art active={active} reduceMotion={reduceMotion} size={artSize} />
      </Animated.View>
      <View style={styles.copy}>
        <Headline text={slide.title} active={active} reduceMotion={reduceMotion} />
        <Text allowFontScaling={false} style={styles.body}>
          {slide.body}
        </Text>
      </View>
    </Animated.View>
  );
}

type Props = {
  /** Called when the person taps Get started or Skip. */
  onDone: () => void;
  /** Show the animated logo splash first (first launch and replays). */
  withSplash?: boolean;
};

export function OnboardingScreen({ onDone, withSplash = true }: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduceMotion = useReducedMotion();
  const scrollRef = useAnimatedRef<Animated.ScrollView>();
  const scrollX = useSharedValue(0);
  const [index, setIndex] = useState(0);
  const [splashDone, setSplashDone] = useState(!withSplash);
  const [reachedEnd, setReachedEnd] = useState(false);
  const intro = useSharedValue(withSplash ? 0 : 1);
  const finished = useRef(false);

  const artSize = Math.min(width - spacing.xl * 2, height * 0.42, 340);
  const last = SLIDES.length - 1;

  const onSplashDrawn = useCallback(() => {
    const go = () => {
      intro.value = withTiming(1, { duration: reduceMotion ? 200 : 520, easing: Easing.out(Easing.cubic) });
      setSplashDone(true);
    };
    setTimeout(go, reduceMotion ? 150 : 380);
  }, [intro, reduceMotion]);

  // Reduce motion: the logo is shown static, so move on after a short beat.
  useEffect(() => {
    if (withSplash && reduceMotion) {
      const t = setTimeout(onSplashDrawn, 400);
      return () => clearTimeout(t);
    }
  }, [withSplash, reduceMotion, onSplashDrawn]);

  const onIndexChange = useCallback(
    (next: number) => {
      haptics.tick();
      setIndex(next);
      if (next === last) setReachedEnd(true);
    },
    [last],
  );

  const onScroll = useAnimatedScrollHandler((e) => {
    scrollX.value = e.contentOffset.x;
  });

  useAnimatedReaction(
    () => Math.round(scrollX.value / Math.max(1, width)),
    (current, previous) => {
      if (previous !== null && current !== previous) scheduleOnRN(onIndexChange, current);
    },
    [width, onIndexChange],
  );

  const finish = useCallback(() => {
    if (finished.current) return;
    finished.current = true;
    haptics.success();
    onDone();
  }, [onDone]);

  const next = () => {
    if (index >= last) {
      finish();
      return;
    }
    scrollRef.current?.scrollTo({ x: (index + 1) * width, animated: !reduceMotion });
  };

  const splashStyle = useAnimatedStyle(() => ({
    opacity: 1 - intro.value,
    transform: [{ scale: 1 - 0.12 * intro.value }, { translateY: -40 * intro.value }],
  }));
  const contentStyle = useAnimatedStyle(() => ({
    opacity: intro.value,
    transform: [{ translateY: 24 * (1 - intro.value) }],
  }));

  return (
    <View style={styles.root}>
      <StatusBar style="light" />
      <NavigationBar style="dark" />
      <Animated.View style={[StyleSheet.absoluteFill, contentStyle]} pointerEvents={splashDone ? 'auto' : 'none'}>
        <Glow tints={SLIDES.map((s) => s.tint)} scrollX={scrollX} width={width} top={insets.top + artSize * 0.5 - width * 0.75 + 70} />

        <View style={[styles.topBar, { paddingTop: insets.top + spacing.xs }]}>
          <Logo size={30} />
          <Pressable
            onPress={finish}
            accessibilityRole="button"
            accessibilityLabel="Skip introduction"
            hitSlop={8}
            style={styles.skip}
          >
            <Text allowFontScaling={false} style={styles.skipText}>Skip</Text>
          </Pressable>
        </View>

        <Animated.ScrollView
          ref={scrollRef}
          horizontal
          pagingEnabled
          bounces={false}
          overScrollMode="never"
          showsHorizontalScrollIndicator={false}
          onScroll={onScroll}
          scrollEventThrottle={16}
          style={styles.flex}
          accessibilityLabel="Introduction"
        >
          {SLIDES.map((s, i) => (
            <Page
              key={s.id}
              index={i}
              scrollX={scrollX}
              width={width}
              artSize={artSize}
              active={splashDone && index === i}
              reduceMotion={reduceMotion}
            />
          ))}
        </Animated.ScrollView>

        <View style={[styles.footer, { paddingBottom: insets.bottom + spacing.lg }]}>
          <ProgressPill count={SLIDES.length} scrollX={scrollX} width={width} />
          <ShimmerButton title={index >= last ? 'Get started' : 'Continue'} onPress={next} shimmer={reachedEnd && !reduceMotion} />
        </View>
      </Animated.View>

      {withSplash ? (
        <Animated.View style={[StyleSheet.absoluteFill, styles.splash, splashStyle]} pointerEvents="none">
          <Logo size={120} animated={!reduceMotion} onDrawn={onSplashDrawn} />
          <SplashWordmark reduceMotion={reduceMotion} />
        </Animated.View>
      ) : null}
    </View>
  );
}

function SplashWordmark({ reduceMotion }: { reduceMotion: boolean }) {
  const p = useSharedValue(reduceMotion ? 1 : 0);
  useEffect(() => {
    if (!reduceMotion) p.value = withDelay(350, withTiming(1, { duration: 500 }));
  }, [p, reduceMotion]);
  const style = useAnimatedStyle(() => ({ opacity: p.value, transform: [{ translateY: 8 * (1 - p.value) }] }));
  return <Animated.Text allowFontScaling={false} style={[styles.wordmark, style]}>NOOK</Animated.Text>;
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: darkColors.background },
  flex: { flex: 1 },
  topBar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: spacing.lg,
  },
  skip: { minWidth: 48, minHeight: 48, alignItems: 'flex-end', justifyContent: 'center' },
  skipText: { fontFamily: fonts.sansSemiBold, fontSize: 15, color: darkColors.textMuted },
  page: { flex: 1, paddingHorizontal: spacing.xl, overflow: 'hidden' },
  art: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  copy: { gap: spacing.sm, paddingBottom: spacing.lg, minHeight: 190, justifyContent: 'flex-end' },
  body: { ...typography.bodyLarge, color: darkColors.textMuted, maxWidth: 340 },
  footer: { paddingHorizontal: spacing.xl, gap: spacing.xl, paddingTop: spacing.sm },
  splash: { alignItems: 'center', justifyContent: 'center', backgroundColor: darkColors.background, gap: spacing.md },
  wordmark: { fontFamily: fonts.serif, fontSize: 34, letterSpacing: 6, color: darkColors.text },
});
