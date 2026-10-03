import { StyleSheet, View } from 'react-native';

import { Icon, Text } from '@/components/ui';
import { hashString, initials } from '@/lib/format';
import { useTheme } from '@/theme';

export function GroupAvatar({ name, size = 52, seed }: { name: string; size?: number; seed: string }) {
  const { colors } = useTheme();
  const tints = Object.values(colors.pastel);
  const tint = tints[hashString(seed) % tints.length] ?? colors.pastel.lavender;
  return (
    <View
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
      style={[styles.box, { width: size, height: size, borderRadius: size * 0.34, backgroundColor: tint }]}
    >
      {name ? (
        <Text variant={size >= 64 ? 'headline' : 'label'} color="onPastel">
          {initials(name)}
        </Text>
      ) : (
        <Icon name="people" size={size * 0.45} color="onPastel" />
      )}
    </View>
  );
}

const styles = StyleSheet.create({ box: { alignItems: 'center', justifyContent: 'center' } });
