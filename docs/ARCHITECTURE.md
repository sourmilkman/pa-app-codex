# Stage 1 architecture

## Boundaries

`:core` is platform-free Kotlin/JVM: explicit London time resolution, rota calculation with per-day overrides, exact-decimal pay calculation, routine occurrence generation and reminder state transitions. `:app` supplies Room, DataStore, Android permissions, PendingIntent scheduling, notifications, bounded alarm playback and Compose UI. No networking dependency or Internet permission is needed.

The only activity is `MainActivity`. Full-screen notification intents open the alarm route within that activity; normal launches open the stage-one readiness screen. Every PendingIntent is explicit, immutable and identifies its occurrence/action using its data URI. Internal alarm and action receivers are not exported.

Room schema version 1 is exported into `app/schemas/` and committed. There is no destructive-migration fallback. Later versions must add proper migrations. `reminders` stores stable occurrence IDs, original London date/time, derived next-trigger epoch milliseconds, status, delivered/completed timestamps and style. `events` is an append-only action history. `shift_overrides` supports the rota engine; its editor belongs to the later Shifts tab. DataStore holds alarm settings, not a duplicate authoritative schedule.

## Time semantics

User-entered dates/times always resolve through `ZoneId.of("Europe/London")`, irrespective of the device's timezone. Each occurrence resolves independently; never add 24-hour durations to model a repeating local day. The UK autumn overlap resolves once at the first offset; a nonexistent spring time moves forward by the DST gap. Defaults at 09:45–16:00 avoid the ambiguous hour. Snooze and re-alert deadlines are elapsed instants, so five minutes is five minutes during a rollback. Rebuilding scheduled rows never reinterprets a snoozed deadline.

The rota uses calendar-day arithmetic and whole-week parity from a Saturday anchor, including dates before the anchor. Cancellation, holiday, sick and swap-out suppress work; extra and swap-in enable work. Pay totals subtract each unpaid break from elapsed shift minutes, sum exact minutes, then round the gross total once to two decimal places with HALF_UP. An unset hourly rate yields no invented pay. Overnight shifts use actual elapsed time, including DST. Pay-period ranges are inclusive at the start and exclusive at the end.

## Alarm lifecycle

Enable routines explicitly after permissions. The planner projects the next 28 days into Room, inserting stable `check:date:time` IDs without replacing Done/Dismissed history. Expired prior-day checks are cancelled instead of sounding a storm of obsolete alarms after reboot. Previously persisted outstanding checks from the current day remain actionable; new projections do not backfill elapsed checks. Routine setting changes cancel obsolete queued occurrences; re-enabled future occurrences can be recreated from `CANCELLED` without reviving explicitly Dismissed/Done items.

Only the earliest pending occurrence is armed with `setAlarmClock()`. A dispatch handles all rows whose next-trigger time has arrived, persists their transition/event, issues alerts and arms the next deadline. Refresh and every action replenish the projection. This avoids registering hundreds of OS alarms and permits indefinite operation while routines remain enabled. The OS alarm-clock indicator represents P.A.'s next trigger; there is no hidden midnight maintenance alarm.

A mutex serialises engine operations in the application's single process; transition/event writes use a Room transaction. Stale Done/Snooze broadcasts cannot resurrect terminal items. `SCHEDULED → ALERTING` sets a first retry 10 minutes later; `ALERTING → ALERTING` retries every 5 minutes. `SNOOZED` waits 5 elapsed minutes, then alerts again with a fresh 10-minute grace. Notification-style reminders become `DELIVERED` without a retry and can still be Done/Snoozed/Dismissed.

For alarm-style alerts, a high-importance channel carries the full-screen intent; a media-playback foreground service plays alarm-usage sound and vibration for at most 60 seconds. All future retries remain OS alarms rather than an in-process loop. The foreground-service notification is separate from the persistent actionable reminder. There is one sounding alarm at a time; simultaneous outstanding occurrences retain separate notifications and individual state. If sound cannot start, the actionable notification remains. Android full-screen permission/channel choices determine presentation.

Recovery broadcasts refresh on boot, wall-clock change, timezone change, package replacement and exact-alarm permission grant. Reboot recovery uses credential-encrypted Room storage after first unlock. Force stop is an Android hard boundary: reopening is required. Notification/FSI/exact-alarm grants are checked separately; setup exposes their status.

## Next integration points

Future task/car/briefing producers should write typed, stable reminder occurrences into this queue through a repository rather than instantiate separate alarm schedulers. Repeat generators must produce new local occurrences from their recurrence rules; Done history belongs to an occurrence, not an entire repeating series. Implement next-occurrence projection for each producer before enabling it. Persist tasks/car data separately and keep their IDs as reminder origins.

The home widget and Today should read the same Room state. NotificationListenerService should store only selected source-app notification fields and operate locally; it must never enable accessibility or automate another app. JSON backups must be schema-versioned SAF exports with validation and a transactional restore followed by alarm reconciliation. These integrations are not present in stage 1.

A firing dispatcher first arms a 60-second recovery trigger, then replaces it with the next persisted deadline before starting notification/audio side effects. Receivers hold a bounded 25-second partial wake lock during asynchronous Room work and release it on completion. This narrows the failure window if a process dies during dispatch; Room and AlarmManager cannot participate in a single atomic transaction.
