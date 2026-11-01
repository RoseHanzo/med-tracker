# Med Tracker

A personal Android app for tracking one medication, how you feel, and your measurements.

- **Dose reminders that keep coming back** every few minutes until you tap *Taken*
- **Separate weekday and weekend times**, with your own choice of weekend days
- **1, 2 or 3 doses a day**, each with its own reminder
- **Pill supply** that counts down and warns you before you run out
- **Calendar** showing taken, partly taken and missed days, plus that day's check-in and readings
- **Daily check-in**: tap 1–10 for each symptom, then one Save button. Hide anything you don't want
- **Measurements**: weight (kg), blood pressure and pulse, typed in or pulled from Samsung Health
- **Trends**: line graphs over 7, 30 or 90 days, and how many doses you took
- **Backup and restore** to a file

## Install on your phone

1. On your phone, open **github.com/RoseHanzo/med-tracker/releases/tag/latest**
2. Tap **MedTracker.apk** to download it, then open the download
3. If asked, allow your browser to install apps, then tap **Install**

New versions install the same way and keep your data.

## Samsung Health

The app reads weight, blood pressure and heart rate through Health Connect, which is built into Android.

1. In the app: **Settings → Samsung Health → Connect**, and allow all three
2. In Samsung Health: **Settings → Health Connect**, and allow it to share weight, blood pressure and heart rate

Samsung's stress score isn't shared with Health Connect, so other apps can't read it.

## Reliable reminders on Samsung phones

In **Settings → Reminders**, tap **Don't put this app to sleep** if it appears. Samsung's battery saving can otherwise delay reminders.

## How it's built

Kotlin and Jetpack Compose. Every push to `main` builds a signed APK with GitHub Actions and publishes it to the `latest` release.
The build log is saved to the `ci-log` branch.
