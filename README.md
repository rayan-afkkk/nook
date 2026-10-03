# NOOK

A private chat app for you and your friend group. Android first, built with Expo (React Native, TypeScript) on Firebase's free **Spark** plan.

> **NOOK is not end-to-end encrypted.** Messages, profiles and chat details are stored in Google Firebase. They are
> encrypted in transit and at rest by Google, and security rules only let chat members read a chat, but anyone who
> administers the Firebase project could technically read them.

## Status

| Phase | What | State |
|---|---|---|
| 1 | Design system, animated onboarding, Google sign-in, username, app lock, tab shell | **Done** (waiting for on-device test) |
| 2 | DMs, groups, add by username, text, replies, reactions, photos, files, rules + tests | Not started |
| 3 | Cloudflare Worker, push notifications, seen, typing, presence | Not started |
| 4 | Voice notes, GIFs, stickers, disappearing messages | Not started |
| 5 | Calls (LiveKit) | Not started |
| 6 | Polish, performance, release APK | Not started |

What was verified for Phase 1 (in a Linux container without Android tooling):

- `npm run typecheck` (TypeScript strict), `npm run lint` (ESLint with the React Compiler rules) and `npm test` (27 unit tests) pass.
- `cd firebase && npm test`: 29 security-rules tests pass against the Firestore and Realtime Database emulators.
- `npx expo export --platform android` bundles the app, and `npx expo prebuild --platform android` generates the native project (package `com.nook.msgapp`, Google services plugin applied, permissions as expected).
- The screens were rendered in a browser with Firebase stubbed out, to check layout and theming.
- **Not verified:** a native Gradle build, and anything running on a real phone. Google's Maven repository was blocked in the build environment, so no APK has been built yet. Your first EAS build is the first real native build.

## Tech

Expo SDK 57 · React Native 0.86 (New Architecture) · Expo Router · TypeScript strict · React Native Firebase 26 (app, auth, firestore, database, messaging) · `@react-native-google-signin/google-signin` · Reanimated 4 + Worklets · react-native-svg · Zustand · expo-secure-store · expo-local-authentication · expo-screen-capture · Instrument Serif + DM Sans (`@expo-google-fonts`) · Ionicons (outline/filled).

No Firebase Cloud Functions and no Firebase Storage (neither is available on Spark). Later phases add one Cloudflare Worker (push notifications, LiveKit tokens, Cloudinary cleanup) and Cloudinary for media.

## Project layout

```
app.config.ts            Expo config (package com.nook.msgapp, plugins, permissions)
eas.json                 EAS build profiles (all produce an APK)
assets/                  App icon, adaptive icon, splash, notification icon (original NOOK mark)
src/
  app/                   Routes (Expo Router)
    _layout.tsx          Providers, fonts, splash, auth/profile listeners, lock overlay
    index.tsx            Sends you to the right first-run step
    (onboarding)/        welcome (animated intro), sign-in
    (setup)/             username, permissions, set-lock
    (app)/(tabs)/        chats, friends, stickers, calls, account
    (app)/settings/      app-lock, notifications, disappearing, blocked, help
    (app)/intro.tsx      Account > View Onboarding
    (app)/debug.tsx      Firestore read/write counter (dev builds only)
    privacy.tsx          Privacy & Terms
  theme/                 Design tokens (colours, type, spacing, radii, motion) and ThemeProvider
  components/ui/         Button, Card, Header, SegmentedControl, ListRow, EmptyState, DashedCard,
                         Avatar, Skeleton, ProgressBar, TextField, Sheet, Toast, OfflineBanner ...
  components/brand/      Animated logo
  features/
    onboarding/          Pager, parallax, progress pill, glow, shimmer button, 4 illustrations
    auth/                Google sign-in, session store, first-run state machine
    profile/             Username rules, claim transaction, exact-username lookup
    lock/                PBKDF2 lock, PIN pad, lock screen, background timer, app-switcher privacy
    permissions/         Permission primer
    account/             Local cache size and clearing
  lib/                   Firebase singletons + dev metrics, haptics, formatting, errors
  stores/prefs.ts        Device preferences (appearance, onboarding seen, ...)
firebase/                Firestore and Realtime Database rules, emulator tests, deploy scripts
```

## Setup

You need: Node 22+, a free [Expo](https://expo.dev) account (for EAS builds), the Firebase project you created, and an Android phone. Java 21 is only needed to run the rules tests.

### 1. Install

```bash
npm install
cp .env.example .env
```

### 2. Firebase project (`nook-39914`)

You already added the Android app `com.nook.msgapp`. In the [Firebase console](https://console.firebase.google.com/project/nook-39914):

1. **Authentication > Get started > Sign-in method > Google > Enable.** Pick a support email and save.
   Open the Google provider again and expand **Web SDK configuration**. Copy the **Web client ID** (it ends in
   `.apps.googleusercontent.com`) into `.env` as `EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID`.
2. **Firestore Database > Create database**, production mode, in a region near you (it can't be changed later).
3. Deploy the security rules:
   ```bash
   cd firebase
   npm install
   npx firebase login
   npm run deploy:rules
   ```
   (Realtime Database is created in Phase 3; its rules are deployed then.)

### 3. Link the project to EAS

```bash
npx eas-cli@latest login
npx eas-cli@latest init
```

`eas init` creates the project on expo.dev and prints a **project ID**. Because the config is `app.config.ts`, it asks
you to add the ID yourself: paste it into `EAS_PROJECT_ID` at the top of `app.config.ts`. The ID is not a secret.

### 4. SHA-1 fingerprints (required for Google sign-in)

Google sign-in on Android only works if the SHA-1 of the key that **signed the APK** is registered in Firebase. Without it, sign-in fails with "developer error 10". You don't need Android Studio for this:

1. Create the build credentials once: `npx eas-cli@latest credentials -p android`. Choose the `development` profile, then *Keystore > Set up a new keystore*. It prints the **SHA-1** (and SHA-256). Builds from every profile use this keystore unless you create others.
2. Firebase console > **Project settings > Your apps > com.nook.msgapp > Add fingerprint**. Paste the SHA-1 (and the SHA-256 too).
3. If you ever add another keystore, register its SHA-1 too. If you later publish on Google Play, also add the **App signing key** SHA-1 from the Play Console.
4. Download `google-services.json` again from the same page. It now contains an `oauth_client` section. Put it in the project root, replacing the old one. It is gitignored and must never be committed.

Then give EAS the two things it can't see in git (`google-services.json` and `.env` are gitignored):

```bash
npx eas-cli@latest env:set --name GOOGLE_SERVICES_JSON --type file --value ./google-services.json \
  --visibility secret --environment development --environment preview --environment production
npx eas-cli@latest env:set --name EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID --value "<your web client id>" \
  --visibility plaintext --environment development --environment preview --environment production
```

### 5. Build and run on your phone

```bash
npx eas-cli@latest build --profile development --platform android
```

Install the APK from the link EAS gives you (allow "install unknown apps"), then start the dev server on your computer and open NOOK on the phone:

```bash
npx expo start --dev-client
```

A standalone APK that doesn't need your computer: `npx eas-cli@latest build --profile preview --platform android`.

> **About Google AI Studio:** AI Studio builds web apps. It can't compile this project: React Native Firebase, Google
> sign-in, secure storage and biometrics are native Android code, so the app has to be built with EAS (free tier) or
> Android Studio. You can still read and edit the code anywhere.

## Scripts

| Command | What it does |
|---|---|
| `npm run typecheck` | TypeScript, strict |
| `npm run lint` | ESLint (Expo config, React Compiler rules) |
| `npm test` | Unit tests (lock hashing, username rules, first-run flow) |
| `npm run check` | All three |
| `cd firebase && npm test` | Security rules tests on the Firebase emulators (needs Java) |
| `cd firebase && npm run deploy:rules` | Deploy Firestore rules and indexes |

## Security notes

- **Sign-in:** Google only. Usernames are lowercase, 3 to 20 characters, unique and permanent. They are claimed in one
  transaction together with the profile. The rules enforce one username per account, no overwriting or stealing, no
  renaming, no enumeration (only exact-name `get`, never `list`), and release only on account deletion.
- **App lock:** your PIN or password (at least 6 characters) is never stored. It is turned into PBKDF2-HMAC-SHA256
  (60,000 iterations, 16-byte random salt), and the result is kept in `expo-secure-store` (Android Keystore). After 5
  wrong tries there is a growing cooldown (30 s doubling up to 15 min) that survives restarts. NOOK locks on cold
  start and after 30 s in the background. Fingerprint unlock is optional. "Forgot" signs you out, which clears the
  lock.
- **App switcher:** "Hide in app switcher" (on by default) sets Android's `FLAG_SECURE`, so the recent-apps preview is
  blank. Android ties this to screenshot blocking, so screenshots of NOOK are blocked while it's on.
- **Secrets:** `.env`, `google-services.json`, keystores and service-account files are gitignored. Nothing in this
  repo is a secret. The Firebase project id in `firebase/.firebaserc` is not one.

## Spark plan budget (group of about 6)

Free quota per day: **50,000 reads, 20,000 writes, 20,000 deletes**, 1 GiB stored; Realtime Database: 100 simultaneous connections.

How the design keeps usage low:

- Offline persistence is on (100 MB cache), so reopening a screen is served from the phone's cache.
- One profile listener and (from Phase 2) one chat-list listener. Each chat loads its latest 30 messages and pages
  older ones on scroll. Listeners are detached when screens lose focus.
- Typing and online status go in the Realtime Database with `onDisconnect`, never Firestore.
- Read receipts are one `lastRead` timestamp per member on the chat doc, written when a chat is opened or scrolled to
  the bottom. There are no per-message seen/delivered writes and no counter or analytics writes.
- Username availability checks are debounced: one read per pause in typing.

Estimate for the finished app. Assumptions: 6 people, 2 groups and ~10 DMs, **600 messages a day** across the group,
each person opening NOOK ~20 times a day.

| Operation | Per day | Share of free quota |
|---|---|---|
| Writes: 600 messages + 600 chat-doc "last message" updates + ~250 `lastRead` updates + ~150 reactions/edits | ~1,600 | ~8% of 20,000 |
| Reads: chat-list updates delivered to other members (~600 × 2) + messages delivered to open chats (~600 × 2) + cold reopens after the cache expires (worst case ~6 × 20 × 15 docs) + opening chats (~6 × 40 × a few new messages) | ~6,000 to 12,000 | ~12 to 25% of 50,000 |
| Deletes: disappearing messages, if every chat uses them | up to ~600 | ~3% of 20,000 |
| Realtime Database connections | 6 | 6% of 100 |

Phase 1 alone uses about 1 read per app start (profile), 1 read per username check and 2 reads per friend lookup.
The **Debug** screen (Account > Debug, dev builds only) counts this phone's real reads, writes and deletes, so you can
check these numbers against your group's actual use.
