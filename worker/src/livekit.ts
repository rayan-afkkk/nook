import { signHS256 } from './crypto';

/** LiveKit access token: one room (the call id), this one identity, valid for 2 hours. */
export async function liveKitToken(apiKey: string, apiSecret: string, opts: { identity: string; name?: string; room: string; nowSeconds?: number }) {
  const now = opts.nowSeconds ?? Math.floor(Date.now() / 1000);
  return signHS256(
    {
      iss: apiKey,
      sub: opts.identity,
      name: opts.name,
      nbf: now - 10,
      exp: now + 2 * 60 * 60,
      video: { room: opts.room, roomJoin: true, canPublish: true, canSubscribe: true, canPublishData: false },
    },
    apiSecret,
  );
}
