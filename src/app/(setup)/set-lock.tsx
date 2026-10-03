import { router } from 'expo-router';
import { StyleSheet, View } from 'react-native';

import { Screen } from '@/components/ui';
import { LockSetupFlow } from '@/features/lock/LockSetupFlow';
import { spacing } from '@/theme';

export default function SetLockStep() {
  return (
    <Screen edges={['top', 'bottom']}>
      <View style={styles.body}>
        <LockSetupFlow mode="setup" onDone={() => router.replace('/')} />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({ body: { flex: 1, paddingBottom: spacing.md } });
