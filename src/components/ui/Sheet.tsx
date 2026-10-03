import { useEffect, useState, type PropsWithChildren } from 'react';
import { BackHandler, Modal, Pressable, StyleSheet, View } from 'react-native';
import Animated, { useAnimatedStyle, useSharedValue, withSpring, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { motion, radius, spacing, useTheme } from '@/theme';

import { Button } from './Button';
import { Text } from './Text';

type SheetProps = PropsWithChildren<{ visible: boolean; onClose: () => void; accessibilityLabel: string }>;

/** Bottom sheet with a fading backdrop and a spring slide-up, all on the UI thread. */
export function Sheet({ visible, onClose, children, accessibilityLabel }: SheetProps) {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const [mounted, setMounted] = useState(visible);
  const progress = useSharedValue(0);

  // Mount as soon as it should show; stay mounted until the closing animation ends.
  if (visible && !mounted) setMounted(true);

  useEffect(() => {
    if (visible) {
      progress.value = withSpring(1, motion.spring);
    } else if (mounted) {
      progress.value = withTiming(0, { duration: motion.fast }, (done) => {
        if (done) scheduleOnRN(setMounted, false);
      });
    }
  }, [visible, mounted, progress]);

  useEffect(() => {
    if (!visible) return;
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      onClose();
      return true;
    });
    return () => sub.remove();
  }, [visible, onClose]);

  const backdrop = useAnimatedStyle(() => ({ opacity: progress.value }));
  const sheet = useAnimatedStyle(() => ({ transform: [{ translateY: (1 - progress.value) * 400 }] }));

  if (!mounted) return null;
  return (
    <Modal transparent visible statusBarTranslucent navigationBarTranslucent onRequestClose={onClose} animationType="none">
      <Animated.View style={[StyleSheet.absoluteFill, { backgroundColor: colors.overlay }, backdrop]}>
        <Pressable style={StyleSheet.absoluteFill} onPress={onClose} accessibilityLabel="Close" accessibilityRole="button" />
      </Animated.View>
      <View style={styles.bottom} pointerEvents="box-none">
        <Animated.View
          accessibilityViewIsModal
          accessibilityLabel={accessibilityLabel}
          style={[
            styles.sheet,
            { backgroundColor: colors.surface, borderColor: colors.border, paddingBottom: insets.bottom + spacing.lg },
            sheet,
          ]}
        >
          <View style={[styles.grabber, { backgroundColor: colors.border }]} />
          {children}
        </Animated.View>
      </View>
    </Modal>
  );
}

type ConfirmProps = {
  visible: boolean;
  title: string;
  message: string;
  confirmLabel: string;
  destructive?: boolean;
  loading?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
};

export function ConfirmSheet({ visible, title, message, confirmLabel, destructive, loading, onConfirm, onCancel }: ConfirmProps) {
  return (
    <Sheet visible={visible} onClose={onCancel} accessibilityLabel={title}>
      <View style={styles.confirm}>
        <Text variant="headline">{title}</Text>
        <Text variant="body" color="textMuted">
          {message}
        </Text>
        <View style={styles.actions}>
          <Button
            title={confirmLabel}
            variant={destructive ? 'destructive' : 'primary'}
            loading={loading}
            onPress={onConfirm}
          />
          <Button title="Cancel" variant="ghost" onPress={onCancel} disabled={loading} />
        </View>
      </View>
    </Sheet>
  );
}

const styles = StyleSheet.create({
  bottom: { flex: 1, justifyContent: 'flex-end' },
  sheet: {
    borderTopLeftRadius: radius.card,
    borderTopRightRadius: radius.card,
    borderWidth: 1,
    borderBottomWidth: 0,
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.sm,
  },
  grabber: { alignSelf: 'center', width: 40, height: 4, borderRadius: 2, marginBottom: spacing.md },
  confirm: { gap: spacing.sm },
  actions: { marginTop: spacing.md, gap: spacing.xs },
});
