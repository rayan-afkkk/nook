import { router } from 'expo-router';
import { StyleSheet, View } from 'react-native';

import { Avatar, Chip, Divider, IconButton, PressableScale, Text } from '@/components/ui';
import { spacing } from '@/theme';

import { DISAPPEARING_LABEL } from '../model';
import type { Chat } from '../types';

import { GroupAvatar } from './GroupAvatar';

type Props = {
  chat: Chat;
  title: string;
  photoURL?: string | null;
  online?: boolean;
  status: string;
  seed: string;
  onCall?: (video: boolean) => void;
  onOpenInfo: () => void;
  onTimer: () => void;
};

export function ChatHeader({ chat, title, photoURL, online, status, seed, onCall, onOpenInfo, onTimer }: Props) {
  const timer = chat.disappearing ?? 'off';
  return (
    <View>
      <View style={styles.row}>
        <IconButton icon="chevron-back" label="Back" onPress={() => router.back()} />
        <PressableScale
          scaleTo={0.98}
          accessibilityRole="button"
          accessibilityLabel={`${title}, ${status}. Chat info`}
          onPress={onOpenInfo}
          style={styles.identity}
        >
          {chat.type === 'group' ? <GroupAvatar name={title} seed={seed} size={40} /> : <Avatar name={title} uri={photoURL} size={40} online={online} seed={seed} />}
          <View style={styles.texts}>
            <Text variant="subhead" numberOfLines={1}>
              {title}
            </Text>
            <Text variant="micro" color={status.endsWith('typing…') ? 'accent' : 'textMuted'} numberOfLines={1} accessibilityLiveRegion="polite">
              {status}
            </Text>
          </View>
        </PressableScale>
        <PressableScale accessibilityRole="button" accessibilityLabel={`Disappearing messages: ${DISAPPEARING_LABEL[timer]}. Change`} onPress={onTimer} style={styles.timer}>
          <Chip label={timer === 'off' ? 'Timer' : DISAPPEARING_LABEL[timer]} icon="timer-outline" tone={timer === 'off' ? 'neutral' : 'info'} />
        </PressableScale>
        {onCall ? (
          <>
            <IconButton icon="call-outline" label="Voice call" onPress={() => onCall(false)} />
            <IconButton icon="videocam-outline" label="Video call" onPress={() => onCall(true)} />
          </>
        ) : null}
      </View>
      <Divider />
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: spacing.xs, paddingVertical: spacing.xs, gap: 2 },
  identity: { flex: 1, flexDirection: 'row', alignItems: 'center', gap: spacing.sm, minHeight: 48 },
  texts: { flex: 1 },
  timer: { minHeight: 48, justifyContent: 'center', paddingHorizontal: 2 },
});
