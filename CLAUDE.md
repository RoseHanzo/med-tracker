# Cadence: notes for future work

Personal Android app (package `com.rosehanzo.medtracker`, shown as **Cadence**) for one
once-a-day medication (Xaggitin XL 18 mg), daily check-ins, body readings and Samsung Health data.
Built for one user (Hanzo, GitHub `RoseHanzo`) on a Samsung phone with Galaxy wearables.

## How to work with the owner

- Show a visual preview (mockup) of any UI change and get approval before building it.
- The owner often batches several requests and asks to "discuss first, then apply all in one update".
- Say when a big coding task is about to start, so they can raise the effort level.
- Explain things plainly; they review on a phone.

## Build and release (no computer needed)

- Every push to `main` runs `.github/workflows/build.yml`: Gradle 8.14.3, JDK 17, `gradle assembleRelease`.
- The APK is published to the GitHub release tagged `latest` as `Cadence.apk`, plus `version.json`
  (`{"build": N}`) and the release title "Cadence build N". N = `GITHUB_RUN_NUMBER` = `versionCode`.
- Build logs are force-pushed to the `ci-log` branch (`status.txt`, `errors.txt`, `build.log`).
  To check a build: `git fetch origin ci-log && git show FETCH_HEAD:status.txt` (look for the commit sha).
- Signing: `keystore/medtracker.jks` is committed but locked; its password exists only in the
  `KEYSTORE_PASSWORD` repository secret (set by the owner). Never put the password in the repo or chat.
- Add `[skip ci]` to commit messages that shouldn't produce a new build (docs only).
- The workspace can't reach Google Maven/Gradle downloads, so code can't be compiled locally.
  Push and read `ci-log` instead.

## In-app updates

- `update/Updater.kt` reads `version.json` from the release downloads (fallback: GitHub API title),
  downloads `Cadence.apk`, and opens Android's standard installer via a `FileProvider`
  (`${applicationId}.updates`, `res/xml/update_paths.xml`).
- Works only while the repository is **public**. The owner switches it public to update, then private.
- A PackageInstaller-session approach was tried in builds 4–5 and did not install on the phone; don't go back to it.

## Code map (`app/src/main/java/com/rosehanzo/medtracker/`)

- `data/Data.kt`: models, JSON storage in `filesDir/data.json`, `Codec` with versioned migrations.
- `data/Logic.kt`: dose plan, taken/missed, supply, orders, forms.
- `data/Scales.kt`: check-in bar scales, words, impact conversion.
- `reminders/Reminders.kt`: alarms and notifications (`AlarmReceiver`, `BootReceiver`).
- `health/HealthSync.kt`: Health Connect reads and the "Check Health Connect" diagnostic.
- `update/Updater.kt`: in-app updates.
- `ui/`: `TodayScreen`, `CalendarScreen`, `TrendsScreen`, `SettingsScreen`, `Common`, `Chart`, `Theme`.
- Release builds use R8 (`isMinifyEnabled = true`); the full Material icons-extended library is included.

## Data model and rules

- Saved data version is **4**. Bump it and add a migration in `Codec.decode` for any format change.
- **Dose plan**: `Phase(start, end?, pills 1–4, daysOff)`. Once a day only. The latest started phase
  applies and carries on after its end date. For *future* calendar days, only days a phase
  explicitly covers are shown (`Logic.explicitPlanFor`); carry-on applies once the day arrives.
- **Doses**: one `DoseTaken(time, pills)` per date. Days are orange until marked taken or missed
  (`AppData.missed`). Only an explicit "Mark missed" makes a day red. Marking a past day taken
  defaults to that day's planned time; the time can be edited afterwards.
- **Reminders**: one daily reminder at the weekday or weekend time, repeating every N minutes until
  taken or missed; stops at midnight.
- **Notifications must stay discreet**: "N new task(s) due today", "N form(s) due today",
  "Supplies running low", channels "Daily tasks" and "Supplies". The status-bar icon is a pill (owner's choice).
- **Supply**: pills decrease by the day's pills on Taken. Order reminders (`OrderEvent`) open when supply
  reaches the warning days (3, 5, 7, 10, 14, 21, 30); "Ordered" only ticks it off; a refill starts a new cycle.
- **Forms**: name, date, time, repeat (once or every 1, 2 or 4 weeks); discreet repeating notification with Done.
- **Check-in**: saved values are bar positions (1 = left end).
  - Mood: 1–7, normal in the middle (`SCALE_MOOD`).
  - Focus / energy: 1–10, best on the right (`SCALE_GOOD`).
  - All others: 1–10, left = normal/none, right = worst (`SCALE_IMPACT`).
  - No numbers are shown when logging; a word is shown instead.
  - Trends and the by-dose table always use impact, where higher = worse (`Scales.impact`).

## Calendar (agreed design, "style D")

- Hollow ring around the day number: green taken, red marked missed, orange not yet marked
  (today or past), grey planned future, none when nothing planned.
- Small dots inside the ring below the number show the pill count.
- Under the ring: check-in mark (green tick done, red cross not done, orange dash today) and, beside it,
  an outline icon: pill box for an order reminder, otherwise form (two lines). Blue due, green done,
  red missed. Pill box wins over form on the same day.

## Trends (agreed design)

- Four tiles: doses taken, usual time and lateness, current dose and day, biggest change.
- "Dose and how you feel": dose bands with chips for check-ins and readings; Combined (default) or Separate.
- "By dose level" table of averages; red marks clear worsening versus the lowest dose.

## Samsung Health / Health Connect

- Reads weight, blood pressure, heart rate, sleep and steps for the last 30 days on every sync
  (deduplicated by id). Works out daily average pulse, sleeping heart rate, and heart rate in the
  4 hours after each dose (v1) versus 2 hours before (v2).
- Samsung's stress, HRV and skin temperature are not shared with Health Connect.
- If nothing syncs, use Settings → Samsung Health → Check Health Connect to see whether
  Health Connect holds any data and which app wrote it.
