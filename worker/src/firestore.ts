import { HttpError } from './env';

/** Minimal Firestore REST client. Every read uses a field mask so message text is never fetched. */

type Value =
  | { stringValue: string }
  | { integerValue: string }
  | { doubleValue: number }
  | { booleanValue: boolean }
  | { nullValue: null }
  | { timestampValue: string }
  | { mapValue: { fields?: Record<string, Value> } }
  | { arrayValue: { values?: Value[] } }
  | { referenceValue: string };

export type Doc = Record<string, unknown>;

export function decodeValue(v: Value): unknown {
  if ('stringValue' in v) return v.stringValue;
  if ('integerValue' in v) return Number(v.integerValue);
  if ('doubleValue' in v) return v.doubleValue;
  if ('booleanValue' in v) return v.booleanValue;
  if ('nullValue' in v) return null;
  if ('timestampValue' in v) return Date.parse(v.timestampValue);
  if ('mapValue' in v) return decodeFields(v.mapValue.fields ?? {});
  if ('arrayValue' in v) return (v.arrayValue.values ?? []).map(decodeValue);
  if ('referenceValue' in v) return v.referenceValue;
  return undefined;
}

export function decodeFields(fields: Record<string, Value>): Doc {
  const out: Doc = {};
  for (const [k, v] of Object.entries(fields)) out[k] = decodeValue(v);
  return out;
}

export class Firestore {
  readonly root: string;
  constructor(
    readonly projectId: string,
    private readonly token: string,
  ) {
    this.root = `projects/${projectId}/databases/(default)/documents`;
  }

  private url(suffix: string) {
    return `https://firestore.googleapis.com/v1/${suffix}`;
  }

  private headers() {
    return { authorization: `Bearer ${this.token}`, 'content-type': 'application/json' };
  }

  name(path: string) {
    return `${this.root}/${path}`;
  }

  async get(path: string, mask: string[]): Promise<Doc | null> {
    const q = mask.map((f) => `mask.fieldPaths=${encodeURIComponent(f)}`).join('&');
    const res = await fetch(this.url(`${this.name(path)}?${q}`), { headers: this.headers() });
    if (res.status === 404) return null;
    if (!res.ok) throw new HttpError(502, `Firestore read failed (${res.status})`);
    const body = (await res.json()) as { fields?: Record<string, Value> };
    return decodeFields(body.fields ?? {});
  }

  async batchGet(paths: string[], mask: string[]): Promise<Map<string, Doc | null>> {
    const out = new Map<string, Doc | null>();
    if (!paths.length) return out;
    const res = await fetch(this.url(`${this.root}:batchGet`), {
      method: 'POST',
      headers: this.headers(),
      body: JSON.stringify({ documents: paths.map((p) => this.name(p)), mask: { fieldPaths: mask } }),
    });
    if (!res.ok) throw new HttpError(502, `Firestore batch read failed (${res.status})`);
    const rows = (await res.json()) as { found?: { name: string; fields?: Record<string, Value> }; missing?: string }[];
    for (const row of rows) {
      if (row.found) out.set(row.found.name.slice(this.root.length + 1), decodeFields(row.found.fields ?? {}));
      else if (row.missing) out.set(row.missing.slice(this.root.length + 1), null);
    }
    return out;
  }

  async commit(writes: unknown[]): Promise<void> {
    if (!writes.length) return;
    const res = await fetch(this.url(`${this.root}:commit`), { method: 'POST', headers: this.headers(), body: JSON.stringify({ writes }) });
    if (!res.ok) throw new HttpError(502, `Firestore write failed (${res.status})`);
  }

  deleteWrite(path: string) {
    return { delete: this.name(path) };
  }

  arrayRemoveWrite(path: string, field: string, values: string[]) {
    return {
      transform: {
        document: this.name(path),
        fieldTransforms: [{ fieldPath: field, removeAllFromArray: { values: values.map((s) => ({ stringValue: s })) } }],
      },
    };
  }
}
