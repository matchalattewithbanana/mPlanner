# mPlanner — native Kotlin Android app

This is the in-progress Kotlin/Jetpack Compose replacement for the earlier Tauri/WebView planner.

## Implemented
- Native Compose Today, Plan, Revise, Focus and Insights screens.
- Room database storing study tasks, school examinations, revision events, focus sessions and mock results.
- Original 2026–27 study template bundled as 369 dated task entries.
- Examination-day warnings for preboard/rehearsal dates from the school's uploaded schedules.
- Task completion schedules 1, 3, 7 and 21-day revision prompts. Revision ratings of Again/Hard create earlier follow-up.
- Monotonic-clock study timer that resumes when the app process restarts on the same device boot; partial sessions can be saved.
- JSON import/export via Android's system document picker. Import handles old `pcb1`/`pcb2` progress backups.
- Native Android validation CI builds a debug APK **without publishing artifacts or releases**.

## Development
Requires Android SDK 36, JDK 17 and Gradle 8.13.

```bash
cd android-native
gradle :app:testDebugUnitTest :app:assembleDebug
```

The included Gradle wrapper *properties* are not a complete wrapper; local builds currently require installed Gradle. CI provisions it.

## Migration
The old WebView's `localStorage` is **not** directly accessible to the native app. On the old app, use its backup button to copy the `pcb2` JSON. Save that text as a .json file on the device. In Kotlin mPlanner, tap Backup > Import and select it. Export a native backup before importing anything. A new app update can only replace the old APK in place if applicationId and signing certificates match, otherwise remove/install after backing up user data.

## Known limitations
- Not yet device-tested; verify CI compilation, migration and runtime behavior before shipping.
- Reminders are visible in the revision tab but not currently dispatched as system notifications.
- Native mock-history and subject editing are basic. App release signing and distribution remain unconfigured.
- Do not delete the old app until its data has been exported and verified in native.

See [migration plan](../docs/KOTLIN_MIGRATION_PLAN.md).
