import { Image } from 'expo-image';
import { Modal, StyleSheet, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useSharedValue, withSpring, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { IconButton } from '@/components/ui';
import { motion, spacing } from '@/theme';

/** Full-screen photo: pinch to zoom, drag to pan, swipe down to close. */
export function ImageViewer({ uri, onClose }: { uri: string | null; onClose: () => void }) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const scale = useSharedValue(1);
  const savedScale = useSharedValue(1);
  const tx = useSharedValue(0);
  const ty = useSharedValue(0);
  const fade = useSharedValue(1);

  const reset = () => {
    'worklet';
    scale.value = withSpring(1, motion.spring);
    savedScale.value = 1;
    tx.value = withSpring(0, motion.spring);
    ty.value = withSpring(0, motion.spring);
    fade.value = withTiming(1);
  };

  const pinch = Gesture.Pinch()
    .onUpdate((e) => {
      scale.value = Math.max(1, Math.min(savedScale.value * e.scale, 5));
    })
    .onEnd(() => {
      savedScale.value = scale.value;
      if (scale.value <= 1.02) reset();
    });

  const pan = Gesture.Pan()
    .onUpdate((e) => {
      if (savedScale.value > 1) {
        tx.value = e.translationX;
        ty.value = e.translationY;
      } else {
        ty.value = e.translationY;
        fade.value = 1 - Math.min(0.7, Math.abs(e.translationY) / height);
      }
    })
    .onEnd((e) => {
      if (savedScale.value <= 1 && Math.abs(e.translationY) > 120) {
        scheduleOnRN(onClose);
      } else if (savedScale.value <= 1) {
        reset();
      }
    });

  const doubleTap = Gesture.Tap()
    .numberOfTaps(2)
    .onEnd(() => {
      if (savedScale.value > 1) reset();
      else {
        scale.value = withSpring(2.5, motion.spring);
        savedScale.value = 2.5;
      }
    });

  const image = useAnimatedStyle(() => ({
    transform: [{ translateX: tx.value }, { translateY: ty.value }, { scale: scale.value }],
  }));
  const backdrop = useAnimatedStyle(() => ({ opacity: fade.value }));

  return (
    <Modal visible={!!uri} transparent animationType="fade" onRequestClose={onClose} statusBarTranslucent navigationBarTranslucent>
      <GestureHandlerRootView style={styles.flex}>
        <Animated.View style={[StyleSheet.absoluteFill, styles.backdrop, backdrop]} />
        <GestureDetector gesture={Gesture.Simultaneous(pinch, pan, doubleTap)}>
          <Animated.View style={[styles.flex, styles.center, image]}>
            {uri ? <Image source={{ uri }} style={{ width, height: height * 0.85 }} contentFit="contain" accessibilityLabel="Photo" /> : null}
          </Animated.View>
        </GestureDetector>
        <View style={[styles.close, { top: insets.top + spacing.xs }]}>
          <IconButton icon="close" label="Close photo" onPress={onClose} variant="filled" />
        </View>
      </GestureHandlerRootView>
    </Modal>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  center: { alignItems: 'center', justifyContent: 'center' },
  backdrop: { backgroundColor: '#000' },
  close: { position: 'absolute', right: spacing.md },
});
