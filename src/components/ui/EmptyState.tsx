import type { ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, { FadeInDown } from 'react-native-reanimated';

import { spacing, useTheme } from '@/theme';

import { Icon, type IconName } from './Icon';
import { Text } from './Text';

type Props = { icon: IconName; title: string; message: string; action?: ReactNode };

export function EmptyState({ icon, title, message, action }: Props) {
  const { colors } = useTheme();
  return (
    <Animated.View entering={FadeInDown.duration(300)} style={styles.wrap} accessible accessibilityLabel={`${title}. ${message}`}>
      <View style={[styles.iconRing, { borderColor: colors.border }]}>
        <Icon name={icon} size={30} color="textMuted" />
      </View>
      <Text variant="headline" align="center">
        {title}
      </Text>
      <Text variant="body" color="textMuted" align="center" style={styles.message}>
        {message}
      </Text>
      {action ? <View style={styles.action}>{action}</View> : null}
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  wrap: { alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing.xxl, paddingVertical: spacing.xxxl, gap: spacing.sm },
  iconRing: {
    width: 72,
    height: 72,
    borderRadius: 36,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.xs,
  },
  message: { maxWidth: 300 },
  action: { marginTop: spacing.md, alignSelf: 'stretch' },
});
