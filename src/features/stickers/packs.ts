import {
  addDoc,
  arrayUnion,
  collection,
  doc,
  limit,
  onSnapshot,
  orderBy,
  query,
  serverTimestamp,
  updateDoc,
} from '@react-native-firebase/firestore';
import { useEffect, useState } from 'react';

import { uploadToCloudinary } from '@/features/media/cloudinary';
import type { PickedImage } from '@/features/media/pickers';
import { UserFacingError } from '@/lib/errors';
import { db, metrics } from '@/lib/firebase';

export type Sticker = { url: string; publicId: string; addedBy: string };
/** stickerPacks/{packId}: shared with everyone on NOOK. */
export type StickerPack = { id: string; name: string; createdBy: string; stickers: Sticker[] };

export const MAX_STICKERS_PER_PACK = 60;

/** Live list of packs, only while a sticker screen or panel is open. */
export function useStickerPacks(enabled = true) {
  const [packs, setPacks] = useState<StickerPack[]>([]);
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  useEffect(() => {
    if (!enabled) return;
    const q = query(collection(db(), 'stickerPacks'), orderBy('createdAt', 'desc'), limit(50));
    return onSnapshot(
      q,
      (snap) => {
        if (!snap.metadata.fromCache) metrics.read(Math.max(1, snap.docChanges().length), 'sticker packs');
        setPacks(snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<StickerPack, 'id'>) })));
        setStatus('ready');
      },
      () => setStatus('error'),
    );
  }, [enabled]);
  return { packs, status };
}

async function uploadSticker(image: PickedImage, onProgress?: (p: number) => void) {
  const r = await uploadToCloudinary({ uri: image.uri, name: image.name, mime: image.mime, resourceType: 'image', onProgress });
  return { url: r.url, publicId: r.publicId };
}

export async function createPackWithSticker(me: string, name: string, image: PickedImage, onProgress?: (p: number) => void) {
  const clean = name.trim().slice(0, 30);
  if (!clean) throw new UserFacingError('Name the pack.');
  const s = await uploadSticker(image, onProgress);
  await addDoc(collection(db(), 'stickerPacks'), {
    name: clean,
    createdBy: me,
    createdAt: serverTimestamp(),
    stickers: [{ ...s, addedBy: me }],
  });
  metrics.write(1, 'create pack');
}

export async function addStickerToPack(me: string, pack: StickerPack, image: PickedImage, onProgress?: (p: number) => void) {
  if (pack.stickers.length >= MAX_STICKERS_PER_PACK) throw new UserFacingError('That pack is full. Start a new one.');
  const s = await uploadSticker(image, onProgress);
  await updateDoc(doc(db(), 'stickerPacks', pack.id), { stickers: arrayUnion({ ...s, addedBy: me }) });
  metrics.write(1, 'add sticker');
}
