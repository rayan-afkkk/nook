# NOOK

A private chat app for you and your friend group. Android first, built with Expo (React Native, TypeScript) on
Firebase's free **Spark** plan, a free Cloudflare Worker, Cloudinary (media), Giphy and LiveKit (calls).

> **NOOK is not end-to-end encrypted.** Messages, profiles and chat details are stored in Google Firebase. They are
> encrypted in transit and at rest by Google, and security rules only let chat members read a chat, but anyone who
> administers the Firebase project could technically read them. Photos, voice notes and files are stored on
> Cloudinary under the same conditions.

## What's in it

- **Onboarding:** animated logo splash and four original animated slides (parallax, staggered headline, morphing
  progress pill, per-slide glow, haptics, shimmer; crossfades under "reduce motion"). Then Google sign-in, a
  permanent username, a permission primer and an app lock.
- **App lock:** PIN or password (6+), stored only as a salted PBKDF2 hash in the Android Keystore. Optional
  fingerprint. Locks on launch and after 30 s in the background, hides the app-switcher preview, slows down repeated
  wrong guesses. "Forgot" signs you out.
- **Chats:** DMs and groups (add by exact username, group info, rename, leave), text, replies (swipe right),
  reactions, long-press sheet (react / reply / copy / forward / delete), photos, files up to 10 MB, voice notes
  (hold to record, slide to cancel, waveform, 1×/1.5×/2×), GIFs and stickers (Giphy), custom sticker packs shared
  with everyone, date separators, "Seen", typing dots, online dots, unread badges, optimistic sending with retry.
- **Disappearing messages** per chat (off / 24 h / 7 d).
- **Push notifications** that only ever say who it's from ("Ali sent you a photo", "New message in The Boys").
- **1:1 voice and video calls** with a full-screen incoming call screen, even when the app is closed (see
  [Android limitations](#incoming-calls-on-android-honest-limitations)).
- **Account:** profile, device storage with Clear cache, Appearance (System / Light / Dark), app lock, notifications,
  disappearing default, blocked users, View Onboarding, Help, Privacy & Terms, Sign Out, Delete Account, and a
  Firestore read/write counter in development builds.

## Verified here, and what isn't

Run in a Linux container without Android tooling:

| Check | Result |
|---|---|
| `npm run typecheck` (TypeScript strict) | passes |
| `npm run lint` (Expo ESLint + React Compiler rules) | passes |
| `npm test`: lock hashing, usernames, first-run flow, chat model | 35 tests pass |
| `cd firebase && npm test`: Firestore + Realtime Database rules on the emulators | 56 tests pass |
| `cd worker && npm test`: token verification, `/notify` end to end with Google APIs mocked | 15 tests pass |
| `cd worker && npx wrangler deploy --dry-run` | bundles (20 KB) |
| `npx expo export --platform android` | JS bundle builds |
| `npx expo prebuild --platform android` | native project generates; the local `nook-call` module autolinks; permissions present |
| Screens rendered in a browser with Firebase stubbed | checked visually |

**Not verified:** a native Gradle build and anything on a real phone (Google's Maven repository was blocked in the
build environment). The Kotlin in `modules/nook-call` has never been compiled. Your first EAS build is the first real
native build. If it fails, send me the build log.

## Project layout

```
index.ts                   Entry: LiveKit globals + FCM background handler, then Expo Router
app.config.ts              Expo config (com.nook.msgapp, plugins, permissions)
eas.json                   EAS build profiles (all produce an APK)
src/
  app/                     Routes (Expo Router)
    (onboarding)/          welcome (animated intro), sign-in
    (setup)/               username, permissions, set-lock
    (app)/(tabs)/          chats, friends, stickers, calls, account
    (app)/chat/[id]        chat screen          (app)/chat-info/[id]   group / DM info
    (app)/call/[id]        active call          (app)/call/incoming    incoming call
    (app)/new-group, gifs, packs, make-sticker, intro, debug, settings/*
    privacy.tsx            Privacy & Terms
  theme/                   Design tokens and ThemeProvider
  components/ui/           Design-system components
  features/
    onboarding/            Pager, illustrations, motion
    auth/ profile/ lock/ permissions/
    chat/                  Model (pure, tested), services, listeners, chat UI components
    media/                 Cloudinary upload, outbox (optimistic + retry), pickers, voice recorder
    stickers/              Giphy, emoji, shared sticker packs, media panel
    presence/              Realtime Database presence + typing
    push/                  FCM token registration, routing, background handler
    calls/                 Call records, LiveKit room hook, native call module wrapper
    friends/               Blocks + push tokens (userPrivate doc), username search
    session/               Starts/stops app-wide listeners
modules/nook-call/         Local Expo module (Kotlin): full-screen call notification, ringtone, show-when-locked
worker/                    Cloudflare Worker: /notify, /messages/delete, /call/token, /call/invite, /call/cancel
firebase/                  Firestore + Realtime Database rules, indexes, emulator tests
```

## Data model

| Path | What | Who can read / write |
|---|---|---|
| `users/{uid}` | display name, photo, permanent username | any signed-in user can `get` one; only the owner edits name/photo |
| `usernames/{name}` | `{ uid }` reservation | `get` by exact name only; created with the profile; never changed |
| `userPrivate/{uid}` | FCM tokens, blocked uids | owner only (the Worker reads it with a service account) |
| `chats/{chatId}` | members, type, name, last message preview, `lastRead` per member, `mutedBy`, `disappearing` | members only; each field has its own rule |
| `chats/{chatId}/messages/{id}` | sender, kind, text or media reference, reply, reactions, `expireAt` | members read and send; reactions per person; sender deletes; any member deletes expired |
| `calls/{callId}` | 1:1 call record and status | the two people in the call |
| `stickerPacks/{id}` | shared sticker packs | everyone signed in; anyone adds, creator renames or deletes |
| RTDB `status/{uid}` | online / offline + last changed | read: signed-in; write: owner |
| RTDB `typing/{chatId}/{uid}` | timestamp while typing | write: owner |

DM ids are `dm_<uidA>_<uidB>` (sorted), so two people can only ever have one DM.

## Setup

Everything below is free. You need Node 22+, a Google account, and an Android phone. Nothing here needs a credit card.

### 1. Install

```bash
npm install
cp .env.example .env
```

### 2. Firebase (`nook-39914`)

In the [Firebase console](https://console.firebase.google.com/project/nook-39914):

1. **Authentication > Get started > Sign-in method > Google > Enable.** Open it again, expand **Web SDK
   configuration** and copy the **Web client ID** into `.env` as `EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID`.
2. **Firestore Database > Create database**, production mode, region near you.
3. **Realtime Database > Create database**, locked mode, same region.
4. **Cloud Messaging:** nothing to click; the FCM HTTP v1 API is on for new projects. If the Worker later reports
   "Firebase Cloud Messaging API has not been used", enable it from the link in that error.
5. Deploy rules and indexes:
   ```bash
   cd firebase && npm install && npx firebase login && npm run deploy:rules && cd ..
   ```

### 3. Link EAS and register the SHA-1 (required for Google sign-in)

```bash
npx eas-cli@latest login
npx eas-cli@latest init
```

`eas init` prints a **project ID**. Paste it into `EAS_PROJECT_ID` at the top of `app.config.ts` (it isn't a secret).

```bash
npx eas-cli@latest credentials -p android
```

Choose the `development` profile, then *Keystore > Set up a new keystore*. It prints the **SHA-1** and **SHA-256**.
In Firebase: **Project settings > Your apps > com.nook.msgapp > Add fingerprint**, and add both. Then **download
`google-services.json` again** (it now has an `oauth_client` section) and put it in the project root, replacing the
old one. It is gitignored. Without the SHA-1, Google sign-in fails with "developer error 10".

You also need the **Realtime Database URL** in that file. If you created the Realtime Database *after* downloading
`google-services.json`, download it once more so it includes `firebase_url`.

### 4. Cloudinary (photos, voice notes, files, stickers)

1. Sign up at [cloudinary.com](https://cloudinary.com) (Free plan).
2. **Settings > Product environment:** copy the **Cloud name**.
3. **Settings > Upload > Upload presets > Add upload preset:**
   - Signing mode: **Unsigned**
   - Asset folder: `nook`
   - Allowed formats: `jpg,jpeg,png,webp,gif,m4a,mp4,aac,pdf,txt,zip,doc,docx,xls,xlsx,ppt,pptx`
   - Max file size: `10485760` (10 MB)
   - Save, and copy the preset **name**.
4. In `.env`: `EXPO_PUBLIC_CLOUDINARY_CLOUD_NAME` and `EXPO_PUBLIC_CLOUDINARY_UPLOAD_PRESET`.
5. **Settings > API Keys:** keep the **API Key** and **API Secret** for the Worker (step 7). Never put them in `.env`.

### 5. Giphy (GIFs and stickers)

[developers.giphy.com](https://developers.giphy.com) > Create an App > **API** (not SDK) > copy the key into `.env`
as `EXPO_PUBLIC_GIPHY_API_KEY`. The NOOK UI shows the required "Powered by GIPHY" attribution.

### 6. LiveKit (calls)

1. Sign up at [cloud.livekit.io](https://cloud.livekit.io) (free tier) and create a project.
2. **Settings > Keys:** create a key. Copy the **URL** (`wss://…livekit.cloud`), **API Key** and **API Secret** for
   the Worker.

### 7. Cloudflare Worker (push notifications, call tokens, media cleanup)

1. Sign up at [dash.cloudflare.com](https://dash.cloudflare.com) (Free plan).
2. Firebase console > **Project settings > Service accounts > Generate new private key**. This downloads a JSON
   file. Store it outside this folder.
3. In `worker/wrangler.toml` set `LIVEKIT_URL` and `CLOUDINARY_CLOUD_NAME` (both public).
4. Deploy and add secrets:
   ```bash
   cd worker
   npm install
   npx wrangler login
   npx wrangler deploy
   npx wrangler secret put FIREBASE_SERVICE_ACCOUNT   # paste the whole JSON file contents
   npx wrangler secret put CLOUDINARY_API_KEY
   npx wrangler secret put CLOUDINARY_API_SECRET
   npx wrangler secret put LIVEKIT_API_KEY
   npx wrangler secret put LIVEKIT_API_SECRET
   cd ..
   ```
5. `wrangler deploy` prints the URL (`https://nook-worker.<you>.workers.dev`). Put it in `.env` as
   `EXPO_PUBLIC_WORKER_URL`. Opening it in a browser should show `{"ok":true,"service":"nook-worker"}`.

The Worker verifies every request's Firebase ID token, checks chat/call membership, reads Firestore with field masks
(it never even fetches message text), and logs nothing about content.

### 8. Give EAS your config, build, install

`.env` and `google-services.json` are gitignored, so EAS needs them as environment variables:

```bash
npx eas-cli@latest env:set --name GOOGLE_SERVICES_JSON --type file --value ./google-services.json \
  --visibility secret --environment development --environment preview --environment production
# Each line in .env is a public client identifier, so plaintext is fine:
for key in EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID EXPO_PUBLIC_WORKER_URL EXPO_PUBLIC_CLOUDINARY_CLOUD_NAME \
           EXPO_PUBLIC_CLOUDINARY_UPLOAD_PRESET EXPO_PUBLIC_GIPHY_API_KEY; do
  npx eas-cli@latest env:set --name "$key" --value "$(grep "^$key=" .env | cut -d= -f2-)" \
    --visibility plaintext --environment development --environment preview --environment production
done
```

Development build (connects to the dev server on your computer):

```bash
npx eas-cli@latest build --profile development --platform android
npx expo start --dev-client
```

Release APK for your friends (standalone, no computer needed):

```bash
npx eas-cli@latest build --profile production --platform android
```

Install it from the link EAS prints (allow "Install unknown apps"). Everyone in the group installs the same APK.

> **About Google AI Studio:** it builds web apps and can't compile this project. NOOK uses native Android code
> (Firebase, Google sign-in, Keystore, biometrics, WebRTC, the call module), so it has to be built with EAS (free)
> or Android Studio.

## Incoming calls on Android: honest limitations

Calls ring with a **high-priority FCM data message**. The app's background handler turns it into a
**full-screen-intent notification** (ringtone, Answer/Decline, shows over the lock screen) via the local
`modules/nook-call` module. In practice:

- **Force-stopped apps get nothing.** If someone swipes NOOK away and the phone's maker treats that as "force stop",
  or you tap *Force stop* in settings, Android delivers no pushes until NOOK is opened again.
- **Battery savers on some brands** (Xiaomi/MIUI/HyperOS, Oppo/ColorOS, Vivo, Huawei, some Samsung modes) delay or
  drop background pushes. Set NOOK's battery usage to *Unrestricted / No restrictions* and allow *Autostart*.
- **Android 14+** requires the "full-screen notifications" permission. Sideloaded APKs normally have it; if not,
  Account > Notifications shows a button to turn it on. Without it, calls arrive as a heads-up banner instead of a
  full screen.
- **Doze / idle phones:** high-priority FCM is allowed through, but Google may deprioritise apps whose
  high-priority messages don't show a notification; NOOK always shows one.
- **Declining from the notification** stops the ringing immediately but is reported to the caller only the next
  time NOOK opens (the caller sees "No answer" after 40 seconds). Declining from the in-app screen is instant.
- Calls are 1:1. They time out after 40 seconds without an answer.

## Security notes

- **Usernames:** lowercase, 3 to 20 characters, unique and permanent. Claimed in one transaction with the profile.
  The rules prevent stealing, renaming, enumeration and second usernames.
- **Chats:** only members can read or write. Each field change is checked: you can only set your own `lastRead`,
  your own mute and your own reaction. Only the person being called can answer, only the sender can delete a
  message, and a group member can remove only themselves.
- **App lock:** PBKDF2-HMAC-SHA256, 60,000 iterations, 16-byte salt, in `expo-secure-store`. Cooldown after 5 wrong
  tries (30 s doubling to 15 min), which survives restarts. "Hide in app switcher" uses `FLAG_SECURE`, which also
  blocks screenshots while on.
- **Notifications** never contain message text; the Worker never fetches it.
- **Blocking** hides the person's DM and messages, stops their pushes and calls reaching you, and keeps them from
  ringing you. It's enforced in the app and the Worker. The Firestore rules don't check blocks on every message,
  because that would cost a read per message.
- **Secrets:** `.env`, `google-services.json`, keystores, service-account files and `worker/.dev.vars` are gitignored.
  Cloudinary/LiveKit/Firebase secrets live only as Worker secrets.

## Spark plan budget (group of about 6)

Free per day: **50,000 reads, 20,000 writes, 20,000 deletes**, 1 GiB stored. Realtime Database: 100 simultaneous
connections, 1 GB.

How the design keeps usage low:

- Offline persistence on (100 MB cache). Reopening screens is served from the phone.
- **One** chat-list listener (detached while NOOK is in the background) and one tiny private-doc listener. Chats
  load the latest 30 messages live and page older ones on scroll. The message listener exists only while the chat is
  on screen.
- Profiles are fetched once per session (cache first).
- Typing and presence use the Realtime Database, which costs no Firestore quota. The RTDB connection closes in the
  background.
- Read receipts are one `lastRead` write when a chat is opened or scrolled to the bottom (throttled), never per
  message. There are no counters and no analytics writes.
- Disappearing-message cleanup scans at most 25 expired messages per chat open.

Estimate. Assumptions: 6 people, 2 groups + ~10 DMs, **600 messages/day**, each person opening NOOK ~20 times/day,
~5 calls/day.

| | Per day | Share of free quota |
|---|---|---|
| **Writes**: 600 messages + 600 chat previews (same batch) + ~250 `lastRead` + ~150 reactions + ~20 call updates + ~10 tokens/misc | ~1,650 | ~8% of 20,000 |
| **Reads, app**: chat-list updates delivered to other members (~600 × 2) + new messages delivered to open chats (~600 × 2) + cold reopens after the cache goes stale (≤ 6 × 20 × 12) + first opens of chats (~6 × 30 × a few) + profiles, packs, calls | ~6,000 to 10,000 | ~12 to 20% |
| **Reads, Worker**: per message 3 + one per recipient (chat, message, sender, recipients' private docs, all field-masked) | ~3,500 | ~7% |
| **Reads, rules**: one `get(chat)` per message write and per message query | ~1,200 | ~2% |
| **Deletes**: disappearing messages if every chat uses them | up to ~600 | ~3% of 20,000 |
| **RTDB connections** | ≤ 6 | 6% of 100 |
| **Storage**: ~600 messages × ~1 KB/day | ~0.2 GB/year | well under 1 GiB |

Cloudinary's free tier gives 25 monthly credits (roughly 25 GB storage or bandwidth). Voice notes are ~100 KB per
minute and photos are downscaled to 1600 px before upload. LiveKit's free tier includes thousands of participant
minutes per month. Giphy is free with attribution.

The **Debug** screen (Account > Debug, development builds only) counts this phone's real reads, writes and deletes,
so you can check these numbers against your group's real use.

## Scripts

| Command | What it does |
|---|---|
| `npm run check` | typecheck + lint + unit tests |
| `cd firebase && npm test` | rules tests on the emulators (needs Java 21) |
| `cd firebase && npm run deploy:rules` | deploy Firestore + Realtime Database rules and indexes |
| `cd worker && npm test` / `npm run deploy` | Worker tests / deploy |
| `npx expo start --dev-client` | dev server for the development build |
