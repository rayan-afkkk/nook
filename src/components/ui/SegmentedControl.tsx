import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, View, type LayoutChangeEvent } from 'react-native';
import Animated, { useAnimatedStyle, useSharedValue, withSpring } from 'react-native-reanimated';

import { haptics } from '@/lib/haptics';
import { motion, radius, touchTarget, useTheme } from '@/theme';

import { Text } from './Text';

type Option<T extends string> = { value: T; label: string };

type Props<T extends string> = {
  options: readonly Option<T>[];
  value: T;
  onChange: (value: T) => void;
  accessibilityLabel: string;
};

/** Dark pill container; the selected segment is a lighter raised pill that slides between options. */
export function SegmentedControl<T extends string>({ options, value, onChange, accessibilityLabel }: Props<T>) {
  const { colors, scheme } = useTheme();
  const [width, setWidth] = useState(0);
  const index = Math.max(
    0,
    options.findIndex((o) => o.value === value),
  );
  const segment = options.length ? (width - 8) / options.length : 0;
  const x = useSharedValue(0);

  useEffect(() => {
    x.set(withSpring(index * segment, motion.spring));
  }, [index, segment, x]);

  const thumbStyle = useAnimatedStyle(() => ({ transform: [{ translateX: x.value }] }));

  const onLayout = (e: LayoutChangeEvent) => {
    const w = e.nativeEvent.layout.width;
    if (w !== width) {
      setWidth(w);
      x.set(index * ((w - 8) / options.length));
    }
  };

  return (
    <View
      accessibilityRole="tablist"
      accessibilityLabel={accessibilityLabel}
      onLayout={onLayout}
      style={[styles.container, { backgroundColor: colors.surface, borderColor: colors.border }]}
    >
      {width > 0 ? (
        <Animated.View
          style={[
            styles.thumb,
            {
              width: segment,
              backgroundColor: scheme === 'dark' ? colors.surfaceRaised : colors.background,
              borderColor: colors.border,
            },
            thumbStyle,
          ]}
        />
      ) : null}
      {options.map((o) => {
        const selected = o.value === value;
        return (
          <Pressable
            key={o.value}
            accessibilityRole="tab"
            accessibilityState={{ selected }}
            accessibilityLabel={o.label}
            style={styles.segment}
            onPress={() => {
              if (!selected) {
                haptics.tick();
                onChange(o.value);
              }
            }}
          >
            <Text variant="captionBold" color={selected ? 'text' : 'textMuted'} numberOfLines={1}>
              {o.label}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    borderRadius: radius.pill,
    borderWidth: 1,
    padding: 3,
    minHeight: touchTarget,
  },
  thumb: {
    position: 'absolute',
    top: 3,
    bottom: 3,
    left: 3,
    borderRadius: radius.pill,
    borderWidth: 1,
    shadowColor: '#000',
    shadowOpacity: 0.25,
    shadowRadius: 6,
    shadowOffset: { width: 0, height: 2 },
    elevation: 2,
  },
  segment: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 6 },
});
