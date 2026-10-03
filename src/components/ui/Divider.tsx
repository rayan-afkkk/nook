import { StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';

import { useTheme } from '@/theme';

export function Divider({ inset = 0, style }: { inset?: number; style?: StyleProp<ViewStyle> }) {
  const { colors } = useTheme();
  return (
    <View
      importantForAccessibility="no"
      style={[{ height: StyleSheet.hairlineWidth, backgroundColor: colors.border, marginLeft: inset }, style]}
    />
  );
}
