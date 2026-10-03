import { getIdToken } from '@react-native-firebase/auth';

import { WORKER_URL } from '@/constants/app';

import { auth } from './firebase';

export const workerConfigured = () => WORKER_URL.length > 0;

export class WorkerError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
  }
}

/** Authenticated JSON call to the NOOK Cloudflare Worker (Firebase ID token in the Authorization header). */
export async function callWorker<T = unknown>(path: string, body: Record<string, unknown>): Promise<T> {
  if (!WORKER_URL) throw new WorkerError('The NOOK server (Cloudflare Worker) is not configured in this build.', 0);
  const user = auth().currentUser;
  if (!user) throw new WorkerError('Not signed in.', 401);
  const token = await getIdToken(user);
  const res = await fetch(`${WORKER_URL}${path}`, {
    method: 'POST',
    headers: { 'content-type': 'application/json', authorization: `Bearer ${token}` },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    let message = `Server error (${res.status})`;
    try {
      const data = (await res.json()) as { error?: string };
      if (data.error) message = data.error;
    } catch {
      // not JSON
    }
    throw new WorkerError(message, res.status);
  }
  return (await res.json()) as T;
}

/** Fire-and-forget with exponential backoff; never throws. Used for push notifications. */
export function callWorkerInBackground(path: string, body: Record<string, unknown>, attempts = 4): void {
  if (!workerConfigured()) return;
  const run = async (n: number) => {
    try {
      await callWorker(path, body);
    } catch (e) {
      const status = e instanceof WorkerError ? e.status : 0;
      // Don't retry requests the server rejected on purpose.
      if (status >= 400 && status < 500) return;
      if (n + 1 < attempts) setTimeout(() => void run(n + 1), 1000 * 2 ** n);
    }
  };
  void run(0);
}
