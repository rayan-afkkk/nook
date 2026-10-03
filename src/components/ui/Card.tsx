import { StyleSheet, View, type ViewProps } from 'react-native';

import { radius, spacing, useTheme } from '@/theme';

type Props = ViewProps & {
  tone?: 'surface' | 'highlight' | 'mint' | 'sky' | 'peach' | 'lavender' | 'rose';
  padded?: boolean;
};

export function Card({ tone = 'surface', padded = true, style, ...rest }: Props) {
  const { colors } = useTheme();
  const bg =
    tone === 'surface' ? colors.surface : tone === 'highlight' ? colors.highlight : colors.pastel[tone];
  const border = tone === 'surface' ? colors.border : 'transparent';
  return (
    <View
      {...rest}
      style={[styles.card, { backgroundColor: bg, borderColor: border }, padded && styles.padded, style]}
    />
  );
}

const styles = StyleSheet.create({
  card: { borderRadius: radius.card, borderWidth: 1, overflow: 'hidden' },
  padded: { padding: spacing.lg },
});
