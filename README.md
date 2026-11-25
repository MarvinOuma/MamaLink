# MamaLink

MamaLink is a Kotlin Android app for maternal and child wellness support. It is a university group project prototype. It helps a caregiver keep fictional-in-development records of themselves, their children and upcoming appointments, and sends local appointment reminders. The app provides no medical diagnosis or treatment advice.

**Intended users:** caregivers of young children who want a simple place to track appointments.

## Features

**Implemented (functional)**
- Caregiver records: add, view, edit and delete.
- Child profiles linked to a caregiver, with birth-date validation.
- Appointments linked to a caregiver and an optional child: create, view, edit, reschedule and cancel.
- Local appointment reminders using WorkManager and notifications.
- Data saved on the device with Room, and restored after the app is closed and reopened.
- Navigation between the main sections, with toolbar Up and system Back support.
- Unsaved form state restored after screen rotation.

**Placeholder or planned (not implemented)**
- Care timeline and health resources screens are clearly labelled placeholders.
- Authentication, reviewed educational content, care events and clinical rules are future work.
- No cloud services and no medical diagnosis or advice.

## Technology stack

- Kotlin, Android Views with XML layouts and View Binding
- MVVM with ViewModel and LiveData
- Room 2.7.2 (with KSP) for local persistence
- WorkManager for appointment reminders, and Android notification APIs
- Android Navigation component
- Gradle (wrapper 8.14.7) and GitHub Actions for CI

## Architecture

```text
Fragments (UI)  →  ViewModels (LiveData)  →  Repository interfaces  →  Room (DAO + database)
```

- **UI layer:** a single activity and Fragments. They display state, collect input, show messages and navigate. They observe LiveData with `viewLifecycleOwner`.
- **ViewModels:** validate input, call repositories from `viewModelScope`, and expose read-only LiveData. They hold no Activity or Fragment references.
- **Repositories:** the single contract between the ViewModels and the data layer.
- **Room:** the source of truth. Foreign keys enforce that an appointment's child belongs to the same caregiver. Migrations are explicit and the schema history is committed in `app/schemas`.

## Installation

1. Clone this repository and open its root in Android Studio Meerkat (2024.3.1) or a newer version supporting AGP 8.9.2.
2. Set the Gradle JDK to **17**. Install Android SDK Platform **35** and Build Tools **35.0.0** with SDK Manager.
3. Let Android Studio sync the project, select the `app` run configuration, and run on an API **23+** device or emulator.

The application ID and namespace are `com.mamalink.app`. Android Studio creates `local.properties` for the local SDK path; do not commit it. For command-line builds, set `JAVA_HOME` to a JDK 17 installation and `ANDROID_HOME` to your SDK directory.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

On Windows, use `.\gradlew.bat` in PowerShell. No system Gradle installation is required. The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## User guide

Button and screen names may differ slightly from the app. Use fictional data only.

1. **Open the app.** The Dashboard shows links to each section.
2. **Register a caregiver.** Open the caregiver section, add a caregiver, fill in the required fields and save. Required fields left empty show an error.
3. **Add a child.** Open the child profiles section, choose the caregiver, enter the child's name and birth date (it cannot be in the future) and save.
4. **Schedule an appointment.** Open Appointments and create a new one. On Android 13 and later the app asks for permission to show notifications at this point. Choose the caregiver, optionally a child, and a date and time that are not in the past, then save.
5. **Enable reminders.** Allow notifications when asked. If you denied them, enable them in the phone's Settings under Apps, MamaLink, Notifications. Appointments still save without the permission, but no reminder is shown.
6. **Reschedule or cancel.** Open an appointment to edit its date and time, or cancel it. The reminder is replaced or removed with it.

## Testing

### Automated verification
| Check | Result |
|---|---|
| GitHub Actions on `main` before Stage 6 | Passing |
| Debug build | [Successful or Failed] |
| Release build | [Successful or Failed] |
| Debug unit tests | [tests] run, [failures] failures, [skipped] skipped |
| Release unit tests | [tests] run, [failures] failures, [skipped] skipped |
| Debug lint | [errors] errors, [warnings] warnings |
| Release lint | [errors] errors, [warnings] warnings |

The Stage 5 corrective changes (PR #9) are merged into `main`, and their tests are part of the suite above. Lint warnings that remain are dependency update advisories for the deliberately pinned toolchain: [confirm from your lint report].

### Emulator verification
The app launched and opened to the Dashboard on an Android emulator ([API level] and [API level]). The following were **not performed** in Stage 6 and are unverified:
- A full manual walkthrough of caregiver, child and appointment management
- Manual verification of reminders (scheduling, rescheduling, cancelling, restart recovery, permission denial and tapping a notification)
- Accessibility, large font, dark theme and small-screen checks
- Testing on a physical device

## Known limitations

- Reminders use WorkManager. Delivery is deferrable and is **not guaranteed at the exact time**. Android may delay it because of Doze, battery restrictions, force-stop or device policy.
- The manual and device behaviours listed above as not performed have not been verified.
- Care timeline and health resources are placeholders. There is no authentication, cloud sync or medical content.
- This is a university prototype and not a medical product.
- [Add any security or privacy limitation you found, for example what the notification text shows.]

## Contributors

MamaLink was built by a six-stage university group project. Contributors are listed by GitHub username.

| Stage | Work | Contributor | Pull requests |
|---|---|---|---|
| Foundation | Shared domain models and repository contracts | MarvinOuma | [#3](https://github.com/MarvinOuma/MamaLink/pull/3) |
| 1 | Android architecture and foundation | MarvinOuma | [#2](https://github.com/MarvinOuma/MamaLink/pull/2) |
| 1 (follow-up) | Robolectric SDK resolution fix and build variant verification | MarvinOuma | [#5](https://github.com/MarvinOuma/MamaLink/pull/5) |
| 2 | ViewModels and LiveData | Annah-2003 | [#6](https://github.com/MarvinOuma/MamaLink/pull/6) |
| 3 | Room database and persistence | ChanelNasokho | [#1](https://github.com/MarvinOuma/MamaLink/pull/1), [#4](https://github.com/MarvinOuma/MamaLink/pull/4) |
| 4 | XML user interface | EMMANUELMUTINDA | [#7](https://github.com/MarvinOuma/MamaLink/pull/7) |
| 5 | Integration and appointment reminders | Squiffy1 | [#8](https://github.com/MarvinOuma/MamaLink/pull/8) |
| 5 (corrective) | Reminder reliability and editor lifecycle fixes | MarvinOuma | [#9](https://github.com/MarvinOuma/MamaLink/pull/9) |
| 6 | Final testing, documentation and release preparation | Paul Mukua (Paawl) | [Stage 6 PR]([add link after opening the PR]) |

---

# Development history

The sections below are the original stage notes, preserved unchanged.

## Included in Stage 1

- Single activity, XML layouts, View Binding, Material DayNight theme, system bar insets and scrollable screens.
- Dashboard links to caregiver profile, child profiles, appointments, care timeline and health resources. Toolbar Up and system Back use the Navigation component's back stack.
- A working MVVM dependency path: Fragment → ViewModel/LiveData → repository interface → Room DAO/database.
- Room 2.7.2 with KSP and exported schema version 1. The only table is **non-clinical workspace metadata**. It starts empty; no data is seeded. No profile or appointment schema is assumed.
- Local JVM tests for state mapping, actual Room reads/writes and repository observation, navigation to all five placeholders, Up, system Back, and activity recreation on API 23 and 35. Android-dependent tests run with Robolectric; no emulator is needed for these tests.
- GitHub Actions builds the debug APK, runs unit tests and lint, checks the schema, and uploads reports and the debug APK.

## Stage 2 — ViewModels

- `ui/caregiver/CaregiverViewModel`, `ui/child/ChildViewModel`, and `ui/appointment/AppointmentViewModel` observe lists and individual records through the domain repository interfaces.
- Each ViewModel exposes a read-only `operationState` LiveData for write progress, completion and errors. Inserts pass `id = 0` so the repository can generate an ID; updates and deletes report missing records.
- Each ViewModel has a repository-injected `ViewModelProvider.Factory`, uses `viewModelScope` for suspend writes, and propagates coroutine cancellation.
- Required names/titles, positive relationship IDs and saved-record IDs are validated before repository writes. Birth dates cannot be in the future. New appointments cannot be in the past; existing appointments may be corrected after their scheduled time. Appointment instants must fit Room's epoch-millisecond representation. Date/time values are already typed; forms must parse input at their boundary. Injectable clocks make date boundaries deterministic in tests.
- One write runs at a time per ViewModel; further writes during loading are ignored so forms should disable their submit controls. Cancellation clears loading and propagates. Unexpected repository errors use a safe message; missing records and invalid fields have explicit messages. A later attempt replaces the previous result.
- Production Room implementations now satisfy the domain repository interfaces. UI forms remain future work, so the current dashboard continues to open the clearly labelled placeholders. See [Stage 2 integration](docs/stage2-viewmodels.md) for validation and test scope.

```text
app/src/main/java/com/mamalink/app/
  MamaLinkApplication.kt          # application-scoped dependencies
  data/local/                    # Room database, DAO and metadata entity
  data/repository/               # repository contract + Room implementation
  ui/                            # activity
  ui/dashboard/                  # Fragment, ViewModel and UI state
  ui/placeholder/                # shared, clearly labelled unfinished screens
app/src/main/res/                 # layouts, navigation graph, theme and strings
app/src/test/                     # JVM + Robolectric verification
app/schemas/                     # committed Room schema history
```

The ViewModel receives its repository through a factory, observes it with LiveData, and contains no Activity/Fragment references. Fragments observe with `viewLifecycleOwner` and do not retain view bindings. Room uses the application context, performs DAO writes with suspend functions, and does not permit main-thread database access. The production UI only observes a label and has no data-entry controls.

## Stage 1 verification — 23 November 2026

Local JDK 17 validation passed: `assembleDebug`, **9 unit/Robolectric tests**, and `lintDebug`.
Lint has **0 errors** and 42 update advisories for the deliberately pinned dependencies/toolchain (including repeated notices across build variants); no application-code warnings remain.
The packaged APK was inspected: application ID `com.mamalink.app`, min SDK 23, target SDK 35.
Physical-device/emulator smoke testing and release signing were not performed.
Before release, check scrolling and text scaling, light/dark themes, system-bar spacing, each destination, Up/Back, and rotation on a device.
See the pull request checks for the independent GitHub Actions result and downloadable debug APK/reports.

## Stage 3 persistence

Room repositories now implement the shared domain contracts. The v2 database stores only the agreed profile and appointment fields, enforces same-caregiver child links, restricts related deletions, and migrates workspace metadata from v1 explicitly. See [Stage 3 persistence](docs/stage3-persistence.md) for representation, deletion policy and verification scope.

## Stage 5 — records and appointment reminders

Caregiver, child and appointment forms use the existing Room repositories. Their editor dialogs restore unsaved text, selections, dates and times after recreation; saving is explicit, and cancelling discards edits. Room foreign keys continue to require that an appointment's child belongs to the same caregiver.

Appointment reminders use unique one-time WorkManager requests keyed by the appointment's stable Room ID. The default reminder runs 24 hours before the appointment; if it is less than 24 hours away, the request runs as soon as WorkManager can execute it. Appointments already in the past are not scheduled. WorkManager persists eligible work across process death and device restart. Delivery is deferrable and can be delayed by Doze, battery restrictions, force-stop, or device policy; this is not an exact alarm.

Android 8.0 and later use the `appointment_reminders_channel` notification channel. Android 13 and later require `POST_NOTIFICATIONS`; the app requests permission when the user opens appointment creation. If permission is denied, records continue to work and the worker safely skips displaying a notification. Notification content is marked private for lock-screen redaction. Tapping a reminder opens Appointments.

Room remains the source of truth. Successful writes enqueue or replace corresponding unique work; app startup and device restart reconcile reminders from persisted appointments. A worker rereads the appointment before posting, so deleted, rescheduled, or elapsed appointments do not show stale reminders. Exact-time delivery is intentionally not requested, so no exact-alarm permission or background service is needed.

Before release, run debug and release builds, unit tests and lint, verify Room schema output, and exercise appointment CRUD, permission denial, notification tap, rotation and app restart on an emulator or device. Do not use real patient information during development.

## Later-stage handoff

Use the agreed domain models, collection rules and repository contracts. Add explicit Room migrations and preserve exported schema history when changing the database; destructive fallback is deliberately absent. Authentication, reviewed educational content, care events and clinical rules remain future work. Do not use real patient information during development.

Work on feature branches and open pull requests against `main`. Review the build/test/lint reports and perform a device smoke test before release. CI does not substitute for device testing, accessibility review, or clinical content review. No release signing keys belong in the repository.

## Repository preservation

This foundation branches from the README-only reset of `main`. The preserved branch `archive/pre-mamalink-rebuild-2026-11-23` is untouched; the old `feature/room` branch is also unchanged. Do not merge the archived tenant-management code into the new app.