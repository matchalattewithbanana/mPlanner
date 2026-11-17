# mPlanner — Native Kotlin migration and exam-aware replanning

Status: implementation plan and initial pure Kotlin scheduling core. The existing Tauri application remains intact until a tested replacement is ready.

## Direction
- Native Android application in Kotlin with Jetpack Compose and Material 3.
- Room for durable offline storage; DataStore for preferences; WorkManager for deferrable reminders; Android notifications for alerts.
- MVVM with repositories and use cases; immutable domain models, coroutines and Flow.
- No account, network permission, or cloud sync required for the initial release.
- Preserve the original Boards/NEET/CUET schedule as an editable imported template, not hardcoded business logic.

## Migration phases
1. **Foundation**: Gradle Android app module, Compose navigation, Room entities/migrations, CI unit tests, design system. Keep Tauri build available until parity.
2. **Data migration**: export legacy `pcb1`/`pcb2` JSON from the old app; explicit import flow into Kotlin. Validate shape, size, dates, score bounds, and text; make imports transactional, idempotent, and reversible. Android WebView storage is not automatically shared with a new app. Preserve the existing applicationId and signing key if in-place updates are required; verify update eligibility before shipping.
3. **Core parity**: Today, Plan, Revise, Focus, Insights; daily tasks, progress, mocks, backup/export, countdowns and revision queue.
4. **Reliability**: elapsed-realtime session accounting, persisted start/pause state, timezone/date rollover, permission-aware reminders, database backup and restore, test coverage.
5. **Polish**: editable templates, responsive layouts, accessibility, release signing, performance checks and migration QA.

## Schedule precedence
Confirmed school preboard and rehearsal examinations override ordinary study tasks on the same day; official board exams take precedence after that. NEET/CUET tentative dates must be visibly tentative and editable. Preserve displaced tasks in a backlog, never silently mark them done.

### Preboard (school datesheet)
- 2026-11-18 Chemistry
- 2026-11-19 English
- 2026-11-23 Physics
- 2026-11-25 Biology
- 2026-11-28 Psychology
- 2026-12-01 Yoga

### Rehearsal (school datesheet)
- 2026-12-15 Physics
- 2026-12-17 Psychology
- 2026-12-18 English
- 2027-01-02 Chemistry
- 2027-01-04 Biology
- 2027-01-06 Yoga

For both school examination series, reading time is 09:15–09:30 and writing starts at 09:30. The PDFs print general end time as 12:30 **AM**, which conflicts with a three-hour morning examination; keep the end time unverified rather than silently changing the source. Yoga is explicitly 09:30–11:30.

### Official board dates currently present in legacy planner
Physics 2027-02-16; Chemistry 2027-02-27; Psychology 2027-03-02; Yoga 2027-03-04; English 2027-03-05; Biology 2027-03-25. Verify against official bulletins before release.

## Study allocation
- Until Nov 18: prioritize Chemistry and English, avoid adding new heavy tasks.
- Nov 18–Dec 1: preboard exam days = exam + optional light recovery/next-paper preparation; gaps emphasize the next paper. Deferred original tasks move into a reviewable backlog.
- Dec 2–14: first-pass catch-up plus targeted rehearsal preparation.
- Dec 15–Jan 6: rehearsal exam priority; lighter preparation and recovery on exam days; fill gaps with next-exam revision.
- Jan 7–Feb 13: board second pass, practicals and mock papers, with subject weaknesses informed by both school exam results.
- Feb 14–Mar 25: existing board exam sprint with editable official dates.
- Mar 26 onward: NEET and CUET preparation, with tentative dates clearly labeled.

## Scheduling rules
- Never overwrite historical task completion when re-planning.
- Exam dates and time zones are LocalDate/LocalTime, not 24-hour millisecond offsets.
- Exam tasks are distinct from study tasks; subject-specific priority and duration are configurable.
- No automatic task carry-over without user visibility and approval.
- Limit planned workload, preserve rest, and avoid treating all mock results as one comparable score series.

## Acceptance tests
- All 12 school exams appear on the correct date and subject.
- Conflicting study tasks remain recoverable in the backlog.
- Importing the same backup twice creates no duplicate records.
- A calendar day boundary does not corrupt due dates or study minutes.
- Offline planning, timer, review queue and export/import work after process death.
- No production APK or release until explicit approval.
