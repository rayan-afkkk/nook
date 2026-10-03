import { Redirect } from 'expo-router';
import { StyleSheet, View } from 'react-native';

import { Button, Card, Divider, Header, Screen, Text } from '@/components/ui';
import { useFirestoreMetrics } from '@/lib/firebase';
import { spacing } from '@/theme';

const QUOTA = { reads: 50_000, writes: 20_000, deletes: 20_000 };

/** Dev builds only: counts Firestore operations made by this app session. */
export default function Debug() {
  const m = useFirestoreMetrics();
  if (!__DEV__) return <Redirect href="/account" />;
  const stat = (label: string, n: number, quota: number) => (
    <View style={styles.stat}>
      <Text variant="headline">{n}</Text>
      <Text variant="caption" color="textMuted">
        {label}
      </Text>
      <Text variant="micro" color="textMuted">
        {((n / quota) * 100).toFixed(2)}% of daily
      </Text>
    </View>
  );
  return (
    <Screen scroll header={<Header title="Debug" back />}>
      <View style={styles.body}>
        <Card style={styles.row}>
          {stat('Reads', m.reads, QUOTA.reads)}
          {stat('Writes', m.writes, QUOTA.writes)}
          {stat('Deletes', m.deletes, QUOTA.deletes)}
        </Card>
        <Text variant="caption" color="textMuted">
          Counted since {new Date(m.since).toLocaleTimeString()}. Only documents delivered by the server count; cache hits are free. This is one
          phone, so multiply by your group size for a daily estimate.
        </Text>
        <Button title="Reset counters" variant="secondary" icon="refresh-outline" onPress={m.reset} />
        <Card padded={false} style={styles.log}>
          {m.log.length === 0 ? (
            <Text variant="caption" color="textMuted" style={styles.logRow}>
              No Firestore operations yet.
            </Text>
          ) : (
            m.log.map((e, i) => (
              <View key={`${e.at}-${i}`}>
                {i > 0 ? <Divider /> : null}
                <View style={[styles.logRow, styles.logLine]}>
                  <Text variant="captionBold">
                    {e.kind} ×{e.count}
                  </Text>
                  <Text variant="caption" color="textMuted" style={styles.flex} numberOfLines={1}>
                    {e.label}
                  </Text>
                  <Text variant="micro" color="textMuted">
                    {new Date(e.at).toLocaleTimeString()}
                  </Text>
                </View>
              </View>
            ))
          )}
        </Card>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  body: { gap: spacing.md, paddingTop: spacing.lg },
  row: { flexDirection: 'row', justifyContent: 'space-between' },
  stat: { alignItems: 'center', flex: 1, gap: 2 },
  log: { paddingHorizontal: spacing.md },
  logRow: { paddingVertical: spacing.sm },
  logLine: { flexDirection: 'row', gap: spacing.sm, alignItems: 'center' },
  flex: { flex: 1 },
});
