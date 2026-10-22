# Approved plan and checkpoint

Stop after stage 1 until Tom confirms. No feature tabs have been implemented at this checkpoint.

## Stage 1 — current

Project/CI, Room/DataStore foundations, permission setup, exact-alarm engine, two alert styles, re-alert/snooze/done/dismiss, London DST rules, rota/pay/scheduling unit tests, recovery broadcasts, visible version, sample Gmail compose action and device-test instructions. Release requires repository keystore secrets; physical alarm acceptance requires device tests.

## Stage 2 — after confirmation

- Four bottom tabs: Today, Shifts, Car, Tasks; settings at top right; Today build footer. Dark Material 3 with large, reachable controls for Galaxy S21.
- Shifts: editable default times, all override types with explicit swap semantics, holidays by day/range and allowance, weekly/pay-period hours and gross pay, initially empty rate and break inputs, configurable pay frequency/day. CalendarContract export to a picked calendar. No reading WFM storage.
- Checks: connect actual working-day overrides, offer installed-app picker to open a sign-off app, daily log and CSV export through SAF. Never automate taps or other app interactions.
- Car: insurance/MOT/tax/service/custom items; date or mileage due, 30/7/1-day offsets, notes/cost, dated mileage log and explicit estimate confidence, completion prompting for next due date (annual default where applicable).
- Notification reader: opt-in NotificationListenerService and explainer; user-selected apps only, Gmail/Outlook/WFM suggestions. Store only sender/title/snippet/time/source. Local keyword/date/time rules with custom keywords; Make task, Remind later, Dismiss and open source app. WFM notifications offer Add/confirm shift; never silently import unconfirmed data.
- Tasks: title/date/time, priority, notes, category Work/RMS/Art/Car/Home/Personal, daily/weekly/monthly/custom repeats. Once-daily overdue nags at selected time; Sunday-evening review summarising next week's shifts/tasks/car dates.
- Today: user-time morning briefing notification and screen, today's shift/checks/tasks, car within 30 days, flagged mail, timeline and quick-add task. Widget: next shift/check/due item.
- Complete settings: pay parameters, allowance, shift times/anchor, checks/nags, briefing, watched apps and rules. Versioned JSON backup/restore via SAF with all data/settings and alarm reconciliation.

## Deferred beyond v1

Full Gmail API, Microsoft Graph, AI summarisation and reading calendar events into Today (planned read-only v1.1). No backend, accounts, analytics, credentials or autonomous sending of email. Optional Gmail nudges are compose intents the user sends themselves.
