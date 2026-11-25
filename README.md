# MamaLink

MamaLink is a Kotlin Android app for maternal and child wellness support. It uses XML layouts, Android Views, View Binding, MVVM, LiveData, Room and the Navigation component. Stage 5 integrates caregiver, child and appointment records with local appointment reminders. The app provides no medical diagnosis or treatment advice.

## Open and run

1. Clone this repository and open its root in Android Studio Meerkat (2024.3.1) or a newer version supporting AGP 8.9.2.
2. Set the Gradle JDK to **17**. Install Android SDK Platform **35** and Build Tools **35.0.0** with SDK Manager.
3. Let Android Studio sync the project, select the `app` run configuration, and run on an API **23+** device or emulator.

The application ID and namespace are `com.mamalink.app`. Version `0.1.0` is an unsigned-for-release development foundation.
Android Studio creates `local.properties` for the local SDK path; do not commit it.
For command-line builds, set `JAVA_HOME` to a JDK 17 installation and `ANDROID_HOME` to your SDK directory.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

On Windows, use `gradlew.bat`. No system Gradle installation is required. The checked-in wrapper uses Gradle **8.14.7**. No system Gradle installation is required.
The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
Dependencies are pinned in `gradle/libs.versions.toml`; first sync/build needs internet access to Google Maven and Maven Central.
Robolectric's API 23, 28 and 35 instrumented SDKs are pinned for Robolectric 4.14.1 and resolved by Gradle from Google's Maven Central mirror into `app/build/robolectric-sdks` before tests. The test JVM uses offline SDK resolution, so it performs no MavenArtifactFetcher downloads. Update these SDK coordinates when upgrading Robolectric or adding an API level. This does not make the first Gradle build network-free.
CI runs `:app:build :app:lintDebug :app:lintRelease`, including debug and release unit tests, and checks that Room schema generation leaves the committed schemas unchanged.
This baseline intentionally pins a compatible stable toolchain rather than tracking latest releases.
[AGP compatibility](https://developer.android.com/build/releases/agp-8-9-0-release-notes) · [Room releases](https://developer.android.com/jetpack/androidx/releases/room)

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
