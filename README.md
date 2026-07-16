# MI Trade Master

A trading-education companion app: chart pattern analysis (offline + online),
a floating on-screen analyzer bubble, license-gated access, and a learning
section — built with Kotlin/Jetpack Compose (Android) and FastAPI (Hugging
Face Space backend).

## Scope & framing (please read before extending)

This app performs **general technical-analysis pattern recognition** on
swing-style charts (15-minute candles and above): trend direction, basic
candlestick pattern tags, and support/resistance proximity. Every result is
shown as an **observation + historical lean + confidence level** — never a
guaranteed instruction, and never tied to sub-minute/binary-option style
expiries or any specific broker. Please keep new features within that
framing; it's a deliberate choice, not a technical limitation.

## Project structure

```
mi-trade-master/
├── android-app/            # Kotlin/Compose Android app
│   └── app/src/main/java/com/mitv/trademaster/
│       ├── ui/screens/      # Splash, License, Home, Analyzer, Learn, Account, Settings
│       ├── overlay/         # Floating bubble + screen capture services
│       ├── analysis/        # On-device (offline) chart analyzer
│       ├── network/         # Retrofit API client
│       └── data/            # License repository (DataStore-backed)
├── huggingface-space/       # FastAPI backend (license system + online analysis)
│   ├── app.py
│   ├── analysis_engine.py
│   ├── templates/admin.html # License key admin console
│   └── Dockerfile
└── .github/workflows/
    └── build-apk.yml        # Builds a debug APK on every push
```

## Setup — Hugging Face Space (backend)

1. Create a new Space → SDK: **Docker** → upload everything in
   `huggingface-space/`.
2. In **Settings → Repository secrets**, add `MITV_ADMIN_PASSWORD` (pick a
   strong password — this protects your license admin console).
3. Once the Space is running, open its URL directly in a browser to reach
   the admin console and issue your first license key.

## Setup — Android app

1. Open `android-app/` in Android Studio (Jellyfish or newer).
2. In `app/build.gradle.kts`, replace the placeholder in
   `API_BASE_URL` with your actual Hugging Face Space URL, e.g.
   `https://your-username-mi-trade-master.hf.space/`.
3. (Optional but recommended) Replace `app/google-services.sample.json`
   with a real `app/google-services.json` from your Firebase project if you
   want Firestore/Auth/Messaging features to work — copy it to
   `app/google-services.json`.
4. Run on a device/emulator, or let GitHub Actions build it for you (see
   below).

## Setup — GitHub Actions (automatic APK builds)

1. Push this whole repo to GitHub.
2. If you have a real `google-services.json`, add its full contents as a
   repository secret named `GOOGLE_SERVICES_JSON`
   (Settings → Secrets and variables → Actions → New repository secret).
   If you skip this, the build falls back to a placeholder Firebase config
   so the app still compiles — Firebase features just won't work until you
   add a real one.
3. Push to `main` (or run the workflow manually from the Actions tab). The
   resulting APK appears as a downloadable artifact on the workflow run.

## First run on a device

1. Install the APK, open the app → animated splash → license activation
   screen.
2. Enter the license key you generated from the admin console.
3. From Home, tap **Start Floating Analyzer** — you'll be asked to grant
   the "draw over other apps" permission once.
4. Use the Analyzer tab for a full-screen analysis, or the floating bubble
   for a quick check while in another app.

## Known limitation to finish yourself

`ScreenCaptureService` is scaffolded but the actual
`ImageReader` + `VirtualDisplay` wiring (to continuously capture frames
from the MediaProjection session) is left as a `TODO` — MediaProjection
requires a one-time user-consent dialog launched from an Activity, which
needs a small activity-result flow. Everything else (bubble UI, on-device
analyzer, online analyzer, license system, admin panel) is complete and
functional.
