import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import {
  Avatar,
  Button,
  Card,
  ConfirmSheet,
  Divider,
  Header,
  ListRow,
  Screen,
  SegmentedControl,
  Skeleton,
  Text,
  TextField,
  Toggle,
  toast,
} from '@/components/ui';
import { useSession } from '@/features/auth/session';
import { addMembers, leaveGroup, renameGroup, setDisappearing, setMuted } from '@/features/chat/chatService';
import { GroupAvatar } from '@/features/chat/components/GroupAvatar';
import { otherMember } from '@/features/chat/model';
import type { Chat, Disappearing } from '@/features/chat/types';
import { useChatDoc } from '@/features/chat/useChatDoc';
import { UsernameSearch } from '@/features/friends/UsernameSearch';
import { setBlocked, useIsBlocked } from '@/features/friends/privateDoc';
import { usePresence } from '@/features/presence/presence';
import { displayNameOf, useProfile } from '@/features/profile/profiles';
import { describeError } from '@/lib/errors';
import { haptics } from '@/lib/haptics';
import { spacing } from '@/theme';

const TIMER = [
  { value: 'off', label: 'Off' },
  { value: '24h', label: '24 hours' },
  { value: '7d', label: '7 days' },
] as const;

function MemberRow({ uid, me }: { uid: string; me: string }) {
  const p = useProfile(uid);
  const presence = usePresence(uid);
  const name = uid === me ? `${displayNameOf(p)} (you)` : displayNameOf(p);
  return (
    <View style={styles.member} accessible accessibilityLabel={`${name}${presence.online ? ', online' : ''}`}>
      <Avatar name={displayNameOf(p)} uri={p?.photoURL} size={40} online={presence.online} seed={uid} />
      <View style={styles.flex}>
        <Text variant="bodyBold" numberOfLines={1}>
          {name}
        </Text>
        <Text variant="caption" color="textMuted">
          {p ? `@${p.username}` : ' '}
        </Text>
      </View>
    </View>
  );
}

export default function ChatInfo() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const me = useSession((s) => s.user?.uid ?? '');
  const { chat } = useChatDoc(String(id));
  if (!chat) {
    return (
      <Screen header={<Header title="Info" back />}>
        <View style={styles.body}>
          <Skeleton width={80} height={80} radius={40} />
          <Skeleton width="60%" height={20} />
        </View>
      </Screen>
    );
  }
  return chat.type === 'group' ? <GroupInfo chat={chat} me={me} /> : <DirectInfo chat={chat} me={me} />;
}

function SharedSettings({ chat, me }: { chat: Chat; me: string }) {
  const muted = !!chat.mutedBy?.includes(me);
  return (
    <>
      <Card padded={false} style={styles.card}>
        <ListRow
          icon="notifications-off-outline"
          title="Mute notifications"
          subtitle="No pushes from this chat on this account"
          showChevron={false}
          right={<Toggle label="Mute notifications" value={muted} onValueChange={(v) => void setMuted(chat.id, me, v).catch((e) => toast.error(describeError(e)))} />}
        />
      </Card>
      <View style={styles.section}>
        <Text variant="captionBold" color="textMuted">
          DISAPPEARING MESSAGES
        </Text>
        <SegmentedControl<Disappearing>
          accessibilityLabel="Disappearing messages"
          options={TIMER}
          value={chat.disappearing ?? 'off'}
          onChange={(v) => void setDisappearing(chat.id, v).catch((e) => toast.error(describeError(e)))}
        />
      </View>
    </>
  );
}

function DirectInfo({ chat, me }: { chat: Chat; me: string }) {
  const otherId = otherMember(chat.members, me) ?? '';
  const other = useProfile(otherId);
  const presence = usePresence(otherId);
  const blocked = useIsBlocked(otherId);
  const [confirm, setConfirm] = useState(false);
  const name = displayNameOf(other);
  return (
    <Screen scroll header={<Header title="Info" back />}>
      <View style={styles.body}>
        <View style={styles.hero}>
          <Avatar name={name} uri={other?.photoURL} size={96} online={presence.online} seed={otherId} />
          <Text variant="title" align="center">
            {name}
          </Text>
          <Text variant="body" color="textMuted">
            {other ? `@${other.username}` : ''}
          </Text>
        </View>
        <SharedSettings chat={chat} me={me} />
        <Button
          title={blocked ? `Unblock ${name}` : `Block ${name}`}
          icon="ban-outline"
          variant="destructive"
          onPress={() => (blocked ? void setBlocked(me, otherId, false).then(() => toast.show('Unblocked')) : setConfirm(true))}
        />
      </View>
      <ConfirmSheet
        visible={confirm}
        title={`Block ${name}?`}
        message="They won’t be able to message or call you, and you won’t get notifications from them. They aren’t told."
        confirmLabel="Block"
        destructive
        onCancel={() => setConfirm(false)}
        onConfirm={() => {
          setConfirm(false);
          void setBlocked(me, otherId, true)
            .then(() => {
              haptics.success();
              toast.show(`${name} is blocked.`);
            })
            .catch((e) => toast.error(describeError(e)));
        }}
      />
    </Screen>
  );
}

function GroupInfo({ chat, me }: { chat: Chat; me: string }) {
  const [name, setName] = useState(chat.name ?? '');
  const [saving, setSaving] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const [confirmLeave, setConfirmLeave] = useState(false);
  const dirty = name.trim() !== (chat.name ?? '') && name.trim().length > 0;
  return (
    <Screen scroll header={<Header title="Group" back />}>
      <View style={styles.body}>
        <View style={styles.hero}>
          <GroupAvatar name={chat.name ?? ''} seed={chat.id} size={96} />
          <Text variant="caption" color="textMuted">
            {chat.members.length} members
          </Text>
        </View>
        <TextField
          label="Group name"
          value={name}
          onChangeText={setName}
          maxLength={40}
          right={
            dirty ? (
              <Button
                title="Save"
                block={false}
                loading={saving}
                onPress={() => {
                  setSaving(true);
                  renameGroup(chat.id, name)
                    .then(() => toast.success('Renamed'))
                    .catch((e) => toast.error(describeError(e)))
                    .finally(() => setSaving(false));
                }}
              />
            ) : null
          }
        />
        <SharedSettings chat={chat} me={me} />
        <View style={styles.section}>
          <Text variant="captionBold" color="textMuted">
            MEMBERS
          </Text>
          <Card padded={false} style={styles.card}>
            {chat.members.map((uid, i) => (
              <View key={uid}>
                {i > 0 ? <Divider inset={52} /> : null}
                <MemberRow uid={uid} me={me} />
              </View>
            ))}
          </Card>
          <UsernameSearch
            me={me}
            exclude={chat.members}
            label="Add someone"
            onPick={(p) =>
              void addMembers(chat, [p.uid])
                .then(() => toast.success(`Added ${p.displayName}`))
                .catch((e) => toast.error(describeError(e)))
            }
          />
        </View>
        <Button title="Leave group" icon="exit-outline" variant="destructive" onPress={() => setConfirmLeave(true)} />
      </View>
      <ConfirmSheet
        visible={confirmLeave}
        title="Leave this group?"
        message="You’ll stop getting its messages. Someone in the group can add you back."
        confirmLabel="Leave"
        destructive
        loading={leaving}
        onCancel={() => setConfirmLeave(false)}
        onConfirm={() => {
          setLeaving(true);
          leaveGroup(chat.id, me)
            .then(() => {
              setConfirmLeave(false);
              router.dismissTo('/chats');
            })
            .catch((e) => {
              setLeaving(false);
              toast.error(describeError(e));
            });
        }}
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  body: { gap: spacing.lg, paddingTop: spacing.lg },
  hero: { alignItems: 'center', gap: spacing.xs },
  section: { gap: spacing.sm },
  card: { paddingHorizontal: spacing.md },
  member: { flexDirection: 'row', alignItems: 'center', gap: spacing.md, paddingVertical: spacing.sm, minHeight: 56 },
  flex: { flex: 1 },
});
