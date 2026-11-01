# Cadence

A personal Android app for one once-a-day medication, how you feel, and your measurements.

- **Dose plan**: phases with dates, pills per day and days off. The last phase carries on until you change it
- **One daily reminder that keeps coming back** every few minutes until you tap *Taken*
- **Separate weekday and weekend times**, with your own choice of weekend days
- **Pill supply** that counts down by the day's pills and warns you before you run out
- **Calendar** showing taken and missed days, planned pills, and each day's check-in and readings
- **Daily check-in**: tap 1–10 for each symptom, then one Save button. Hide anything you don't want
- **Measurements**: weight (kg), blood pressure and pulse, typed in or pulled from Samsung Health
- **Trends**: pills taken each day lined up with graphs of your readings and symptoms over 7, 30 or 90 days
- **One-tap updates** from inside the app
- **Backup and restore** to a file

## Install on your phone (first time)

1. On your phone, open **github.com/RoseHanzo/med-tracker/releases/tag/latest**
2. Tap **Cadence.apk** to download it, then open the download
3. If asked, allow your browser to install apps, then tap **Install**

After that, update from inside the app: **Settings → Updates**, or the banner on the Today screen.
The repository must stay public for in-app updates to work.

## Samsung Health

Cadence reads weight, blood pressure and heart rate through Health Connect, which is built into Android.

1. In Cadence: **Settings → Samsung Health → Connect**, and allow all three
2. In Samsung Health: **Settings → Health Connect**, and allow it to share weight, blood pressure and heart rate

Samsung's stress score isn't shared with Health Connect, so other apps can't read it.

## Reliable reminders on Samsung phones

In **Settings → Reminders**, tap **Don't put Cadence to sleep** if it appears. Samsung's battery saving can otherwise delay reminders.

## How it's built

Kotlin and Jetpack Compose. Every push to `main` builds a signed APK with GitHub Actions and publishes it to the `latest` release as "Cadence build N".
The signing key's password lives only in the `KEYSTORE_PASSWORD` repository secret. The build log is saved to the `ci-log` branch.
