import { router, useLocalSearchParams } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { BackHandler, StyleSheet, View } from 'react-native';
import Animated, { interpolate, useAnimatedStyle, useReducedMotion, useSharedValue, withRepeat, withTiming, type SharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Avatar, Icon, PressableScale, Text } from '@/components/ui';
import { setCallStatus, useCall } from '@/features/calls/callService';
import { nookCall } from '@/features/calls/nativeCall';
import { displayNameOf, useProfile } from '@/features/profile/profiles';
import { haptics } from '@/lib/haptics';
import { darkColors, fonts, palette, spacing } from '@/theme';

function Ring({ t, offset }: { t: SharedValue<number>; offset: number }) {
  const style = useAnimatedStyle(() => {
    const p = (t.value + offset) % 1;
    return { opacity: interpolate(p, [0, 0.2, 1], [0, 0.5, 0]), transform: [{ scale: interpolate(p, [0, 1], [1, 2.1]) }] };
  });
  return <Animated.View style={[styles.ring, style]} />;
}

function close() {
  if (router.canGoBack()) router.back();
  else router.replace('/chats');
}

/** Full-screen incoming call (also opened from the lock-screen notification). */
export default function IncomingCall() {
  const insets = useSafeAreaInsets();
  const reduce = useReducedMotion();
  const { callId, accept } = useLocalSearchParams<{ callId: string; accept?: string }>();
  const id = String(callId);
  const call = useCall(id);
  const caller = useProfile(call?.callerId);
  const name = displayNameOf(caller);
  const [busy, setBusy] = useState(false);
  const [failed, setFailed] = useState<string | null>(null);
  const [actedOn, setActedOn] = useState(false);
  const handled = useRef(false);
  const t = useSharedValue(0);

  useEffect(() => {
    nookCall.dismiss(id);
    nookCall.setShowWhenLocked(true);
    if (!accept) nookCall.startRingtone();
    if (!reduce) t.value = withRepeat(withTiming(1, { duration: 2200 }), -1, false);
    const sub = BackHandler.addEventListener('hardwareBackPress', () => true);
    return () => {
      nookCall.stopRingtone();
      sub.remove();
    };
  }, [id, accept, reduce, t]);

  const answer = async () => {
    if (handled.current) return;
    handled.current = true;
    setActedOn(true);
    setBusy(true);
    nookCall.stopRingtone();
    haptics.success();
    try {
      await setCallStatus(id, 'answered');
      router.replace({ pathname: '/call/[id]', params: { id } });
    } catch {
      setFailed('Couldn’t answer');
      setTimeout(close, 1200);
    }
  };

  const decline = () => {
    if (handled.current) return;
    handled.current = true;
    setActedOn(true);
    nookCall.stopRingtone();
    nookCall.setShowWhenLocked(false);
    haptics.warning();
    void setCallStatus(id, 'declined').catch(() => undefined);
    close();
  };

  // The call stopped ringing before anyone here answered it.
  const over = !actedOn && call !== undefined && (call === null || call.status !== 'ringing');
  const gone = failed ?? (over ? (call?.status === 'answered' ? 'Answered on another device' : 'Missed call') : null);

  useEffect(() => {
    if (!over) return;
    handled.current = true;
    nookCall.stopRingtone();
    const timer = setTimeout(close, 1400);
    return () => clearTimeout(timer);
  }, [over]);

  useEffect(() => {
    // Tapped "Answer" on the notification: answer as soon as the call record loads.
    if (!accept || call?.status !== 'ringing') return;
    const timer = setTimeout(() => void answer(), 0);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [call?.status, accept]);

  return (
    <View style={[styles.root, { paddingTop: insets.top + spacing.xxxl, paddingBottom: insets.bottom + spacing.xxl }]}>
      <StatusBar style="light" />
      <View style={styles.top}>
        <View style={styles.avatarWrap}>
          {!gone ? [0, 0.33, 0.66].map((o) => <Ring key={o} t={t} offset={o} />) : null}
          <Avatar name={name} uri={caller?.photoURL} size={148} seed={call?.callerId} />
        </View>
        <Text style={styles.name} numberOfLines={1} accessibilityRole="header">
          {name}
        </Text>
        <Text variant="body" style={styles.status} accessibilityLiveRegion="assertive">
          {gone ?? (call?.video ? 'Incoming video call' : 'Incoming voice call')}
        </Text>
      </View>
      {!gone ? (
        <View style={styles.actions}>
          <View style={styles.actionWrap}>
            <PressableScale accessibilityRole="button" accessibilityLabel="Decline" onPress={decline} style={[styles.action, { backgroundColor: palette.coral }]}>
              <Icon name="call" size={30} color="#fff" />
            </PressableScale>
            <Text variant="caption" style={styles.status}>
              Decline
            </Text>
          </View>
          <View style={styles.actionWrap}>
            <PressableScale accessibilityRole="button" accessibilityLabel="Answer" disabled={busy} onPress={() => void answer()} style={[styles.action, { backgroundColor: palette.online }]}>
              <Icon name={call?.video ? 'videocam' : 'call'} size={30} color="#000" />
            </PressableScale>
            <Text variant="caption" style={styles.status}>
              Answer
            </Text>
          </View>
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#000', justifyContent: 'space-between' },
  top: { alignItems: 'center', gap: spacing.md, paddingHorizontal: spacing.xl },
  avatarWrap: { alignItems: 'center', justifyContent: 'center', width: 260, height: 260 },
  ring: { position: 'absolute', width: 148, height: 148, borderRadius: 74, borderWidth: 1.5, borderColor: palette.cream },
  name: { fontFamily: fonts.serif, fontSize: 44, lineHeight: 50, color: darkColors.text },
  status: { color: darkColors.textMuted },
  actions: { flexDirection: 'row', justifyContent: 'space-around' },
  actionWrap: { alignItems: 'center', gap: spacing.xs },
  action: { width: 76, height: 76, borderRadius: 38, alignItems: 'center', justifyContent: 'center' },
});
