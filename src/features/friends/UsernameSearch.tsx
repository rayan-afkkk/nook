import { useState } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';

import { Avatar, IconButton, PressableScale, Text, TextField, useIsOffline } from '@/components/ui';
import { USERNAME_MAX } from '@/constants/app';
import { findUserByUsername } from '@/features/profile/profileService';
import type { Profile } from '@/features/profile/types';
import { normalizeUsername, validateUsername } from '@/features/profile/username';
import { describeError } from '@/lib/errors';
import { radius, spacing, useTheme } from '@/theme';

type Props = { me: string; exclude?: string[]; onPick: (p: Profile) => void; label?: string };

/** Exact-username lookup used to add people to a chat or group. */
export function UsernameSearch({ me, exclude = [], onPick, label = 'Add by username' }: Props) {
  const { colors } = useTheme();
  const offline = useIsOffline();
  const [q, setQ] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [found, setFound] = useState<Profile | null>(null);
  const name = normalizeUsername(q);
  const invalid = name ? validateUsername(name) : null;

  const search = async () => {
    if (!name || invalid) return;
    setBusy(true);
    setMessage(null);
    setFound(null);
    try {
      const p = await findUserByUsername(name);
      if (!p) setMessage(`Nobody has claimed @${name}.`);
      else if (p.uid === me) setMessage("That's you.");
      else if (exclude.includes(p.uid)) setMessage(`@${p.username} is already here.`);
      else setFound(p);
    } catch (e) {
      setMessage(describeError(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <View style={styles.wrap}>
      <TextField
        label={label}
        prefix="@"
        placeholder="exact username"
        value={q}
        onChangeText={(t) => {
          setQ(t.replace(/\s/g, '').toLowerCase());
          setFound(null);
          setMessage(null);
        }}
        autoCapitalize="none"
        autoCorrect={false}
        maxLength={USERNAME_MAX + 1}
        returnKeyType="search"
        onSubmitEditing={() => void search()}
        error={name ? (invalid ?? null) : null}
        helper={offline ? "You're offline." : message}
        right={busy ? <ActivityIndicator color={colors.textMuted} /> : <IconButton icon="arrow-forward-circle" label="Search" disabled={!name || !!invalid || offline} onPress={() => void search()} />}
      />
      {found ? (
        <PressableScale
          accessibilityRole="button"
          accessibilityLabel={`Add ${found.displayName}`}
          onPress={() => {
            onPick(found);
            setFound(null);
            setQ('');
          }}
          style={[styles.result, { backgroundColor: colors.surface, borderColor: colors.border }]}
        >
          <Avatar name={found.displayName} uri={found.photoURL} size={40} seed={found.uid} />
          <View style={styles.flex}>
            <Text variant="bodyBold">{found.displayName}</Text>
            <Text variant="caption" color="textMuted">
              @{found.username}
            </Text>
          </View>
          <Text variant="label" color="accent">
            Add
          </Text>
        </PressableScale>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: spacing.xs },
  flex: { flex: 1 },
  result: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm, padding: spacing.sm, borderRadius: radius.md, borderWidth: 1, marginTop: -spacing.sm },
});
