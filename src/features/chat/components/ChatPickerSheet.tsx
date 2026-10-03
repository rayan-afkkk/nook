import { View, StyleSheet } from 'react-native';

import { EmptyState, ListRow, Sheet, Text } from '@/components/ui';
import { displayNameOf, useProfiles } from '@/features/profile/profiles';
import { spacing } from '@/theme';

import { useChatList } from '../chatList';
import { otherMember } from '../model';
import type { Chat } from '../types';

type Props = { visible: boolean; me: string; title: string; onPick: (chat: Chat, name: string) => void; onClose: () => void };

/** Pick one of your chats (forwarding, sending a GIF from the Stickers tab). */
export function ChatPickerSheet({ visible, me, title, onPick, onClose }: Props) {
  const chats = useChatList((s) => s.chats);
  const profiles = useProfiles((s) => s.byId);
  const nameFor = (c: Chat) => (c.type === 'group' ? (c.name ?? 'Group') : displayNameOf(profiles[otherMember(c.members, me) ?? '']));
  return (
    <Sheet visible={visible} onClose={onClose} accessibilityLabel={title}>
      <Text variant="headline">{title}</Text>
      <View style={styles.list}>
        {chats.length === 0 ? (
          <EmptyState icon="chatbubbles-outline" title="No chats yet" message="Start a chat from the Friends tab first." />
        ) : (
          chats.slice(0, 12).map((c) => (
            <ListRow
              key={c.id}
              icon={c.type === 'group' ? 'people-outline' : 'person-outline'}
              title={nameFor(c)}
              showChevron={false}
              onPress={() => onPick(c, nameFor(c))}
            />
          ))
        )}
      </View>
    </Sheet>
  );
}

const styles = StyleSheet.create({ list: { maxHeight: 440, marginTop: spacing.xs } });
