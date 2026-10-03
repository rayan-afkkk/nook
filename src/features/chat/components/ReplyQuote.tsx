import { StyleSheet, View } from 'react-native';

import { Icon, Text } from '@/components/ui';
import { radius, spacing, useTheme } from '@/theme';

import type { ReplyRef } from '../types';

const KIND_ICON = { image: 'image-outline', file: 'document-outline', voice: 'mic-outline', gif: 'film-outline', sticker: 'happy-outline' } as const;

/** Quote of the message being replied to, inside a bubble or above the composer. */
export function ReplyQuote({ reply, author, onMine, compact }: { reply: ReplyRef; author: string; onMine?: boolean; compact?: boolean }) {
  const { colors } = useTheme();
  const fg = onMine ? colors.onPrimary : colors.text;
  const icon = reply.kind in KIND_ICON ? KIND_ICON[reply.kind as keyof typeof KIND_ICON] : null;
  return (
    <View
      accessible
      accessibilityLabel={`Reply to ${author}: ${reply.text}`}
      style={[
        styles.quote,
        { borderLeftColor: colors.accent, backgroundColor: onMine ? 'rgba(0,0,0,0.08)' : colors.surfaceRaised },
        compact && styles.compact,
      ]}
    >
      <Text variant="captionBold" style={{ color: fg }} numberOfLines={1}>
        {author}
      </Text>
      <View style={styles.row}>
        {icon ? <Icon name={icon} size={13} color={fg} /> : null}
        <Text variant="caption" style={{ color: fg, opacity: 0.75, flexShrink: 1 }} numberOfLines={compact ? 1 : 2}>
          {reply.text || 'Message'}
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  quote: { borderLeftWidth: 3, borderRadius: radius.sm, paddingHorizontal: spacing.sm, paddingVertical: 6, marginBottom: 6 },
  compact: { marginBottom: 0, flex: 1 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 4 },
});
