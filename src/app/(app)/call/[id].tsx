import { VideoView } from '@livekit/react-native';
import { useKeepAwake } from 'expo-keep-awake';
import { router, useLocalSearchParams } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useRef, useState } from 'react';
import { BackHandler, StyleSheet, View } from 'react-native';
import Animated, { FadeIn } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Avatar, Icon, PressableScale, Text, type IconName } from '@/components/ui';
import { CALL_RING_TIMEOUT_MS } from '@/constants/app';
import { useSession } from '@/features/auth/session';
import { setCallStatus, useCall } from '@/features/calls/callService';
import { nookCall } from '@/features/calls/nativeCall';
import { useCallRoom } from '@/features/calls/useCallRoom';
import { durationLabel } from '@/features/chat/model';
import { displayNameOf, useProfile } from '@/features/profile/profiles';
import { haptics } from '@/lib/haptics';
import { darkColors, fonts, palette, spacing } from '@/theme';

function leave() {
  if (router.canGoBack()) router.back();
  else router.replace('/calls');
}

function Control({ icon, label, active, onPress, danger }: { icon: IconName; label: string; active?: boolean; onPress: () => void; danger?: boolean }) {
  return (
    <View style={styles.controlWrap}>
      <PressableScale
        accessibilityRole="button"
        accessibilityLabel={label}
        accessibilityState={{ selected: !!active }}
        onPress={() => {
          haptics.light();
          onPress();
        }}
        scaleTo={0.9}
        style={[styles.control, { backgroundColor: danger ? palette.coral : active ? palette.cream : palette.charcoalRaised }]}
      >
        <Icon name={icon} size={26} color={danger ? '#fff' : active ? '#000' : palette.cream} />
      </PressableScale>
      <Text variant="micro" style={styles.controlLabel}>
        {label}
      </Text>
    </View>
  );
}

/** The live call: black, big avatar or remote video, serif name, timer, controls. */
export default function CallScreen() {
  useKeepAwake();
  const insets = useSafeAreaInsets();
  const { id } = useLocalSearchParams<{ id: string }>();
  const callId = String(id);
  const me = useSession((s) => s.user?.uid ?? '');
  const call = useCall(callId);
  const otherId = call ? (call.callerId === me ? call.calleeId : call.callerId) : undefined;
  const other = useProfile(otherId);
  const name = displayNameOf(other);
  const isCaller = call?.callerId === me;
  const live = !!call && (call.status === 'ringing' || call.status === 'answered');
  const { state, toggleMute, toggleCamera, toggleSpeaker, flip, hangUp } = useCallRoom(callId, !!call?.video, live);
  const [ending, setEnding] = useState<string | null>(null);
  const [now, setNow] = useState(() => Date.now());
  const ended = useRef(false);

  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, []);
  useEffect(() => {
    const sub = BackHandler.addEventListener('hardwareBackPress', () => true);
    return () => {
      sub.remove();
      nookCall.setShowWhenLocked(false);
    };
  }, []);

  const finish = useCallback(
    (label: string) => {
      if (ended.current) return;
      ended.current = true;
      setEnding(label);
      void hangUp();
      haptics.warning();
      setTimeout(leave, 1200);
    },
    [hangUp],
  );

  // Status changes from the other side (or the timeout).
  useEffect(() => {
    if (!call) {
      if (call === null) finish('Call not found');
      return;
    }
    if (call.status === 'declined') finish(isCaller ? 'Declined' : 'Call ended');
    else if (call.status === 'missed') finish(isCaller ? 'No answer' : 'Missed call');
    else if (call.status === 'cancelled') finish('Call cancelled');
    else if (call.status === 'ended') finish('Call ended');
  }, [call, finish, isCaller]);

  // The other person hung up / lost connection after joining.
  useEffect(() => {
    if (state.remoteLeft && call?.status === 'answered') {
      void setCallStatus(callId, 'ended').catch(() => undefined);
      finish('Call ended');
    }
  }, [state.remoteLeft, call?.status, callId, finish]);

  // Caller: ring for 40 seconds, then give up.
  useEffect(() => {
    if (!isCaller || call?.status !== 'ringing') return;
    const started = call.startedAt?.toMillis() ?? Date.now();
    const t = setTimeout(() => void setCallStatus(callId, 'missed').catch(() => undefined), Math.max(0, started + CALL_RING_TIMEOUT_MS - Date.now()));
    return () => clearTimeout(t);
  }, [isCaller, call?.status, call?.startedAt, callId]);

  const end = () => {
    const status = call?.status === 'answered' ? 'ended' : isCaller ? 'cancelled' : 'declined';
    void setCallStatus(callId, status).catch(() => undefined);
    finish('Call ended');
  };

  const statusLine =
    ending ??
    (state.connection === 'error'
      ? (state.error ?? 'Connection failed')
      : state.connection === 'reconnecting'
        ? 'Reconnecting…'
        : call?.status === 'answered' && call.answeredAt
          ? durationLabel(now - call.answeredAt.toMillis())
          : call?.status === 'ringing'
            ? isCaller
              ? 'Ringing…'
              : 'Connecting…'
            : 'Connecting…');

  const showRemoteVideo = !!state.remoteVideo;

  return (
    <View style={styles.root}>
      <StatusBar style="light" />
      {showRemoteVideo ? <VideoView videoTrack={state.remoteVideo} style={styles.fill} objectFit="cover" /> : null}
      {state.localVideo ? (
        <Animated.View entering={FadeIn} style={[styles.self, { top: insets.top + spacing.md }]}>
          <VideoView videoTrack={state.localVideo} style={styles.selfVideo} objectFit="cover" mirror={state.facing === 'user'} zOrder={1} />
        </Animated.View>
      ) : null}

      <View style={[styles.top, { paddingTop: insets.top + spacing.xxl }]}>
        {!showRemoteVideo ? <Avatar name={name} uri={other?.photoURL} size={148} seed={otherId} /> : null}
        <Text style={styles.name} numberOfLines={1} accessibilityRole="header">
          {name}
        </Text>
        <Text variant="body" style={styles.status} accessibilityLiveRegion="polite">
          {statusLine}
        </Text>
      </View>

      <View style={[styles.controls, { paddingBottom: insets.bottom + spacing.xl }]}>
        <Control icon={state.muted ? 'mic-off' : 'mic-outline'} label={state.muted ? 'Unmute' : 'Mute'} active={state.muted} onPress={() => void toggleMute()} />
        <Control icon="volume-high-outline" label="Speaker" active={state.speaker} onPress={() => void toggleSpeaker()} />
        <Control icon={state.cameraOn ? 'videocam' : 'videocam-off-outline'} label="Camera" active={state.cameraOn} onPress={() => void toggleCamera()} />
        {state.cameraOn ? <Control icon="camera-reverse-outline" label="Flip" onPress={() => void flip()} /> : null}
        <Control icon="call" label="End" danger onPress={end} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#000' },
  fill: { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0 },
  top: { alignItems: 'center', gap: spacing.md, paddingHorizontal: spacing.xl },
  name: { fontFamily: fonts.serif, fontSize: 44, lineHeight: 50, color: darkColors.text, marginTop: spacing.md },
  status: { color: darkColors.textMuted },
  self: { position: 'absolute', right: spacing.md, width: 110, height: 160, borderRadius: 20, overflow: 'hidden', zIndex: 2, borderWidth: 1, borderColor: palette.border },
  selfVideo: { width: '100%', height: '100%' },
  controls: { position: 'absolute', left: 0, right: 0, bottom: 0, flexDirection: 'row', justifyContent: 'space-evenly', paddingHorizontal: spacing.md },
  controlWrap: { alignItems: 'center', gap: 6 },
  control: { width: 64, height: 64, borderRadius: 32, alignItems: 'center', justifyContent: 'center' },
  controlLabel: { color: darkColors.textMuted },
});
