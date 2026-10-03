# NOOK for Android (Kotlin + Jetpack Compose)

The native Android version of NOOK. It's the same app as the Expo version in the repo root, rewritten in Kotlin with
Jetpack Compose. It uses the same backend, so it works with your existing Firebase project (`nook-39914`), rules,
Cloudflare Worker, Cloudinary, Giphy and LiveKit. Chats are shared: the Expo build and this build can talk to each
other.

- Package: `com.nook.msgapp` · minSdk 26 (Android 8) · targetSdk 35
- Kotlin 2.1, AGP 8.7, Gradle 8.11 (wrapper included), Compose BOM 2024.12
- Firebase Auth / Firestore / Realtime Database / Messaging, Credential Manager (Google sign-in), Coil (images and
  GIFs), OkHttp (uploads, Worker), LiveKit (calls), AndroidX Biometric (fingerprint unlock)

## Open / build

Open this `nook-android` folder (not the repo root) in Android Studio, or import it in Google AI Studio's
"build native Android app" flow. Then build:

```bash
./gradlew :app:assembleDebug     # APK in app/build/outputs/apk/debug/
./gradlew :core:test             # unit tests for the pure-Kotlin logic
```

Release builds (`./gradlew :app:bundleRelease`) are signed with your **upload key**. See "Play Console" below.

## Before the first build: 3 things

1. **`app/google-services.json`.** Download it from Firebase (**Project settings > Your apps > com.nook.msgapp**) and
   put it at `nook-android/app/google-services.json`. It is gitignored. The build fails without it.
2. **Keys in `gradle.properties`.** These are public client values, not secrets:
   ```properties
   nook.workerUrl=https://nook-worker.<you>.workers.dev
   nook.cloudinaryCloudName=...
   nook.cloudinaryUploadPreset=...
   nook.giphyApiKey=...
   nook.googleWebClientId=904247852572-...apps.googleusercontent.com   # already filled in
   ```
   You can also put them in `local.properties`, which is gitignored and takes priority. If a key is empty, the app
   still runs and that feature shows a "not set up yet" message.
3. **The SHA-1 of the key that signs the APK** must be added in Firebase (**Project settings > Your apps >
   com.nook.msgapp > Add fingerprint**). Without it, Google sign-in fails with "developer error 10". Run
   `./gradlew signingReport` and copy the `SHA1` of the `debug` variant. If AI Studio signs the app with its own key,
   use that key's SHA-1. After adding it, download `google-services.json` again.

To set up the backend (Firebase, Cloudinary preset, Giphy key, LiveKit, Cloudflare Worker), follow steps 2 and 4–7
of the [root README](../README.md#setup). Nothing there changes for the Kotlin app.

## Play Console

### 1. Make an upload key (once, keep it safe forever)

```bash
keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

Put `upload.jks` in `nook-android/` and create `nook-android/keystore.properties`. Both are gitignored:

```properties
storeFile=upload.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

Then `./gradlew :app:bundleRelease` gives you `app/build/outputs/bundle/release/app-release.aab`. Upload that to Play.

To have GitHub build the `.aab` instead, add these repo secrets: `NOOK_KEYSTORE_BASE64` (output of
`base64 -w0 upload.jks`), `NOOK_KEYSTORE_PASSWORD`, `NOOK_KEY_ALIAS` and `NOOK_KEY_PASSWORD`. Each Actions run then
uploads a signed `nook-release-aab` with an increasing version code.

### 2. Firebase fingerprints for Play

Play re-signs the app with its own **app signing key**. In Play Console, go to **Test and release > App integrity >
App signing**. Copy the SHA-1 and SHA-256 of both the app signing key and the upload key, and add all of them in
Firebase. Then download `google-services.json` again. If you skip this, Google sign-in fails for Play installs.

### 3. What the Console will ask

| Item | What to enter |
|---|---|
| Privacy policy URL | Required. Host the text from the in-app Privacy screen (e.g. on GitHub Pages or Google Sites). |
| Data safety | Collected: name, email, user ID (Google sign-in); messages, photos, audio, files (app functionality); device push token. Encrypted in transit: yes. Deletion: yes, in-app (Account > Delete account). Not shared with third parties for ads. Not end-to-end encrypted. |
| Account deletion | In-app path: Account > Delete account. Play also wants a web link or form for deletion requests; a Google Form works. |
| Foreground service | Type `microphone` / `camera`: "keeps an ongoing voice or video call running while the app is in the background". |
| Full-screen intent | The app makes calls: "shows the incoming call screen". |
| Content rating | Communication app with user-generated content and messaging between users. |
| Target audience | 13+ (or 18+). User chat means no "designed for children". |
| App access | Google sign-in is required. Give reviewers a test Google account, or explain that any Google account works. |

targetSdk is 35, which matches Play's current requirement. Release builds use R8 (minify and resource shrinking).

## Layout

```
core/                      Pure Kotlin (no Android): models, chat logic, usernames, app-lock crypto, first-run flow
  src/test/                JUnit tests (19 tests)
app/src/main/java/com/nook/msgapp/
  NookApp.kt               Firebase init (offline cache), prefs, lock, channels, foreground/background hooks
  MainActivity.kt          Single activity, edge-to-edge, themed system bars, FLAG_SECURE, deep links
  data/                    Firebase, Session, Chats, Profiles, Calls, Stickers, Presence (RTDB), Outbox (uploads),
                           Cloudinary, Giphy, Worker client, Auth (Credential Manager), Prefs, Toasts
  lock/                    App lock store (PBKDF2 hash only) and biometric prompt
  push/                    FCM service, notification channels, full-screen incoming call, Decline action
  calls/                   CallController (LiveKit room + Firestore call record), foreground CallService, VideoView
  nav/                     Routes and notification deep links
  ui/theme/                NOOK design tokens: palette, Instrument Serif + DM Sans, spacing, radius, fixed font scale
  ui/components/           Buttons, cards, rows, avatar, text field, toggle, segmented control, toast, pickers, logo
  ui/onboarding/ setup/ lock/ home/ chat/ chatinfo/ groups/ stickers/ settings/ calls/   The screens
```

## How it maps to the backend

Same data model as the Expo app (see the root README): `users`, `usernames`, `userPrivate`, `chats/{id}/messages`,
`calls`, `stickerPacks` in Firestore, and `status/{uid}` and `typing/{chatId}/{uid}` in the Realtime Database. Media is
uploaded unsigned to Cloudinary. The Worker sends pushes (`/notify`, `/call/invite`, `/call/cancel`), deletes media
(`/messages/delete`) and issues LiveKit tokens (`/call/token`).

## Design notes

- Text size is fixed: the app ignores the phone's font-size setting (`fontScale = 1f` in `NookTheme`).
- Every Material colour slot is mapped to a NOOK token, so no default purple or blue appears anywhere. The status and
  navigation bars follow the theme.
- The app lock relocks after 30 s in the background. Opening the photo, file or camera picker doesn't count as
  leaving the app.

## Verification status

`core` compiles, and its 19 unit tests pass on the JVM. The Android module could not be compiled in the environment
it was written in, because Google's Maven repository was blocked there. So the first real build is in Android Studio
or AI Studio. If something fails to compile, paste the error and it's a quick fix.
