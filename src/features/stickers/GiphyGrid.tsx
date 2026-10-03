import { Image } from 'expo-image';
import { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, FlatList, StyleSheet, View } from 'react-native';

import { EmptyState, PressableScale, Text } from '@/components/ui';
import { describeError } from '@/lib/errors';
import { radius, spacing, useTheme } from '@/theme';

import { searchGiphy, type GiphyItem, type GiphyType } from './giphy';

type Props = { type: GiphyType; query: string; columns?: number; onPick: (item: GiphyItem) => void };

/** Trending/search grid with debounced search and infinite scroll. */
export function GiphyGrid({ type, query, columns = 3, onPick }: Props) {
  const { colors } = useTheme();
  const [items, setItems] = useState<GiphyItem[]>([]);
  const [next, setNext] = useState<number | null>(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const reqId = useRef(0);

  const load = useCallback(
    async (offset: number, replace: boolean) => {
      const id = ++reqId.current;
      setLoading(true);
      try {
        const r = await searchGiphy(type, query, offset);
        if (id !== reqId.current) return;
        setItems((prev) => (replace ? r.items : [...prev, ...r.items]));
        setNext(r.next);
        setError(null);
      } catch (e) {
        if (id === reqId.current) setError(describeError(e));
      } finally {
        if (id === reqId.current) setLoading(false);
      }
    },
    [type, query],
  );

  useEffect(() => {
    const t = setTimeout(() => void load(0, true), query ? 400 : 0);
    return () => clearTimeout(t);
  }, [load, query]);

  if (error && items.length === 0) return <EmptyState icon="cloud-offline-outline" title="No GIFs right now" message={error} />;

  return (
    <FlatList
      data={items}
      key={columns}
      numColumns={columns}
      keyExtractor={(i) => i.id}
      keyboardShouldPersistTaps="handled"
      contentContainerStyle={styles.content}
      columnWrapperStyle={styles.gap}
      onEndReachedThreshold={0.6}
      onEndReached={() => {
        if (!loading && next !== null) void load(next, false);
      }}
      ListEmptyComponent={
        loading ? null : <EmptyState icon="search-outline" title="Nothing found" message="Try another word." />
      }
      ListFooterComponent={
        <View style={styles.footer}>
          {loading ? <ActivityIndicator color={colors.textMuted} /> : null}
          <Text variant="micro" color="textMuted">
            Powered by GIPHY
          </Text>
        </View>
      }
      renderItem={({ item }) => (
        <PressableScale
          scaleTo={0.94}
          accessibilityRole="button"
          accessibilityLabel={item.title || (type === 'gifs' ? 'GIF' : 'Sticker')}
          onPress={() => onPick(item)}
          style={[styles.cell, { backgroundColor: type === 'gifs' ? colors.surfaceRaised : 'transparent' }]}
        >
          <Image source={{ uri: item.url }} placeholder={{ uri: item.previewUrl }} style={styles.img} contentFit={type === 'gifs' ? 'cover' : 'contain'} autoplay cachePolicy="memory-disk" />
        </PressableScale>
      )}
    />
  );
}

const styles = StyleSheet.create({
  content: { padding: spacing.xs, gap: spacing.xs },
  gap: { gap: spacing.xs },
  cell: { flex: 1, aspectRatio: 1, borderRadius: radius.sm, overflow: 'hidden' },
  img: { width: '100%', height: '100%' },
  footer: { alignItems: 'center', gap: spacing.xs, paddingVertical: spacing.md },
});
