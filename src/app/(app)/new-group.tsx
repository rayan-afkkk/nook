import { router } from 'expo-router';
import { useState } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, { FadeIn, FadeOut, LinearTransition } from 'react-native-reanimated';

import { Avatar, Button, Header, IconButton, Screen, Text, TextField, toast, useIsOffline } from '@/components/ui';
import { useSession } from '@/features/auth/session';
import { createGroup } from '@/features/chat/chatService';
import { UsernameSearch } from '@/features/friends/UsernameSearch';
import { useFriends } from '@/features/friends/useFriends';
import { displayNameOf, useProfiles } from '@/features/profile/profiles';
import type { Profile } from '@/features/profile/types';
import { describeError } from '@/lib/errors';
import { haptics } from '@/lib/haptics';
import { usePrefs } from '@/stores/prefs';
import { radius, spacing, useTheme } from '@/theme';

export default function NewGroup() {
  const { colors } = useTheme();
  const me = useSession((s) => s.user?.uid ?? '');
  const offline = useIsOffline();
  const disappearing = usePrefs((s) => s.disappearingDefault);
  const friends = useFriends(me);
  const profiles = useProfiles((s) => s.byId);
  const [name, setName] = useState('');
  const [picked, setPicked] = useState<Profile[]>([]);
  const [busy, setBusy] = useState(false);

  const toggle = (p: Profile) => {
    haptics.tick();
    setPicked((prev) => (prev.some((x) => x.uid === p.uid) ? prev.filter((x) => x.uid !== p.uid) : [...prev, p]));
  };

  const create = async () => {
    setBusy(true);
    try {
      const id = await createGroup(me, name, picked.map((p) => p.uid), disappearing);
      haptics.success();
      router.replace({ pathname: '/chat/[id]', params: { id } });
    } catch (e) {
      toast.error(describeError(e));
      setBusy(false);
    }
  };

  return (
    <Screen
      scroll
      header={<Header title="New group" back />}
      footer={
        <View style={styles.footer}>
          <Button
            title={picked.length ? `Create with ${picked.length} ${picked.length === 1 ? 'friend' : 'friends'}` : 'Create group'}
            icon="people-outline"
            loading={busy}
            disabled={!name.trim() || picked.length === 0 || offline}
            onPress={() => void create()}
          />
        </View>
      }
    >
      <View style={styles.body}>
        <TextField label="Group name" placeholder="The Boys" value={name} onChangeText={setName} maxLength={40} autoFocus />
        {picked.length ? (
          <Animated.View layout={LinearTransition} style={styles.chips}>
            {picked.map((p) => (
              <Animated.View key={p.uid} entering={FadeIn} exiting={FadeOut} style={[styles.chip, { backgroundColor: colors.surface, borderColor: colors.border }]}>
                <Avatar name={p.displayName} uri={p.photoURL} size={28} seed={p.uid} />
                <Text variant="captionBold">{p.displayName.split(' ')[0]}</Text>
                <IconButton icon="close" label={`Remove ${p.displayName}`} size={16} onPress={() => toggle(p)} />
              </Animated.View>
            ))}
          </Animated.View>
        ) : null}
        <UsernameSearch me={me} exclude={picked.map((p) => p.uid)} onPick={toggle} />
        {friends.length ? (
          <View style={styles.section}>
            <Text variant="captionBold" color="textMuted">
              PEOPLE YOU CHAT WITH
            </Text>
            {friends.map((uid) => {
              const p = profiles[uid];
              if (!p) return null;
              const on = picked.some((x) => x.uid === uid);
              return (
                <Button
                  key={uid}
                  title={`${on ? '✓ ' : ''}${displayNameOf(p)}`}
                  variant={on ? 'primary' : 'secondary'}
                  onPress={() => toggle(p)}
                />
              );
            })}
          </View>
        ) : null}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  body: { gap: spacing.md, paddingTop: spacing.lg },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.xs },
  chip: { flexDirection: 'row', alignItems: 'center', gap: 6, borderRadius: radius.pill, borderWidth: 1, paddingLeft: 4 },
  section: { gap: spacing.xs },
  footer: { paddingHorizontal: spacing.lg, paddingBottom: spacing.md, paddingTop: spacing.xs },
});
