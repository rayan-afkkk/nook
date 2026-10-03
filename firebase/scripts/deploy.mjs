/**
 * Deploys Firestore rules, Firestore indexes and Realtime Database rules with plain REST calls,
 * authenticated by the service account in GOOGLE_APPLICATION_CREDENTIALS.
 *
 * Used by CI instead of `firebase deploy`, which first calls the Service Usage API, which the
 * default Firebase Admin SDK service account isn't allowed to do.
 */
import { createSign } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const sa = JSON.parse(readFileSync(process.env.GOOGLE_APPLICATION_CREDENTIALS, 'utf8'));
const project = process.env.FIREBASE_PROJECT || sa.project_id;
const dbUrl = process.env.FIREBASE_DATABASE_URL || `https://${project}-default-rtdb.firebaseio.com`;

const b64 = (x) => Buffer.from(x).toString('base64url');

async function accessToken() {
  const now = Math.floor(Date.now() / 1000);
  const claims = {
    iss: sa.client_email,
    scope: [
      'https://www.googleapis.com/auth/cloud-platform',
      'https://www.googleapis.com/auth/firebase',
      'https://www.googleapis.com/auth/firebase.database',
      'https://www.googleapis.com/auth/userinfo.email',
    ].join(' '),
    aud: 'https://oauth2.googleapis.com/token',
    iat: now,
    exp: now + 3600,
  };
  const unsigned = `${b64(JSON.stringify({ alg: 'RS256', typ: 'JWT' }))}.${b64(JSON.stringify(claims))}`;
  const sig = createSign('RSA-SHA256').update(unsigned).sign(sa.private_key, 'base64url');
  const res = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion: `${unsigned}.${sig}` }),
  });
  if (!res.ok) throw new Error(`token: ${res.status} ${await res.text()}`);
  return (await res.json()).access_token;
}

async function call(token, method, url, body, okStatuses = []) {
  const res = await fetch(url, {
    method,
    headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  if (!res.ok && !okStatuses.includes(res.status)) throw new Error(`${method} ${url} -> ${res.status} ${text}`);
  return { status: res.status, body: text ? JSON.parse(text) : null };
}

const token = await accessToken();

// 1. Firestore rules: create a ruleset, then point the cloud.firestore release at it.
const rules = readFileSync(resolve(root, 'firestore.rules'), 'utf8');
const rs = await call(token, 'POST', `https://firebaserules.googleapis.com/v1/projects/${project}/rulesets`, {
  source: { files: [{ name: 'firestore.rules', content: rules }] },
});
const releaseName = `projects/${project}/releases/cloud.firestore`;
const release = { name: releaseName, rulesetName: rs.body.name };
const upd = await call(token, 'PATCH', `https://firebaserules.googleapis.com/v1/${releaseName}`, { release }, [404]);
if (upd.status === 404) await call(token, 'POST', `https://firebaserules.googleapis.com/v1/projects/${project}/releases`, release);
console.log(`✔ Firestore rules released (${rs.body.name})`);

// 2. Realtime Database rules.
const dbRules = readFileSync(resolve(root, 'database.rules.json'), 'utf8');
const put = await fetch(`${dbUrl}/.settings/rules.json`, { method: 'PUT', headers: { authorization: `Bearer ${token}` }, body: dbRules });
if (!put.ok) throw new Error(`database rules -> ${put.status} ${await put.text()}`);
console.log('✔ Realtime Database rules deployed');

// 3. Firestore indexes (409 = already exists).
const { indexes, fieldOverrides } = JSON.parse(readFileSync(resolve(root, 'firestore.indexes.json'), 'utf8'));
const base = `https://firestore.googleapis.com/v1/projects/${project}/databases/(default)`;
// Index creation needs the "Cloud Datastore Index Admin" role, which the default Firebase key lacks.
// Missing permission is a warning (indexes can also be created once by hand in the console).
const indexHelp = 'Grant the service account the "Cloud Datastore Index Admin" role, or create the indexes in Firebase console > Firestore > Indexes (see README).';
let indexWarnings = 0;
for (const ix of indexes) {
  const r = await call(token, 'POST', `${base}/collectionGroups/${ix.collectionGroup}/indexes`, { queryScope: ix.queryScope, fields: ix.fields }, [409, 403]);
  if (r.status === 403) {
    indexWarnings += 1;
    console.log(`::warning::No permission to create the ${ix.collectionGroup} index. ${indexHelp}`);
  } else {
    console.log(r.status === 409 ? `• index on ${ix.collectionGroup} already exists` : `✔ index on ${ix.collectionGroup} created (builds in a few minutes)`);
  }
}
for (const fo of fieldOverrides) {
  const r = await call(
    token,
    'PATCH',
    `${base}/collectionGroups/${fo.collectionGroup}/fields/${fo.fieldPath}?updateMask=indexConfig`,
    { indexConfig: { indexes: fo.indexes.map((i) => ({ queryScope: i.queryScope, fields: [{ fieldPath: fo.fieldPath, order: i.order }] })) } },
    [403],
  );
  if (r.status === 403) {
    indexWarnings += 1;
    console.log(`::warning::No permission to set the ${fo.collectionGroup}.${fo.fieldPath} index. ${indexHelp}`);
  } else console.log(`✔ field index ${fo.collectionGroup}.${fo.fieldPath}`);
}
if (indexWarnings) console.log(`${indexWarnings} index step(s) skipped for lack of permission.`);
console.log('Done.');
