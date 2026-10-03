import { router } from 'expo-router';
import type { ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';

import { spacing } from '@/theme';

import { Divider } from './Divider';
import { IconButton } from './IconButton';
import { Text } from './Text';

type Props = {
  title: string;
  /** One or two IconButtons. */
  right?: ReactNode;
  back?: boolean;
  divider?: boolean;
};

/** Big serif title on the left, outline icon buttons on the right, hairline below. */
export function Header({ title, right, back, divider = true }: Props) {
  return (
    <View>
      {back ? (
        <View style={styles.backRow}>
          <IconButton icon="chevron-back" label="Back" onPress={() => router.back()} />
        </View>
      ) : null}
      <View style={[styles.row, back && styles.rowAfterBack]}>
        <Text variant="title" accessibilityRole="header" style={styles.title} numberOfLines={1} adjustsFontSizeToFit>
          {title}
        </Text>
        {right ? <View style={styles.actions}>{right}</View> : null}
      </View>
      {divider ? <Divider /> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  backRow: { paddingHorizontal: spacing.xs, paddingTop: spacing.xxs },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.sm,
    paddingBottom: spacing.sm,
    minHeight: 56,
  },
  rowAfterBack: { paddingTop: 0 },
  title: { flex: 1 },
  actions: { flexDirection: 'row', alignItems: 'center', marginRight: -spacing.xs },
});
