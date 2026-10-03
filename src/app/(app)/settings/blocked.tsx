import { EmptyState, Header, Screen } from '@/components/ui';

export default function Blocked() {
  return (
    <Screen header={<Header title="Blocked" back />}>
      <EmptyState
        icon="ban-outline"
        title="No one’s blocked"
        message="People you block can’t message or call you. Block someone from their profile."
      />
    </Screen>
  );
}
