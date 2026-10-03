import { Image } from 'expo-image';
import { StyleSheet, View } from 'react-native';

import { Icon, PressableScale, ProgressBar, Text } from '@/components/ui';
import { discardOutbox, retryOutbox, type OutboxItem } from '@/features/media/outbox';
import { formatBytes } from '@/lib/format';
import { radius, spacing, useTheme } from '@/theme';

/** A media message that is still uploading (or failed), shown optimistically at the bottom of the chat. */
export function OutboxBubble({ item, me }: { item: OutboxItem; me: string }) {
  const { colors } = useTheme();
  const failed = item.status === 'failed';
  const label =
    item.kind === 'image' || item.kind === 'sticker' ? 'Photo' : item.kind === 'voice' ? 'Voice note' : item.name;
  return (
    <View style={styles.wrap}>
      <View style={[styles.bubble, { backgroundColor: colors.primary, opacity: failed ? 0.85 : 1 }]}>
        {item.kind === 'image' || item.kind === 'sticker' ? (
          <Image source={{ uri: item.uri }} style={styles.preview} contentFit="cover" />
        ) : (
          <View style={styles.row}>
            <Icon name={item.kind === 'voice' ? 'mic-outline' : 'document-outline'} size={20} color={colors.onPrimary} />
            <Text variant="bodyBold" style={{ color: colors.onPrimary, flexShrink: 1 }} numberOfLines={1}>
              {label}
            </Text>
            {item.size ? (
              <Text variant="micro" style={{ color: colors.onPrimary, opacity: 0.7 }}>
                {formatBytes(item.size)}
              </Text>
            ) : null}
          </View>
        )}
        {failed ? (
          <View style={styles.failRow}>
            <Text variant="caption" style={{ color: colors.danger, flex: 1 }} numberOfLines={2}>
              {item.error ?? 'Upload failed'}
            </Text>
            <PressableScale accessibilityRole="button" accessibilityLabel="Retry upload" onPress={() => retryOutbox(item.localId, me)} style={styles.action}>
              <Icon name="refresh" size={18} color={colors.onPrimary} />
            </PressableScale>
            <PressableScale accessibilityRole="button" accessibilityLabel="Discard" onPress={() => discardOutbox(item.localId)} style={styles.action}>
              <Icon name="close" size={18} color={colors.onPrimary} />
            </PressableScale>
          </View>
        ) : (
          <View style={styles.progress}>
            <ProgressBar progress={Math.max(0.04, item.progress)} label={`Uploading ${label}`} />
          </View>
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { paddingHorizontal: spacing.md, marginTop: spacing.sm, alignItems: 'flex-end' },
  bubble: { maxWidth: '80%', borderRadius: 22, borderBottomRightRadius: 6, padding: 8, gap: 8, minWidth: 180 },
  preview: { width: 220, height: 220, borderRadius: 16 },
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing.xs, paddingHorizontal: 6, paddingTop: 4 },
  progress: { paddingHorizontal: 6, paddingBottom: 4 },
  failRow: { flexDirection: 'row', alignItems: 'center', gap: 4, paddingHorizontal: 6 },
  action: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center', borderRadius: radius.pill },
});
