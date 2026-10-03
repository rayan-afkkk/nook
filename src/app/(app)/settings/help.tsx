import { StyleSheet, View } from 'react-native';

import { Card, Header, Screen, Text } from '@/components/ui';
import { spacing } from '@/theme';

const FAQ = [
  {
    q: 'How do friends find me?',
    a: 'By your exact username. There’s no public directory, so share yours with the people you want to talk to.',
  },
  {
    q: 'I forgot my PIN or password',
    a: 'Tap “Forgot” on the lock screen. You’ll sign out, sign back in with Google and choose a new one. Your chats are kept in your account.',
  },
  {
    q: 'Can I change my username?',
    a: 'No. Usernames are permanent so nobody can take over a name your friends already trust.',
  },
  {
    q: 'Are my messages end-to-end encrypted?',
    a: 'No. Messages are encrypted in transit and at rest by Google Firebase, and only chat members can read them through the app, but they are not end-to-end encrypted. See Privacy & Terms.',
  },
  {
    q: 'Something’s broken',
    a: 'Tell whoever set up NOOK for your group. Mention your phone model and what you tapped just before it went wrong.',
  },
];

export default function Help() {
  return (
    <Screen scroll header={<Header title="Help" back />}>
      <View style={styles.body}>
        {FAQ.map((item) => (
          <Card key={item.q} style={styles.card}>
            <Text variant="bodyBold">{item.q}</Text>
            <Text variant="body" color="textMuted">
              {item.a}
            </Text>
          </Card>
        ))}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  body: { gap: spacing.sm, paddingTop: spacing.lg },
  card: { gap: spacing.xs },
});
