# MamaLink shared domain contracts (pre-Stage 2/3)

This document fixes the shared API that Irene's ViewModels and Chanel's persistence implementation will use. The domain classes are pure Kotlin data models, **not** Room entities, and there are **no implementations or new database tables in this PR**.

| Model | Fields | Types and meaning |
| --- | --- | --- |
| `Caregiver` | id, fullName, phoneNumber | `Long`, `String`, optional `String?` |
| `Child` | id, caregiverId, name, dateOfBirth | `Long`, `Long`, `String`, `java.time.LocalDate` |
| `Appointment` | id, caregiverId, childId, title, scheduledAt, location | `Long`, `Long`, optional `Long?`, `String`, `java.time.Instant`, optional `String?` |

## Contract conventions

- **Identifiers:** repository-generated positive `Long` IDs. Pass `id = 0L` to `insert`; use the returned ID for later operations. Stage 3 can use Room auto-generated integer primary keys represented as `Long`.
- **Relationships:** `Child.caregiverId` references an existing `Caregiver.id`. `Appointment.caregiverId` must reference an existing caregiver. If `Appointment.childId` is non-null, it must reference a child belonging to the **same caregiver**. Stage 3 should enforce this with appropriate validation/transactions, alongside database foreign keys.
- **Dates:** `Child.dateOfBirth` uses ISO calendar semantics with `LocalDate` (no timezone). For Room, persist as an ISO-8601 date string with a converter. Never represent a birth date as a timestamp.
- **Appointment times:** `Appointment.scheduledAt` is an absolute `Instant`; Room should persist it as UTC epoch milliseconds (`Long`). The UI formats this for the user's local timezone, not as a hardcoded local clock.
- **Optional fields:** `phoneNumber`, `childId` and `location` may be null. Phone is a string, not a number, to preserve leading zeros and `+` prefixes.
- **Observation:** the repository returns `LiveData`; lists are empty rather than null. `observeById` emits null when absent. Appointment lists are chronological (earliest first).
- **Writes:** `insert` returns the newly assigned ID. `update` and `deleteById` return affected row counts (0 when no matching record). Writes are suspend functions, so ViewModels can call them within `viewModelScope`.
- **Data constraints:** Stage 2 should validate required names/titles and prevent invalid dates or missing relationships. Stage 3 must defend referential integrity even if a caller bypasses UI validation. Decide with the group whether caregiver deletion cascades, restricts, or requires explicit confirmation before implementing it.
- **Privacy:** no real patient information, clinical records, diagnoses, or health recommendations belong in fixtures, tests, logs or commits.

## Implementation handoff

1. **Irene / Stage 2:** implement ViewModels against `domain.repository` interfaces using fakes in tests. Do not depend on Room or its entity representations.
2. **Chanel / Stage 3:** implement Room entities, DAOs, converters and adapters that satisfy these interfaces. Introduce an explicit **v1 → v2** Room migration, export schema history and test migration. Preserve workspace metadata and avoid destructive migration.
3. **Emmanuel / Stage 4:** design forms and displays around these data types; convert user-entered local date/time to `LocalDate` / `Instant` at the UI boundary.

This contract is intentionally not connected to the Stage 1 dashboard. No persistence or caregiver/child/appointment screens are claimed to be functional yet.
