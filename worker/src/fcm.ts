export type FcmResult = 'ok' | 'invalid-token' | 'error';

/** Sends one FCM HTTP v1 message. Classifies dead tokens so the caller can remove them. */
export async function sendFcm(projectId: string, accessToken: string, message: Record<string, unknown>): Promise<FcmResult> {
  const res = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
    method: 'POST',
    headers: { authorization: `Bearer ${accessToken}`, 'content-type': 'application/json' },
    body: JSON.stringify({ message }),
  });
  if (res.ok) return 'ok';
  return classifyFcmError(res.status, await res.text().catch(() => ''));
}

export function classifyFcmError(status: number, body: string): FcmResult {
  if (status === 404 || body.includes('UNREGISTERED')) return 'invalid-token';
  if (status === 400 && /registration token|INVALID_ARGUMENT/.test(body) && /token/i.test(body)) return 'invalid-token';
  return 'error';
}
