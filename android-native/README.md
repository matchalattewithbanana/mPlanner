# mPlanner Native — Kotlin Android development

This is an early **native Android project scaffold** using Kotlin, Jetpack Compose and Room. It is not yet feature-complete or a published APK.

## Current features
- Material 3 Compose shell with Today / Plan / Revise / Focus / Insights navigation
- Room database with tasks, exams and study sessions
- Seeded school preboard/rehearsal dates, duplicate-safe on app restarts
- Create and complete local study tasks; browse daily plan and upcoming exams
- Pure Kotlin, non-destructive exam-priority scheduling logic and unit tests

**Still pending:** legacy `pcb1`/`pcb2` import, production focus timer, revision queue, realistic progress analytics, reminders, exam-aware deferral UI, migration QA, signing, and production builds. These are deliberately not presented as complete.

## Build prerequisites
Android SDK API 36, JDK 17, Gradle 8.13 and internet access for dependency resolution.

```sh
cd android-native
gradle :app:assembleDebug
gradle :app:testDebugUnitTest
```

A Gradle wrapper *properties file* is present, but wrapper bootstrap scripts/JAR are not checked in yet; use a compatible installed Gradle or generate a validated wrapper before CI integration.

The Tauri build remains unchanged. The Android application ID matches the previous Tauri identifier to allow evaluating an eventual in-place migration, but a real update depends on matching signing certificates.

Review [migration plan](../docs/KOTLIN_MIGRATION_PLAN.md) before shipping.
