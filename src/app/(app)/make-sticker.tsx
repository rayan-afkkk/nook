import { router } from 'expo-router';
import { Image } from 'expo-image';
import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { Button, Card, Header, ListRow, ProgressBar, Screen, SegmentedControl, Text, TextField, toast } from '@/components/ui';
import { useSession } from '@/features/auth/session';
import { pickStickerImage, type PickedImage } from '@/features/media/pickers';
import { addStickerToPack, createPackWithSticker, useStickerPacks, MAX_STICKERS_PER_PACK } from '@/features/stickers/packs';
import { describeError } from '@/lib/errors';
import { haptics } from '@/lib/haptics';
import { radius, spacing, useTheme } from '@/theme';

const MODES = [
  { value: 'new', label: 'New pack' },
  { value: 'existing', label: 'Add to pack' },
] as const;

export default function MakeSticker() {
  const { colors } = useTheme();
  const me = useSession((s) => s.user?.uid ?? '');
  const { packs } = useStickerPacks();
  const [image, setImage] = useState<PickedImage | null>(null);
  const [mode, setMode] = useState<'new' | 'existing'>('new');
  const [name, setName] = useState('');
  const [packId, setPackId] = useState<string | null>(null);
  const [progress, setProgress] = useState<number | null>(null);

  const pick = async () => {
    try {
      const img = await pickStickerImage();
      if (img) setImage(img);
    } catch (e) {
      toast.error(describeError(e));
    }
  };

  const save = async () => {
    if (!image) return;
    setProgress(0);
    try {
      if (mode === 'new') {
        await createPackWithSticker(me, name, image, setProgress);
      } else {
        const pack = packs.find((p) => p.id === packId);
        if (!pack) throw new Error('Pick a pack.');
        await addStickerToPack(me, pack, image, setProgress);
      }
      haptics.success();
      toast.success('Sticker added for everyone.');
      router.back();
    } catch (e) {
      toast.error(describeError(e));
      setProgress(null);
    }
  };

  const canSave = !!image && (mode === 'new' ? name.trim().length > 0 : !!packId) && progress === null;

  return (
    <Screen
      scroll
      header={<Header title="Make a sticker" back />}
      footer={
        <View style={styles.footer}>
          {progress !== null ? <ProgressBar progress={progress} label="Uploading sticker" /> : null}
          <Button title="Save sticker" icon="checkmark" disabled={!canSave} loading={progress !== null} onPress={() => void save()} />
        </View>
      }
    >
      <View style={styles.body}>
        <Text variant="body" color="textMuted">
          Pick a photo and crop it square. Stickers are shared with everyone on NOOK.
        </Text>
        <Card style={styles.previewCard}>
          {image ? (
            <Image source={{ uri: image.uri }} style={styles.preview} contentFit="contain" accessibilityLabel="Sticker preview" />
          ) : (
            <View style={[styles.preview, styles.placeholder, { borderColor: colors.border }]}>
              <Text variant="caption" color="textMuted">
                No photo yet
              </Text>
            </View>
          )}
          <Button title={image ? 'Choose another photo' : 'Choose a photo'} icon="images-outline" variant="secondary" onPress={() => void pick()} />
        </Card>
        <SegmentedControl accessibilityLabel="Where to add it" options={MODES} value={mode} onChange={setMode} />
        {mode === 'new' ? (
          <TextField label="Pack name" placeholder="Inside jokes" value={name} onChangeText={setName} maxLength={30} />
        ) : packs.length === 0 ? (
          <Text variant="caption" color="textMuted">
            There are no packs yet. Start a new one.
          </Text>
        ) : (
          <View>
            {packs.map((p) => (
              <ListRow
                key={p.id}
                icon={packId === p.id ? 'radio-button-on' : 'radio-button-off'}
                title={p.name}
                subtitle={`${p.stickers.length}/${MAX_STICKERS_PER_PACK} stickers`}
                showChevron={false}
                onPress={() => setPackId(p.id)}
              />
            ))}
          </View>
        )}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  body: { gap: spacing.lg, paddingTop: spacing.lg },
  previewCard: { alignItems: 'center', gap: spacing.md },
  preview: { width: 200, height: 200 },
  placeholder: { borderWidth: 1.5, borderStyle: 'dashed', borderRadius: radius.card, alignItems: 'center', justifyContent: 'center' },
  footer: { paddingHorizontal: spacing.lg, paddingBottom: spacing.md, paddingTop: spacing.xs, gap: spacing.sm },
});
