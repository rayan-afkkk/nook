import { StyleSheet, View } from 'react-native';

import { Text } from '@/components/ui';
import { spacing, useTheme } from '@/theme';

export function DateSeparator({ label }: { label: string }) {
  const { colors } = useTheme();
  return (
    <View style={styles.row} accessibilityRole="header">
      <View style={[styles.line, { backgroundColor: colors.border }]} />
      <Text variant="micro" color="textMuted">
        {label.toUpperCase()}
      </Text>
      <View style={[styles.line, { backgroundColor: colors.border }]} />
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm, paddingVertical: spacing.md, paddingHorizontal: spacing.xl },
  line: { flex: 1, height: StyleSheet.hairlineWidth },
});
