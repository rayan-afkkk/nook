import { StyleSheet, View } from 'react-native';

import { palette } from '@/theme';

import { Float, PopIn, type IllustrationProps } from './motion';

type Bubble = { side: 'left' | 'right'; width: number; lines: number; color: string; lineColor: string };

const BUBBLES: Bubble[] = [
  { side: 'left', width: 0.62, lines: 2, color: palette.charcoal, lineColor: '#4A433D' },
  { side: 'right', width: 0.5, lines: 1, color: palette.cream, lineColor: '#CFC6B8' },
  { side: 'left', width: 0.44, lines: 1, color: palette.lavender, lineColor: '#B9A8DC' },
  { side: 'right', width: 0.66, lines: 2, color: palette.cream, lineColor: '#CFC6B8' },
];

/** Slide 1, "Private by design": chat bubbles pop in one by one, then float. */
export function BubblesIllustration({ active, reduceMotion, size }: IllustrationProps) {
  return (
    <View style={[styles.stage, { width: size, height: size }]} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
      {BUBBLES.map((b, i) => (
        <PopIn
          key={i}
          active={active}
          reduceMotion={reduceMotion}
          delay={180 + i * 220}
          rotate={b.side === 'left' ? -1.5 : 1.5}
          style={{ alignSelf: b.side === 'left' ? 'flex-start' : 'flex-end' }}
        >
          <Float active={active} reduceMotion={reduceMotion} delay={i * 260} amplitude={4 + (i % 2)} duration={2200 + i * 200}>
            <View style={styles.bubbleRow}>
              {b.side === 'left' ? <View style={[styles.avatar, { backgroundColor: i === 0 ? palette.peach : palette.mint }]} /> : null}
              <View
                style={[
                  styles.bubble,
                  {
                    width: size * b.width,
                    backgroundColor: b.color,
                    borderBottomLeftRadius: b.side === 'left' ? 6 : 22,
                    borderBottomRightRadius: b.side === 'right' ? 6 : 22,
                  },
                ]}
              >
                {Array.from({ length: b.lines }).map((_, l) => (
                  <View
                    key={l}
                    style={[styles.line, { backgroundColor: b.lineColor, width: l === b.lines - 1 && b.lines > 1 ? '60%' : '88%' }]}
                  />
                ))}
              </View>
            </View>
          </Float>
        </PopIn>
      ))}
      <PopIn active={active} reduceMotion={reduceMotion} delay={1150} style={styles.typing}>
        <View style={styles.typingBubble}>
          {[0, 1, 2].map((d) => (
            <Float key={d} active={active} reduceMotion={reduceMotion} delay={d * 150} amplitude={2.5} duration={700}>
              <View style={styles.typingDot} />
            </Float>
          ))}
        </View>
      </PopIn>
    </View>
  );
}

const styles = StyleSheet.create({
  stage: { justifyContent: 'center', gap: 14, paddingHorizontal: 8 },
  bubbleRow: { flexDirection: 'row', alignItems: 'flex-end', gap: 8 },
  avatar: { width: 28, height: 28, borderRadius: 14 },
  bubble: { borderRadius: 22, paddingHorizontal: 16, paddingVertical: 14, gap: 8 },
  line: { height: 8, borderRadius: 4 },
  typing: { alignSelf: 'flex-start', marginLeft: 36 },
  typingBubble: {
    flexDirection: 'row',
    gap: 5,
    backgroundColor: palette.charcoal,
    borderRadius: 18,
    paddingHorizontal: 14,
    paddingVertical: 12,
    borderWidth: 1,
    borderColor: palette.border,
  },
  typingDot: { width: 7, height: 7, borderRadius: 4, backgroundColor: palette.muted },
});
