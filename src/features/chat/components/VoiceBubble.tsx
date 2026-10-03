import { useAudioPlayer, useAudioPlayerStatus } from 'expo-audio';
import { useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { Icon, PressableScale, Text } from '@/components/ui';
import { haptics } from '@/lib/haptics';
import { radius, spacing, useTheme } from '@/theme';

import { durationLabel } from '../model';
import type { Media } from '../types';

const SPEEDS = [1, 1.5, 2] as const;
const FLAT = Array.from({ length: 40 }, () => 0.2);

function Waveform({ samples, progress, mine }: { samples: number[]; progress: number; mine: boolean }) {
  const { colors } = useTheme();
  const played = mine ? colors.onPrimary : colors.accent;
  const rest = mine ? 'rgba(0,0,0,0.25)' : colors.border;
  return (
    <View style={styles.wave} accessibilityElementsHidden>
      {samples.map((v, i) => (
        <View
          key={i}
          style={[styles.bar, { height: Math.max(3, v * 28), backgroundColor: i / samples.length < progress ? played : rest }]}
        />
      ))}
    </View>
  );
}

/** The player is only created after the first tap, so a long chat doesn't load every voice note. */
function ActivePlayer({ media, mine, speed, onEnd }: { media: Media; mine: boolean; speed: number; onEnd: () => void }) {
  const player = useAudioPlayer({ uri: media.url }, { updateInterval: 100 });
  const status = useAudioPlayerStatus(player);
  useEffect(() => {
    player.play();
  }, [player]);
  useEffect(() => {
    player.setPlaybackRate(speed);
  }, [player, speed]);
  useEffect(() => {
    if (status.didJustFinish) onEnd();
  }, [status.didJustFinish, onEnd]);
  const duration = status.duration || (media.durationMs ?? 0) / 1000;
  const progress = duration ? status.currentTime / duration : 0;
  return (
    <PlayerBody
      mine={mine}
      playing={status.playing}
      loading={!status.isLoaded}
      progress={progress}
      label={durationLabel((status.playing ? status.currentTime : duration) * 1000)}
      samples={media.waveform ?? FLAT}
      onToggle={() => (status.playing ? player.pause() : player.play())}
    />
  );
}

function PlayerBody({
  mine,
  playing,
  loading,
  progress,
  label,
  samples,
  onToggle,
}: {
  mine: boolean;
  playing: boolean;
  loading?: boolean;
  progress: number;
  label: string;
  samples: number[];
  onToggle: () => void;
}) {
  const { colors } = useTheme();
  return (
    <View style={styles.row}>
      <PressableScale
        scaleTo={0.9}
        accessibilityRole="button"
        accessibilityLabel={playing ? 'Pause voice note' : 'Play voice note'}
        onPress={() => {
          haptics.tick();
          onToggle();
        }}
        style={[styles.play, { backgroundColor: mine ? colors.onPrimary : colors.primary }]}
      >
        <Icon name={playing ? 'pause' : 'play'} size={18} color={mine ? colors.primary : colors.onPrimary} />
      </PressableScale>
      <View style={styles.flex}>
        <Waveform samples={samples} progress={progress} mine={mine} />
        <Text variant="micro" style={{ color: mine ? colors.onPrimary : colors.textMuted, opacity: loading ? 0.5 : 0.8 }}>
          {label}
        </Text>
      </View>
    </View>
  );
}

export function VoiceBubble({ media, mine }: { media: Media; mine: boolean }) {
  const { colors } = useTheme();
  const [active, setActive] = useState(false);
  const [speedIndex, setSpeedIndex] = useState(0);
  const speed = SPEEDS[speedIndex] ?? 1;
  return (
    <View style={styles.container}>
      {active ? (
        <ActivePlayer media={media} mine={mine} speed={speed} onEnd={() => setActive(false)} />
      ) : (
        <PlayerBody
          mine={mine}
          playing={false}
          progress={0}
          label={durationLabel(media.durationMs ?? 0)}
          samples={media.waveform ?? FLAT}
          onToggle={() => setActive(true)}
        />
      )}
      <PressableScale
        scaleTo={0.9}
        accessibilityRole="button"
        accessibilityLabel={`Playback speed ${speed} times. Change`}
        onPress={() => setSpeedIndex((i) => (i + 1) % SPEEDS.length)}
        style={[styles.speed, { borderColor: mine ? 'rgba(0,0,0,0.25)' : colors.border }]}
      >
        <Text variant="micro" style={{ color: mine ? colors.onPrimary : colors.text }}>
          {speed}×
        </Text>
      </PressableScale>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flexDirection: 'row', alignItems: 'center', gap: spacing.xs, width: 240 },
  row: { flex: 1, minWidth: 0, flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  flex: { flex: 1, minWidth: 0, gap: 2 },
  play: { width: 40, height: 40, borderRadius: 20, alignItems: 'center', justifyContent: 'center' },
  wave: { flexDirection: 'row', alignItems: 'center', gap: 1.5, height: 30, overflow: 'hidden' },
  bar: { flex: 1, maxWidth: 3, borderRadius: 2 },
  speed: { borderWidth: 1, borderRadius: radius.pill, paddingHorizontal: 8, minHeight: 32, minWidth: 44, alignItems: 'center', justifyContent: 'center' },
});
