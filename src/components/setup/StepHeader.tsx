import { StyleSheet, View } from 'react-native';

import { Text } from '@/components/ui';
import { radius, spacing, useTheme } from '@/theme';

type Props = { step: number; total: number; title: string; subtitle: string };

/** Progress bar segments + serif title used across the first-run setup steps. */
export function StepHeader({ step, total, title, subtitle }: Props) {
  const { colors } = useTheme();
  return (
    <View style={styles.wrap}>
      <View style={styles.bars} accessible accessibilityLabel={`Step ${step} of ${total}`}>
        {Array.from({ length: total }).map((_, i) => (
          <View
            key={i}
            style={[styles.bar, { backgroundColor: i < step ? colors.accent : colors.surfaceRaised }]}
          />
        ))}
      </View>
      <Text variant="title" accessibilityRole="header">
        {title}
      </Text>
      <Text variant="body" color="textMuted">
        {subtitle}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: spacing.sm, paddingTop: spacing.lg, paddingBottom: spacing.xl },
  bars: { flexDirection: 'row', gap: 6, marginBottom: spacing.md },
  bar: { flex: 1, height: 4, borderRadius: radius.pill },
});
