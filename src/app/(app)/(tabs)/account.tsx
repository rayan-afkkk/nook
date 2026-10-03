import { router, useFocusEffect, type Href } from 'expo-router';
import { useCallback, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, { FadeInDown } from 'react-native-reanimated';

import {
  Avatar,
  Button,
  Card,
  Chip,
  ConfirmSheet,
  Divider,
  Header,
  ListRow,
  ProgressBar,
  Screen,
  SegmentedControl,
  Text,
  toast,
} from '@/components/ui';
import { CACHE_BUDGET_BYTES, clearMediaCache, readStorageInfo, type StorageInfo } from '@/features/account/cache';
import { deleteAccount, signOut } from '@/features/auth/authService';
import { useSession } from '@/features/auth/session';
import { useLock } from '@/features/lock/lockStore';
import { describeError } from '@/lib/errors';
import { formatBytes } from '@/lib/format';
import { haptics } from '@/lib/haptics';
import { usePrefs, type Appearance } from '@/stores/prefs';
import { spacing } from '@/theme';

const APPEARANCE = [
  { value: 'system', label: 'System' },
  { value: 'light', label: 'Light' },
  { value: 'dark', label: 'Dark' },
] as const;

const DISAPPEARING_LABEL = { off: 'Off', '24h': '24 hours', '7d': '7 days' } as const;

export default function Account() {
  const profile = useSession((s) => s.profile);
  const lock = useLock((s) => s.record);
  const appearance = usePrefs((s) => s.appearance);
  const disappearing = usePrefs((s) => s.disappearingDefault);
  const [storage, setStorage] = useState<StorageInfo | null>(null);
  const [clearing, setClearing] = useState(false);
  const [confirm, setConfirm] = useState<'signOut' | 'delete' | null>(null);
  const [working, setWorking] = useState(false);

  useFocusEffect(
    useCallback(() => {
      setStorage(readStorageInfo());
    }, []),
  );

  const onClear = async () => {
    setClearing(true);
    await clearMediaCache();
    setStorage(readStorageInfo());
    setClearing(false);
    haptics.success();
    toast.success('Media cache cleared on this phone.');
  };

  const onConfirm = async () => {
    setWorking(true);
    try {
      if (confirm === 'signOut') {
        await signOut();
      } else if (confirm === 'delete') {
        const done = await deleteAccount(profile?.username ?? null);
        if (!done) {
          setWorking(false);
          return;
        }
        toast.show('Your account was deleted.');
      }
    } catch (e) {
      haptics.error();
      toast.error(describeError(e));
      setWorking(false);
    }
  };

  const go = (href: Href) => () => router.push(href);
  const used = storage && storage.totalBytes > 0 ? (storage.totalBytes - storage.freeBytes) / storage.totalBytes : 0;

  return (
    <Screen scroll padded={false} header={<Header title="Account" />}>
      <View style={styles.body}>
        <Animated.View entering={FadeInDown.duration(260)}>
          <Card style={styles.profile}>
            <Avatar name={profile?.displayName ?? '?'} uri={profile?.photoURL} size={64} seed={profile?.uid} />
            <View style={styles.flex}>
              <Text variant="headline" numberOfLines={1}>
                {profile?.displayName}
              </Text>
              <Text variant="body" color="textMuted" numberOfLines={1}>
                @{profile?.username}
              </Text>
              <View style={styles.chip}>
                <Chip label="Member" icon="sparkles-outline" />
              </View>
            </View>
          </Card>
        </Animated.View>

        <Animated.View entering={FadeInDown.delay(60).duration(260)}>
          <Card style={styles.device}>
            <View style={styles.deviceHead}>
              <Text variant="subhead">This phone</Text>
              <Text variant="caption" color="textMuted">
                Stored locally
              </Text>
            </View>
            <View style={styles.meter}>
              <View style={styles.meterLabels}>
                <Text variant="bodyBold">Media cache</Text>
                <Text variant="caption" color="textMuted">
                  {storage ? formatBytes(storage.cacheBytes) : '…'}
                </Text>
              </View>
              <ProgressBar label="Media cache size" progress={storage ? storage.cacheBytes / CACHE_BUDGET_BYTES : 0} />
            </View>
            {storage && storage.totalBytes > 0 ? (
              <View style={styles.meter}>
                <View style={styles.meterLabels}>
                  <Text variant="bodyBold">Phone storage</Text>
                  <Text variant="caption" color="textMuted">
                    {formatBytes(storage.freeBytes)} free of {formatBytes(storage.totalBytes)}
                  </Text>
                </View>
                <ProgressBar label="Phone storage used" progress={used} tone="info" />
              </View>
            ) : null}
            <Button title="Clear cache" icon="trash-outline" variant="secondary" loading={clearing} onPress={() => void onClear()} />
          </Card>
        </Animated.View>

        <View style={styles.section}>
          <Text variant="captionBold" color="textMuted">
            APPEARANCE
          </Text>
          <SegmentedControl<Appearance>
            accessibilityLabel="Appearance"
            options={APPEARANCE}
            value={appearance}
            onChange={(a) => usePrefs.getState().setAppearance(a)}
          />
        </View>

        <View>
          <ListRow
            icon="lock-closed-outline"
            title="App lock"
            subtitle={lock ? `${lock.kind === 'pin' ? 'PIN' : 'Password'}${lock.biometric ? ' · Fingerprint on' : ''}` : 'Off'}
            onPress={go('/settings/app-lock')}
          />
          <Divider inset={56} />
          <ListRow icon="notifications-outline" title="Notifications" subtitle="Who it's from, never what they said" onPress={go('/settings/notifications')} />
          <Divider inset={56} />
          <ListRow icon="timer-outline" title="Disappearing default" subtitle={DISAPPEARING_LABEL[disappearing]} onPress={go('/settings/disappearing')} />
          <Divider inset={56} />
          <ListRow icon="ban-outline" title="Blocked users" subtitle="People who can't message or call you" onPress={go('/settings/blocked')} />
          <Divider inset={56} />
          <ListRow icon="play-circle-outline" title="View Onboarding" subtitle="Replay the intro" onPress={go('/intro')} />
          <Divider inset={56} />
          <ListRow icon="help-buoy-outline" title="Help & Feedback" subtitle="Answers and how to reach us" onPress={go('/settings/help')} />
          <Divider inset={56} />
          <ListRow icon="document-text-outline" title="Privacy & Terms" subtitle="What we store and why" onPress={go('/privacy')} />
          {__DEV__ ? (
            <>
              <Divider inset={56} />
              <ListRow icon="speedometer-outline" title="Debug" subtitle="Firestore reads and writes (dev builds only)" onPress={go('/debug')} />
            </>
          ) : null}
          <Divider inset={56} />
          <ListRow icon="log-out-outline" title="Sign Out" subtitle="Also removes the app lock on this phone" onPress={() => setConfirm('signOut')} showChevron={false} />
          <Divider inset={56} />
          <ListRow
            icon="trash-outline"
            title="Delete Account"
            subtitle="Permanently remove your profile and username"
            destructive
            showChevron={false}
            onPress={() => setConfirm('delete')}
          />
        </View>
        <Text variant="caption" color="textMuted" align="center">
          NOOK 0.1 · Made for the crew
        </Text>
      </View>

      <ConfirmSheet
        visible={confirm === 'signOut'}
        title="Sign out?"
        message="You'll need Google to sign back in, and you'll set a new app lock on this phone."
        confirmLabel="Sign out"
        loading={working}
        onConfirm={() => void onConfirm()}
        onCancel={() => setConfirm(null)}
      />
      <ConfirmSheet
        visible={confirm === 'delete'}
        title="Delete your account?"
        message="This removes your profile, frees your username and deletes your sign-in. Google will ask you to confirm it's you. This can't be undone."
        confirmLabel="Delete forever"
        destructive
        loading={working}
        onConfirm={() => void onConfirm()}
        onCancel={() => setConfirm(null)}
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  body: { padding: spacing.lg, gap: spacing.xl },
  flex: { flex: 1 },
  profile: { flexDirection: 'row', alignItems: 'center', gap: spacing.md },
  chip: { marginTop: spacing.xs },
  device: { gap: spacing.lg },
  deviceHead: { flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between' },
  meter: { gap: spacing.xs },
  meterLabels: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline' },
  section: { gap: spacing.sm },
});
