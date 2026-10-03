import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { StyleSheet, TextInput, View } from 'react-native';
import Animated, { FadeInDown } from 'react-native-reanimated';

import {
  Avatar,
  Button,
  Card,
  ConfirmSheet,
  Divider,
  EmptyState,
  Header,
  IconButton,
  PressableScale,
  Screen,
  Skeleton,
  Text,
  TextField,
  toast,
  useIsOffline,
} from '@/components/ui';
import { USERNAME_MAX } from '@/constants/app';
import { useSession } from '@/features/auth/session';
import { startCall } from '@/features/calls/callService';
import { addMembers, openDirectChat } from '@/features/chat/chatService';
import { useChatList } from '@/features/chat/chatList';
import { ChatPickerSheet } from '@/features/chat/components/ChatPickerSheet';
import { setBlocked, usePrivate } from '@/features/friends/privateDoc';
import { useFriends } from '@/features/friends/useFriends';
import { usePresence } from '@/features/presence/presence';
import { findUserByUsername } from '@/features/profile/profileService';
import { displayNameOf, useProfile } from '@/features/profile/profiles';
import type { Profile } from '@/features/profile/types';
import { normalizeUsername, validateUsername } from '@/features/profile/username';
import { describeError } from '@/lib/errors';
import { haptics } from '@/lib/haptics';
import { usePrefs } from '@/stores/prefs';
import { spacing } from '@/theme';

type Result = { kind: 'idle' } | { kind: 'loading' } | { kind: 'none'; name: string } | { kind: 'found'; profile: Profile } | { kind: 'error'; message: string };

function FriendRow({ uid, onPress }: { uid: string; onPress: (uid: string) => void }) {
  const p = useProfile(uid);
  const presence = usePresence(uid);
  const name = displayNameOf(p);
  return (
    <PressableScale
      scaleTo={0.98}
      accessibilityRole="button"
      accessibilityLabel={`${name}, ${presence.online ? 'online' : 'offline'}. Message`}
      onPress={() => onPress(uid)}
      style={styles.friend}
    >
      <Avatar name={name} uri={p?.photoURL} size={48} online={presence.online} seed={uid} />
      <View style={styles.flex}>
        <Text variant="bodyBold" numberOfLines={1}>
          {name}
        </Text>
        <Text variant="caption" color={presence.online ? 'text' : 'textMuted'}>
          {presence.online ? 'Online' : p ? `@${p.username}` : ' '}
        </Text>
      </View>
    </PressableScale>
  );
}

export default function Friends() {
  const { focus } = useLocalSearchParams<{ focus?: string }>();
  const me = useSession((s) => s.profile);
  const myUid = me?.uid ?? '';
  const offline = useIsOffline();
  const friends = useFriends(myUid);
  const listStatus = useChatList((s) => s.status);
  const blocked = usePrivate((s) => s.blocked);
  const disappearing = usePrefs((s) => s.disappearingDefault);
  const input = useRef<TextInput>(null);
  const [query, setQuery] = useState('');
  const [result, setResult] = useState<Result>({ kind: 'idle' });
  const [busy, setBusy] = useState<string | null>(null);
  const [groupPick, setGroupPick] = useState(false);
  const [confirmBlock, setConfirmBlock] = useState(false);

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

  const message = async (uid: string) => {
    setBusy('message');
    try {
      const id = await openDirectChat(myUid, uid, disappearing);
      router.push({ pathname: '/chat/[id]', params: { id } });
    } catch (e) {
      toast.error(describeError(e));
    } finally {
      setBusy(null);
    }
  };

  const call = async (uid: string) => {
    setBusy('call');
    try {
      const chatId = await openDirectChat(myUid, uid, disappearing);
      const callId = await startCall(chatId, myUid, uid, false);
      router.push({ pathname: '/call/[id]', params: { id: callId } });
    } catch (e) {
      toast.error(describeError(e));
    } finally {
      setBusy(null);
    }
  };

  const found = result.kind === 'found' ? result.profile : null;
  const foundBlocked = !!found && blocked.includes(found.uid);
  const visibleFriends = friends.filter((f) => !blocked.includes(f));

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
          right={<IconButton icon="arrow-forward-circle" label="Search" disabled={!name || !!invalid || offline} onPress={() => void search()} />}
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

        {found ? (
          <Animated.View entering={FadeInDown.duration(260)}>
            <Card style={styles.found}>
              <View style={styles.resultRow}>
                <Avatar name={found.displayName} uri={found.photoURL} size={56} seed={found.uid} />
                <View style={styles.flex}>
                  <Text variant="subhead" numberOfLines={1}>
                    {found.displayName}
                  </Text>
                  <Text variant="caption" color="textMuted">
                    @{found.username}
                  </Text>
                </View>
              </View>
              {found.uid === myUid ? (
                <Text variant="caption" color="textMuted">
                  That&apos;s you. Share your username so friends can find you.
                </Text>
              ) : foundBlocked ? (
                <Button title="Unblock" variant="secondary" icon="ban-outline" onPress={() => void setBlocked(myUid, found.uid, false).then(() => toast.show('Unblocked'))} />
              ) : (
                <View style={styles.actions}>
                  <Button title="Message" icon="chatbubble-outline" loading={busy === 'message'} onPress={() => void message(found.uid)} />
                  <View style={styles.row}>
                    <Button style={styles.flex} title="Call" icon="call-outline" variant="secondary" loading={busy === 'call'} onPress={() => void call(found.uid)} />
                    <Button style={styles.flex} title="Add to group" icon="people-outline" variant="secondary" onPress={() => setGroupPick(true)} />
                  </View>
                  <Button title="Block" icon="ban-outline" variant="destructive" onPress={() => setConfirmBlock(true)} />
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
          listStatus !== 'ready' ? (
            <View>
              {[0, 1, 2].map((i) => (
                <View key={i} style={styles.friend}>
                  <Skeleton width={48} height={48} radius={24} />
                  <Skeleton width="40%" height={14} />
                </View>
              ))}
            </View>
          ) : visibleFriends.length ? (
            <View>
              <Text variant="captionBold" color="textMuted" style={styles.sectionLabel}>
                PEOPLE YOU CHAT WITH
              </Text>
              {visibleFriends.map((uid, i) => (
                <View key={uid}>
                  {i > 0 ? <Divider inset={64} /> : null}
                  <FriendRow uid={uid} onPress={(u) => void message(u)} />
                </View>
              ))}
            </View>
          ) : (
            <EmptyState icon="people-outline" title="Your people" message={`Friends you chat with will appear here with their online status. You're @${me?.username ?? '…'}.`} />
          )
        ) : null}
      </View>

      <ChatPickerSheet
        visible={groupPick}
        me={myUid}
        title="Add to which group?"
        onClose={() => setGroupPick(false)}
        onPick={(chat, groupName) => {
          setGroupPick(false);
          if (!found) return;
          if (chat.type !== 'group') {
            toast.show('Pick a group, not a direct chat.');
            return;
          }
          if (chat.members.includes(found.uid)) {
            toast.show(`${found.displayName} is already in ${groupName}.`);
            return;
          }
          void addMembers(chat, [found.uid])
            .then(() => {
              haptics.success();
              toast.success(`Added to ${groupName}`);
            })
            .catch((e) => toast.error(describeError(e)));
        }}
      />
      <ConfirmSheet
        visible={confirmBlock}
        title={`Block ${found?.displayName ?? ''}?`}
        message="They won’t be able to message or call you, and you won’t get notifications from them. They aren’t told."
        confirmLabel="Block"
        destructive
        onCancel={() => setConfirmBlock(false)}
        onConfirm={() => {
          setConfirmBlock(false);
          if (found) void setBlocked(myUid, found.uid, true).then(() => toast.show(`${found.displayName} is blocked.`)).catch((e) => toast.error(describeError(e)));
        }}
      />
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
  friend: { flexDirection: 'row', alignItems: 'center', gap: spacing.md, paddingVertical: spacing.sm, minHeight: 64 },
  sectionLabel: { marginBottom: spacing.xs },
});
