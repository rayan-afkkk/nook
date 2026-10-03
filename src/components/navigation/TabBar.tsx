import type { Tabs } from 'expo-router';
import type { ComponentProps } from 'react';
import { StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Icon, PressableScale, Text, type IconName } from '@/components/ui';
import { haptics } from '@/lib/haptics';
import { spacing, useTheme } from '@/theme';

type TabBarProps = Parameters<NonNullable<ComponentProps<typeof Tabs>['tabBar']>>[0];

export const TAB_ICONS: Record<string, { active: IconName; inactive: IconName; label: string }> = {
  chats: { active: 'chatbubbles', inactive: 'chatbubbles-outline', label: 'Chats' },
  friends: { active: 'people', inactive: 'people-outline', label: 'Friends' },
  stickers: { active: 'happy', inactive: 'happy-outline', label: 'Stickers' },
  calls: { active: 'call', inactive: 'call-outline', label: 'Calls' },
  account: { active: 'person-circle', inactive: 'person-circle-outline', label: 'Account' },
};

/** Active tab: filled icon + cream label. Inactive: muted outline. Hairline top border. */
export function TabBar({ state, navigation, descriptors }: TabBarProps) {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  return (
    <View
      accessibilityRole="tablist"
      style={[
        styles.bar,
        { backgroundColor: colors.background, borderTopColor: colors.border, paddingBottom: Math.max(insets.bottom, spacing.xs) },
      ]}
    >
      {state.routes.map((route, index) => {
        const focused = state.index === index;
        const meta = TAB_ICONS[route.name];
        if (!meta) return null;
        const badge = descriptors[route.key]?.options.tabBarBadge;
        const onPress = () => {
          const event = navigation.emit({ type: 'tabPress', target: route.key, canPreventDefault: true });
          if (!focused && !event.defaultPrevented) {
            haptics.tick();
            navigation.navigate(route.name, route.params);
          }
        };
        return (
          <PressableScale
            key={route.key}
            accessibilityRole="tab"
            accessibilityState={{ selected: focused }}
            accessibilityLabel={badge ? `${meta.label}, ${badge} unread` : meta.label}
            onPress={onPress}
            onLongPress={() => navigation.emit({ type: 'tabLongPress', target: route.key })}
            scaleTo={0.9}
            style={styles.tab}
          >
            <View>
              <Icon name={focused ? meta.active : meta.inactive} size={24} color={focused ? 'text' : 'textMuted'} />
              {badge ? <View style={[styles.badge, { backgroundColor: colors.accent, borderColor: colors.background }]} /> : null}
            </View>
            <Text variant="micro" color={focused ? 'text' : 'textMuted'}>
              {meta.label}
            </Text>
          </PressableScale>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  bar: { flexDirection: 'row', borderTopWidth: StyleSheet.hairlineWidth, paddingTop: spacing.xs },
  tab: { flex: 1, minHeight: 52, alignItems: 'center', justifyContent: 'center', gap: 3 },
  badge: { position: 'absolute', top: -2, right: -4, width: 10, height: 10, borderRadius: 5, borderWidth: 2 },
});
