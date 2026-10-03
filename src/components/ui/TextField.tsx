import { forwardRef, useState, type ReactNode } from 'react';
import { Platform, StyleSheet, TextInput, View, type TextInputProps } from 'react-native';

import { fonts, radius, spacing, touchTarget, useTheme } from '@/theme';

import { Text } from './Text';

type Props = TextInputProps & {
  label: string;
  /** Muted helper line; replaced by `error` when present. */
  helper?: string | null;
  error?: string | null;
  success?: string | null;
  prefix?: string;
  right?: ReactNode;
};

export const TextField = forwardRef<TextInput, Props>(function TextField(
  { label, helper, error, success, prefix, right, style, onFocus, onBlur, ...rest },
  ref,
) {
  const { colors } = useTheme();
  const [focused, setFocused] = useState(false);
  const borderColor = error ? colors.danger : focused ? colors.text : colors.border;
  const message = error ?? success ?? helper;
  return (
    <View style={styles.wrap}>
      <Text variant="captionBold" color="textMuted">
        {label}
      </Text>
      <View style={[styles.field, { backgroundColor: colors.surface, borderColor }]}>
        {prefix ? (
          <Text variant="bodyLarge" color="textMuted">
            {prefix}
          </Text>
        ) : null}
        <TextInput
          ref={ref}
          accessibilityLabel={label}
          accessibilityHint={message ?? undefined}
          placeholderTextColor={colors.textMuted}
          selectionColor={colors.accent}
          cursorColor={colors.accent}
          onFocus={(e) => {
            setFocused(true);
            onFocus?.(e);
          }}
          onBlur={(e) => {
            setFocused(false);
            onBlur?.(e);
          }}
          style={[styles.input, { color: colors.text }, style]}
          {...rest}
        />
        {right}
      </View>
      <Text
        variant="caption"
        color={error ? 'danger' : 'textMuted'}
        style={[styles.message, success && !error ? { color: colors.online } : null]}
        accessibilityLiveRegion="polite"
      >
        {message ?? ' '}
      </Text>
    </View>
  );
});

const styles = StyleSheet.create({
  wrap: { gap: spacing.xs, marginBottom: spacing.xs },
  field: {
    minHeight: 56,
    borderRadius: radius.md,
    borderWidth: 1,
    paddingHorizontal: spacing.md,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 2,
  },
  input: { flex: 1, minHeight: touchTarget, fontFamily: fonts.sans, fontSize: 17, ...(Platform.OS === 'web' ? { outlineWidth: 0 } : null) },
  message: { minHeight: 18 },
});
