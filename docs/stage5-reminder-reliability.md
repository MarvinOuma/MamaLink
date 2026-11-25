# Stage 5 reminder reliability correction

This corrective change starts at main `f80460de0466666a5269cae0d389bd5900895042`. Caleb's original Stage 5 contribution (`63424df`, Squiffy1) and the preceding Git history remain ancestors. It does not merge the correction, publish a release, or start Stage 6. Independent review and green CI are required before proceeding.

## Behavior

- Delivery receipts are private, synchronous preferences keyed by appointment ID and persisted scheduledAt milliseconds. Every delivered revision is retained, including an A -> B -> A edit. WorkManager KEEP only avoids duplicate unfinished requests; receipts prevent replay after completed work, dismissal, process recreation, or reboot.
- A process-wide coroutine mutex serializes appointment writes, reconciliation, and delivery. Scheduling re-reads Room inside that mutex. Incoming lists and CRUD callbacks never supply the authoritative revision.
- Before a Room write, recovery work must be durably registered, and the journal records a pending sweep and existing appointment IDs/revisions. An unavailable recovery registration rejects the write. A failed Room write does not cancel work or withdraw notifications. A later sweep also preserves a posted notification for an unchanged revision.
- Successful rescheduling withdraws the preceding notification, replaces the obsolete request, and retains the previous receipt. Successful deletion withdraws the notification and cancels work. IDs remain journaled if cancellation fails, so recovery can finish after a restart.
- WorkManager enqueue/cancel/periodic-registration operations are suspended until their result futures complete. Exceptions propagate to retrying workers or are logged after a successful Room write, with the durable journal and periodic repair already in place. Cancellation of the caller does not cancel an already submitted persistence operation.
- Startup and permission grant request a unique reconciliation worker. Boot/package replacement uses goAsync only while persisting that work, with an eight-second bound and finally/finish. Database reconciliation runs in the worker and returns retry on failure. A 15-minute periodic repair is registered before writes and survives process death. No foreground service or exact alarm is introduced.
- The appointment editor keeps its lifecycle-owned child subscription while stopped and disposes it when the dialog view is destroyed. Both editor Save states respect loaded records and submission state; the child editor recomputes Save when caregiver data arrives late.

Room remains schema v2, compile/target SDK 35 and minimum SDK 23. Notification permission/channel checks, private visibility, immutable navigation intents, and stable notification IDs remain in place. Denied permission does not consume a delivery receipt; grant triggers repair.

## Verification

The original five regression checks all failed against the unmodified Stage 5 tree (which is identical to the requested main merge). The committed checks cover those behaviors using real editors, real WorkManager and production notification delivery. Additional checks cover serialized reads/writes, real Room foreign-key failure, failed enqueue/cancel recovery, first-run journal initialization, revision history, permission recovery, asynchronous Operation failure/cancellation, and boot enqueue persistence. The WorkManager test factory routes reminder execution to production delivery with a fixed clock; it does not replace the scheduler with a recording fake.

Commands used for full validation:

```
./gradlew --no-daemon --max-workers=2 :app:build :app:lintDebug :app:lintRelease
git diff -- app/schemas
git status --porcelain -- app/schemas
```

The wrapper reports Gradle 8.14.7. The distribution ZIP SHA-256 is `903bfc4bc7622323124054c10f509246212a1902948bdaf26c379cca8735e856`, matching the [official Gradle checksums](https://gradle.org/release-checksums/). All 316 installed files were compared against this verified archive with zero mismatches. The wrapper JAR hash is `ad16bca46cb71bb5887c546860d93992aeee35c1fdaf4a242b0a612375d937e0`, also matching the published checksum.

## Limits for review

- Delivery is deliberately at most once per revision. A durable receipt is claimed immediately before the platform notify call. Synchronous failure or permission denial rolls it back. A process kill between the durable claim and notify can suppress that single reminder; Android notification posting and application storage do not share a transaction. This implementation does not claim exactly-once delivery across that boundary.
- Receipts cannot retroactively establish which reminders an older app version already delivered. They protect delivery performed by the corrected worker.
- WorkManager delivery/repair timing remains subject to Android scheduling, Doze, background restrictions, force-stop state, and notification settings. Fifteen minutes is the periodic minimum interval, not an execution-time guarantee.
- No physical-device validation is included. Emulator results and CI results should be reviewed alongside the deterministic tests before release work.
