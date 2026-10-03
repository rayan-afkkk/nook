import type { PropsWithChildren, ReactNode } from 'react';
import { ScrollView, StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';
import { SafeAreaView, useSafeAreaInsets, type Edge } from 'react-native-safe-area-context';

import { spacing, useTheme } from '@/theme';

type Props = PropsWithChildren<{
  header?: ReactNode;
  scroll?: boolean;
  edges?: Edge[];
  padded?: boolean;
  contentStyle?: StyleProp<ViewStyle>;
  footer?: ReactNode;
}>;

/** Safe-area aware page with the theme background, optional header, scroll body and sticky footer. */
export function Screen({ children, header, scroll, edges = ['top'], padded = true, contentStyle, footer }: Props) {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  // Without the bottom edge, keep footers and the end of scroll content clear of Android's navigation bar.
  const navBar = edges.includes('bottom') ? 0 : insets.bottom;
  const body = scroll ? (
    <ScrollView
      contentContainerStyle={[padded && styles.padded, styles.scrollContent, !footer && { paddingBottom: spacing.xxxl + navBar }, contentStyle]}
      keyboardShouldPersistTaps="handled"
      keyboardDismissMode="on-drag"
      showsVerticalScrollIndicator={false}
    >
      {children}
    </ScrollView>
  ) : (
    <View style={[styles.flex, padded && styles.padded, contentStyle]}>{children}</View>
  );
  return (
    <SafeAreaView edges={edges} style={[styles.flex, { backgroundColor: colors.background }]}>
      {header}
      {body}
      {footer ? <View style={{ paddingBottom: navBar }}>{footer}</View> : null}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  padded: { paddingHorizontal: spacing.lg },
  scrollContent: { paddingBottom: spacing.xxxl },
});
