import { FlashList } from '@shopify/flash-list';
import { router } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { Avatar, EmptyState, Header, Icon, IconButton, PressableScale, Screen, SegmentedControl, SkeletonRow, Text, toast } from '@/components/ui';
import { useSession } from '@/features/auth/session';
import { startCall, useCallHistory } from '@/features/calls/callService';
import type { Call } from '@/features/calls/types';
import { durationLabel, timeLabel } from '@/features/chat/model';
import { displayNameOf, useProfile } from '@/features/profile/profiles';
import { describeError } from '@/lib/errors';
import { spacing } from '@/theme';

const FILTERS = [
  { value: 'all', label: 'All' },
  { value: 'missed', label: 'Missed' },
] as const;

const isMissed = (c: Call, me: string) => c.calleeId === me && (c.status === 'missed' || c.status === 'cancelled' || c.status === 'ringing');

function CallRow({ call, me, now }: { call: Call; me: string; now: number }) {
  const otherId = call.members.find((m) => m !== me) ?? '';
  const other = useProfile(otherId);
  const name = displayNameOf(other);
  const missed = isMissed(call, me);
  const outgoing = call.callerId === me;
  const length =
    call.answeredAt && call.endedAt ? durationLabel(call.endedAt.toMillis() - call.answeredAt.toMillis()) : null;
  const detail = missed ? 'Missed' : call.status === 'declined' ? 'Declined' : outgoing && !call.answeredAt ? 'No answer' : (length ?? (outgoing ? 'Outgoing' : 'Incoming'));
  const callBack = () => {
    startCall(call.chatId, me, otherId, call.video)
      .then((id) => router.push({ pathname: '/call/[id]', params: { id } }))
      .catch((e) => toast.error(describeError(e)));
  };
  return (
    <PressableScale scaleTo={0.98} accessibilityRole="button" accessibilityLabel={`${name}, ${detail}. Call back`} onPress={callBack} style={styles.row}>
      <Avatar name={name} uri={other?.photoURL} size={48} seed={otherId} />
      <View style={styles.flex}>
        <Text variant="bodyBold" color={missed ? 'danger' : 'text'} numberOfLines={1}>
          {name}
        </Text>
        <View style={styles.detail}>
          <Icon name={outgoing ? 'arrow-up-outline' : 'arrow-down-outline'} size={13} color={missed ? 'danger' : 'textMuted'} />
          <Icon name={call.video ? 'videocam-outline' : 'call-outline'} size={13} color={missed ? 'danger' : 'textMuted'} />
          <Text variant="caption" color={missed ? 'danger' : 'textMuted'}>
            {detail}
          </Text>
        </View>
      </View>
      <Text variant="micro" color="textMuted">
        {call.startedAt ? timeLabel(call.startedAt.toMillis(), now) : ''}
      </Text>
      <IconButton icon={call.video ? 'videocam-outline' : 'call-outline'} label={`Call ${name} back`} onPress={callBack} />
    </PressableScale>
  );
}

export default function Calls() {
  const me = useSession((s) => s.user?.uid ?? '');
  const { calls, status } = useCallHistory(me);
  const [filter, setFilter] = useState<'all' | 'missed'>('all');
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 60_000);
    return () => clearInterval(t);
  }, []);
  const visible = useMemo(() => (filter === 'missed' ? calls.filter((c) => isMissed(c, me)) : calls), [calls, filter, me]);
  return (
    <Screen padded={false} header={<Header title="Calls" />}>
      <View style={styles.top}>
        <SegmentedControl accessibilityLabel="Filter calls" options={FILTERS} value={filter} onChange={setFilter} />
      </View>
      {status === 'loading' ? (
        <View style={styles.pad}>
          {[0, 1, 2].map((i) => (
            <SkeletonRow key={i} />
          ))}
        </View>
      ) : status === 'error' ? (
        <EmptyState icon="cloud-offline-outline" title="Couldn’t load calls" message="Check your connection and try again." />
      ) : (
        <FlashList
          data={visible}
          keyExtractor={(c) => c.id}
          renderItem={({ item }) => <CallRow call={item} me={me} now={now} />}
          ListEmptyComponent={
            <EmptyState
              icon={filter === 'missed' ? 'call-outline' : 'videocam-outline'}
              title={filter === 'missed' ? 'Nothing missed' : 'No calls yet'}
              message={filter === 'missed' ? 'Calls you didn’t pick up will show here in coral.' : 'Call a friend from their chat. Tap a call here to call back.'}
            />
          }
        />
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  top: { padding: spacing.lg, paddingBottom: spacing.sm },
  pad: { paddingHorizontal: spacing.lg },
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing.md, paddingLeft: spacing.lg, paddingRight: spacing.xs, paddingVertical: spacing.xs, minHeight: 68 },
  flex: { flex: 1 },
  detail: { flexDirection: 'row', alignItems: 'center', gap: 4 },
});
