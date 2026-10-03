import { Switch } from 'react-native';

import { haptics } from '@/lib/haptics';
import { useTheme } from '@/theme';

type Props = { value: boolean; onValueChange: (v: boolean) => void; label: string; disabled?: boolean };

export function Toggle({ value, onValueChange, label, disabled }: Props) {
  const { colors } = useTheme();
  return (
    <Switch
      accessibilityLabel={label}
      value={value}
      disabled={disabled}
      onValueChange={(v) => {
        haptics.tick();
        onValueChange(v);
      }}
      trackColor={{ false: colors.surfaceRaised, true: colors.accent }}
      thumbColor={colors.text}
    />
  );
}
