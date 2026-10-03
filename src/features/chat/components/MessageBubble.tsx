import { Image } from 'expo-image';
import { memo, useRef, type ReactNode } from 'react';
import { Pressable, StyleSheet, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  FadeIn,
  interpolate,
  useAnimatedStyle,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { Icon, Text } from '@/components/ui';
import { thumbnailUrl } from '@/features/media/cloudinary';
import { haptics } from '@/lib/haptics';
import { motion, radius, spacing, useTheme } from '@/theme';

import { timeLabel } from '../model';
import type { Message } from '../types';

import { FileCard } from './FileCard';
import { ReactionPills } from './ReactionPills';
import { ReplyQuote } from './ReplyQuote';
import { VoiceBubble } from './VoiceBubble';

const REPLY_TRIGGER = 56;

export type BubbleProps = {
  message: Message;
  me: string;
  mine: boolean;
  /** Group chats: show the sender's name above the first bubble of a run. */
  senderName?: string | null;
  replyAuthor?: string;
  /** Next message is from the same sender (tighter spacing, no tail). */
  chained: boolean;
  /** "Seen" line under my last message. */
  footer?: string | null;
  now: number;
  onReply: (m: Message) => void;
  onLongPress: (m: Message, rect?: BubbleRect) => void;
  onOpenImage: (m: Message) => void;
  onToggleReaction: (m: Message, emoji: string) => void;
};

/** Where the bubble is on screen (window coordinates), so the long-press menu can lift it in place. */
export type BubbleRect = { x: number; y: number; width: number; height: number };

function sizeFor(media: { width?: number; height?: number }, max: number) {
  const w = media.width ?? max;
  const h = media.height ?? max;
  const scale = Math.min(max / w, (max * 1.3) / h, 1);
  return { width: Math.round(w * scale), height: Math.round(h * scale) };
}

function BubbleBase(props: BubbleProps) {
  const { message: m, me, mine, senderName, replyAuthor, chained, footer, now, onReply, onLongPress, onOpenImage, onToggleReaction } = props;
  const { width } = useWindowDimensions();
  const maxMedia = Math.min(260, width * 0.66);
  const x = useSharedValue(0);
  const armed = useSharedValue(false);
  const press = useSharedValue(1);
  const anchor = useRef<View>(null);
  const squeeze = useAnimatedStyle(() => ({ transform: [{ scale: press.value }] }));
  const longPress = () => {
    haptics.medium();
    press.set(withSpring(1, motion.spring));
    const node = anchor.current;
    if (!node) {
      onLongPress(m);
      return;
    }
    node.measureInWindow((bx, by, bw, bh) => onLongPress(m, { x: bx, y: by, width: bw, height: bh }));
  };

  const pan = Gesture.Pan()
    .activeOffsetX(14)
    .failOffsetX(-14)
    .failOffsetY([-12, 12])
    .onUpdate((e) => {
      x.value = Math.max(0, Math.min(e.translationX * 0.6, REPLY_TRIGGER + 16));
      if (!armed.value && x.value >= REPLY_TRIGGER) {
        armed.value = true;
        scheduleOnRN(haptics.light);
      } else if (armed.value && x.value < REPLY_TRIGGER) {
        armed.value = false;
      }
    })
    .onEnd(() => {
      if (armed.value) scheduleOnRN(onReply, m);
      armed.value = false;
      x.value = withSpring(0, motion.spring);
    });

  const rowStyle = useAnimatedStyle(() => ({ transform: [{ translateX: x.value }] }));
  const arrowStyle = useAnimatedStyle(() => ({
    opacity: interpolate(x.value, [0, REPLY_TRIGGER], [0, 1]),
    transform: [{ scale: interpolate(x.value, [0, REPLY_TRIGGER], [0.6, 1], 'clamp') }],
  }));

  return (
    <Animated.View entering={m.pending ? FadeIn.duration(180) : undefined} style={[styles.wrap, chained ? styles.chained : styles.spaced]}>
      {senderName && !mine ? (
        <Text variant="captionBold" color="textMuted" style={styles.sender}>
          {senderName}
        </Text>
      ) : null}
      <GestureDetector gesture={pan}>
        <View>
          <Animated.View style={[styles.replyArrow, arrowStyle]} pointerEvents="none">
            <Icon name="arrow-undo" size={18} color="textMuted" />
          </Animated.View>
          <Animated.View style={[styles.row, mine ? styles.rowMine : styles.rowTheirs, rowStyle]}>
            <View ref={anchor} collapsable={false} style={styles.anchor}>
              <Animated.View style={squeeze}>
                <BubbleBody
                  message={m}
                  me={me}
                  mine={mine}
                  senderName={senderName}
                  replyAuthor={replyAuthor}
                  chained={chained}
                  now={now}
                  maxMedia={maxMedia}
                  onOpenImage={onOpenImage}
                  onLongPress={longPress}
                  onPressIn={() => {
                    press.set(withTiming(0.96, { duration: 350 }));
                  }}
                  onPressOut={() => {
                    press.set(withSpring(1, motion.spring));
                  }}
                />
              </Animated.View>
            </View>
          </Animated.View>
        </View>
      </GestureDetector>
      <ReactionPills reactions={m.reactions} me={me} mine={mine} onToggle={(e) => onToggleReaction(m, e)} />
      {footer ? (
        <Text variant="micro" color="textMuted" style={styles.footer} accessibilityLiveRegion="polite">
          {footer}
        </Text>
      ) : null}
    </Animated.View>
  );
}

export const MessageBubble = memo(BubbleBase);

type BodyProps = {
  message: Message;
  me: string;
  mine: boolean;
  senderName?: string | null;
  replyAuthor?: string;
  chained: boolean;
  now: number;
  maxMedia: number;
  onOpenImage: (m: Message) => void;
  onLongPress?: () => void;
  onPressIn?: () => void;
  onPressOut?: () => void;
  /** Rendered inside the long-press menu: not interactive, fills its measured width. */
  preview?: boolean;
};

/** The bubble itself (no swipe, reactions or footer). Shared by the list and the long-press menu. */
export function BubbleBody({ message: m, mine, senderName, replyAuthor, chained, now, maxMedia, onOpenImage, onLongPress, onPressIn, onPressOut, preview }: BodyProps): ReactNode {
  const { colors } = useTheme();
  const time = m.createdAt ? timeLabel(m.createdAt.toMillis(), now) : '';
  const bare = m.kind === 'sticker' || m.kind === 'gif' || m.kind === 'image';
  const fg = mine ? colors.onPrimary : colors.text;

  const content = (() => {
    switch (m.kind) {
      case 'image': {
        const size = sizeFor(m.media ?? {}, maxMedia);
        return (
          <Pressable accessibilityRole="imagebutton" accessibilityLabel="Photo. Open" onPress={() => onOpenImage(m)} onLongPress={onLongPress} delayLongPress={320} disabled={preview}>
            <Image
              source={{ uri: thumbnailUrl(m.media?.url ?? '', size.width * 2) }}
              style={[styles.media, size, { backgroundColor: colors.surfaceRaised }]}
              contentFit="cover"
              transition={200}
              cachePolicy="disk"
              recyclingKey={m.id}
            />
          </Pressable>
        );
      }
      case 'gif':
      case 'sticker': {
        const sticker = m.kind === 'sticker';
        const size = sticker ? { width: 150, height: 150 } : sizeFor(m.media ?? {}, maxMedia);
        return (
          <View>
            <Image
              source={{ uri: m.media?.url }}
              placeholder={m.media?.previewUrl ? { uri: m.media.previewUrl } : undefined}
              style={[size, sticker ? null : styles.media, sticker ? null : { backgroundColor: colors.surfaceRaised }]}
              contentFit="contain"
              autoplay
              cachePolicy="disk"
              recyclingKey={m.id}
              accessibilityLabel={sticker ? 'Sticker' : 'GIF'}
            />
            {!sticker ? (
              <View style={[styles.gifChip, { backgroundColor: colors.overlay }]}>
                <Text variant="micro" style={{ color: '#fff' }}>
                  GIF
                </Text>
              </View>
            ) : null}
          </View>
        );
      }
      case 'file':
        return m.media ? <FileCard media={m.media} mine={mine} /> : null;
      case 'voice':
        return m.media ? <VoiceBubble media={m.media} mine={mine} /> : null;
      default:
        return (
          <Text variant="bodyLarge" style={{ color: fg, fontSize: 15, lineHeight: 21 }} selectable={false}>
            {m.text}
          </Text>
        );
    }
  })();

  return (
    <Pressable
      onLongPress={onLongPress}
      onPressIn={onPressIn}
      onPressOut={onPressOut}
      delayLongPress={320}
      disabled={preview}
      accessibilityRole="text"
      accessibilityHint="Long press for reactions and more. Swipe right to reply."
      accessibilityLabel={`${mine ? 'You' : (senderName ?? 'Them')}: ${m.kind === 'text' ? (m.text ?? '') : m.kind}, ${time}`}
      style={[
        styles.bubble,
        bare
          ? styles.bare
          : {
              backgroundColor: mine ? colors.primary : colors.surface,
              borderColor: mine ? colors.primary : colors.border,
            },
        !bare && !chained && (mine ? styles.tailMine : styles.tailTheirs),
        preview && styles.fill,
      ]}
    >
      {m.forwarded ? (
        <View style={styles.forwarded}>
          <Icon name="arrow-redo-outline" size={12} color={mine ? colors.onPrimary : colors.textMuted} />
          <Text variant="micro" style={{ color: mine ? colors.onPrimary : colors.textMuted, opacity: 0.8 }}>
            Forwarded
          </Text>
        </View>
      ) : null}
      {m.replyTo ? <ReplyQuote reply={m.replyTo} author={replyAuthor ?? 'Message'} onMine={mine && !bare} /> : null}
      {content}
      <View style={[styles.meta, bare && styles.metaBare, bare && { backgroundColor: colors.overlay }]}>
        {m.expireAt ? <Icon name="timer-outline" size={11} color={bare ? '#fff' : mine ? colors.onPrimary : colors.textMuted} /> : null}
        <Text variant="micro" style={{ color: bare ? '#fff' : mine ? colors.onPrimary : colors.textMuted, opacity: 0.75 }}>
          {time}
        </Text>
        {mine && m.pending ? (
          <Icon name="time-outline" size={11} color={bare ? '#fff' : colors.onPrimary} />
        ) : null}
      </View>
    </Pressable>
  );
}


const styles = StyleSheet.create({
  wrap: { paddingHorizontal: spacing.md },
  spaced: { marginTop: spacing.sm },
  chained: { marginTop: 3 },
  sender: { marginLeft: spacing.sm, marginBottom: 3 },
  row: { flexDirection: 'row' },
  rowMine: { justifyContent: 'flex-end' },
  rowTheirs: { justifyContent: 'flex-start' },
  replyArrow: { position: 'absolute', left: 0, top: 0, bottom: 0, justifyContent: 'center' },
  bubble: {
    maxWidth: '100%',
    borderRadius: 20,
    borderWidth: 1,
    paddingHorizontal: 12,
    paddingTop: 8,
    paddingBottom: 6,
  },
  bare: { padding: 0, borderWidth: 0, backgroundColor: 'transparent' },
  tailMine: { borderBottomRightRadius: 6 },
  tailTheirs: { borderBottomLeftRadius: 6 },
  media: { borderRadius: 18 },
  gifChip: { position: 'absolute', left: 8, top: 8, borderRadius: radius.pill, paddingHorizontal: 6, paddingVertical: 2 },
  meta: { flexDirection: 'row', alignItems: 'center', gap: 3, alignSelf: 'flex-end', marginTop: 2 },
  metaBare: { position: 'absolute', right: 8, bottom: 8, borderRadius: radius.pill, paddingHorizontal: 6, paddingVertical: 2 },
  forwarded: { flexDirection: 'row', alignItems: 'center', gap: 4, marginBottom: 4 },
  anchor: { maxWidth: '80%' },
  fill: { maxWidth: '100%' },
  footer: { alignSelf: 'flex-end', marginRight: spacing.xs, marginTop: 3 },
});
