import { ActivityIndicator, StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';

import { haptics } from '@/lib/haptics';
import { radius, spacing, useTheme } from '@/theme';

import { Icon, type IconName } from './Icon';
import { PressableScale } from './PressableScale';
import { Text } from './Text';

export type ButtonVariant = 'primary' | 'secondary' | 'destructive' | 'ghost';

type Props = {
  title: string;
  onPress: () => void;
  variant?: ButtonVariant;
  icon?: IconName;
  loading?: boolean;
  disabled?: boolean;
  /** Full width is the default for primary actions. */
  block?: boolean;
  accessibilityHint?: string;
  style?: StyleProp<ViewStyle>;
};

export function Button({
  title,
  onPress,
  variant = 'primary',
  icon,
  loading,
  disabled,
  block = true,
  accessibilityHint,
  style,
}: Props) {
  const { colors } = useTheme();
  const palette = {
    primary: { bg: colors.primary, fg: colors.onPrimary, border: colors.primary },
    secondary: { bg: colors.surface, fg: colors.text, border: colors.border },
    destructive: { bg: colors.surface, fg: colors.danger, border: colors.border },
    ghost: { bg: 'transparent', fg: colors.text, border: 'transparent' },
  }[variant];

  return (
    <PressableScale
      accessibilityRole="button"
      accessibilityLabel={title}
      accessibilityHint={accessibilityHint}
      accessibilityState={{ disabled: !!disabled, busy: !!loading }}
      disabled={disabled || loading}
      onPress={() => {
        haptics.light();
        onPress();
      }}
      style={[
        styles.base,
        { backgroundColor: palette.bg, borderColor: palette.border },
        block ? styles.block : styles.inline,
        style,
      ]}
    >
      <View style={styles.row}>
        {loading ? (
          <ActivityIndicator color={palette.fg} />
        ) : (
          <>
            {icon ? <Icon name={icon} size={20} color={palette.fg} /> : null}
            <Text variant="label" style={{ color: palette.fg }}>
              {title}
            </Text>
          </>
        )}
      </View>
    </PressableScale>
  );
}

const styles = StyleSheet.create({
  base: {
    minHeight: 56,
    borderRadius: radius.pill,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: spacing.xl,
  },
  block: { alignSelf: 'stretch' },
  inline: { alignSelf: 'flex-start', minHeight: 48 },
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing.xs },
});
