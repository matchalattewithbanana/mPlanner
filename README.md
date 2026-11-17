# mPlanner

Offline-first Android study planner for Class XII preparation and entrance exams.

## Native Kotlin app

The active development version lives in **[android-native](android-native/)** and uses Kotlin, Jetpack Compose, Material 3, Room, and WorkManager. The former Tauri/WebView prototype remains in the repository for legacy data export until the native app is validated on real devices.

### Features

- Today view with editable, dated study tasks, completion progress, and exam-day priorities.
- 527 original 2026–27 plan entries preserved as editable local tasks.
- Preboard and rehearsal dates from the uploaded school timetables.
- CBSE board dates, plus visibly tentative NEET and CUET date markers.
- Revision queue with 1/3/7/21-day intervals and recall ratings.
- Background-aware focus timer with partial study-session saving.
- Offline mock-score records, basic statistics and subject totals.
- Opt-in revision notifications; optional, no account or internet connection required.
- Versioned JSON backup/export and legacy `pcb1`/`pcb2` import.

### Development and tests

```bash
cd android-native
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Prerequisites: JDK 17, Android SDK 36 and a configured `ANDROID_HOME`. Build validation is defined in [.github/workflows/native-android.yml](.github/workflows/native-android.yml). This is not a published release.

### Data migration — important

The old Tauri APK stores progress in WebView `localStorage`; Android does **not** automatically transfer that data to Room. **Export your legacy backup before uninstalling the old app.**

1. Open the old app, select Progress → Back up, and save the copied JSON text as a `.json` file.
2. Install the native build alongside the old app (the native application ID is `app.mplanner.native`).
3. Use the native app's Backup → Import to select the saved JSON file.
4. Confirm that task completion, focus totals, mock scores and revision records appear; make a new native export.

Existing task IDs are stable. Reimporting identical legacy data does not create duplicated rows. No destructive, silent migration is attempted.

### Status and cautions

This repository is under active development. Native Android builds and unit tests are part of the acceptance gate, but instrumented device testing, sign-off on backup restores, accessible interaction checks, and release signing must all precede distribution. Do not treat debug builds as production releases.

The general exam end time on the school PDFs reads `12:30 AM` despite a 09:30 start, so native data intentionally leaves those end times unspecified. The Yoga 11:30 finish is explicit. NEET/CUET dates are tentative.

### Legacy prototype

The Tauri 2/Rust wrapper and static HTML plan remain in `src-tauri/` and `dist/`. Its old APK workflow is manual-only and should not be used for new releases.

See [native setup](android-native/README.md) and [migration design](docs/KOTLIN_MIGRATION_PLAN.md).
