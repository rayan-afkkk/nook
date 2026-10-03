import type { ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';

import { radius, spacing, touchTarget, useTheme } from '@/theme';

import { Icon, type IconName } from './Icon';
import { PressableScale } from './PressableScale';
import { Text } from './Text';

type Props = {
  icon?: IconName;
  title: string;
  subtitle?: string;
  onPress?: () => void;
  destructive?: boolean;
  /** Replaces the chevron (e.g. a Switch or a value). */
  right?: ReactNode;
  showChevron?: boolean;
  accessibilityHint?: string;
};

/** Settings-style row: icon tile, bold title, muted subtitle, chevron. */
export function ListRow({ icon, title, subtitle, onPress, destructive, right, showChevron = true, accessibilityHint }: Props) {
  const { colors } = useTheme();
  const content = (
    <View style={styles.row}>
      {icon ? (
        <View style={[styles.iconTile, { backgroundColor: colors.surfaceRaised }]}>
          <Icon name={icon} size={18} color={destructive ? 'danger' : 'text'} />
        </View>
      ) : null}
      <View style={styles.texts}>
        <Text variant="bodyBold" color={destructive ? 'danger' : 'text'} numberOfLines={1}>
          {title}
        </Text>
        {subtitle ? (
          <Text variant="caption" color="textMuted" numberOfLines={2}>
            {subtitle}
          </Text>
        ) : null}
      </View>
      {right ?? (onPress && showChevron ? <Icon name="chevron-forward" size={18} color="textMuted" /> : null)}
    </View>
  );
  if (!onPress) return <View accessible accessibilityLabel={[title, subtitle].filter(Boolean).join(', ')}>{content}</View>;
  return (
    <PressableScale
      scaleTo={0.98}
      accessibilityRole="button"
      accessibilityLabel={title}
      accessibilityHint={accessibilityHint ?? subtitle}
      onPress={onPress}
    >
      {content}
    </PressableScale>
  );
}

const styles = StyleSheet.create({
  row: {
    minHeight: 56,
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    paddingVertical: spacing.sm,
  },
  iconTile: {
    width: 36,
    height: 36,
    borderRadius: radius.sm,
    alignItems: 'center',
    justifyContent: 'center',
  },
  texts: { flex: 1, gap: 2, minHeight: touchTarget - 8, justifyContent: 'center' },
});
