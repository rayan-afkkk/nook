import * as DocumentPicker from 'expo-document-picker';
import { SaveFormat, manipulateAsync } from 'expo-image-manipulator';
import * as ImagePicker from 'expo-image-picker';

import { MAX_FILE_BYTES } from '@/constants/app';
import { UserFacingError } from '@/lib/errors';

export type PickedImage = { uri: string; width: number; height: number; size?: number; name: string; mime: string };

/** Downscales to 1600px and re-encodes as JPEG so photos stay small (bandwidth + Cloudinary quota). */
async function compress(uri: string, width: number, height: number): Promise<PickedImage> {
  const scale = Math.min(1, 1600 / Math.max(width, height));
  const out = await manipulateAsync(
    uri,
    scale < 1 ? [{ resize: { width: Math.round(width * scale), height: Math.round(height * scale) } }] : [],
    { compress: 0.8, format: SaveFormat.JPEG },
  );
  return { uri: out.uri, width: out.width, height: out.height, name: `photo-${Date.now()}.jpg`, mime: 'image/jpeg' };
}

export async function pickPhoto(source: 'library' | 'camera'): Promise<PickedImage | null> {
  const options: ImagePicker.ImagePickerOptions = { mediaTypes: ['images'], quality: 1, exif: false };
  if (source === 'camera') {
    const perm = await ImagePicker.requestCameraPermissionsAsync();
    if (!perm.granted) throw new UserFacingError('Camera access is off. Turn it on in Android settings.');
  }
  const result = source === 'camera' ? await ImagePicker.launchCameraAsync(options) : await ImagePicker.launchImageLibraryAsync(options);
  if (result.canceled || !result.assets[0]) return null;
  const a = result.assets[0];
  return compress(a.uri, a.width, a.height);
}

/** Square crop for stickers, 512px PNG. */
export async function pickStickerImage(): Promise<PickedImage | null> {
  const result = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], allowsEditing: true, aspect: [1, 1], quality: 1 });
  if (result.canceled || !result.assets[0]) return null;
  const a = result.assets[0];
  const out = await manipulateAsync(a.uri, [{ resize: { width: 512, height: 512 } }], { compress: 0.9, format: SaveFormat.PNG });
  return { uri: out.uri, width: 512, height: 512, name: `sticker-${Date.now()}.png`, mime: 'image/png' };
}

export type PickedFile = { uri: string; name: string; mime: string; size: number };

export async function pickFile(): Promise<PickedFile | null> {
  const result = await DocumentPicker.getDocumentAsync({ copyToCacheDirectory: true, multiple: false });
  if (result.canceled || !result.assets[0]) return null;
  const a = result.assets[0];
  if ((a.size ?? 0) > MAX_FILE_BYTES) throw new UserFacingError('That file is over the 10 MB limit.');
  return { uri: a.uri, name: a.name, mime: a.mimeType ?? 'application/octet-stream', size: a.size ?? 0 };
}
