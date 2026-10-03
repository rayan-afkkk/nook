import { useCallback, useEffect, useRef, useState } from 'react';
import { BackHandler, StyleSheet, View } from 'react-native';
import { KeyboardAvoidingView } from 'react-native-keyboard-controller';
import Animated, { FadeIn, FadeOut } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Logo } from '@/components/brand/Logo';
import { Button, ConfirmSheet, PressableScale, Text, TextField, toast } from '@/components/ui';
import { signOut } from '@/features/auth/authService';
import { describeError } from '@/lib/errors';
import { haptics } from '@/lib/haptics';
import { spacing, useTheme } from '@/theme';

import { FREE_ATTEMPTS } from './lockCrypto';
import { PinDots, PinPad } from './PinPad';
import { biometricAvailability, useLock } from './lockStore';

function useCountdown(until: number): number {
  const [left, setLeft] = useState(0);
  useEffect(() => {
    const tick = () => setLeft(Math.max(0, Math.ceil((until - Date.now()) / 1000)));
    const first = setTimeout(tick, 0);
    const interval = setInterval(tick, 250);
    return () => {
      clearTimeout(first);
      clearInterval(interval);
    };
  }, [until]);
  return left;
}

/** Full-screen overlay shown while NOOK is locked. Nothing underneath is visible or reachable. */
export function LockScreen() {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const record = useLock((s) => s.record);
  const attempts = useLock((s) => s.attempts);
  const [value, setValue] = useState('');
  const [busy, setBusy] = useState(false);
  const [errorKey, setErrorKey] = useState(0);
  const [message, setMessage] = useState<string | null>(null);
  const [forgotOpen, setForgotOpen] = useState(false);
  const [signingOut, setSigningOut] = useState(false);
  const [bio, setBio] = useState<{ available: boolean; label: string }>({ available: false, label: 'Biometrics' });
  const prompted = useRef(false);
  const wait = useCountdown(attempts.until);

  const isPin = record?.kind === 'pin';
  const biometricOn = !!record?.biometric && bio.available;

  // Android back button must not dismiss the lock.
  useEffect(() => {
    const sub = BackHandler.addEventListener('hardwareBackPress', () => true);
    return () => sub.remove();
  }, []);

  const tryBiometrics = useCallback(async () => {
    try {
      const ok = await useLock.getState().unlockWithBiometrics();
      if (ok) haptics.success();
    } catch {
      // Cancelled or unavailable: the PIN/password stays available.
    }
  }, []);

  useEffect(() => {
    void biometricAvailability().then((b) => {
      setBio(b);
      if (b.available && record?.biometric && !prompted.current) {
        prompted.current = true;
        void tryBiometrics();
      }
    });
  }, [record?.biometric, tryBiometrics]);

  const submit = useCallback(
    async (secret: string) => {
      if (busy || !secret) return;
      setBusy(true);
      const ok = await useLock.getState().unlockWithSecret(secret);
      setBusy(false);
      if (ok) {
        haptics.success();
        return;
      }
      haptics.error();
      setErrorKey((k) => k + 1);
      setValue('');
      const failed = useLock.getState().attempts.failed;
      const left = FREE_ATTEMPTS - failed;
      setMessage(
        left > 0
          ? `That's not it. ${left} ${left === 1 ? 'try' : 'tries'} left before a short wait.`
          : 'Too many tries. Take a breath and try again shortly.',
      );
    },
    [busy],
  );

  const onDigit = (d: string) => {
    if (wait > 0 || busy) return;
    const next = (value + d).slice(0, 32);
    setValue(next);
    setMessage(null);
    if (record?.pinLength && next.length === record.pinLength) void submit(next);
  };

  const onForgot = async () => {
    setSigningOut(true);
    try {
      await signOut();
    } catch (e) {
      toast.error(describeError(e));
      setSigningOut(false);
    }
  };

  return (
    <Animated.View
      entering={FadeIn.duration(150)}
      exiting={FadeOut.duration(220)}
      style={[StyleSheet.absoluteFill, styles.root, { backgroundColor: colors.background, paddingTop: insets.top + spacing.xl, paddingBottom: insets.bottom + spacing.lg }]}
      accessibilityViewIsModal
    >
      <KeyboardAvoidingView behavior="padding" style={styles.flex}>
        <View style={styles.top}>
          <Logo size={56} />
          <Text variant="title" align="center">
            Welcome back
          </Text>
          <Text variant="body" color={message ? 'danger' : 'textMuted'} align="center" accessibilityLiveRegion="polite">
            {wait > 0
              ? `Try again in ${wait}s`
              : (message ?? (isPin ? 'Enter your PIN to unlock NOOK.' : 'Enter your password to unlock NOOK.'))}
          </Text>
        </View>

        {isPin ? (
          <View style={styles.pinArea}>
            <PinDots length={record?.pinLength ?? 6} filled={value.length} errorKey={errorKey} busy={busy} />
            <PinPad
              disabled={wait > 0 || busy}
              onDigit={onDigit}
              onDelete={() => setValue((v) => v.slice(0, -1))}
              leftKey={
                biometricOn
                  ? { icon: 'finger-print', label: `Unlock with ${bio.label}`, onPress: () => void tryBiometrics() }
                  : !record?.pinLength && value.length > 0
                    ? { icon: 'checkmark', label: 'Unlock', onPress: () => void submit(value) }
                    : null
              }
            />
          </View>
        ) : (
          <View style={styles.passwordArea}>
            <TextField
              label="Password"
              value={value}
              onChangeText={(t) => {
                setValue(t);
                setMessage(null);
              }}
              secureTextEntry
              autoFocus={!biometricOn}
              autoCapitalize="none"
              autoCorrect={false}
              editable={wait === 0 && !busy}
              returnKeyType="go"
              onSubmitEditing={() => void submit(value)}
            />
            <Button title="Unlock" icon="lock-open-outline" loading={busy} disabled={wait > 0 || !value} onPress={() => void submit(value)} />
            {biometricOn ? (
              <Button title={`Use ${bio.label}`} icon="finger-print" variant="secondary" onPress={() => void tryBiometrics()} />
            ) : null}
          </View>
        )}

        <PressableScale
          accessibilityRole="button"
          accessibilityLabel={isPin ? 'Forgot PIN' : 'Forgot password'}
          onPress={() => setForgotOpen(true)}
          style={styles.forgot}
        >
          <Text variant="label" color="textMuted">
            {isPin ? 'Forgot PIN?' : 'Forgot password?'}
          </Text>
        </PressableScale>
      </KeyboardAvoidingView>

      <ConfirmSheet
        visible={forgotOpen}
        title="Reset your lock"
        message="You'll be signed out on this phone. Sign back in with Google and set a new PIN or password. Your chats stay in your account."
        confirmLabel="Sign out and reset"
        destructive
        loading={signingOut}
        onConfirm={() => void onForgot()}
        onCancel={() => setForgotOpen(false)}
      />
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  root: { zIndex: 500, elevation: 50, paddingHorizontal: spacing.lg },
  flex: { flex: 1, justifyContent: 'space-between' },
  top: { alignItems: 'center', gap: spacing.sm, paddingHorizontal: spacing.md },
  pinArea: { gap: spacing.xxl },
  passwordArea: { gap: spacing.sm },
  forgot: { alignSelf: 'center', minHeight: 48, justifyContent: 'center', paddingHorizontal: spacing.md },
});
