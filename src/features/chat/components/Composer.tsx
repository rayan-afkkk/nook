import { useEffect, useRef, useState } from 'react';
import { Keyboard, Platform, StyleSheet, TextInput, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  FadeIn,
  FadeOut,
  ZoomIn,
  ZoomOut,
  useAnimatedStyle,
  useSharedValue,
  withRepeat,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { Icon, IconButton, ListRow, PressableScale, Sheet, Text, toast } from '@/components/ui';
import { useVoiceRecorder, type VoiceClip } from '@/features/media/useVoiceRecorder';
import { MediaPanel } from '@/features/stickers/MediaPanel';
import type { GiphyItem, GiphyType } from '@/features/stickers/giphy';
import type { Sticker } from '@/features/stickers/packs';
import { describeError } from '@/lib/errors';
import { haptics } from '@/lib/haptics';
import { fonts, motion, radius, spacing, touchTarget, useTheme } from '@/theme';

import { durationLabel } from '../model';
import type { ReplyRef } from '../types';

import { ReplyQuote } from './ReplyQuote';

const CANCEL_DISTANCE = -110;
const INPUT_LINE = touchTarget - 2;
/** Web textareas default to two rows; native multiline inputs already start at one line. */
const singleRow = Platform.OS === 'web' ? { numberOfLines: 1 } : {};

type Props = {
  replyTo: ReplyRef | null;
  replyAuthor: string;
  onCancelReply: () => void;
  onSendText: (text: string) => void;
  onTyping: (typing: boolean) => void;
  onAttach: (source: 'library' | 'camera' | 'file') => void;
  onVoice: (clip: VoiceClip) => void;
  onGiphy: (item: GiphyItem, type: GiphyType) => void;
  onSticker: (s: Sticker) => void;
};

export function Composer({ replyTo, replyAuthor, onCancelReply, onSendText, onTyping, onAttach, onVoice, onGiphy, onSticker }: Props) {
  const { colors } = useTheme();
  const input = useRef<TextInput>(null);
  const [text, setText] = useState('');
  const [panel, setPanel] = useState(false);
  const [attachOpen, setAttachOpen] = useState(false);
  const voice = useVoiceRecorder();
  const [recordingUi, setRecordingUi] = useState(false);
  const dragX = useSharedValue(0);
  const cancelled = useSharedValue(false);
  const pulse = useSharedValue(1);

  const hasText = text.trim().length > 0;

  useEffect(() => {
    if (replyTo) input.current?.focus();
  }, [replyTo]);

  useEffect(() => {
    pulse.value = recordingUi ? withRepeat(withTiming(0.3, { duration: 600 }), -1, true) : 1;
  }, [recordingUi, pulse]);

  const send = () => {
    const t = text.trim();
    if (!t) return;
    haptics.light();
    onSendText(t);
    setText('');
    onTyping(false);
  };

  const beginRecording = async () => {
    haptics.medium();
    setRecordingUi(true);
    setPanel(false);
    Keyboard.dismiss();
    try {
      await voice.start();
    } catch (e) {
      setRecordingUi(false);
      toast.error(describeError(e));
    }
  };

  const endRecording = async (cancel: boolean) => {
    setRecordingUi(false);
    const clip = await voice.stop(cancel);
    if (cancel) {
      haptics.warning();
      return;
    }
    if (clip) {
      haptics.success();
      onVoice(clip);
    } else {
      toast.show('Hold the mic to record a voice note.');
    }
  };

  const hold = Gesture.Pan()
    .activateAfterLongPress(220)
    .onStart(() => {
      cancelled.value = false;
      dragX.value = 0;
      scheduleOnRN(beginRecording);
    })
    .onUpdate((e) => {
      dragX.value = Math.min(0, e.translationX);
      if (!cancelled.value && dragX.value < CANCEL_DISTANCE) {
        cancelled.value = true;
        scheduleOnRN(endRecording, true);
      }
    })
    .onEnd(() => {
      if (!cancelled.value) scheduleOnRN(endRecording, false);
      dragX.value = withSpring(0, motion.spring);
    });
  const tap = Gesture.Tap().onEnd(() => {
    scheduleOnRN(toast.show, 'Hold to record, slide left to cancel.');
  });
  const micGesture = Gesture.Race(hold, tap);

  const slideStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: dragX.value }],
    opacity: 1 + dragX.value / 160,
  }));
  const dotStyle = useAnimatedStyle(() => ({ opacity: pulse.value }));

  return (
    <View style={{ backgroundColor: colors.background }}>
      {replyTo ? (
        <Animated.View entering={FadeIn.duration(180)} exiting={FadeOut.duration(150)} style={[styles.replyBar, { borderTopColor: colors.border }]}>
          <ReplyQuote reply={replyTo} author={replyAuthor} compact />
          <IconButton icon="close" label="Cancel reply" onPress={onCancelReply} size={18} />
        </Animated.View>
      ) : null}
      <View style={[styles.bar, { borderTopColor: colors.border }]}>
        {recordingUi ? (
          <Animated.View entering={FadeIn.duration(150)} style={styles.recording}>
            <Animated.View style={[styles.recDot, { backgroundColor: colors.danger }, dotStyle]} />
            <Text variant="label" style={styles.recTime}>
              {durationLabel(voice.durationMs)}
            </Text>
            <Animated.View style={[styles.slide, slideStyle]}>
              <Icon name="chevron-back" size={16} color="textMuted" />
              <Text variant="caption" color="textMuted">
                Slide to cancel
              </Text>
            </Animated.View>
          </Animated.View>
        ) : (
          <>
            <IconButton icon="add" label="Attach" onPress={() => setAttachOpen(true)} variant="filled" />
            <View style={[styles.field, { backgroundColor: colors.surface, borderColor: colors.border }]}>
              <TextInput
                ref={input}
                value={text}
                onChangeText={(t) => {
                  setText(t);
                  onTyping(t.trim().length > 0);
                }}
                onFocus={() => setPanel(false)}
                onBlur={() => onTyping(false)}
                placeholder="Message"
                placeholderTextColor={colors.textMuted}
                selectionColor={colors.accent}
                cursorColor={colors.accent}
                multiline
                {...singleRow}
                maxLength={4000}
                accessibilityLabel="Message"
                style={[styles.input, { color: colors.text }]}
              />
              <PressableScale
                scaleTo={0.85}
                accessibilityRole="button"
                accessibilityLabel={panel ? 'Show keyboard' : 'Emoji, GIFs and stickers'}
                onPress={() => {
                  haptics.tick();
                  if (panel) {
                    setPanel(false);
                    input.current?.focus();
                  } else {
                    Keyboard.dismiss();
                    setPanel(true);
                  }
                }}
                style={styles.panelButton}
              >
                <Icon name={panel ? 'keypad-outline' : 'happy-outline'} size={22} color="textMuted" />
              </PressableScale>
            </View>
          </>
        )}
        {hasText ? (
          <Animated.View key="send" entering={ZoomIn.duration(160)} exiting={ZoomOut.duration(120)}>
            <PressableScale accessibilityRole="button" accessibilityLabel="Send" onPress={send} style={[styles.round, { backgroundColor: colors.primary }]}>
              <Icon name="arrow-up" size={22} color={colors.onPrimary} />
            </PressableScale>
          </Animated.View>
        ) : (
          <Animated.View key="mic" entering={ZoomIn.duration(160)} exiting={ZoomOut.duration(120)}>
            <GestureDetector gesture={micGesture}>
              <View
                accessible
                accessibilityRole="button"
                accessibilityLabel="Record voice note"
                accessibilityHint="Hold to record, release to send, slide left to cancel"
                style={[styles.round, { backgroundColor: recordingUi ? colors.danger : colors.primary }, recordingUi && styles.recordingMic]}
              >
                <Icon name="mic" size={22} color={colors.onPrimary} />
              </View>
            </GestureDetector>
          </Animated.View>
        )}
      </View>
      {panel ? (
        <MediaPanel
          height={320}
          onEmoji={(e) => {
            haptics.tick();
            setText((t) => t + e);
          }}
          onGiphy={(item, type) => {
            haptics.light();
            onGiphy(item, type);
          }}
          onSticker={(s) => {
            haptics.light();
            onSticker(s);
          }}
        />
      ) : null}

      <Sheet visible={attachOpen} onClose={() => setAttachOpen(false)} accessibilityLabel="Attach">
        <ListRow icon="images-outline" title="Photo" subtitle="From your gallery" showChevron={false} onPress={() => { setAttachOpen(false); onAttach('library'); }} />
        <ListRow icon="camera-outline" title="Camera" subtitle="Take a photo now" showChevron={false} onPress={() => { setAttachOpen(false); onAttach('camera'); }} />
        <ListRow icon="document-outline" title="File" subtitle="Up to 10 MB" showChevron={false} onPress={() => { setAttachOpen(false); onAttach('file'); }} />
      </Sheet>
    </View>
  );
}

const styles = StyleSheet.create({
  replyBar: { flexDirection: 'row', alignItems: 'center', gap: spacing.xs, paddingLeft: spacing.md, paddingRight: spacing.xs, paddingTop: spacing.xs, borderTopWidth: StyleSheet.hairlineWidth },
  bar: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: spacing.xs,
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.xs,
    borderTopWidth: StyleSheet.hairlineWidth,
  },
  field: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'flex-end',
    borderRadius: radius.card,
    borderWidth: 1,
    paddingLeft: spacing.md,
    minHeight: touchTarget,
    overflow: 'hidden',
  },
  // One line = exactly 46pt (12 + 22 + 12), matching the 48pt round buttons beside it (field has a 1pt border).
  input: {
    flex: 1,
    fontFamily: fonts.sans,
    fontSize: 16,
    lineHeight: 22,
    minHeight: INPUT_LINE,
    maxHeight: 140,
    paddingTop: 12,
    paddingBottom: 12,
    textAlignVertical: 'center',
  },
  panelButton: { width: 44, height: INPUT_LINE, alignItems: 'center', justifyContent: 'center' },
  round: { width: touchTarget, height: touchTarget, borderRadius: touchTarget / 2, alignItems: 'center', justifyContent: 'center' },
  recordingMic: { transform: [{ scale: 1.15 }] },
  recording: { flex: 1, flexDirection: 'row', alignItems: 'center', gap: spacing.sm, minHeight: touchTarget, paddingLeft: spacing.sm },
  recDot: { width: 10, height: 10, borderRadius: 5 },
  recTime: { minWidth: 44 },
  slide: { flexDirection: 'row', alignItems: 'center', gap: 2, marginLeft: 'auto', marginRight: spacing.sm },
});
