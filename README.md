<div align="center">

<img src=".github/assets/banner.png" alt="MI Trade Master Banner" width="100%" />

---

<img src=".github/assets/app_icon.png" width="120" height="120" alt="MI Trade Master App Icon" />

# MI Trade Master

### Trading Education & Paper-Trading Companion App

**v8.0.0** · Android · Powered by Firebase + Groq AI

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white)](#)
[![Version](https://img.shields.io/badge/Version-8.0.0-34E39A?style=flat-square)](#)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26-1a7a56?style=flat-square)](#)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-34-1a7a56?style=flat-square)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](#)
[![Firebase](https://img.shields.io/badge/Backend-Firebase-FFCA28?style=flat-square&logo=firebase&logoColor=white)](#)
[![License](https://img.shields.io/badge/License-Proprietary-red?style=flat-square)](./LICENSE)

*A publisher brand of* **Muslim Islam Org**

</div>

---

## 📋 Table of Contents

- [Overview](#overview)
- [Key Features](#-key-features)
- [Tech Stack](#-tech-stack)
- [What Changed from v1](#what-changed-from-v1)
- [Setup Guide](#setup--part-1-firebase-console)
- [Project Structure](#where-things-live-in-firestore)
- [Changelog](#v4-changelog)
- [License](#-license)

---

## Overview

A full learning + trading-education companion app: Firebase multi-method
auth (Email/Google/GitHub/Phone), student profiles, courses with YouTube
lessons and PDF books, a paper-trading practice mode, an on-device chart
analyzer, a Groq-powered support chat, and a monthly subscription with
QR-code payment + admin approval — all controlled from a browser-based
admin panel, no separate backend server required.

> **Educational tool.** All trading features are paper/demo simulations for
> learning purposes. The chart analyzer produces educational observations,
> never guaranteed instructions or financial advice. See [Scope Note](#scope-note-unchanged-from-v1-please-keep).

## ✨ Key Features

| Feature | Description |
|---|---|
| 🔐 **Multi-Auth** | Email/Password, Google, GitHub, Phone/OTP via Firebase |
| 📚 **Courses & Lessons** | YouTube video lessons + PDF books, book-style reader UI |
| 📈 **Practice Mode** | Simulated live paper-trading with animated candles, virtual balance |
| 🧠 **On-Device Analyzer** | Pattern recognition with confidence scoring — no network call |
| 💬 **AI Support Chat** | Groq-powered assistant (text + chart image analysis) |
| 💳 **Subscriptions** | QR-code payment with admin approval workflow |
| 🛠️ **Admin Panel** | Single-file browser dashboard — no backend server required |
| 🔔 **Push Notifications** | FCM with deep-link routing to any in-app screen |
| ⬆️ **Auto-Update** | In-place APK updates via Firestore version control |

## 🧩 Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose, Material 3
- **Backend:** Firebase (Auth, Firestore, Cloud Messaging)
- **Image Hosting:** ImgBB (free-tier friendly, no Firebase Storage/Blaze needed)
- **AI:** Groq API (`llama-3.1-8b-instant` for text, `llama-4-scout-17b` for vision)
- **CI/CD:** GitHub Actions (automated debug APK build + GitHub Release)
- **Admin Panel:** Single-file HTML/JS + Firestore SDK

---

## What changed from v1

- **License-key system removed.** Replaced with Firebase Authentication
  (Email/Password, Google, GitHub, Phone/OTP).
- **Hugging Face Space backend removed.** The chart analyzer is now
  on-device only (Kotlin, no network call needed).
- **Firebase Storage NOT used** (requires the paid Blaze plan). Images
  (profile photos, ID cards, payment screenshots, QR code) are hosted on
  **ImgBB** instead — free, and the URL is stored in Firestore.
- **New admin panel** (`firebase-admin-panel/admin.html`) — a single HTML
  file you can open locally or host anywhere (e.g. Firebase Hosting,
  GitHub Pages) that lets you manage students, approve payments, author
  courses/lessons, and rotate the Groq API key — all without touching code.
- **Overlay panel redesigned**: shows the real app icon, has an explicit
  Close button, and no longer has the old "auto-tap" toggle system.

## Scope note (unchanged from v1, please keep)

The chart analyzer performs general technical-analysis pattern recognition
on swing-style charts (15-minute candles and above) and always frames
results as an observation + confidence level — never a guaranteed
instruction, and never tied to any specific broker.

---

## Setup — Part 1: Firebase Console

### 1. Authentication

Console → **Build → Authentication → Get Started → Sign-in method**, enable:
- **Email/Password**
- **Google** (pick a support email)
- **GitHub** (see step 2 below — needs an OAuth App first)
- **Phone**

### 2. GitHub OAuth App (required for GitHub sign-in)

1. On the Firebase Console's GitHub provider screen, copy the
   **Authorization callback URL** shown (looks like
   `https://YOUR-PROJECT.firebaseapp.com/__/auth/handler`).
2. Go to GitHub → Settings → Developer settings → OAuth Apps → **New OAuth App**.
3. Paste that callback URL into "Authorization callback URL".
4. Copy the generated **Client ID** and **Client Secret** back into the
   Firebase Console's GitHub provider fields → Save.

### 3. Firestore Database (replaces Storage — stays on the free Spark plan)

1. Console → **Build → Firestore Database → Create Database** → Production
   mode → pick a region close to your users.
2. Go to the **Rules** tab, paste the contents of
   `firebase-admin-panel/firestore.rules` from this project, and Publish.

### 4. Create your admin account

1. Console → Authentication → Users → **Add user** → enter your own email
   and a password. This is what you'll use to log into the admin panel.
2. Console → Firestore → **Start collection** → collection ID `admins` →
   document ID: paste the **UID** of the user you just created (found in
   the Authentication users list) → add any field, e.g. `role: "admin"` →
   Save.

### 5. Get your Web app config (for the admin panel)

Console → Project Settings (gear icon) → General → scroll to "Your apps" →
if there's no Web app yet, click **Add app → Web**, name it anything, skip
hosting setup. Copy the `firebaseConfig` object shown.

---

## Setup — Part 2: Admin Panel

1. Open `firebase-admin-panel/admin.html` in a text editor.
2. Find the `firebaseConfig` object near the bottom and replace it with
   the one you copied above.
3. Open the file in a browser (double-click it, or host it anywhere —
   Firebase Hosting, GitHub Pages, Netlify, or just open the local file).
4. Sign in with the admin email/password you created in step 4 above.
5. Go to **Payment QR & Config**:
   - Upload your payment QR code image.
   - Set your subscription price, WhatsApp number, and Groq API key
     (free key from console.groq.com — this is what powers the in-app
     support chat, and you can rotate it here anytime without updating
     the app).
6. Go to **Courses & Lessons** and add your first course + lessons
   (YouTube video ID is just the part after `v=` in a YouTube URL; PDF
   link can be any shareable link, e.g. Google Drive).

## Setup — Part 3: Android app

1. Replace `android-app/app/google-services.sample.json` — get your real
   `google-services.json` from Firebase Console → Project Settings →
   your Android app → download → save it as
   `android-app/app/google-services.json`.
2. In `android-app/app/build.gradle.kts`, find `default_web_client_id` and
   replace the placeholder with your **Web client ID** — Firebase Console
   → Authentication → Sign-in method → Google → Web SDK configuration →
   copy the Web client ID shown there.
3. Build in Android Studio, or push to GitHub and let Actions build it
   (see below).

## Setup — Part 4: GitHub Actions (automatic APK + Release)

1. Push this repo to GitHub.
2. Add a repository secret `GOOGLE_SERVICES_JSON` with the full contents
   of your real `google-services.json` (Settings → Secrets and variables
   → Actions → New repository secret).
3. Push to `main` (or run the workflow manually). It builds a debug APK,
   attaches it to a new GitHub Release, tagged automatically.

---

## First run on a device

1. Install the APK → animated splash (shows your real app icon) → since
   no account exists yet, goes to **Sign In / Sign Up**.
2. Sign up (any method) → fills out the **student profile form** (name,
   father's name, ID card number, address, qualification, photo).
3. Lands on the **payment screen** — shows your QR code. Student pays,
   uploads a screenshot, submits.
4. You (admin) open the admin panel → **Payments** tab → **Approve** →
   student's account is instantly marked active.
5. Next time the student opens the app, they go straight to the main app
   (Home / Analyzer / Learn / Practice / Support Chat / Account / Settings).

## Where things live in Firestore

```
students/{uid}                          — full student profile
courses/{courseId}                      — course metadata
courses/{courseId}/lessons/{lessonId}   — lesson content, YouTube ID, PDF link
payments/{paymentId}                    — payment screenshot submissions
chats/{uid}/messages/{messageId}        — support chat history per student
config/app                              — QR URL, price, WhatsApp #, Groq key
admins/{uid}                            — marks a UID as an admin (console-only)
```

## Known follow-ups

- `ScreenCaptureService` (used by the floating overlay's "Capture &
  Analyze" button) is scaffolded but the actual `ImageReader` +
  `VirtualDisplay` wiring is a `TODO` — it needs a one-time
  MediaProjection consent dialog launched from an Activity. Everything
  else (auth, profile, payment, courses, chat, practice mode, on-device
  analyzer, admin panel) is complete and functional.
- The Groq API key is fetched from Firestore at runtime rather than
  bundled in the APK, as requested — rotate it anytime from the admin
  panel's Config tab.

---

# v4.0 Changelog

## Fixed
- **Returning-user login bug**: logging in previously always sent the user
  back to the profile-setup form, even with an existing profile. Now a
  `PostLoginCheckScreen` checks Firestore first (same logic as splash) and
  routes correctly.
- **Analyzer result not scrollable**: the detailed signal panel could
  overflow off-screen with no way to scroll. Fixed with a proper
  `verticalScroll` wrapper.
- **Screen-capture permission never actually granted**: the floating
  bubble's "Capture & Analyze" had no way to trigger the required
  MediaProjection consent dialog (which can only come from an Activity,
  not a Service). Added `ScreenCaptureConsentActivity` (transparent,
  triggered from the bubble) and wired up full `ImageReader` +
  `VirtualDisplay` capture in `ScreenCaptureService`.
- **YouTube "video unavailable" crash-adjacent dead end**: lesson videos
  that fail to embed (deleted, region-locked, embedding disabled) now show
  a fallback card with an "Open on YouTube" button instead of a broken
  player.
- **Slow splash screen**: the profile/auth check used to run *after* a
  fixed animation delay (sequential). Now both run concurrently via
  `coroutineScope` + `awaitAll`, cutting typical wait time roughly in half.
- **GitHub sign-in errors**: added explicit scopes (`read:user`,
  `user:email`), pending-result reuse (in case the user backgrounds the
  app mid-flow), and specific error messages for the two most common
  failure modes (account-collision, callback URL mismatch).

## Added
- **Editable account profile**: Account screen now has an edit mode (photo
  re-upload included) instead of being read-only.
- **Live-feeling Practice mode**: an animated ticking price line and a
  brief "Trade executing..." state make the demo chart feel like a live
  platform, plus configurable starting balance / per-trade amount / target
  (all virtual, clearly labeled DEMO).
- **Richer Analyzer**: explicit "Educational Lean: UP/DOWN/Neutral"
  statement, trend-strength bar, and a signal breakdown list — still
  framed as observation + confidence, never a guaranteed instruction.
- **Home screen announcements**: admin-authored announcement cards
  (bilingual) shown in a horizontal carousel, managed from the new
  Announcements tab in the admin panel.
- **Book-style lesson viewer**: lesson text now renders as serif-typeset
  paragraphs inside a page-like card, with a chapter counter and
  Previous/Next navigation between lessons.
- **Full permissions section in Settings**: live status + one-tap access
  for Display Over Other Apps, Notifications, and Screen Capture.
- **Push notifications with deep links**: `MitvMessagingService` handles
  incoming FCM messages and routes a notification tap straight to a named
  screen (lessons, analyzer, practice, chat, account, settings) via a
  `deepLink` data field.
- **Auto-update system**: on launch, the app checks
  `config/app_version` in Firestore; if a newer `versionCode` is published
  with `forceUpdate: true`, the user sees a blocking update screen that
  downloads the new APK (via DownloadManager, with progress) and launches
  the system install prompt. Because installs use the SAME persisted debug
  keystore (see `DEBUG_KEYSTORE_BASE64` secret), this is a normal in-place
  app update — accounts, local settings, and data are preserved, nothing
  is uninstalled.
- **AI chat image upload**: students can attach a chart screenshot in the
  AI Assistant chat. Text-only messages use `llama-3.1-8b-instant`;
  messages with an image automatically switch to Groq's vision model
  `meta-llama/llama-4-scout-17b-16e-instruct` (current as of mid-2026) for
  chart-pattern discussion — same educational framing as the Analyzer tab.

## New Firestore collections/documents
```
announcements/{id}          — admin-authored home screen announcements
config/app_version          — auto-update metadata (versionCode, apkUrl, forceUpdate, releaseNotes)
students/{uid}.fcmToken     — push notification target token (auto-saved on token refresh)
```

## New admin panel tabs
- **Announcements** — post/delete bilingual announcements shown on Home
- **App Update** — publish new version info (version code/name, APK URL,
  release notes, force-update toggle) after every GitHub Actions release

## Setup notes specific to v4
1. **Auto-update requires the persisted debug keystore** you already set
   up (`DEBUG_KEYSTORE_BASE64` secret) — this is what makes every build
   installable as an *update* rather than requiring uninstall/reinstall.
   Keep using that same secret for all future builds.
2. After each new release, go to the admin panel's **App Update** tab and
   fill in the new `versionCode` (must be higher than the last one), the
   GitHub Release APK URL, and toggle **Force update** if you want to
   require it immediately.
3. **Push notifications**: posting an announcement in the admin panel
   makes it appear in-app immediately (via Firestore). Actually *pushing*
   a notification (so it appears even when the app is closed) requires a
   small server-side call to the FCM Admin SDK with your Firebase Server
   Key — this panel intentionally does not embed that key client-side for
   security. A small Cloud Function (a few lines) is the standard way to
   do this if you want push banners in addition to in-app announcements.

---

## 📄 License

**Copyright © 2026 Muslim Islam Org. All Rights Reserved.**

This project and its source code, assets, and documentation are
proprietary and confidential. Unauthorized copying, modification,
distribution, public display, or use of this software, via any medium,
is strictly prohibited without prior written permission from the
copyright holder.

See the [LICENSE](./LICENSE) file for full terms.

<div align="center">

Made with 🖤 by **Muslim Islam Org**

</div>
