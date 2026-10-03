import { Tabs } from 'expo-router';

import { TabBar } from '@/components/navigation/TabBar';
import { useTheme } from '@/theme';

export default function TabsLayout() {
  const { colors } = useTheme();
  return (
    <Tabs
      tabBar={(props) => <TabBar {...props} />}
      screenOptions={{
        headerShown: false,
        animation: 'shift',
        sceneStyle: { backgroundColor: colors.background },
      }}
    >
      <Tabs.Screen name="chats" />
      <Tabs.Screen name="friends" />
      <Tabs.Screen name="stickers" />
      <Tabs.Screen name="calls" />
      <Tabs.Screen name="account" />
    </Tabs>
  );
}
