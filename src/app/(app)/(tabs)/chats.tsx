import { FlashList } from '@shopify/flash-list';
import { router } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import { StyleSheet, TextInput, View } from 'react-native';
import Animated, { FadeIn, FadeInDown, FadeOut } from 'react-native-reanimated';

import {
  Button,
  DashedCard,
  EmptyState,
  Header,
  Icon,
  IconButton,
  ListRow,
  Screen,
  SegmentedControl,
  Sheet,
  SkeletonRow,
} from '@/components/ui';
import { useSession } from '@/features/auth/session';
import { useChatList } from '@/features/chat/chatList';
import { ChatRow } from '@/features/chat/components/ChatRow';
import { isUnread, otherMember } from '@/features/chat/model';
import type { Chat } from '@/features/chat/types';
import { usePrivate } from '@/features/friends/privateDoc';
import { displayNameOf, useProfiles } from '@/features/profile/profiles';
import { fonts, radius, spacing, useTheme } from '@/theme';

type Filter = 'all' | 'direct' | 'groups' | 'unread';
const FILTERS = [
  { value: 'all', label: 'All' },
  { value: 'direct', label: 'Direct' },
  { value: 'groups', label: 'Groups' },
  { value: 'unread', label: 'Unread' },
] as const;

export default function Chats() {
  const { colors } = useTheme();
  const me = useSession((s) => s.user?.uid ?? '');
  const { chats, status, error } = useChatList();
  const blocked = usePrivate((s) => s.blocked);
  const profiles = useProfiles((s) => s.byId);
  const [filter, setFilter] = useState<Filter>('all');
  const [searching, setSearching] = useState(false);
  const [q, setQ] = useState('');
  const [composeOpen, setComposeOpen] = useState(false);
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 60_000);
    return () => clearInterval(t);
  }, []);

  const nameOf = (c: Chat) => (c.type === 'group' ? (c.name ?? '') : displayNameOf(profiles[otherMember(c.members, me) ?? ''], ''));

  const visible = useMemo(() => {
    const needle = q.trim().toLowerCase();
    return chats.filter((c) => {
      if (c.type === 'direct' && blocked.includes(otherMember(c.members, me) ?? '')) return false;
      if (filter === 'direct' && c.type !== 'direct') return false;
      if (filter === 'groups' && c.type !== 'group') return false;
      if (filter === 'unread' && !isUnread(c.lastMessageAt?.toMillis() ?? null, c.lastRead?.[me]?.toMillis() ?? null, c.lastMessage?.senderId === me)) return false;
      if (needle && !nameOf(c).toLowerCase().includes(needle)) return false;
      return true;
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [chats, filter, q, blocked, me, profiles]);

  const open = (c: Chat) => router.push({ pathname: '/chat/[id]', params: { id: c.id } });
  const empty = status === 'ready' && chats.length === 0;

  return (
    <Screen
      padded={false}
      header={
        <Header
          title="Chats"
          right={
            <>
              <IconButton
                icon={searching ? 'close' : 'search-outline'}
                label={searching ? 'Close search' : 'Search chats'}
                onPress={() => {
                  setSearching((s) => !s);
                  setQ('');
                }}
              />
              <IconButton icon="create-outline" label="New chat" onPress={() => setComposeOpen(true)} />
            </>
          }
        />
      }
    >
      <View style={styles.top}>
        {searching ? (
          <Animated.View entering={FadeIn.duration(180)} exiting={FadeOut.duration(120)} style={[styles.search, { backgroundColor: colors.surface, borderColor: colors.border }]}>
            <Icon name="search-outline" size={18} color="textMuted" />
            <TextInput
                allowFontScaling={false}
              autoFocus
              value={q}
              onChangeText={setQ}
              placeholder="Search by name"
              placeholderTextColor={colors.textMuted}
              accessibilityLabel="Search chats by name"
              style={[styles.searchInput, { color: colors.text }]}
            />
          </Animated.View>
        ) : null}
        <SegmentedControl accessibilityLabel="Filter chats" options={FILTERS} value={filter} onChange={setFilter} />
      </View>

      {status === 'loading' || status === 'idle' ? (
        <View style={styles.skeletons}>
          {[0, 1, 2, 3, 4].map((i) => (
            <SkeletonRow key={i} />
          ))}
        </View>
      ) : status === 'error' && chats.length === 0 ? (
        <EmptyState icon="cloud-offline-outline" title="Couldn’t load chats" message={error ?? 'Check your connection.'} />
      ) : empty ? (
        <Animated.View entering={FadeInDown.duration(280)} style={styles.firstRun}>
          <DashedCard title="Start your first chat" message="Find a friend by their exact username and say hi." actionLabel="Find a friend" onPress={() => router.push({ pathname: '/friends', params: { focus: '1' } })} />
          <EmptyState icon="chatbubbles-outline" title="No chats yet" message="Your conversations will show up here." />
        </Animated.View>
      ) : (
        <FlashList
          data={visible}
          keyExtractor={(c) => c.id}
          renderItem={({ item }) => <ChatRow chat={item} me={me} now={now} onPress={open} />}
          contentContainerStyle={styles.list}
          ListEmptyComponent={
            <EmptyState
              icon={filter === 'unread' ? 'checkmark-done-outline' : q ? 'search-outline' : filter === 'groups' ? 'people-outline' : 'chatbubbles-outline'}
              title={q ? 'No matches' : filter === 'unread' ? 'All caught up' : filter === 'groups' ? 'No groups yet' : 'Nothing here'}
              message={
                q
                  ? 'No chat has that name.'
                  : filter === 'unread'
                    ? 'New messages you haven’t read will wait here.'
                    : filter === 'groups'
                      ? 'Make a group for the crew and everyone’s in one place.'
                      : 'Chats will show up here.'
              }
              action={filter === 'groups' && !q ? <Button title="New group" icon="people-outline" onPress={() => router.push('/new-group')} /> : undefined}
            />
          }
        />
      )}

      <Sheet visible={composeOpen} onClose={() => setComposeOpen(false)} accessibilityLabel="New chat">
        <ListRow
          icon="person-add-outline"
          title="New message"
          subtitle="Find a friend by username"
          onPress={() => {
            setComposeOpen(false);
            router.push({ pathname: '/friends', params: { focus: '1' } });
          }}
        />
        <ListRow
          icon="people-outline"
          title="New group"
          subtitle="Name it and add your crew"
          onPress={() => {
            setComposeOpen(false);
            router.push('/new-group');
          }}
        />
      </Sheet>
    </Screen>
  );
}

const styles = StyleSheet.create({
  top: { paddingHorizontal: spacing.lg, paddingTop: spacing.lg, paddingBottom: spacing.sm, gap: spacing.sm },
  search: { flexDirection: 'row', alignItems: 'center', gap: spacing.xs, borderRadius: radius.pill, borderWidth: 1, paddingHorizontal: spacing.md, minHeight: 48 },
  searchInput: { flex: 1, fontFamily: fonts.sans, fontSize: 16, minHeight: 48 },
  skeletons: { paddingHorizontal: spacing.lg },
  firstRun: { padding: spacing.lg, gap: spacing.lg },
  list: { paddingBottom: spacing.xxl },
});
