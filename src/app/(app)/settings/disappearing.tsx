import { StyleSheet, View } from 'react-native';

import { Card, Header, Screen, SegmentedControl, Text } from '@/components/ui';
import { usePrefs, type DisappearingDefault } from '@/stores/prefs';
import { spacing } from '@/theme';

const OPTIONS = [
  { value: 'off', label: 'Off' },
  { value: '24h', label: '24 hours' },
  { value: '7d', label: '7 days' },
] as const;

export default function DisappearingDefaultSettings() {
  const value = usePrefs((s) => s.disappearingDefault);
  return (
    <Screen scroll header={<Header title="Disappearing" back />}>
      <View style={styles.body}>
        <Text variant="body" color="textMuted">
          New chats you start will use this timer. Anyone in a chat can change its timer later from the chat header.
        </Text>
        <SegmentedControl<DisappearingDefault>
          accessibilityLabel="Default disappearing timer"
          options={OPTIONS}
          value={value}
          onChange={(v) => usePrefs.getState().setDisappearingDefault(v)}
        />
        <Card tone="highlight" style={styles.card}>
          <Text variant="subhead" color="onHighlight">
            How it works
          </Text>
          <Text variant="body" color="onHighlight" style={styles.dim}>
            Messages vanish from everyone’s screen when their timer runs out, and are deleted from the server the next time
            someone in the chat opens it. Someone could still screenshot or photograph a message before it disappears.
          </Text>
        </Card>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  body: { gap: spacing.lg, paddingTop: spacing.lg },
  card: { gap: spacing.xs },
  dim: { opacity: 0.85 },
});
