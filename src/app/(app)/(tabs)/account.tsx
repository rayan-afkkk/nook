import { router, useFocusEffect, type Href } from 'expo-router';
import { useCallback, useState } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';
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
  PressableScale,
  ProgressBar,
  Screen,
  SegmentedControl,
  Sheet,
  Text,
  TextField,
  toast,
  Icon,
} from '@/components/ui';
import { CACHE_BUDGET_BYTES, clearMediaCache, readStorageInfo, type StorageInfo } from '@/features/account/cache';
import { deleteAccount, signOut } from '@/features/auth/authService';
import { useSession } from '@/features/auth/session';
import { useLock } from '@/features/lock/lockStore';
import { pickAvatar } from '@/features/media/pickers';
import { setDisplayName, setProfilePhoto } from '@/features/profile/profileService';
import { describeError } from '@/lib/errors';
import { formatBytes } from '@/lib/format';
import { haptics } from '@/lib/haptics';
import { usePrefs, type Appearance } from '@/stores/prefs';
import { radius, spacing, useTheme } from '@/theme';

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
  const { colors } = useTheme();
  const [photoSheet, setPhotoSheet] = useState(false);
  const [uploading, setUploading] = useState<number | null>(null);
  const [nameSheet, setNameSheet] = useState(false);
  const [nameDraft, setNameDraft] = useState('');
  const [savingName, setSavingName] = useState(false);

  const changePhoto = async (source: 'library' | 'camera' | 'remove') => {
    setPhotoSheet(false);
    if (!profile) return;
    try {
      const image = source === 'remove' ? null : await pickAvatar(source);
      if (source !== 'remove' && !image) return;
      setUploading(0);
      await setProfilePhoto(profile.uid, image, setUploading);
      haptics.success();
      toast.success(image ? 'Profile photo updated.' : 'Profile photo removed.');
    } catch (e) {
      toast.error(describeError(e));
    } finally {
      setUploading(null);
    }
  };

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
            <PressableScale
              scaleTo={0.94}
              accessibilityRole="button"
              accessibilityLabel="Change profile photo"
              onPress={() => setPhotoSheet(true)}
              disabled={uploading !== null}
            >
              <Avatar name={profile?.displayName ?? '?'} uri={profile?.photoURL} size={60} seed={profile?.uid} />
              {uploading !== null ? (
                <View style={[styles.avatarBusy, { backgroundColor: colors.overlay }]}>
                  <ActivityIndicator color="#fff" />
                </View>
              ) : null}
              <View style={[styles.cameraBadge, { backgroundColor: colors.primary, borderColor: colors.surface }]}>
                <Icon name="camera" size={12} color={colors.onPrimary} />
              </View>
            </PressableScale>
            <View style={styles.flex}>
              <PressableScale
                scaleTo={0.98}
                accessibilityRole="button"
                accessibilityLabel={`Name: ${profile?.displayName ?? ''}. Edit`}
                onPress={() => {
                  setNameDraft(profile?.displayName ?? '');
                  setNameSheet(true);
                }}
                style={styles.nameRow}
              >
                <Text variant="headline" numberOfLines={1} style={styles.flexShrink}>
                  {profile?.displayName}
                </Text>
                <Icon name="pencil" size={14} color="textMuted" />
              </PressableScale>
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

      <Sheet visible={photoSheet} onClose={() => setPhotoSheet(false)} accessibilityLabel="Profile photo">
        <Text variant="headline">Profile photo</Text>
        <ListRow icon="images-outline" title="Choose a photo" subtitle="Cropped square" showChevron={false} onPress={() => void changePhoto('library')} />
        <ListRow icon="camera-outline" title="Take a photo" showChevron={false} onPress={() => void changePhoto('camera')} />
        {profile?.photoURL ? (
          <ListRow icon="trash-outline" title="Remove photo" destructive showChevron={false} onPress={() => void changePhoto('remove')} />
        ) : null}
      </Sheet>
      <Sheet visible={nameSheet} onClose={() => setNameSheet(false)} accessibilityLabel="Edit name">
        <Text variant="headline">Your name</Text>
        <Text variant="caption" color="textMuted" style={styles.sheetText}>
          Shown on your messages. Your @username can’t change.
        </Text>
        <TextField label="Display name" value={nameDraft} onChangeText={setNameDraft} maxLength={40} autoFocus />
        <Button
          title="Save"
          icon="checkmark"
          loading={savingName}
          disabled={!nameDraft.trim() || nameDraft.trim() === profile?.displayName}
          onPress={() => {
            if (!profile) return;
            setSavingName(true);
            setDisplayName(profile.uid, nameDraft)
              .then(() => {
                haptics.success();
                setNameSheet(false);
              })
              .catch((e) => toast.error(describeError(e)))
              .finally(() => setSavingName(false));
          }}
        />
      </Sheet>
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
  avatarBusy: { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, borderRadius: radius.pill, alignItems: 'center', justifyContent: 'center' },
  cameraBadge: { position: 'absolute', right: -2, bottom: -2, width: 24, height: 24, borderRadius: 12, borderWidth: 2, alignItems: 'center', justifyContent: 'center' },
  nameRow: { flexDirection: 'row', alignItems: 'center', gap: 6, minHeight: 36 },
  flexShrink: { flexShrink: 1 },
  sheetText: { marginBottom: spacing.sm },
  device: { gap: spacing.lg },
  deviceHead: { flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between' },
  meter: { gap: spacing.xs },
  meterLabels: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline' },
  section: { gap: spacing.sm },
});
