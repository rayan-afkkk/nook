import { StyleSheet, View } from 'react-native';

import { Card, Header, Screen, Text } from '@/components/ui';
import { spacing } from '@/theme';

const SECTIONS = [
  {
    title: 'What NOOK is',
    body: 'A private chat app for a small group of friends. It is run by the person who set it up for your group, not by a company.',
  },
  {
    title: 'Not end-to-end encrypted',
    body: 'Messages, profiles and chat details are stored in Google Firebase. They are encrypted in transit and at rest by Google, and security rules only let chat members read a chat, but they are not end-to-end encrypted: whoever administers the Firebase project could technically access them.',
  },
  {
    title: 'What we store',
    body: 'Your Google display name and photo, your username, the chats you are in and their messages, and a device token so we can notify you. Photos, voice notes and files are stored with Cloudinary. Your app lock never leaves your phone.',
  },
  {
    title: 'Notifications',
    body: 'Notifications only say who something is from. Message text is never placed in a notification or in server logs.',
  },
  {
    title: 'Disappearing messages',
    body: 'When a timer runs out, messages are hidden immediately and deleted from the server the next time a member opens the chat. Anyone could still screenshot a message before it disappears.',
  },
  {
    title: 'Deleting your account',
    body: 'Account > Delete Account removes your profile, frees your username, deletes your push tokens and your sign-in.',
  },
  {
    title: 'Fair use',
    body: 'Be kind. Don’t use NOOK to harass anyone or share anything illegal. The group’s admin can remove accounts that do.',
  },
];

export default function Privacy() {
  return (
    <Screen scroll header={<Header title="Privacy & Terms" back />}>
      <View style={styles.body}>
        {SECTIONS.map((s, i) => (
          <Card key={s.title} tone={i === 1 ? 'highlight' : 'surface'} style={styles.card}>
            <Text variant="subhead" color={i === 1 ? 'onHighlight' : 'text'}>
              {s.title}
            </Text>
            <Text variant="body" color={i === 1 ? 'onHighlight' : 'textMuted'}>
              {s.body}
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
