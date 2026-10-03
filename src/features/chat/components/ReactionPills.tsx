import { StyleSheet, View } from 'react-native';

import { PressableScale, Text } from '@/components/ui';
import { haptics } from '@/lib/haptics';
import { radius, useTheme } from '@/theme';

import { groupReactions } from '../model';

type Props = { reactions: Record<string, string> | undefined; me: string; mine: boolean; onToggle: (emoji: string) => void };

export function ReactionPills({ reactions, me, mine, onToggle }: Props) {
  const { colors } = useTheme();
  const groups = groupReactions(reactions, me);
  if (!groups.length) return null;
  return (
    <View style={[styles.row, mine ? styles.right : styles.left]}>
      {groups.map((g) => (
        <PressableScale
          key={g.emoji}
          scaleTo={0.9}
          accessibilityRole="button"
          accessibilityLabel={`${g.emoji} ${g.count}${g.mine ? ', you reacted' : ''}`}
          onPress={() => {
            haptics.light();
            onToggle(g.emoji);
          }}
          style={[
            styles.pill,
            { backgroundColor: colors.surfaceRaised, borderColor: g.mine ? colors.accent : colors.border },
          ]}
        >
          <Text variant="caption">{g.emoji}</Text>
          {g.count > 1 ? (
            <Text variant="micro" color="textMuted">
              {g.count}
            </Text>
          ) : null}
        </PressableScale>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', gap: 4, marginTop: -6, flexWrap: 'wrap', maxWidth: '80%' },
  right: { alignSelf: 'flex-end', marginRight: 10 },
  left: { alignSelf: 'flex-start', marginLeft: 10 },
  pill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 3,
    borderRadius: radius.pill,
    borderWidth: 1,
    paddingHorizontal: 8,
    minHeight: 28,
  },
});
