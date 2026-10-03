import { useEffect, useState } from 'react';
import { KeyboardAvoidingView, StyleSheet, View } from 'react-native';
import Animated, { FadeIn, FadeInRight, FadeOutLeft } from 'react-native-reanimated';

import { StepHeader } from '@/components/setup/StepHeader';
import { Button, Card, Icon, SegmentedControl, Text, TextField, Toggle, toast } from '@/components/ui';
import { LOCK_MIN_LENGTH } from '@/constants/app';
import { describeError } from '@/lib/errors';
import { haptics } from '@/lib/haptics';
import { spacing } from '@/theme';

import { validateSecret, type LockKind } from './lockCrypto';
import { PinDots, PinPad } from './PinPad';
import { biometricAvailability, useLock } from './lockStore';

type Phase = 'verify' | 'enter' | 'confirm' | 'biometric';

type Props = {
  mode: 'setup' | 'change';
  onDone: () => void;
};

const KIND_OPTIONS = [
  { value: 'pin', label: 'PIN' },
  { value: 'password', label: 'Password' },
] as const;

export function LockSetupFlow({ mode, onDone }: Props) {
  const current = useLock((s) => s.record);
  const [phase, setPhase] = useState<Phase>(mode === 'change' && current ? 'verify' : 'enter');
  const [kind, setKind] = useState<LockKind>('pin');
  const [first, setFirst] = useState('');
  const [value, setValue] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [errorKey, setErrorKey] = useState(0);
  const [busy, setBusy] = useState(false);
  const [bio, setBio] = useState({ available: false, label: 'Fingerprint' });
  const [useBio, setUseBio] = useState(true);

  useEffect(() => {
    void biometricAvailability().then((b) => {
      setBio(b);
      setUseBio(b.available && (current?.biometric ?? true));
    });
    // Only on mount.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const inputKind: LockKind = phase === 'verify' ? (current?.kind ?? 'pin') : kind;

  const fail = (message: string) => {
    haptics.error();
    setError(message);
    setErrorKey((k) => k + 1);
    setValue('');
  };

  const save = async (biometric: boolean) => {
    setBusy(true);
    try {
      await useLock.getState().setLock(kind, first, biometric);
      haptics.success();
      if (mode === 'change') toast.success('App lock updated.');
      onDone();
    } catch (e) {
      toast.error(describeError(e));
      setBusy(false);
    }
  };

  const next = async (input: string = value) => {
    setError(null);
    if (phase === 'verify') {
      setBusy(true);
      const ok = await useLock.getState().verify(input);
      setBusy(false);
      if (!ok) return fail(`That's not your current ${current?.kind === 'pin' ? 'PIN' : 'password'}.`);
      setValue('');
      setPhase('enter');
      return;
    }
    if (phase === 'enter') {
      const problem = validateSecret(kind, input);
      if (problem) return fail(problem);
      setFirst(input);
      setValue('');
      setPhase('confirm');
      return;
    }
    if (phase === 'confirm') {
      if (input !== first) {
        setFirst('');
        setPhase('enter');
        return fail("Those didn't match. Let's start again.");
      }
      if (bio.available) {
        setPhase('biometric');
        return;
      }
      await save(false);
    }
  };

  const title =
    phase === 'verify'
      ? `Enter current ${current?.kind === 'pin' ? 'PIN' : 'password'}`
      : phase === 'enter'
        ? mode === 'setup'
          ? 'Lock your NOOK'
          : `New ${kind === 'pin' ? 'PIN' : 'password'}`
        : phase === 'confirm'
          ? `Confirm your ${kind === 'pin' ? 'PIN' : 'password'}`
          : `Use ${bio.label.toLowerCase()}?`;

  const subtitle =
    phase === 'verify'
      ? 'Confirm it’s you before changing the lock.'
      : phase === 'enter'
        ? `Choose a PIN or password of at least ${LOCK_MIN_LENGTH} characters. It stays on this phone and is never uploaded.`
        : phase === 'confirm'
          ? 'Type it once more so we know it’s right.'
          : `Unlock in a touch. Your ${kind === 'pin' ? 'PIN' : 'password'} still works as a backup.`;

  const header =
    mode === 'setup' ? (
      <StepHeader step={3} total={3} title={title} subtitle={subtitle} />
    ) : (
      <View style={styles.changeHeader}>
        <Text variant="headline" accessibilityRole="header">
          {title}
        </Text>
        <Text variant="body" color="textMuted">
          {subtitle}
        </Text>
      </View>
    );

  if (phase === 'biometric') {
    return (
      <Animated.View entering={FadeInRight.duration(260)} style={styles.flex}>
        {header}
        <Card style={styles.bioCard}>
          <Icon name="finger-print" size={28} />
          <View style={styles.flex}>
            <Text variant="bodyBold">{bio.label} unlock</Text>
            <Text variant="caption" color="textMuted">
              Uses the {bio.label.toLowerCase()} saved on this phone.
            </Text>
          </View>
          <Toggle value={useBio} onValueChange={setUseBio} label={`${bio.label} unlock`} />
        </Card>
        <View style={styles.spacer} />
        <Button title="Finish" icon="checkmark" loading={busy} onPress={() => void save(useBio)} />
      </Animated.View>
    );
  }

  return (
    <KeyboardAvoidingView behavior="padding" style={styles.flex}>
      <Animated.View key={phase} entering={FadeInRight.duration(240)} exiting={FadeOutLeft.duration(160)} style={styles.flex}>
        {header}
        {phase === 'enter' ? (
          <View style={styles.kind}>
            <SegmentedControl
              accessibilityLabel="Lock type"
              options={KIND_OPTIONS}
              value={kind}
              onChange={(k) => {
                setKind(k);
                setValue('');
                setError(null);
              }}
            />
          </View>
        ) : null}

        {inputKind === 'pin' ? (
          <View style={styles.pin}>
            <PinDots length={Math.max(LOCK_MIN_LENGTH, value.length)} filled={value.length} errorKey={errorKey} busy={busy} />
            <Text variant="caption" color={error ? 'danger' : 'textMuted'} align="center" accessibilityLiveRegion="polite">
              {error ?? (phase === 'verify' && current?.pinLength ? ' ' : `${LOCK_MIN_LENGTH} digits or more`)}
            </Text>
            <PinPad
              disabled={busy}
              onDigit={(d) => {
                setError(null);
                const v = (value + d).slice(0, 16);
                setValue(v);
                if (phase === 'verify' && current?.pinLength === v.length) {
                  void next(v);
                }
              }}
              onDelete={() => setValue((v) => v.slice(0, -1))}
            />
          </View>
        ) : (
          <Animated.View entering={FadeIn} style={styles.password}>
            <TextField
              label={phase === 'confirm' ? 'Confirm password' : 'Password'}
              value={value}
              onChangeText={(t) => {
                setValue(t);
                setError(null);
              }}
              secureTextEntry
              autoFocus
              autoCapitalize="none"
              autoCorrect={false}
              textContentType={phase === 'verify' ? 'password' : 'newPassword'}
              returnKeyType="next"
              onSubmitEditing={() => void next()}
              error={error}
              helper={phase === 'verify' ? null : `At least ${LOCK_MIN_LENGTH} characters.`}
            />
          </Animated.View>
        )}
        <View style={styles.spacer} />
        <Button
          title={phase === 'confirm' && !bio.available ? 'Finish' : 'Next'}
          icon={phase === 'confirm' && !bio.available ? 'checkmark' : 'arrow-forward'}
          loading={busy}
          disabled={value.length < (phase === 'verify' ? 1 : LOCK_MIN_LENGTH)}
          onPress={() => void next()}
        />
      </Animated.View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  changeHeader: { gap: spacing.xs, paddingVertical: spacing.lg },
  kind: { marginBottom: spacing.xl },
  pin: { gap: spacing.lg },
  password: { gap: spacing.sm },
  spacer: { flex: 1, minHeight: spacing.lg },
  bioCard: { flexDirection: 'row', alignItems: 'center', gap: spacing.md },
});
