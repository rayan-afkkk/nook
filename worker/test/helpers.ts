import { b64urlEncode, signRS256 } from '../src/crypto';

export async function makeRsaKey(kid = 'test-kid') {
  const pair = (await crypto.subtle.generateKey(
    { name: 'RSASSA-PKCS1-v1_5', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' },
    true,
    ['sign', 'verify'],
  )) as CryptoKeyPair;
  const jwk = (await crypto.subtle.exportKey('jwk', pair.publicKey)) as JsonWebKey;
  const pkcs8 = (await crypto.subtle.exportKey('pkcs8', pair.privateKey)) as ArrayBuffer;
  const b64 = btoa(String.fromCharCode(...new Uint8Array(pkcs8)));
  const pem = `-----BEGIN PRIVATE KEY-----\n${b64.match(/.{1,64}/g)!.join('\n')}\n-----END PRIVATE KEY-----\n`;
  return { kid, jwk: { ...jwk, kid, alg: 'RS256', use: 'sig' }, pem };
}

export async function makeIdToken(pem: string, kid: string, claims: Record<string, unknown>) {
  return signRS256(claims, pem, kid);
}

export const b64 = b64urlEncode;
