import { sha1Hex } from './crypto';

export type CloudinaryCreds = { cloudName: string; apiKey: string; apiSecret: string };

/** Signed "destroy" call. Returns true when the asset is gone (including "not found"). */
export async function destroyAsset(creds: CloudinaryCreds, publicId: string, resourceType: 'image' | 'video' | 'raw'): Promise<boolean> {
  const timestamp = Math.floor(Date.now() / 1000).toString();
  // Parameters sorted alphabetically, then the secret appended (Cloudinary's signature scheme).
  const toSign = `invalidate=true&public_id=${publicId}&timestamp=${timestamp}${creds.apiSecret}`;
  const signature = await sha1Hex(toSign);
  const form = new URLSearchParams({ public_id: publicId, timestamp, api_key: creds.apiKey, invalidate: 'true', signature });
  const res = await fetch(`https://api.cloudinary.com/v1_1/${creds.cloudName}/${resourceType}/destroy`, { method: 'POST', body: form });
  if (!res.ok) return false;
  const body = (await res.json().catch(() => ({}))) as { result?: string };
  return body.result === 'ok' || body.result === 'not found';
}
