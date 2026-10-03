import { useLocalSearchParams } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { StyleSheet, TextInput, View } from 'react-native';
import Animated, { FadeInDown } from 'react-native-reanimated';

import {
  Avatar,
  Button,
  Card,
  EmptyState,
  Header,
  IconButton,
  Screen,
  Skeleton,
  Text,
  TextField,
  toast,
  useIsOffline,
} from '@/components/ui';
import { useSession } from '@/features/auth/session';
import { findUserByUsername } from '@/features/profile/profileService';
import type { Profile } from '@/features/profile/types';
import { normalizeUsername, validateUsername } from '@/features/profile/username';
import { USERNAME_MAX } from '@/constants/app';
import { describeError } from '@/lib/errors';
import { spacing } from '@/theme';

type Result = { kind: 'idle' } | { kind: 'loading' } | { kind: 'none'; name: string } | { kind: 'found'; profile: Profile } | { kind: 'error'; message: string };

export default function Friends() {
  const { focus } = useLocalSearchParams<{ focus?: string }>();
  const me = useSession((s) => s.profile);
  const offline = useIsOffline();
  const input = useRef<TextInput>(null);
  const [query, setQuery] = useState('');
  const [result, setResult] = useState<Result>({ kind: 'idle' });

  useEffect(() => {
    if (focus) setTimeout(() => input.current?.focus(), 300);
  }, [focus]);

  const name = normalizeUsername(query);
  const invalid = name ? validateUsername(name) : null;

  const search = async () => {
    if (!name || invalid) return;
    setResult({ kind: 'loading' });
    try {
      const profile = await findUserByUsername(name);
      setResult(profile ? { kind: 'found', profile } : { kind: 'none', name });
    } catch (e) {
      setResult({ kind: 'error', message: describeError(e) });
    }
  };

  const comingNext = () => toast.show('Chats and calls unlock in the next update.');

  return (
    <Screen
      scroll
      padded={false}
      header={<Header title="Friends" right={<IconButton icon="person-add-outline" label="Find by username" onPress={() => input.current?.focus()} />} />}
    >
      <View style={styles.body}>
        <TextField
          ref={input}
          label="Find someone"
          prefix="@"
          placeholder="exact username"
          value={query}
          onChangeText={(t) => {
            setQuery(t.replace(/\s/g, '').toLowerCase());
            if (result.kind !== 'loading') setResult({ kind: 'idle' });
          }}
          autoCapitalize="none"
          autoCorrect={false}
          maxLength={USERNAME_MAX + 1}
          returnKeyType="search"
          onSubmitEditing={() => void search()}
          error={name ? invalid : null}
          helper={offline ? "You're offline. Search needs a connection." : 'Usernames are exact, so ask your friend for theirs.'}
          right={
            <IconButton icon="arrow-forward-circle" label="Search" disabled={!name || !!invalid || offline} onPress={() => void search()} />
          }
        />

        {result.kind === 'loading' ? (
          <Card style={styles.resultRow}>
            <Skeleton width={56} height={56} radius={28} />
            <View style={styles.flex}>
              <Skeleton width="50%" height={16} />
              <Skeleton width="30%" height={12} style={styles.gapTop} />
            </View>
          </Card>
        ) : null}

        {result.kind === 'found' ? (
          <Animated.View entering={FadeInDown.duration(260)}>
            <Card style={styles.found}>
              <View style={styles.resultRow}>
                <Avatar name={result.profile.displayName} uri={result.profile.photoURL} size={56} seed={result.profile.uid} />
                <View style={styles.flex}>
                  <Text variant="subhead" numberOfLines={1}>
                    {result.profile.displayName}
                  </Text>
                  <Text variant="caption" color="textMuted">
                    @{result.profile.username}
                  </Text>
                </View>
              </View>
              {result.profile.uid === me?.uid ? (
                <Text variant="caption" color="textMuted">
                  That&apos;s you. Share your username so friends can find you.
                </Text>
              ) : (
                <View style={styles.actions}>
                  <Button title="Message" icon="chatbubble-outline" onPress={comingNext} />
                  <View style={styles.row}>
                    <Button style={styles.flex} title="Call" icon="call-outline" variant="secondary" onPress={comingNext} />
                    <Button style={styles.flex} title="Add to group" icon="people-outline" variant="secondary" onPress={comingNext} />
                  </View>
                  <Button title="Block" icon="ban-outline" variant="destructive" onPress={comingNext} />
                </View>
              )}
            </Card>
          </Animated.View>
        ) : null}

        {result.kind === 'none' ? (
          <EmptyState icon="search-outline" title="No one by that name" message={`Nobody has claimed @${result.name}. Check the spelling with your friend.`} />
        ) : null}
        {result.kind === 'error' ? <EmptyState icon="cloud-offline-outline" title="Couldn't search" message={result.message} /> : null}

        {result.kind === 'idle' ? (
          <EmptyState
            icon="people-outline"
            title="Your people"
            message={`Friends you chat with will appear here with their online status. You're @${me?.username ?? '…'}.`}
          />
        ) : null}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  body: { padding: spacing.lg, gap: spacing.md },
  flex: { flex: 1 },
  gapTop: { marginTop: spacing.xs },
  resultRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.md },
  found: { gap: spacing.lg },
  actions: { gap: spacing.xs },
  row: { flexDirection: 'row', gap: spacing.xs },
});
