import { useCallback, useEffect, useState } from 'react';
import { AppState, StyleSheet, View } from 'react-native';

import { Button, Card, Chip, Header, Screen, Text } from '@/components/ui';
import { getPermission, openSystemSettings, requestPermission, type PermissionState } from '@/features/permissions/permissions';
import { spacing } from '@/theme';

export default function NotificationSettings() {
  const [state, setState] = useState<PermissionState | null>(null);
  const refresh = useCallback(() => {
    getPermission('notifications').then(setState).catch(() => setState('undetermined'));
  }, []);
  useEffect(() => {
    refresh();
    const sub = AppState.addEventListener('change', (s) => s === 'active' && refresh());
    return () => sub.remove();
  }, [refresh]);

  return (
    <Screen scroll header={<Header title="Notifications" back />}>
      <View style={styles.body}>
        <Card style={styles.card}>
          <View style={styles.row}>
            <Text variant="subhead" style={styles.flex}>
              On this phone
            </Text>
            {state ? <Chip label={state === 'granted' ? 'Allowed' : 'Off'} tone={state === 'granted' ? 'info' : 'neutral'} /> : null}
          </View>
          <Text variant="body" color="textMuted">
            NOOK notifications only ever say who something is from, like “Ali sent you a photo” or “New message in The Boys”.
            What people write is never put in a notification.
          </Text>
          {state === 'granted' ? (
            <Button title="Open Android settings" variant="secondary" icon="settings-outline" onPress={openSystemSettings} />
          ) : state === 'blocked' ? (
            <Button title="Turn on in Android settings" icon="settings-outline" onPress={openSystemSettings} />
          ) : (
            <Button title="Allow notifications" icon="notifications-outline" onPress={() => void requestPermission('notifications').then(setState)} />
          )}
        </Card>
        <Text variant="caption" color="textMuted">
          You’ll be able to mute individual chats from each chat’s info screen.
        </Text>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  body: { gap: spacing.md, paddingTop: spacing.lg },
  card: { gap: spacing.md },
  row: { flexDirection: 'row', alignItems: 'center' },
  flex: { flex: 1 },
});
