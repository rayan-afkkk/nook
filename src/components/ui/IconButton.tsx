import { StyleSheet, View } from 'react-native';

import { haptics } from '@/lib/haptics';
import { radius, touchTarget, useTheme } from '@/theme';

import { Icon, type IconName } from './Icon';
import { PressableScale } from './PressableScale';
import { Text } from './Text';

type Props = {
  icon: IconName;
  label: string;
  onPress: () => void;
  badge?: number;
  variant?: 'plain' | 'filled';
  disabled?: boolean;
  size?: number;
};

export function IconButton({ icon, label, onPress, badge, variant = 'plain', disabled, size = 22 }: Props) {
  const { colors } = useTheme();
  return (
    <PressableScale
      accessibilityRole="button"
      accessibilityLabel={label}
      hitSlop={4}
      disabled={disabled}
      onPress={() => {
        haptics.tick();
        onPress();
      }}
      style={[
        styles.button,
        variant === 'filled' && { backgroundColor: colors.surface, borderColor: colors.border, borderWidth: 1 },
      ]}
    >
      <Icon name={icon} size={size} />
      {badge ? (
        <View style={[styles.badge, { backgroundColor: colors.accent }]}>
          <Text variant="micro" style={{ color: '#000' }}>
            {badge > 99 ? '99+' : badge}
          </Text>
        </View>
      ) : null}
    </PressableScale>
  );
}

const styles = StyleSheet.create({
  button: {
    width: touchTarget,
    height: touchTarget,
    borderRadius: radius.pill,
    alignItems: 'center',
    justifyContent: 'center',
  },
  badge: {
    position: 'absolute',
    top: 6,
    right: 4,
    minWidth: 18,
    height: 18,
    borderRadius: 9,
    paddingHorizontal: 4,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
