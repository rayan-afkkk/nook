import { useState } from 'react';
import { StyleSheet, View, useWindowDimensions } from 'react-native';
import { useReducedMotion } from 'react-native-reanimated';

import { Header, Icon, PressableScale, Screen, Text, TextField, toast, type IconName } from '@/components/ui';
import { Float, PopIn } from '@/features/onboarding/illustrations/motion';
import { radius, spacing, useTheme } from '@/theme';

const TAGS: { label: string; tone: 'mint' | 'sky' | 'peach' | 'lavender' | 'rose'; tilt: number }[] = [
  { label: 'lol', tone: 'peach', tilt: -6 },
  { label: 'mood', tone: 'lavender', tilt: 4 },
  { label: 'hype', tone: 'mint', tilt: -3 },
  { label: 'sorry', tone: 'rose', tilt: 7 },
  { label: 'birthday', tone: 'sky', tilt: -5 },
  { label: 'no way', tone: 'peach', tilt: 3 },
  { label: 'sleepy', tone: 'mint', tilt: -7 },
  { label: 'yes!!', tone: 'lavender', tilt: 5 },
];

const TILES: { title: string; subtitle: string; icon: IconName; tone: 'mint' | 'sky' | 'peach' | 'lavender' }[] = [
  { title: 'GIFs', subtitle: 'Search the whole internet', icon: 'film-outline', tone: 'lavender' },
  { title: 'Stickers', subtitle: 'Big, bold reactions', icon: 'happy-outline', tone: 'peach' },
  { title: 'Our packs', subtitle: 'Made by your crew', icon: 'albums-outline', tone: 'mint' },
  { title: 'Make a sticker', subtitle: 'From any photo', icon: 'crop-outline', tone: 'sky' },
];

export default function Stickers() {
  const { colors } = useTheme();
  const reduceMotion = useReducedMotion();
  const { width } = useWindowDimensions();
  const tileSize = Math.floor((width - spacing.lg * 2 - spacing.sm) / 2);
  const [query, setQuery] = useState('');
  const soon = () => toast.show('GIFs and sticker packs arrive in an upcoming update.');

  return (
    <Screen scroll padded={false} header={<Header title="Stickers" />}>
      <View style={styles.body}>
        <Text variant="display" accessibilityRole="header">
          Find the perfect reaction
        </Text>
        <TextField
          label="Search"
          placeholder="GIFs and stickers"
          value={query}
          onChangeText={setQuery}
          returnKeyType="search"
          onSubmitEditing={soon}
          right={<Icon name="search-outline" color="textMuted" />}
        />

        <View style={styles.tags} accessibilityLabel="Trending categories">
          {TAGS.map((t, i) => (
            <PopIn key={t.label} active reduceMotion={reduceMotion} delay={i * 60} rotate={t.tilt}>
              <Float active reduceMotion={reduceMotion} delay={i * 180} amplitude={3} duration={2400 + i * 120}>
                <PressableScale
                  accessibilityRole="button"
                  accessibilityLabel={`Trending: ${t.label}`}
                  onPress={soon}
                  style={[styles.tag, { backgroundColor: colors.pastel[t.tone] }]}
                >
                  <Text variant="label" color="onPastel">
                    {t.label}
                  </Text>
                </PressableScale>
              </Float>
            </PopIn>
          ))}
        </View>

        <View style={styles.grid}>
          {TILES.map((tile) => (
            <PressableScale
              key={tile.title}
              accessibilityRole="button"
              accessibilityLabel={tile.title}
              accessibilityHint={tile.subtitle}
              onPress={soon}
              style={[styles.tile, { width: tileSize, height: tileSize, backgroundColor: colors.pastel[tile.tone] }]}
            >
              <Icon name={tile.icon} size={30} color="onPastel" />
              <View>
                <Text variant="subhead" color="onPastel">
                  {tile.title}
                </Text>
                <Text variant="caption" color="onPastel" style={styles.tileSub}>
                  {tile.subtitle}
                </Text>
              </View>
            </PressableScale>
          ))}
        </View>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  body: { padding: spacing.lg, gap: spacing.lg },
  tags: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm, paddingVertical: spacing.xs },
  tag: { minHeight: 44, paddingHorizontal: spacing.md, borderRadius: radius.pill, justifyContent: 'center' },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  tile: {
    borderRadius: radius.card,
    padding: spacing.lg,
    justifyContent: 'space-between',
  },
  tileSub: { opacity: 0.7 },
});
