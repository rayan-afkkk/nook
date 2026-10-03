import { useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { Header, Icon, Screen, SegmentedControl, TextField, toast } from '@/components/ui';
import { useSession } from '@/features/auth/session';
import { sendMessage } from '@/features/chat/chatService';
import { ChatPickerSheet } from '@/features/chat/components/ChatPickerSheet';
import { GiphyGrid } from '@/features/stickers/GiphyGrid';
import type { GiphyItem, GiphyType } from '@/features/stickers/giphy';
import { describeError } from '@/lib/errors';
import { haptics } from '@/lib/haptics';
import { spacing } from '@/theme';

const TYPES = [
  { value: 'gifs', label: 'GIFs' },
  { value: 'stickers', label: 'Stickers' },
] as const;

/** Browse Giphy from the Stickers tab, then send to a chat. */
export default function GifBrowser() {
  const params = useLocalSearchParams<{ type?: string; q?: string }>();
  const me = useSession((s) => s.user?.uid ?? '');
  const [type, setType] = useState<GiphyType>(params.type === 'stickers' ? 'stickers' : 'gifs');
  const [q, setQ] = useState(params.q ?? '');
  const [picked, setPicked] = useState<GiphyItem | null>(null);
  return (
    <Screen padded={false} header={<Header title={type === 'gifs' ? 'GIFs' : 'Stickers'} back />}>
      <View style={styles.top}>
        <SegmentedControl accessibilityLabel="Type" options={TYPES} value={type} onChange={setType} />
        <TextField label="Search" placeholder="Search Giphy" value={q} onChangeText={setQ} returnKeyType="search" right={<Icon name="search-outline" color="textMuted" />} />
      </View>
      <View style={styles.flex}>
        <GiphyGrid type={type} query={q} columns={type === 'gifs' ? 2 : 3} onPick={setPicked} />
      </View>
      <ChatPickerSheet
        visible={!!picked}
        me={me}
        title="Send to"
        onClose={() => setPicked(null)}
        onPick={(chat, name) => {
          const item = picked;
          setPicked(null);
          if (!item) return;
          sendMessage(chat, me, {
            kind: type === 'gifs' ? 'gif' : 'sticker',
            media: { url: item.url, previewUrl: item.previewUrl, width: item.width, height: item.height },
          })
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
  top: { paddingHorizontal: spacing.lg, paddingTop: spacing.md, gap: spacing.sm },
  flex: { flex: 1, paddingHorizontal: spacing.sm },
});
