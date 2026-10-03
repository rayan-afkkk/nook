import { router } from 'expo-router';
import { useState } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, { FadeInDown } from 'react-native-reanimated';

import { DashedCard, EmptyState, Header, IconButton, Screen, SegmentedControl } from '@/components/ui';
import { spacing } from '@/theme';

type Filter = 'all' | 'direct' | 'groups' | 'unread';
const FILTERS = [
  { value: 'all', label: 'All' },
  { value: 'direct', label: 'Direct' },
  { value: 'groups', label: 'Groups' },
  { value: 'unread', label: 'Unread' },
] as const;

export default function Chats() {
  const [filter, setFilter] = useState<Filter>('all');
  const goFind = () => router.push({ pathname: '/friends', params: { focus: '1' } });

  return (
    <Screen
      padded={false}
      header={
        <Header
          title="Chats"
          right={
            <>
              <IconButton icon="search-outline" label="Search chats" onPress={goFind} />
              <IconButton icon="create-outline" label="New chat" onPress={goFind} />
            </>
          }
        />
      }
    >
      <View style={styles.body}>
        <SegmentedControl accessibilityLabel="Filter chats" options={FILTERS} value={filter} onChange={setFilter} />
        {filter === 'all' ? (
          <Animated.View entering={FadeInDown.duration(280)}>
            <DashedCard
              title="Start your first chat"
              message="Find a friend by their exact username and say hi."
              actionLabel="Find a friend"
              onPress={goFind}
            />
          </Animated.View>
        ) : null}
        <EmptyState
          icon={filter === 'unread' ? 'checkmark-done-outline' : filter === 'groups' ? 'people-outline' : 'chatbubbles-outline'}
          title={filter === 'unread' ? 'All caught up' : filter === 'groups' ? 'No groups yet' : 'No chats yet'}
          message={
            filter === 'unread'
              ? 'New messages you haven’t read will wait here.'
              : filter === 'groups'
                ? 'Make a group for the crew and everyone’s in one place.'
                : 'Your conversations will show up here.'
          }
        />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  body: { padding: spacing.lg, gap: spacing.lg },
});
