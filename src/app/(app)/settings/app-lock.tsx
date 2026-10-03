import { useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { Button, Card, Divider, Header, ListRow, Screen, Text, Toggle } from '@/components/ui';
import { LockSetupFlow } from '@/features/lock/LockSetupFlow';
import { biometricAvailability, useLock } from '@/features/lock/lockStore';
import { usePrefs } from '@/stores/prefs';
import { spacing } from '@/theme';

export default function AppLockSettings() {
  const record = useLock((s) => s.record);
  const hide = usePrefs((s) => s.hideInSwitcher);
  const [changing, setChanging] = useState(false);
  const [bio, setBio] = useState({ available: false, label: 'Fingerprint' });

  useEffect(() => {
    void biometricAvailability().then(setBio);
  }, []);

  if (changing) {
    return (
      <Screen edges={['top', 'bottom']} header={<Header title="App lock" back divider={false} />}>
        <View style={styles.flow}>
          <LockSetupFlow mode="change" onDone={() => setChanging(false)} />
        </View>
      </Screen>
    );
  }

  return (
    <Screen scroll header={<Header title="App lock" back />}>
      <View style={styles.body}>
        <Text variant="body" color="textMuted">
          NOOK locks when you open it and after 30 seconds in the background. Your {record?.kind === 'pin' ? 'PIN' : 'password'} is
          stored only on this phone as a salted hash.
        </Text>
        <Card padded={false} style={styles.card}>
          <ListRow
            icon="key-outline"
            title={`Change ${record?.kind === 'pin' ? 'PIN' : 'password'}`}
            subtitle="Or switch between PIN and password"
            onPress={() => setChanging(true)}
          />
          <Divider inset={56} />
          <ListRow
            icon="finger-print"
            title={`${bio.label} unlock`}
            subtitle={bio.available ? 'Unlock with a touch' : 'Set up a fingerprint in Android settings first'}
            showChevron={false}
            right={
              <Toggle
                label={`${bio.label} unlock`}
                value={!!record?.biometric && bio.available}
                disabled={!bio.available}
                onValueChange={(v) => void useLock.getState().setBiometric(v)}
              />
            }
          />
          <Divider inset={56} />
          <ListRow
            icon="eye-off-outline"
            title="Hide in app switcher"
            subtitle="Blank preview in recent apps. Also blocks screenshots in NOOK."
            showChevron={false}
            right={<Toggle label="Hide in app switcher" value={hide} onValueChange={(v) => usePrefs.getState().setHideInSwitcher(v)} />}
          />
        </Card>
        <Text variant="caption" color="textMuted">
          Forgot it? Choose “Forgot” on the lock screen: you’ll sign out, sign back in with Google, and set a new one.
        </Text>
        <Button title="Lock now" icon="lock-closed-outline" variant="secondary" onPress={() => useLock.getState().lock()} />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  body: { gap: spacing.lg, paddingTop: spacing.lg },
  card: { paddingHorizontal: spacing.md },
  flow: { flex: 1, paddingBottom: spacing.md },
});
