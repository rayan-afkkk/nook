import { useCallback, useEffect, useState } from 'react';
import { AppState, StyleSheet, View } from 'react-native';

import { Button, Card, Icon, Text, type IconName } from '@/components/ui';
import { haptics } from '@/lib/haptics';
import { radius, spacing, useTheme } from '@/theme';

import { getPermission, openSystemSettings, requestPermission, type PermissionKey, type PermissionState } from './permissions';

type Props = {
  permission: PermissionKey;
  icon: IconName;
  tone: 'mint' | 'sky' | 'peach' | 'lavender' | 'rose';
  title: string;
  reason: string;
};

/** Explains a permission first; the system prompt only appears after the person taps Allow. */
export function PermissionCard({ permission, icon, tone, title, reason }: Props) {
  const { colors } = useTheme();
  const [state, setState] = useState<PermissionState | null>(null);
  const [busy, setBusy] = useState(false);

  const refresh = useCallback(() => {
    getPermission(permission)
      .then(setState)
      .catch(() => setState('undetermined'));
  }, [permission]);

  useEffect(() => {
    refresh();
    // Re-check when returning from system Settings.
    const sub = AppState.addEventListener('change', (s) => s === 'active' && refresh());
    return () => sub.remove();
  }, [refresh]);

  const onAllow = async () => {
    if (state === 'blocked') {
      openSystemSettings();
      return;
    }
    setBusy(true);
    try {
      const next = await requestPermission(permission);
      setState(next);
      if (next === 'granted') haptics.success();
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card style={styles.card}>
      <View style={[styles.tile, { backgroundColor: colors.pastel[tone] }]}>
        <Icon name={icon} size={22} color="onPastel" />
      </View>
      <View style={styles.texts}>
        <Text variant="bodyBold">{title}</Text>
        <Text variant="caption" color="textMuted">
          {reason}
        </Text>
      </View>
      {state === 'granted' ? (
        <View style={styles.allowed} accessible accessibilityLabel={`${title} allowed`}>
          <Icon name="checkmark-circle" size={22} color={colors.online} />
        </View>
      ) : (
        <Button
          block={false}
          variant={state === 'blocked' ? 'secondary' : 'primary'}
          title={state === 'blocked' ? 'Settings' : 'Allow'}
          loading={busy || state === null}
          onPress={() => void onAllow()}
          accessibilityHint={reason}
          style={styles.button}
        />
      )}
    </Card>
  );
}

const styles = StyleSheet.create({
  card: { flexDirection: 'row', alignItems: 'center', gap: spacing.md },
  tile: { width: 44, height: 44, borderRadius: radius.sm, alignItems: 'center', justifyContent: 'center' },
  texts: { flex: 1, gap: 2 },
  allowed: { width: 48, height: 48, alignItems: 'center', justifyContent: 'center' },
  button: { paddingHorizontal: spacing.md },
});
