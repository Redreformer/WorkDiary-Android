# My Work Diary — Android

> 📋 Work shift tracker for UK workers. Track duties, holidays, lieu days, and annual leave balance.

The Android companion to [My Work Diary iOS](https://apps.apple.com/gb/app/my-work-diary/id6762786125).

## Features

- **Calendar** — Day / Week / Month views with swipe navigation
- **OCR Duty Scanner** — Photograph a duty board and extract Duty number, Sign On/Off times (ML Kit)
- **PDF Duty Board Lookup** — Upload MF/SAT/SUN PDFs, search by duty number
- **Shift Pattern Generator** — Set up 4-on/4-off or custom roster cycles, auto-generate weeks of shifts
- **Photo Attachments** — Up to 3 photos per day with camera/gallery picker
- **Holiday Tracking** — Annual leave, allocated days, personal days with full CRUD
- **Balance Dashboard** — Circular chart showing remaining/taken allowance, lieu balance, carried over
- **UK Bank Holidays** — Auto-calculated (Easter algorithm, moving bank holidays)
- **Multi-Profile** — Two separate profiles with independent data
- **Notifications** — Daily shift reminders at configurable time
- **Data Export/Import** — Share duties and holidays via clipboard
- **Dark Mode** — Toggle between light and dark themes

## Tech Stack

- **Kotlin** + **Jetpack Compose** (Material 3)
- **Hilt** for dependency injection
- **Room** for local database
- **DataStore** for preferences
- **ML Kit** for OCR text recognition
- **Coil** for image loading
- **WorkManager** for scheduled notifications
- **Firebase Analytics** (optional)

## Requirements

- Android Studio (Hedgehog or later)
- Android SDK 35 (compile/target)
- Min SDK 26 (Android 8.0)
- JDK 17

## Building

1. Clone the repo:
   ```bash
   git clone https://github.com/Redreformer/WorkDiary-Android.git
   cd WorkDiary-Android
   ```

2. Copy the example properties file and fill in your signing details:
   ```bash
   cp gradle.properties.example gradle.properties
   ```
   Edit `gradle.properties` and add your keystore credentials:
   ```properties
   WORKDIARY_STORE_PASSWORD=your_password
   WORKDIARY_KEY_PASSWORD=your_password
   ```

3. Place your keystore file in the project root as `workdiary-release.keystore`

4. (Optional) Add `app/google-services.json` for Firebase Analytics

5. Build:
   ```bash
   ./gradlew assembleRelease
   ```

The signed APK will be at `app/build/outputs/apk/release/`.

## License

MIT — see [LICENSE](LICENSE).

## Links

- **iOS App:** [App Store](https://apps.apple.com/gb/app/my-work-diary/id6762786125)
- **Rob's Batcher:** [GitHub](https://github.com/Redreformer/robsmusic)