import { memo } from 'react';
import { StyleSheet, View } from 'react-native';

import { Avatar, Icon, PressableScale, Text, type IconName } from '@/components/ui';
import { usePresence } from '@/features/presence/presence';
import { displayNameOf, useProfile } from '@/features/profile/profiles';
import { radius, spacing, useTheme } from '@/theme';

import { isUnread, lastMessageLine, otherMember, timeLabel } from '../model';
import type { Chat } from '../types';

import { GroupAvatar } from './GroupAvatar';

const KIND_ICON: Partial<Record<string, IconName>> = {
  image: 'image-outline',
  voice: 'mic-outline',
  gif: 'film-outline',
  sticker: 'happy-outline',
  file: 'document-outline',
};

function Row({ chat, me, now, onPress }: { chat: Chat; me: string; now: number; onPress: (c: Chat) => void }) {
  const { colors } = useTheme();
  const otherId = chat.type === 'direct' ? otherMember(chat.members, me) : undefined;
  const other = useProfile(otherId);
  const presence = usePresence(otherId);
  const name = chat.type === 'group' ? (chat.name ?? 'Group') : displayNameOf(other);
  const last = chat.lastMessage;
  const mineLast = last?.senderId === me;
  const unread = isUnread(chat.lastMessageAt?.toMillis() ?? null, chat.lastRead?.[me]?.toMillis() ?? null, mineLast);
  const muted = chat.mutedBy?.includes(me);
  const icon = last ? KIND_ICON[last.kind] : undefined;
  const line = lastMessageLine(last, mineLast);
  return (
    <PressableScale
      scaleTo={0.98}
      accessibilityRole="button"
      accessibilityLabel={`${name}${unread ? ', unread' : ''}. ${line}`}
      onPress={() => onPress(chat)}
      style={styles.row}
    >
      {chat.type === 'group' ? (
        <GroupAvatar name={name} seed={chat.id} size={46} />
      ) : (
        <Avatar name={name} uri={other?.photoURL} size={46} online={presence.online} seed={otherId} />
      )}
      <View style={styles.texts}>
        <View style={styles.top}>
          <Text variant="bodyBold" numberOfLines={1} style={styles.flex}>
            {name}
          </Text>
          {muted ? <Icon name="notifications-off-outline" size={14} color="textMuted" /> : null}
          <Text variant="micro" color={unread ? 'accent' : 'textMuted'}>
            {chat.lastMessageAt ? timeLabel(chat.lastMessageAt.toMillis(), now) : ''}
          </Text>
        </View>
        <View style={styles.bottom}>
          {icon ? <Icon name={icon} size={14} color={unread ? 'text' : 'textMuted'} /> : null}
          <Text variant="caption" color={unread ? 'text' : 'textMuted'} numberOfLines={1} style={styles.flex}>
            {line}
          </Text>
          {unread ? (
            <View style={[styles.badge, { backgroundColor: colors.accent }]} accessibilityElementsHidden>
              <Text variant="micro" style={styles.badgeText}>
                New
              </Text>
            </View>
          ) : null}
        </View>
      </View>
    </PressableScale>
  );
}

export const ChatRow = memo(Row);

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing.md, paddingHorizontal: spacing.lg, paddingVertical: 10, minHeight: 64 },
  texts: { flex: 1, gap: 3 },
  top: { flexDirection: 'row', alignItems: 'center', gap: spacing.xs },
  bottom: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  flex: { flex: 1 },
  badge: { borderRadius: radius.pill, paddingHorizontal: 8, paddingVertical: 2 },
  badgeText: { color: '#000' },
});
