import { useCallback, useEffect, useState } from 'react';
import { AppState, StyleSheet, View } from 'react-native';

import { Button, Card, Chip, Header, Screen, Text } from '@/components/ui';
import { nookCall } from '@/features/calls/nativeCall';
import { getPermission, openSystemSettings, requestPermission, type PermissionState } from '@/features/permissions/permissions';
import { spacing } from '@/theme';

export default function NotificationSettings() {
  const [state, setState] = useState<PermissionState | null>(null);
  const [fullScreen, setFullScreen] = useState(() => nookCall.canUseFullScreenIntent());
  const refresh = useCallback(() => {
    getPermission('notifications').then(setState).catch(() => setState('undetermined'));
  }, []);
  useEffect(() => {
    refresh();
    const sub = AppState.addEventListener('change', (s) => {
      if (s !== 'active') return;
      refresh();
      setFullScreen(nookCall.canUseFullScreenIntent());
    });
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
        <Card style={styles.card}>
          <View style={styles.row}>
            <Text variant="subhead" style={styles.flex}>
              Incoming calls
            </Text>
            <Chip label={fullScreen ? 'Full screen' : 'Banner only'} tone={fullScreen ? 'info' : 'neutral'} />
          </View>
          <Text variant="body" color="textMuted">
            Calls ring full screen, even on the lock screen. On Android 14 and newer this needs the “full-screen
            notifications” permission. Some phones (Xiaomi, Oppo, Vivo, Huawei…) also need NOOK set to “No
            restrictions” in battery settings, or calls can’t reach a closed app.
          </Text>
          {!fullScreen ? (
            <Button title="Allow full-screen calls" icon="call-outline" onPress={nookCall.openFullScreenIntentSettings} />
          ) : null}
        </Card>
        <Text variant="caption" color="textMuted">
          Mute a single chat from its info screen (tap the name at the top of the chat).
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
