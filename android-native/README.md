# mPlanner Native

The native Android implementation uses Kotlin, Jetpack Compose, Room and WorkManager. Java 17, Android SDK 36 and the **Gradle 8.13 wrapper** are required.

## Build and test

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Use `gradlew.bat` on Windows. CI runs the native test and debug-assemble tasks, without publishing an APK or release.

## Implemented capabilities

- Today: date navigation, task creation/editing and completion; exam-day warnings and move-to-tomorrow action.
- Plan: upcoming/all examination calendar, custom exam entry and imported study template.
- Revise: scheduled 1, 3, 7 and 21-day reviews with Again / Hard / Good / Easy ratings.
- Focus: on-device monotonic countdown, pause/resume, save partial duration, per-subject tracking.
- Insights: completion totals, focus time and personal mock-score history.
- Backup: system document picker for JSON exports and imports; supports legacy `pcb1`/`pcb2` exports and versioned native backups.
- Alerts: optional WorkManager-based periodic review reminders; notification permission requested on Android 13+.
- Persistent Room store, offline by default; no sign-in needed.

## Exam timetable and source confidence

- School preboard: Nov 18–Dec 1, 2026.
- School rehearsal: Dec 15, 2026–Jan 6, 2027.
- CBSE Class XII boards: Feb 16–Mar 25, 2027 (subject schedule).
- NEET/CUET markers are tentative and editable by adding a personal exam date.

The school's general exam end time is contradictory in the PDF; it remains unverified in the app instead of being guessed.

## Safe migration

The old Tauri app uses a different storage mechanism. The native app has application ID `app.mplanner.native` so both apps can coexist for side-by-side validation. Back up from the old app, save the JSON locally, then import through the native app. **Keep the old app installed until all records have been verified.** Native import is additive and idempotent for matching records, but changing original records with matching IDs will update them.

## Release gate

Do not publish until a physical device test validates clock recovery after background/force-stop, Android notification permission behavior, the import of a real Tauri backup, and signed upgrade continuity. Test process death and a local-time rollover separately. No production keystore or Google Play configuration is committed to this repository.
