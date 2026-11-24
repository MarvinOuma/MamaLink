# Stage 2 ViewModel integration

Annah's original commit `37e44fd` and her repository-injected ViewModels, factories, LiveData APIs and operation-state model remain in history. The branch merges verified main, including Chanel's Stage 3 repositories, without rebasing or force pushing. The README conflict was resolved by retaining both contributions.

## Input and operation rules

- Names and appointment titles must contain a non-whitespace character. Phone and location remain optional; no new format or clinical rules are imposed.
- Child and appointment caregiver IDs must be positive. A supplied appointment child ID must be positive. Room checks existence and same-caregiver ownership atomically; the ViewModel does not make a racy read-before-write check.
- Updates and deletes require positive saved IDs; zero affected rows report a missing record. Inserts retain Annah's convention of resetting the incoming ID to zero and expose the generated positive ID on success.
- Birth dates are typed `LocalDate` values and cannot exceed today in the injected clock's timezone. Today and past leap dates are valid. There is no arbitrary age restriction.
- Appointment times are typed `Instant` values and must fit Room's epoch-millisecond range. New appointments cannot be in the past. Updates may correct existing past appointments. Room retains millisecond precision. Forms must parse and explain local date/time input before invoking these typed APIs.
- One write runs at a time per ViewModel. Further writes during loading are ignored; future forms should disable submit controls while `inProgress` is true. Completion and error replace old results. Cancellation resets progress and propagates, including when the ViewModel is cleared.
- Explicit validation and missing-record errors are shown directly. Unexpected repository exceptions use a generic retry/related-record message so database diagnostics are not exposed. A retry can succeed after an error.

## Verification scope

Fake-repository tests exercise all three ViewModels: live list/record observations, CRUD parameters and generated IDs, progress transitions, missing records, repository errors, retries, cancellation, ViewModel clearing and concurrent submissions. Deterministic clock tests cover required fields, relationship and saved IDs, birth-date boundaries, local midnight, appointment time boundaries and factories.

Room integration tests drive all three ViewModels through the actual Stage 3 repository implementations. They cover linked CRUD and observations, nullable fields, rejected foreign/mismatched references, restricted deletion and retry. Existing DAO, repository, migration, navigation and contract tests remain part of both CI variants. CI also builds all variants, runs debug/release lint and checks schema drift.

The Stage 1 dashboard still opens placeholder forms. This integration supplies presentation state and persistence; device smoke tests, real forms, authentication, reminders and release readiness remain later work.
