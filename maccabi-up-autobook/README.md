# UpBooker: weekly Pilates auto-booking for Maccabi upapp

A personal Android app that books your Friday Pilates class in Maccabi's upapp
each week, the moment booking opens. It logs in by reading the Maccabi SMS
code on the phone itself. The code never leaves the device.

> Not affiliated with Maccabi. Automating upapp may be against its terms. Use it
> for your own account only. The app books at most one class per week and
> retries slowly (every 10 s, at most 12 times).

## How a run works

1. An alarm-clock alarm fires 90 s before booking opens. The app gets this
   alarm even in Doze.
2. A foreground service starts listening for SMS and asks Maccabi to send the
   login code to your ID number.
3. The SMS arrives. The app extracts the code and logs in.
4. If you're already booked for that Friday (by the app or manually), it stops.
5. At the opening time it books the class matching activity, studio, day and
   time. If that class is full, it tries the backup time.
6. You get a notification with the result, and the next week is scheduled.

## Status

| Part | State |
|---|---|
| Scheduling, SMS code reading, retries, safeguards, UI, notifications | Done, unit-tested |
| Dry run of the whole flow with a fake backend | Done |
| Real upapp API calls (`MaccabiApiClient.kt`) | **Needs step 1:** map the API from your phone ([docs/CAPTURE.md](docs/CAPTURE.md)) |

Until the API is mapped, a scheduled run notifies you that it couldn't book. It
doesn't do anything else.

## Build and install

Requirements: JDK 17+ and the Android SDK (platform 35).

```bash
./gradlew testDebugUnitTest   # run unit tests
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or open the folder in Android Studio and press Run.

## Set up on the phone

1. Open **UpBooker** and tap **Grant permissions**. Allow these, tapping the
   button again until the "Missing" line disappears:
   - SMS
   - notifications
   - alarms & reminders (exact alarms)
   - unrestricted battery use
2. Fill in your ID number, class time, backup time, studio, and when booking
   opens (for example, 7 days before at 08:00).
3. Turn on **Auto-book every week** and tap **Save and schedule**.
4. Tap **Dry run now**. From another phone, text this phone
   `מכבי 123456` (or whatever the SMS keyword is). You should get a
   "Dry run: Pilates booked ✅" notification. That proves the alarm, SMS
   capture and notifications work.
5. On Xiaomi, Samsung and similar phones, also allow autostart and set the app
   to "No restrictions" in battery settings.

## Code layout

```
app/src/main/java/dev/upbooker/
  core/       Pure Kotlin, no Android: unit-tested
    BookingJob.kt        the weekly run: login → wait → book → backup → result
    Schedule.kt          when the next run starts (Asia/Jerusalem)
    OtpParser.kt         picks the code out of the Maccabi SMS
    ClassMatcher.kt      finds your class in the class list
    MaccabiApiClient.kt  ← fill in after step 1
    FakeMaccabiClient.kt used by "Dry run"
  android/    Alarm, foreground service, SMS receiver, Keystore storage, UI
```
