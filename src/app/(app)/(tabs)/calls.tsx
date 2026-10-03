import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { EmptyState, Header, Screen, SegmentedControl } from '@/components/ui';
import { spacing } from '@/theme';

const FILTERS = [
  { value: 'all', label: 'All' },
  { value: 'missed', label: 'Missed' },
] as const;

export default function Calls() {
  const [filter, setFilter] = useState<'all' | 'missed'>('all');
  return (
    <Screen padded={false} header={<Header title="Calls" />}>
      <View style={styles.body}>
        <SegmentedControl accessibilityLabel="Filter calls" options={FILTERS} value={filter} onChange={setFilter} />
        <EmptyState
          icon={filter === 'missed' ? 'call-outline' : 'videocam-outline'}
          title={filter === 'missed' ? 'Nothing missed' : 'No calls yet'}
          message={
            filter === 'missed'
              ? 'Calls you didn’t pick up will show here in coral.'
              : 'Voice and video calls with your friends will show up here. Tap one to call back.'
          }
        />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({ body: { padding: spacing.lg, gap: spacing.lg } });
