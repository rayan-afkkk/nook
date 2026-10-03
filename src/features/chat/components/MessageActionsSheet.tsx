import * as Clipboard from 'expo-clipboard';
import { StyleSheet, View } from 'react-native';

import { Divider, ListRow, PressableScale, Sheet, Text, toast } from '@/components/ui';
import { haptics } from '@/lib/haptics';
import { radius, spacing, useTheme } from '@/theme';

import type { Message } from '../types';

export const QUICK_REACTIONS = ['❤️', '😂', '😮', '😢', '🙏', '👍', '🔥'];

type Props = {
  message: Message | null;
  mine: boolean;
  myReaction?: string;
  onClose: () => void;
  onReact: (emoji: string | null) => void;
  onReply: () => void;
  onForward: () => void;
  onDelete: () => void;
};

/** Long-press sheet: react, reply, copy, forward, delete. */
export function MessageActionsSheet({ message, mine, myReaction, onClose, onReact, onReply, onForward, onDelete }: Props) {
  const { colors } = useTheme();
  const canCopy = message?.kind === 'text' && !!message.text;
  return (
    <Sheet visible={!!message} onClose={onClose} accessibilityLabel="Message actions">
      <View style={styles.reactions} accessibilityRole="toolbar" accessibilityLabel="React">
        {QUICK_REACTIONS.map((e) => {
          const active = myReaction === e;
          return (
            <PressableScale
              key={e}
              scaleTo={0.85}
              accessibilityRole="button"
              accessibilityLabel={active ? `Remove ${e} reaction` : `React ${e}`}
              onPress={() => {
                haptics.medium();
                onReact(active ? null : e);
              }}
              style={[styles.emoji, { backgroundColor: active ? colors.surfaceRaised : 'transparent', borderColor: active ? colors.accent : 'transparent' }]}
            >
              <Text style={styles.emojiText}>{e}</Text>
            </PressableScale>
          );
        })}
      </View>
      <Divider />
      <ListRow icon="arrow-undo-outline" title="Reply" onPress={onReply} showChevron={false} />
      {canCopy ? (
        <ListRow
          icon="copy-outline"
          title="Copy"
          showChevron={false}
          onPress={() => {
            void Clipboard.setStringAsync(message?.text ?? '');
            toast.show('Copied');
            onClose();
          }}
        />
      ) : null}
      {message?.kind !== 'system' ? <ListRow icon="arrow-redo-outline" title="Forward" onPress={onForward} showChevron={false} /> : null}
      {mine ? <ListRow icon="trash-outline" title="Delete for everyone" destructive onPress={onDelete} showChevron={false} /> : null}
    </Sheet>
  );
}

const styles = StyleSheet.create({
  reactions: { flexDirection: 'row', justifyContent: 'space-between', paddingBottom: spacing.md },
  emoji: { width: 46, height: 46, borderRadius: radius.pill, alignItems: 'center', justifyContent: 'center', borderWidth: 1 },
  emojiText: { fontSize: 26, lineHeight: 32 },
});
