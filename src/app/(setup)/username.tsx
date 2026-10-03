import { router } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';
import { KeyboardAvoidingView } from 'react-native-keyboard-controller';

import { StepHeader } from '@/components/setup/StepHeader';
import { Avatar, Button, Icon, Screen, TextField, toast, useIsOffline } from '@/components/ui';
import { useSession } from '@/features/auth/session';
import { claimUsername, isUsernameAvailable } from '@/features/profile/profileService';
import { normalizeUsername, validateUsername } from '@/features/profile/username';
import { USERNAME_MAX } from '@/constants/app';
import { describeError } from '@/lib/errors';
import { haptics } from '@/lib/haptics';
import { spacing, useTheme } from '@/theme';

type Availability = 'idle' | 'checking' | 'available' | 'taken' | 'error';

export default function UsernameStep() {
  const { colors } = useTheme();
  const user = useSession((s) => s.user);
  const offline = useIsOffline();
  const [displayName, setDisplayName] = useState(user?.displayName ?? '');
  const [username, setUsername] = useState('');
  const [check, setCheck] = useState<{ name: string; status: Availability }>({ name: '', status: 'idle' });
  const [saving, setSaving] = useState(false);
  const seq = useRef(0);

  const name = normalizeUsername(username);
  const invalid = name ? validateUsername(name) : null;

  const checkable = !!name && !invalid && !offline;
  // The last answer only counts for the name it was asked about.
  const availability: Availability = !checkable ? 'idle' : check.name === name ? check.status : 'checking';

  // Debounced availability check: one Firestore read per pause in typing.
  useEffect(() => {
    if (!checkable) return;
    const id = ++seq.current;
    const t = setTimeout(() => {
      isUsernameAvailable(name)
        .then((free) => {
          if (seq.current === id) setCheck({ name, status: free ? 'available' : 'taken' });
        })
        .catch(() => {
          if (seq.current === id) setCheck({ name, status: 'error' });
        });
    }, 550);
    return () => clearTimeout(t);
  }, [name, checkable]);

  const error =
    invalid ??
    (availability === 'taken' ? `@${name} is taken.` : availability === 'error' ? "Couldn't check right now." : null);
  const success = availability === 'available' ? `@${name} is yours if you want it.` : null;
  const canSave = !!name && !invalid && availability === 'available' && !offline && displayName.trim().length > 0;

  const save = async () => {
    if (!user || !canSave) return;
    setSaving(true);
    try {
      await claimUsername(user.uid, name, displayName, user.photoURL ?? null);
      haptics.success();
      router.replace('/');
    } catch (e) {
      haptics.error();
      toast.error(describeError(e));
      setCheck({ name: '', status: 'idle' });
      setSaving(false);
    }
  };

  return (
    <Screen edges={['top', 'bottom']}>
      <KeyboardAvoidingView behavior="padding" style={styles.flex}>
        <View style={styles.flex}>
          <StepHeader
            step={1}
            total={3}
            title="Pick a username"
            subtitle="Friends find you by your exact username. It's permanent, so pick one you'll still like next year."
          />
          <View style={styles.avatarRow}>
            <Avatar name={displayName || 'You'} uri={user?.photoURL} size={64} seed={user?.uid} />
          </View>
          <TextField
            label="Display name"
            value={displayName}
            onChangeText={setDisplayName}
            maxLength={40}
            autoComplete="name"
            textContentType="name"
            returnKeyType="next"
            helper="Shown on your messages. You can change it later."
          />
          <TextField
            label="Username"
            prefix="@"
            value={username}
            onChangeText={(t) => setUsername(t.replace(/\s/g, '').toLowerCase())}
            autoCapitalize="none"
            autoCorrect={false}
            autoComplete="username-new"
            maxLength={USERNAME_MAX + 1}
            returnKeyType="done"
            onSubmitEditing={() => void save()}
            error={name ? error : null}
            success={success}
            helper={offline ? "You're offline. Connect to check usernames." : 'Letters, numbers, dots and underscores.'}
            right={
              availability === 'checking' ? (
                <ActivityIndicator color={colors.textMuted} />
              ) : availability === 'available' ? (
                <Icon name="checkmark-circle" color={colors.online} />
              ) : null
            }
          />
        </View>
        <Button title="Claim username" icon="at" loading={saving} disabled={!canSave} onPress={() => void save()} />
      </KeyboardAvoidingView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, paddingBottom: spacing.md },
  avatarRow: { alignItems: 'flex-start', marginBottom: spacing.lg },
});
