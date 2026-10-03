import Ionicons from '@expo/vector-icons/Ionicons';
import type { ComponentProps } from 'react';

import { useTheme, type ThemeColors } from '@/theme';

export type IconName = ComponentProps<typeof Ionicons>['name'];

type Props = {
  name: IconName;
  size?: number;
  color?: keyof Pick<ThemeColors, 'text' | 'textMuted' | 'accent' | 'danger' | 'onPrimary' | 'info' | 'onPastel'> | (string & {});
};

/** Outline line icons (Ionicons, MIT). Use the "-outline" names by default and the filled name for active states. */
export function Icon({ name, size = 22, color = 'text' }: Props) {
  const { colors } = useTheme();
  const resolved = color in colors ? (colors[color as keyof ThemeColors] as string) : color;
  return <Ionicons name={name} size={size} color={resolved} />;
}
