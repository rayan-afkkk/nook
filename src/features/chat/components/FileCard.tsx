import { File, Paths } from 'expo-file-system';
import * as Sharing from 'expo-sharing';
import { useState } from 'react';
import { ActivityIndicator, Linking, StyleSheet, View } from 'react-native';

import { Icon, PressableScale, Text, toast } from '@/components/ui';
import { formatBytes } from '@/lib/format';
import { radius, spacing, useTheme } from '@/theme';

import type { Media } from '../types';

function iconFor(mime: string | undefined, name: string | undefined) {
  const n = (name ?? '').toLowerCase();
  if (mime?.includes('pdf') || n.endsWith('.pdf')) return 'document-text-outline' as const;
  if (mime?.startsWith('audio') || /\.(mp3|m4a|wav)$/.test(n)) return 'musical-notes-outline' as const;
  if (mime?.startsWith('video') || /\.(mp4|mov)$/.test(n)) return 'film-outline' as const;
  if (/\.(zip|rar|7z)$/.test(n)) return 'archive-outline' as const;
  return 'document-outline' as const;
}

/** Tap downloads to the cache and opens Android's "open with" sheet. */
export function FileCard({ media, mine }: { media: Media; mine: boolean }) {
  const { colors } = useTheme();
  const [busy, setBusy] = useState(false);
  const fg = mine ? colors.onPrimary : colors.text;
  const open = async () => {
    setBusy(true);
    try {
      const safe = (media.name ?? 'file').replace(/[^\w.\- ]+/g, '_');
      const target = new File(Paths.cache, `${Date.now()}-${safe}`);
      const file = await File.downloadFileAsync(media.url, target);
      if (await Sharing.isAvailableAsync()) {
        await Sharing.shareAsync(file.uri, { mimeType: media.mime, dialogTitle: media.name });
      } else {
        await Linking.openURL(media.url);
      }
    } catch {
      toast.error("Couldn't open that file. Check your connection.");
    } finally {
      setBusy(false);
    }
  };
  return (
    <PressableScale
      scaleTo={0.98}
      accessibilityRole="button"
      accessibilityLabel={`File ${media.name ?? ''}, ${formatBytes(media.size ?? 0)}. Open`}
      onPress={() => void open()}
      style={styles.card}
    >
      <View style={[styles.icon, { backgroundColor: mine ? 'rgba(0,0,0,0.08)' : colors.surfaceRaised }]}>
        {busy ? <ActivityIndicator color={fg} /> : <Icon name={iconFor(media.mime, media.name)} size={22} color={fg} />}
      </View>
      <View style={styles.texts}>
        <Text variant="bodyBold" style={{ color: fg }} numberOfLines={2}>
          {media.name ?? 'File'}
        </Text>
        <Text variant="micro" style={{ color: fg, opacity: 0.7 }}>
          {formatBytes(media.size ?? 0)} · Tap to open
        </Text>
      </View>
    </PressableScale>
  );
}

const styles = StyleSheet.create({
  card: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm, minWidth: 200, maxWidth: 260 },
  icon: { width: 44, height: 44, borderRadius: radius.sm, alignItems: 'center', justifyContent: 'center' },
  texts: { flex: 1, gap: 2 },
});
