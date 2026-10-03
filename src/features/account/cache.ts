import { Directory, File, Paths } from 'expo-file-system';
import { Image } from 'expo-image';

export type StorageInfo = { cacheBytes: number; totalBytes: number; freeBytes: number };

/** Local-only: reads sizes on this phone, never touches Firebase. */
export function readStorageInfo(): StorageInfo {
  let cacheBytes = 0;
  try {
    cacheBytes = Paths.cache.size ?? 0;
  } catch {
    cacheBytes = 0;
  }
  let totalBytes = 0;
  let freeBytes = 0;
  try {
    totalBytes = Paths.totalDiskSpace;
    freeBytes = Paths.availableDiskSpace;
  } catch {
    // Unknown on this platform.
  }
  return { cacheBytes, totalBytes, freeBytes };
}

/** Deletes cached media (images, downloads, temp files). Messages and settings are untouched. */
export async function clearMediaCache(): Promise<void> {
  await Promise.allSettled([Image.clearDiskCache(), Image.clearMemoryCache()]);
  try {
    for (const entry of Paths.cache.list()) {
      try {
        if (entry instanceof Directory || entry instanceof File) entry.delete();
      } catch {
        // A file in use by another module; skip it.
      }
    }
  } catch {
    // Cache directory unreadable; nothing else to do.
  }
}

/** Soft budget used to draw the cache bar. */
export const CACHE_BUDGET_BYTES = 500 * 1024 * 1024;
