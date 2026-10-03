import { StyleSheet, View } from 'react-native';

import { Avatar, Button, Divider, EmptyState, Header, Screen, Text, toast } from '@/components/ui';
import { useSession } from '@/features/auth/session';
import { setBlocked, usePrivate } from '@/features/friends/privateDoc';
import { displayNameOf, useProfile } from '@/features/profile/profiles';
import { describeError } from '@/lib/errors';
import { spacing } from '@/theme';

function BlockedRow({ uid, me }: { uid: string; me: string }) {
  const p = useProfile(uid);
  const name = displayNameOf(p);
  return (
    <View style={styles.row}>
      <Avatar name={name} uri={p?.photoURL} size={44} seed={uid} />
      <View style={styles.flex}>
        <Text variant="bodyBold">{name}</Text>
        <Text variant="caption" color="textMuted">
          {p ? `@${p.username}` : ' '}
        </Text>
      </View>
      <Button
        title="Unblock"
        block={false}
        variant="secondary"
        onPress={() => void setBlocked(me, uid, false).then(() => toast.show(`${name} unblocked`)).catch((e) => toast.error(describeError(e)))}
      />
    </View>
  );
}

export default function Blocked() {
  const me = useSession((s) => s.user?.uid ?? '');
  const blocked = usePrivate((s) => s.blocked);
  return (
    <Screen scroll header={<Header title="Blocked" back />}>
      {blocked.length === 0 ? (
        <EmptyState icon="ban-outline" title="No one’s blocked" message="People you block can’t message or call you. Block someone from their profile or chat info." />
      ) : (
        <View style={styles.list}>
          {blocked.map((uid, i) => (
            <View key={uid}>
              {i > 0 ? <Divider inset={60} /> : null}
              <BlockedRow uid={uid} me={me} />
            </View>
          ))}
        </View>
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  list: { paddingTop: spacing.md },
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing.md, paddingVertical: spacing.sm },
  flex: { flex: 1 },
});
