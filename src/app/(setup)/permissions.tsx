import { router } from 'expo-router';
import { StyleSheet, View } from 'react-native';
import Animated, { FadeInDown } from 'react-native-reanimated';

import { StepHeader } from '@/components/setup/StepHeader';
import { Button, Screen, Text } from '@/components/ui';
import { PermissionCard } from '@/features/permissions/PermissionCard';
import { usePrefs } from '@/stores/prefs';
import { spacing } from '@/theme';

export default function PermissionsStep() {
  const done = () => {
    usePrefs.getState().setPermissionsPrimed(true);
    router.replace('/');
  };
  return (
    <Screen
      scroll
      edges={['top', 'bottom']}
      footer={
        <View style={styles.footer}>
          <Button title="Continue" icon="arrow-forward" onPress={done} />
        </View>
      }
    >
      <StepHeader
        step={2}
        total={3}
        title="A few permissions"
        subtitle="We explain each one before Android asks. Skip any of them; you can change your mind in Settings."
      />
      <View style={styles.list}>
        <Animated.View entering={FadeInDown.delay(60).duration(300)}>
          <PermissionCard
            permission="notifications"
            icon="notifications-outline"
            tone="peach"
            title="Notifications"
            reason="Know when someone messages or calls. We only ever say who it's from, never what they wrote."
          />
        </Animated.View>
        <Animated.View entering={FadeInDown.delay(140).duration(300)}>
          <PermissionCard
            permission="camera"
            icon="camera-outline"
            tone="sky"
            title="Camera"
            reason="Snap photos for your chats and turn your camera on in video calls."
          />
        </Animated.View>
        <Animated.View entering={FadeInDown.delay(220).duration(300)}>
          <PermissionCard
            permission="microphone"
            icon="mic-outline"
            tone="mint"
            title="Microphone"
            reason="Record voice notes and talk on calls. NOOK never listens in the background."
          />
        </Animated.View>
        <Text variant="caption" color="textMuted" style={styles.note}>
          Photos and files you pick are shared only when you send them.
        </Text>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  list: { gap: spacing.sm },
  note: { marginTop: spacing.sm },
  footer: { paddingHorizontal: spacing.lg, paddingBottom: spacing.md, paddingTop: spacing.xs },
});
