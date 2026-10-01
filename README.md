# Serava Companion v0.9 — Release Candidate Engineering

**Serava** is an original adult fantasy virtual companion for Android. The app combines a 2D layered avatar, local memory, text chat, Realtime voice, interruption/barge-in, emotional motion cues, viseme mouth animation, and a secure backend that keeps permanent provider credentials out of the APK.

v0.9 deliberately freezes major feature growth and focuses on producing a buildable, signed, verifiable Android APK.

## Current companion features

- Arabic-first text conversation with local fallback.
- Secure backend Responses path; no permanent OpenAI key in Android.
- Realtime PCM 24 kHz voice via short-lived server-issued client credentials.
- semantic VAD and user interruption.
- conversation truncation after interruption so unheard assistant audio is not retained as heard context.
- automatic Realtime reconnect before legacy voice fallback.
- 2D avatar motion: blink, breathing, eye glow, blush, head tilt and emotion cues.
- 8 mouth visemes: silence, rest, MBP, FV, EE, AA, OH, OO.
- local conversation memory and explicit long-term facts.
- password-protected AES-GCM memory export/import.
- voice/realtime diagnostics and low-power mode.

## v0.9 release hardening

- `versionCode 9`, `versionName 0.9.0`.
- AGP 9.4.0 + built-in Kotlin.
- Compose compiler plugin 2.4.10.
- Compose BOM 2026.09.00.
- Java 17 / Gradle 9.6.0 / compile+target API 37.
- R8 minification and resource shrinking on Release.
- Release signing comes only from environment/GitHub Secrets.
- cleartext network traffic disabled; system CA trust only.
- debug app ID `com.serava.companion.debug` can coexist with release `com.serava.companion`.
- CI verifies package ID, zip alignment and APK signatures and preserves `mapping.txt`.

## Backend

Production environment example:

```bash
OPENAI_API_KEY=...
SERAVA_SAFETY_SALT=replace-with-long-random-secret
SERAVA_APP_TOKEN=optional-shared-app-token
SERAVA_DEFAULT_MODEL=gpt-6-luna
SERAVA_ALLOWED_MODELS=gpt-6-luna
SERAVA_REALTIME_MODEL=gpt-realtime-2.1
SERAVA_REALTIME_VOICE=marin
PORT=8787
```

Run:

```bash
cd backend
node server.mjs
```

The phone should connect only to an HTTPS deployment of this backend.

## Build gates

Run locally before pushing when tools are available:

```bash
./scripts/release-preflight.sh
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

GitHub Actions includes two workflows:

- **Build Serava APK** → verified installable debug APK.
- **Build Signed Serava Release** → R8-shrunk, signed, zipaligned and verified release APK.

Read `RELEASE_CHECKLIST.md` before calling any artifact a release candidate.

## Expected artifacts

Debug:

`Serava-v0.9-debug-apk`

Signed release:

`Serava-v0.9-signed-release/Serava-v0.9-release.apk`

## Current limitation

The source package is release-hardened, but a physical Android device remains the authority for installation, microphone, Realtime audio, interruption, battery and thermal behavior. v0.10 is reserved for fixing findings from the first real v0.9 APK build/device pass.
