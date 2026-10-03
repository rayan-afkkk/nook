import { StyleSheet, View } from 'react-native';

import { haptics } from '@/lib/haptics';
import { radius, spacing, useTheme } from '@/theme';

import { Icon } from './Icon';
import { PressableScale } from './PressableScale';
import { Text } from './Text';

type Props = { title: string; message: string; onPress: () => void; actionLabel: string };

/** First-run prompt: dashed border card with a round accent "+" button. */
export function DashedCard({ title, message, onPress, actionLabel }: Props) {
  const { colors } = useTheme();
  return (
    <PressableScale
      scaleTo={0.98}
      accessibilityRole="button"
      accessibilityLabel={actionLabel}
      accessibilityHint={message}
      onPress={() => {
        haptics.light();
        onPress();
      }}
      style={[styles.card, { borderColor: colors.border }]}
    >
      <View style={styles.texts}>
        <Text variant="subhead">{title}</Text>
        <Text variant="caption" color="textMuted">
          {message}
        </Text>
      </View>
      <View style={[styles.plus, { backgroundColor: colors.accent }]}>
        <Icon name="add" size={26} color="#000000" />
      </View>
    </PressableScale>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: radius.card,
    borderWidth: 1.5,
    borderStyle: 'dashed',
    padding: spacing.lg,
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
  },
  texts: { flex: 1, gap: spacing.xxs },
  plus: { width: 48, height: 48, borderRadius: 24, alignItems: 'center', justifyContent: 'center' },
});
