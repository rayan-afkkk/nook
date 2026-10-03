import { CLOUDINARY_CLOUD_NAME, CLOUDINARY_UPLOAD_PRESET, MAX_FILE_BYTES } from '@/constants/app';
import { UserFacingError } from '@/lib/errors';

export type UploadResult = {
  url: string;
  publicId: string;
  resourceType: 'image' | 'video' | 'raw';
  width?: number;
  height?: number;
  bytes: number;
  durationMs?: number;
};

export const cloudinaryConfigured = () => !!CLOUDINARY_CLOUD_NAME && !!CLOUDINARY_UPLOAD_PRESET;

type Options = {
  uri: string;
  name: string;
  mime: string;
  /** image for photos/stickers, video for audio (Cloudinary's convention), raw for files. */
  resourceType: 'image' | 'video' | 'raw';
  size?: number;
  onProgress?: (fraction: number) => void;
  signal?: { cancelled: boolean; abort?: () => void };
};

/**
 * Unsigned upload straight from the phone. The preset (set up in the Cloudinary console) limits
 * formats, size and folder; deletion happens later through the Worker, which holds the API secret.
 */
export function uploadToCloudinary({ uri, name, mime, resourceType, size, onProgress, signal }: Options): Promise<UploadResult> {
  if (!cloudinaryConfigured()) {
    return Promise.reject(new UserFacingError('Media uploads are not set up yet (Cloudinary keys missing in this build).'));
  }
  if (size && size > MAX_FILE_BYTES) {
    return Promise.reject(new UserFacingError('That file is over the 10 MB limit.'));
  }
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', `https://api.cloudinary.com/v1_1/${CLOUDINARY_CLOUD_NAME}/${resourceType}/upload`);
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable && onProgress) onProgress(e.loaded / e.total);
    };
    xhr.onload = () => {
      try {
        const data = JSON.parse(xhr.responseText) as {
          secure_url?: string;
          public_id?: string;
          resource_type?: 'image' | 'video' | 'raw';
          width?: number;
          height?: number;
          bytes?: number;
          duration?: number;
          error?: { message?: string };
        };
        if (xhr.status >= 200 && xhr.status < 300 && data.secure_url && data.public_id) {
          resolve({
            url: data.secure_url,
            publicId: data.public_id,
            resourceType: data.resource_type ?? resourceType,
            width: data.width,
            height: data.height,
            bytes: data.bytes ?? size ?? 0,
            durationMs: data.duration ? Math.round(data.duration * 1000) : undefined,
          });
        } else {
          reject(new UserFacingError(data.error?.message ?? `Upload failed (${xhr.status}).`));
        }
      } catch {
        reject(new UserFacingError('Upload failed. Please try again.'));
      }
    };
    xhr.onerror = () => reject(new UserFacingError("Upload failed. Check your connection and retry."));
    xhr.onabort = () => reject(new UserFacingError('Upload cancelled.'));
    if (signal) signal.abort = () => xhr.abort();
    const form = new FormData();
    // React Native's FormData accepts a file descriptor object.
    form.append('file', { uri, name, type: mime } as unknown as Blob);
    form.append('upload_preset', CLOUDINARY_UPLOAD_PRESET);
    xhr.send(form);
  });
}

/** Cloudinary on-the-fly resize for thumbnails (keeps bandwidth low). */
export function thumbnailUrl(url: string, width: number): string {
  if (!url.includes('/image/upload/')) return url;
  return url.replace('/image/upload/', `/image/upload/c_limit,w_${Math.round(width)},q_auto,f_auto/`);
}
