# Adamantia

Adamantia is a minimal Android app that turns a local HTML file into a
standalone app with the ability to keep running a background task even
while the app isn't in the foreground. It's aimed at people who have a
self-contained HTML/JS tool and want to run it on a phone with persistent
execution, without building a full native app.

Today it's single-file: it loads one HTML file at a time and only
remembers the last one you opened. A multi-tab redesign is in progress
(multiple simultaneously-loaded HTML files, each an independent tab with
its own background task and notification) — see **Development** below.

## What it does

- Opens a file picker on first launch so you can select a local `.html`
  file from your device storage.
- Loads that file into a full-screen WebView (JavaScript, DOM storage,
  and local file access are all enabled).
- Remembers the last file you opened and reloads it automatically the
  next time you start the app. There's currently no in-app tab list —
  only the single most-recently-opened file is restored.
- Exposes a small JavaScript bridge (`AndroidBackground`) so the page you
  loaded can start or stop an Android foreground service that keeps
  running in the background.

## Getting the APK

This repo builds the app automatically via GitHub Actions.

1. Push to the repo (or trigger the workflow manually) — this runs
   `.github/workflows/build.yml`.
2. Open the **Actions** tab on GitHub, select the latest **Build APK**
   run, and download the `app-debug` artifact.
3. Unzip it to get `app-debug.apk`.

You can also build it yourself locally with Android Studio or the
Gradle wrapper:

```bash
./gradlew assembleDebug
```

The output APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Installing on your device

1. Copy `app-debug.apk` to your Android device.
2. Enable "Install unknown apps" for the file manager or browser you use
   to open it (Settings → Apps → Special access → Install unknown apps).
3. Tap the APK to install. Since this is a debug build, Android will
   flag it as unverified — that's expected for a self-built app.

## Using the app

1. Launch **Adamantia**. On first run it opens the system file picker —
   choose the HTML file you want to run.
2. The app grants itself persistent read access to that file, so it will
   reopen the same file automatically on future launches.
3. To load a different file, clear the app's storage (Settings → Apps →
   Adamantia → Storage → Clear data) or add a way to reopen the picker
   from your HTML page.

### Background task control

Your HTML/JS page can call the following from a `<script>` tag:

```javascript
// Start the persistent background service
AndroidBackground.startBackgroundTask();

// Stop it
AndroidBackground.stopBackgroundTask();
```

Once started, the service shows a persistent notification ("Adamantia
Active") and keeps running even if you switch away from the app or lock
the screen. This is intended for tasks like periodic data fetching —
customize the loop in `MyBackgroundService.kt` to do the work you need.

### Permissions

The app declares:

- **Internet** — for the WebView and any network calls your HTML page
  makes.
- **Foreground service / data sync** — required by Android to keep the
  background task alive and visible to the user via a notification.
- **Post notifications** — required on Android 13+ to show the
  foreground service notification. ⚠️ The app currently only *declares*
  this permission in the manifest; it never requests it at runtime. On
  Android 13+ that means the "Adamantia Active" notification can
  silently fail to appear the first time a background task starts,
  with no prompt and no error — the user has to grant it manually via
  Settings → Apps → Adamantia → Notifications. Requesting it properly
  (e.g. via `ActivityResultContracts.RequestPermission`) is an open fix.
- **Read external storage** — declared for API 32 and below, but
  nothing in the codebase reads raw external storage paths (no
  `Environment`/`MediaStore`/`File` access outside the app's own
  sandbox). File access goes entirely through the SAF file picker and
  persisted URI grants, which don't need this permission on any API
  level — it looks unused and may be safe to drop.

## Project structure

```
app/src/main/java/com/peng/adamantia/
  MainActivity.kt          # WebView host, file picker, last-opened-file persistence
  WebAppInterface.kt        # JS bridge exposed as `AndroidBackground`
  MyBackgroundService.kt    # Foreground service for background work
  spike/SpikeActivity.kt    # Dev-only test harness, see Development below
app/src/main/AndroidManifest.xml
.github/workflows/build.yml # CI: builds the debug APK on every push
```

## Development

Adamantia is being extended into a multi-tab browser (multiple loaded
HTML files, each with its own independent background task and
notification). `spike/SpikeActivity.kt` is a throwaway test harness used
to validate risky assumptions on a real device before building the full
feature — it has no launcher icon and isn't part of the app's normal UI.

To run it:

```bash
adb shell am start -n com.peng.adamantia/.spike.SpikeActivity
adb logcat -s AdamantiaSpike
```

It loads two WebViews (one hidden via `View.GONE`) that each log a tick
every 2 seconds, plus a button to check whether both WebView instances
share `localStorage` for the same `file://` URL. This answers two
questions the multi-tab design depends on:

1. Does a hidden-but-attached WebView keep running its JavaScript while
   the app is foregrounded?
2. Do two tabs loading the same local HTML file share browser storage?

Delete `spike/SpikeActivity.kt` and its manifest entry once the
multi-tab feature is built and these questions are answered — it isn't
meant to ship long-term.

## Notes for customization

- Background work logic lives in `MyBackgroundService.performBackgroundWork()`
  — replace the placeholder loop with your actual task.
- The WebView allows universal access from file URLs so local HTML pages
  can make cross-origin requests; keep this in mind if you load untrusted
  content.
