# P.A. — personal assistant for Android

Native Kotlin / Jetpack Compose / Material 3. Android 10+ (minSdk 29), compile/target API 37, single activity, Room and Preferences DataStore. Personal sideloaded app for Tom. No account, backend, analytics, Internet permission or accessibility service. Android cloud backup is disabled.

**Stage 1 checkpoint:** project scaffold, release CI and alarm foundation. Feature tabs have deliberately not been built; approval is required before starting them.

## What works in this stage

- Alarm readiness/setup screen and alarm-only Settings, with versionName and build number visible.
- Exact `AlarmManager.setAlarmClock()` dispatch through an explicit immutable PendingIntent, with a persisted Room queue.
- Notification and alarm styles; full-screen intent when permitted; alarm-volume sound and vibration; Done, Snooze and explicit Dismiss.
- First check re-alert after 10 minutes; then every 5 minutes. Default snooze: 5 minutes. Intervals and check times editable.
- Eight default checks: 09:45 and 10:00 through 16:00. Only Wed/Thu/Fri and alternate Sat/Sun. Working anchor 31 October 2026; 24–25 October off.
- Enable checks explicitly during setup. A separate 20-second alarm/notification test works without enabling the rota.
- Rebuild queue after reboot (after first unlock), clock/timezone changes, app replacement and exact-alarm permission grant. Resume also refreshes settings and queue.
- Done timestamps and action history in Room. No automation of other apps.
- Gmail compose nudge opens a draft for the user to address and send; no email is sent by P.A.
- Pure Kotlin rota, pay and scheduling policy with unit tests, including both DST transitions.

See [architecture](docs/ARCHITECTURE.md), [device acceptance checks](docs/DEVICE-TESTS.md) and [remaining v1 scope](docs/V1-SCOPE.md).

## Build locally

Install JDK 17 and Android Studio with Android SDK Platform 37.0 and Build Tools 36.0.0. The included Gradle 9.3.1 wrapper is checksum-pinned. SDK location comes from `ANDROID_HOME` or an untracked `local.properties`.

```bash
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Install `app/build/outputs/apk/debug/app-debug.apk` or run `adb install -r` with that path. Debug uses `uk.co.tommulliner.pa.debug`, so it can coexist with the release app. Enable the installing browser/file manager's “Install unknown apps” access on the phone.

## GitHub Actions and signed releases

Every push to `main` runs tests, Android lint and a debug build. After verification succeeds, it builds and verifies a release APK and publishes a GitHub release asset. PRs and manual workflow runs verify without publishing. The debug APK is also available in the **verification** Actions artifact.

Create a long-lived **private** signing key on your own computer (keep an offline backup; do not commit it):

```bash
keytool -genkeypair -v -keystore pa-release.jks -alias pa -keyalg RSA -keysize 3072 -validity 10000
```

Add these at GitHub → repository **Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Value |
| --- | --- |
| `PA_KEYSTORE_BASE64` | Base64 of the complete binary JKS file, with line breaks removed |
| `PA_STORE_PASSWORD` | Keystore password |
| `PA_KEY_ALIAS` | Signing-key alias, e.g. `pa` |
| `PA_KEY_PASSWORD` | Private-key password |

Linux/macOS: `base64 < pa-release.jks | tr -d '\n'`. PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes('pa-release.jks'))`. Paste the output only into GitHub Secrets, never a message, issue or committed file.

No personal access token is needed by the workflow: it uses the repository `GITHUB_TOKEN` with `contents: write` for the release job. Repository/organization Actions policy must allow this permission. Missing signing secrets fail the release job with the missing secret's name; the verification artifact remains available.

Local signed builds read `PA_KEYSTORE_PATH`, `PA_STORE_PASSWORD`, `PA_KEY_ALIAS`, `PA_KEY_PASSWORD` from the environment. Without them a local release build is unsigned, never silently debug-signed. CI verifies the signature before publishing. APK signing materials are removed in an `always()` cleanup step.

### Version identity

`version.properties` stores the versionName and local build number. CI uses `github.run_number` as the APK's versionCode, so each push has a unique, increasing build number without source edits. Both jobs get the same build number. Example: versionName `0.1.0`, CI run `12` → UI **0.1.0 (12)** → release tag **v0.1.0+12** → asset **PA-v0.1.0+12.apk**. A release step compares `aapt`'s embedded version against that tag and checks the APK signature. A rerun may replace only the asset for the same tag/commit; it will not move another commit's version tag.

The same signing key is required for future updates. Preserve workflow run numbering if moving this app to another repository, or provide an increasing versionCode offset before releasing there.

## Phone setup and operating limits

Open P.A. once. Grant **Alarms & reminders**, **Notifications**, and **Full-screen notifications** where applicable. Use the battery button to exclude P.A. from battery optimisation. On Samsung, also set battery usage to **Unrestricted** and add P.A. to **Never sleeping apps**. Ensure the Alarms channel is enabled, alarm volume is audible, and DND permits alarms.

`SCHEDULE_EXACT_ALARM` is the user-controlled access used here; `USE_EXACT_ALARM` is an alternative permission, not an additional permission the user must grant. Android 14+ has a separate full-screen access setting. Without full-screen access, the alarm notification is the fallback. User/OEM notification-channel choices and muted alarm volume can prevent an audible/visible alert.

Swiping the app from recents does not cancel PendingIntent alarms. Android **Force stop**, uninstall, a powered-off phone, or revoked exact-alarm access cancels/prevents alarms; reopen the app after Force stop. Room uses credential-encrypted storage: reboot recovery occurs after the first unlock, not before. Doze support is implemented through alarm-clock alarms; locked-screen/reboot behaviour must still be validated on the actual S21. See the device checklist; compilation and policy tests do not prove hardware delivery.

The alarm sounds for at most 60 seconds per alert. If not Done/Dismissed, subsequent re-alerts are scheduled by AlarmManager. One occurrence can never be marked Done by another app, nor does opening a sign-off app confirm its completion.
