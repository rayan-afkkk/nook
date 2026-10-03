import { Image } from 'expo-image';
import { useState } from 'react';
import { ScrollView, StyleSheet, TextInput, View } from 'react-native';

import { EmptyState, Icon, PressableScale, SegmentedControl, Skeleton, Text } from '@/components/ui';
import { fonts, radius, spacing, useTheme } from '@/theme';

import { EMOJI_GROUPS } from './emoji';
import { GiphyGrid } from './GiphyGrid';
import type { GiphyItem, GiphyType } from './giphy';
import { useStickerPacks, type Sticker } from './packs';

type Tab = 'emoji' | 'gifs' | 'stickers' | 'packs';
const TABS = [
  { value: 'emoji', label: 'Emoji' },
  { value: 'gifs', label: 'GIFs' },
  { value: 'stickers', label: 'Stickers' },
  { value: 'packs', label: 'Ours' },
] as const;

type Props = {
  height: number;
  onEmoji: (e: string) => void;
  onGiphy: (item: GiphyItem, type: GiphyType) => void;
  onSticker: (s: Sticker) => void;
};

/** Emoji / GIF / sticker panel shown in place of the keyboard. */
export function MediaPanel({ height, onEmoji, onGiphy, onSticker }: Props) {
  const { colors } = useTheme();
  const [tab, setTab] = useState<Tab>('emoji');
  const [q, setQ] = useState('');
  const { packs, status } = useStickerPacks(tab === 'packs');

  return (
    <View style={[styles.panel, { height, backgroundColor: colors.background, borderTopColor: colors.border }]}>
      <View style={styles.top}>
        <SegmentedControl accessibilityLabel="Panel" options={TABS} value={tab} onChange={setTab} />
        {tab === 'gifs' || tab === 'stickers' ? (
          <View style={[styles.search, { backgroundColor: colors.surface, borderColor: colors.border }]}>
            <Icon name="search-outline" size={16} color="textMuted" />
            <TextInput
              value={q}
              onChangeText={setQ}
              placeholder={tab === 'gifs' ? 'Search GIFs' : 'Search stickers'}
              placeholderTextColor={colors.textMuted}
              style={[styles.input, { color: colors.text }]}
              accessibilityLabel={tab === 'gifs' ? 'Search GIFs' : 'Search stickers'}
              returnKeyType="search"
            />
          </View>
        ) : null}
      </View>
      {tab === 'emoji' ? (
        <ScrollView contentContainerStyle={styles.emojiWrap} keyboardShouldPersistTaps="handled">
          {EMOJI_GROUPS.map((g) => (
            <View key={g.label}>
              <Text variant="micro" color="textMuted" style={styles.groupLabel}>
                {g.label.toUpperCase()}
              </Text>
              <View style={styles.emojiGrid}>
                {g.emoji.map((e) => (
                  <PressableScale key={e} scaleTo={0.8} accessibilityRole="button" accessibilityLabel={e} onPress={() => onEmoji(e)} style={styles.emojiCell}>
                    <Text style={styles.emoji}>{e}</Text>
                  </PressableScale>
                ))}
              </View>
            </View>
          ))}
        </ScrollView>
      ) : tab === 'gifs' || tab === 'stickers' ? (
        <GiphyGrid type={tab} query={q} columns={tab === 'gifs' ? 2 : 3} onPick={(item) => onGiphy(item, tab)} />
      ) : status === 'loading' ? (
        <View style={styles.skeletons}>
          {[0, 1, 2, 3].map((i) => (
            <Skeleton key={i} width={72} height={72} radius={radius.sm} />
          ))}
        </View>
      ) : packs.length === 0 ? (
        <EmptyState icon="albums-outline" title="No packs yet" message="Make one from the Stickers tab with any photo." />
      ) : (
        <ScrollView contentContainerStyle={styles.packs}>
          {packs.map((p) => (
            <View key={p.id} style={styles.pack}>
              <Text variant="captionBold" color="textMuted">
                {p.name}
              </Text>
              <View style={styles.stickerGrid}>
                {p.stickers.map((s) => (
                  <PressableScale key={s.publicId} scaleTo={0.9} accessibilityRole="button" accessibilityLabel={`Sticker from ${p.name}`} onPress={() => onSticker(s)}>
                    <Image source={{ uri: s.url }} style={styles.sticker} contentFit="contain" cachePolicy="disk" />
                  </PressableScale>
                ))}
              </View>
            </View>
          ))}
        </ScrollView>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  panel: { borderTopWidth: StyleSheet.hairlineWidth },
  top: { padding: spacing.sm, gap: spacing.xs },
  search: { flexDirection: 'row', alignItems: 'center', gap: spacing.xs, borderRadius: radius.pill, borderWidth: 1, paddingHorizontal: spacing.md, minHeight: 44 },
  input: { flex: 1, fontFamily: fonts.sans, fontSize: 15, minHeight: 44 },
  emojiWrap: { paddingHorizontal: spacing.sm, paddingBottom: spacing.lg },
  groupLabel: { marginTop: spacing.sm, marginBottom: 4, marginLeft: 4 },
  emojiGrid: { flexDirection: 'row', flexWrap: 'wrap' },
  emojiCell: { width: '12.5%', aspectRatio: 1, alignItems: 'center', justifyContent: 'center' },
  emoji: { fontSize: 28, lineHeight: 36 },
  skeletons: { flexDirection: 'row', gap: spacing.sm, padding: spacing.md },
  packs: { padding: spacing.sm, gap: spacing.md },
  pack: { gap: spacing.xs },
  stickerGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.xs },
  sticker: { width: 76, height: 76 },
});
