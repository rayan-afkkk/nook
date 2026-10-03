import { Image } from 'expo-image';
import { StyleSheet, View } from 'react-native';

import { hashString, initials } from '@/lib/format';
import { useTheme } from '@/theme';

import { Text } from './Text';

type Props = { name: string; uri?: string | null; size?: number; online?: boolean; seed?: string };

export function Avatar({ name, uri, size = 48, online, seed }: Props) {
  const { colors } = useTheme();
  const tints = Object.values(colors.pastel);
  const tint = tints[hashString(seed ?? name) % tints.length] ?? colors.pastel.peach;
  const dot = Math.max(10, Math.round(size * 0.26));
  return (
    <View style={{ width: size, height: size }} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
      {uri ? (
        <Image
          source={{ uri }}
          style={{ width: size, height: size, borderRadius: size / 2, backgroundColor: colors.surface }}
          contentFit="cover"
          transition={200}
          cachePolicy="disk"
          recyclingKey={uri}
        />
      ) : (
        <View style={[styles.fallback, { width: size, height: size, borderRadius: size / 2, backgroundColor: tint }]}>
          <Text variant={size >= 64 ? 'headline' : 'label'} color="onPastel">
            {initials(name)}
          </Text>
        </View>
      )}
      {online ? (
        <View
          style={[
            styles.dot,
            {
              width: dot,
              height: dot,
              borderRadius: dot / 2,
              backgroundColor: colors.online,
              borderColor: colors.background,
            },
          ]}
        />
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  fallback: { alignItems: 'center', justifyContent: 'center' },
  dot: { position: 'absolute', right: 0, bottom: 0, borderWidth: 2 },
});
