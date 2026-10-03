import { Text as RNText, type TextProps as RNTextProps, type TextStyle } from 'react-native';

import { typography, useTheme, type ThemeColors, type TypographyVariant } from '@/theme';

type ColorKey = keyof Pick<
  ThemeColors,
  'text' | 'textMuted' | 'accent' | 'danger' | 'onPrimary' | 'info' | 'onPastel' | 'onHighlight' | 'primary'
>;

export type TextProps = RNTextProps & {
  variant?: TypographyVariant;
  color?: ColorKey;
  align?: TextStyle['textAlign'];
};

export function Text({ variant = 'body', color = 'text', align, style, ...rest }: TextProps) {
  const { colors } = useTheme();
  return (
    <RNText
      maxFontSizeMultiplier={1.6}
      {...rest}
      style={[typography[variant], { color: colors[color], textAlign: align }, style]}
    />
  );
}
