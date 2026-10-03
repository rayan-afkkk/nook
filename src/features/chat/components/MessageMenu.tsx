import { useEffect, useState, type ReactNode } from 'react';
import { BackHandler, Modal, Pressable, StyleSheet, View, useWindowDimensions } from 'react-native';
import Animated, {
  interpolate,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { Icon, PressableScale, Text, type IconName } from '@/components/ui';
import { haptics } from '@/lib/haptics';
import { radius, spacing, useTheme } from '@/theme';

import type { BubbleRect } from './MessageBubble';

export const QUICK_REACTIONS = ['❤️', '😂', '😮', '😢', '🙏', '👍', '🔥'];

const REACTIONS_H = 52;
const GAP = 8;
const ROW_H = 48;
const MENU_W = 220;
const OPEN = { damping: 20, stiffness: 240, mass: 0.9 };

export type MenuAction = { key: string; label: string; icon: IconName; destructive?: boolean; onPress: () => void };

type Props = {
  /** The long-pressed bubble and where it was on screen; null hides the menu. */
  target: { rect: BubbleRect; mine: boolean } | null;
  /** The same bubble, rendered non-interactively. */
  preview: ReactNode;
  myReaction?: string;
  actions: MenuAction[];
  onReact: (emoji: string | null) => void;
  onClose: () => void;
};

function Emoji({ emoji, index, active, open, onPress }: { emoji: string; index: number; active: boolean; open: SharedValue<number>; onPress: () => void }) {
  const { colors } = useTheme();
  const pop = useSharedValue(0);
  useEffect(() => {
    pop.value = withDelay(60 + index * 28, withSpring(1, { damping: 12, stiffness: 260 }));
  }, [index, pop]);
  const style = useAnimatedStyle(() => ({
    opacity: Math.min(pop.value, open.value),
    transform: [{ scale: 0.3 + 0.7 * pop.value }, { translateY: (1 - pop.value) * 8 }],
  }));
  return (
    <Animated.View style={style}>
      <PressableScale
        scaleTo={0.8}
        accessibilityRole="button"
        accessibilityLabel={active ? `Remove ${emoji} reaction` : `React ${emoji}`}
        onPress={onPress}
        style={[styles.emoji, active && { backgroundColor: colors.surfaceRaised }]}
      >
        <Text style={styles.emojiText}>{emoji}</Text>
      </PressableScale>
    </Animated.View>
  );
}

/**
 * iMessage/WhatsApp-style long-press menu: the screen dims, the bubble lifts in place (and slides to fit
 * on screen), reactions pop in one by one above it and the actions fade in below. All on the UI thread.
 */
export function MessageMenu({ target, preview, myReaction, actions, onReact, onClose }: Props) {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const { width: W, height: H } = useWindowDimensions();
  const reduce = useReducedMotion();
  const open = useSharedValue(0);
  const [shown, setShown] = useState(target);

  // Keep the last target mounted while the close animation plays.
  if (target && target !== shown) setShown(target);

  useEffect(() => {
    if (target) {
      open.value = reduce ? withTiming(1, { duration: 150 }) : withSpring(1, OPEN);
    } else if (shown) {
      open.value = withTiming(0, { duration: 170 }, (done) => {
        if (done) scheduleOnRN(setShown, null);
      });
    }
  }, [target, shown, open, reduce]);

  useEffect(() => {
    if (!target) return;
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      onClose();
      return true;
    });
    return () => sub.remove();
  }, [target, onClose]);

  // Layout: reactions above the bubble, actions below; slide the group so all of it fits.
  const rect = shown?.rect ?? { x: 0, y: 0, width: 0, height: 0 };
  const mine = shown?.mine ?? false;
  const previewH = Math.min(rect.height, H * 0.42);
  const menuH = actions.length * ROW_H + spacing.xs * 2;
  const minTop = insets.top + spacing.sm;
  const maxBottom = H - insets.bottom - spacing.sm;
  let targetY = rect.y;
  if (targetY - REACTIONS_H - GAP < minTop) targetY = minTop + REACTIONS_H + GAP;
  if (targetY + previewH + GAP + menuH > maxBottom) targetY = maxBottom - menuH - GAP - previewH;
  const shift = targetY - rect.y;
  const sideStyle = mine ? { right: Math.max(spacing.sm, W - rect.x - rect.width) } : { left: Math.max(spacing.sm, rect.x) };

  const backdrop = useAnimatedStyle(() => ({ opacity: open.value }));
  const lifted = useAnimatedStyle(() => ({
    transform: [{ translateY: shift * open.value }, { scale: 1 + 0.035 * open.value }],
  }));
  const reactionsStyle = useAnimatedStyle(() => ({
    opacity: open.value,
    transform: [{ translateY: shift * open.value + (1 - open.value) * 10 }, { scale: interpolate(open.value, [0, 1], [0.85, 1]) }],
  }));
  const menuStyle = useAnimatedStyle(() => ({
    opacity: open.value,
    transform: [{ translateY: shift * open.value - (1 - open.value) * 12 }, { scale: interpolate(open.value, [0, 1], [0.9, 1]) }],
  }));

  if (!shown) return null;

  const pick = (fn: () => void) => () => {
    haptics.tick();
    onClose();
    fn();
  };

  return (
    <Modal transparent visible statusBarTranslucent navigationBarTranslucent animationType="none" onRequestClose={onClose}>
      <Animated.View style={[StyleSheet.absoluteFill, { backgroundColor: colors.overlay }, backdrop]}>
        <Pressable style={StyleSheet.absoluteFill} onPress={onClose} accessibilityRole="button" accessibilityLabel="Close menu" />
      </Animated.View>

      <Animated.View
        style={[styles.reactions, sideStyle, { top: rect.y - REACTIONS_H - GAP, backgroundColor: colors.surface, borderColor: colors.border, transformOrigin: mine ? 'right bottom' : 'left bottom' }, reactionsStyle]}
        accessibilityRole="toolbar"
        accessibilityLabel="React"
      >
        {QUICK_REACTIONS.map((e, i) => (
          <Emoji
            key={e}
            emoji={e}
            index={i}
            open={open}
            active={myReaction === e}
            onPress={() => {
              haptics.medium();
              onClose();
              onReact(myReaction === e ? null : e);
            }}
          />
        ))}
      </Animated.View>

      <Animated.View
        pointerEvents="none"
        style={[styles.preview, { top: rect.y, left: rect.x, width: rect.width, height: previewH, transformOrigin: mine ? 'right center' : 'left center' }, lifted]}
      >
        {preview}
      </Animated.View>

      <Animated.View
        style={[
          styles.menu,
          sideStyle,
          { top: rect.y + previewH + GAP, backgroundColor: colors.surface, borderColor: colors.border, transformOrigin: mine ? 'right top' : 'left top' },
          menuStyle,
        ]}
        accessibilityRole="menu"
      >
        {actions.map((a, i) => (
          <View key={a.key}>
            {i > 0 ? <View style={[styles.divider, { backgroundColor: colors.border }]} /> : null}
            <PressableScale scaleTo={0.97} accessibilityRole="menuitem" accessibilityLabel={a.label} onPress={pick(a.onPress)} style={styles.row}>
              <Text variant="label" color={a.destructive ? 'danger' : 'text'} style={styles.rowLabel}>
                {a.label}
              </Text>
              <Icon name={a.icon} size={18} color={a.destructive ? 'danger' : 'text'} />
            </PressableScale>
          </View>
        ))}
      </Animated.View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  reactions: {
    position: 'absolute',
    height: REACTIONS_H,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 4,
    borderRadius: radius.pill,
    borderWidth: 1,
    elevation: 8,
    shadowColor: '#000',
    shadowOpacity: 0.3,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 4 },
  },
  emoji: { width: 42, height: 44, borderRadius: radius.pill, alignItems: 'center', justifyContent: 'center' },
  emojiText: { fontSize: 25, lineHeight: 32 },
  preview: { position: 'absolute', overflow: 'hidden' },
  menu: {
    position: 'absolute',
    width: MENU_W,
    borderRadius: radius.md,
    borderWidth: 1,
    paddingVertical: spacing.xs,
    elevation: 8,
    shadowColor: '#000',
    shadowOpacity: 0.3,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 4 },
  },
  row: { minHeight: ROW_H, flexDirection: 'row', alignItems: 'center', paddingHorizontal: spacing.md, gap: spacing.sm },
  rowLabel: { flex: 1 },
  divider: { height: StyleSheet.hairlineWidth, marginHorizontal: spacing.md },
});
