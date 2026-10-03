import { router } from 'expo-router';

import { Button, EmptyState, Screen } from '@/components/ui';

export default function NotFound() {
  return (
    <Screen>
      <EmptyState
        icon="compass-outline"
        title="Nothing here"
        message="That screen doesn't exist."
        action={<Button title="Go home" onPress={() => router.replace('/')} />}
      />
    </Screen>
  );
}
