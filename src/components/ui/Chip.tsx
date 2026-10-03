import { StyleSheet, View } from 'react-native';

import { radius, spacing, useTheme } from '@/theme';

import { Icon, type IconName } from './Icon';
import { Text } from './Text';

type Props = { label: string; icon?: IconName; tone?: 'info' | 'neutral' | 'accent' };

/** Small info chip (amber by default), e.g. "Member", "24h". */
export function Chip({ label, icon, tone = 'info' }: Props) {
  const { colors } = useTheme();
  const bg = tone === 'info' ? colors.info : tone === 'accent' ? colors.accent : colors.surfaceRaised;
  const fg = tone === 'neutral' ? colors.text : '#1A1714';
  return (
    <View style={[styles.chip, { backgroundColor: bg }]}>
      {icon ? <Icon name={icon} size={12} color={fg} /> : null}
      <Text variant="micro" style={{ color: fg }}>
        {label}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    alignSelf: 'flex-start',
    borderRadius: radius.pill,
    paddingHorizontal: spacing.xs + 2,
    paddingVertical: 3,
  },
});
