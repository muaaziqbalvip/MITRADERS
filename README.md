# MI Trade Master v2.0

A full learning + trading-education companion app: Firebase multi-method
auth (Email/Google/GitHub/Phone), student profiles, courses with YouTube
lessons and PDF books, a paper-trading practice mode, an on-device chart
analyzer, a Groq-powered support chat, and a monthly subscription with
QR-code payment + admin approval — all controlled from a browser-based
admin panel, no separate backend server required.

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
