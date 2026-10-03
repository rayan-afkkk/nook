import { router } from 'expo-router';
import { Image } from 'expo-image';
import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { Button, Card, EmptyState, Header, PressableScale, Screen, SkeletonRow, Text, toast } from '@/components/ui';
import { useSession } from '@/features/auth/session';
import { sendMessage } from '@/features/chat/chatService';
import { ChatPickerSheet } from '@/features/chat/components/ChatPickerSheet';
import { useStickerPacks, type Sticker } from '@/features/stickers/packs';
import { describeError } from '@/lib/errors';
import { haptics } from '@/lib/haptics';
import { spacing } from '@/theme';

/** "Our packs": sticker packs made by people on this NOOK, shared with everyone. */
export default function Packs() {
  const me = useSession((s) => s.user?.uid ?? '');
  const { packs, status } = useStickerPacks();
  const [picked, setPicked] = useState<Sticker | null>(null);
  return (
    <Screen
      scroll
      header={<Header title="Our packs" back />}
      footer={
        <View style={styles.footer}>
          <Button title="Make a sticker" icon="crop-outline" onPress={() => router.push('/make-sticker')} />
        </View>
      }
    >
      <View style={styles.body}>
        {status === 'loading' ? (
          [0, 1].map((i) => <SkeletonRow key={i} />)
        ) : status === 'error' ? (
          <EmptyState icon="cloud-offline-outline" title="Couldn’t load packs" message="Check your connection." />
        ) : packs.length === 0 ? (
          <EmptyState icon="albums-outline" title="No packs yet" message="Turn any photo into a sticker and start the first pack for your crew." />
        ) : (
          packs.map((p) => (
            <Card key={p.id} style={styles.pack}>
              <Text variant="subhead">{p.name}</Text>
              <Text variant="caption" color="textMuted">
                {p.stickers.length} {p.stickers.length === 1 ? 'sticker' : 'stickers'}
              </Text>
              <View style={styles.grid}>
                {p.stickers.map((s) => (
                  <PressableScale key={s.publicId} scaleTo={0.9} accessibilityRole="button" accessibilityLabel={`Send sticker from ${p.name}`} onPress={() => setPicked(s)}>
                    <Image source={{ uri: s.url }} style={styles.sticker} contentFit="contain" cachePolicy="disk" />
                  </PressableScale>
                ))}
              </View>
            </Card>
          ))
        )}
      </View>
      <ChatPickerSheet
        visible={!!picked}
        me={me}
        title="Send to"
        onClose={() => setPicked(null)}
        onPick={(chat, name) => {
          const s = picked;
          setPicked(null);
          if (!s) return;
          sendMessage(chat, me, { kind: 'sticker', media: { url: s.url, width: 512, height: 512 } })
            .then(() => {
              haptics.success();
              toast.success(`Sent to ${name}`);
            })
            .catch((e) => toast.error(describeError(e)));
        }}
      />
    </Screen>
  );
}

const styles = StyleSheet.create({
  body: { gap: spacing.md, paddingTop: spacing.lg },
  pack: { gap: spacing.xs },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.xs, marginTop: spacing.xs },
  sticker: { width: 72, height: 72 },
  footer: { paddingHorizontal: spacing.lg, paddingBottom: spacing.md, paddingTop: spacing.xs },
});
