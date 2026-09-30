# CarboMon Tracker

[![Android build](https://github.com/Atx85/carbomon-tracker/actions/workflows/android-build.yml/badge.svg?branch=main)](https://github.com/Atx85/carbomon-tracker/actions/workflows/android-build.yml)

CarboMon Tracker is an Android app for logging meals, tracking daily carbohydrates, and monitoring key nutrition metrics.

## Download Android app

[**Download latest APK**](https://github.com/Atx85/carbomon-tracker/releases/latest/download/carbomon-latest.apk)

Download `carbomon-latest.apk` directly and open it on an Android 10 or newer device. No GitHub account or ZIP extraction is required. This link becomes available after the first successful release-enabled build on `main` and automatically follows subsequent releases.

These are debug test builds. Each successful build on `main` publishes a GitHub Release with the APK attached; release downloads do not have the 30-day Actions artifact expiry. [View release details and previous builds](https://github.com/Atx85/carbomon-tracker/releases).

Public APKs omit embedded API credentials. Saved foods, generic staples and Open Food Facts barcode lookup are available; USDA name search and FatSecret lookups require a personal build with your own API credentials.

### Updating an existing installation

Android requires an update to use the same signing key as the installed app. Earlier GitHub builds used a new temporary debug key on each runner, so those APKs may fail to install over a previous copy. Keep the existing app installed and export a backup from the app's settings before attempting a migration. Uninstalling deletes the app's local data.

The workflow now requires a persistent signing key before publishing from `main`. Reuse the original computer's debug keystore if that computer built the installed app. If the installed copy came from an earlier GitHub build and its temporary key was not saved, the original key cannot be recovered from the APK. Moving to a stable key then requires exporting a backup, reinstalling, and importing the backup; do this only after checking that the backup was saved successfully.

## Features
- Daily and weekly diary views
- Food lookup by name and barcode
- Manual food entries
- Offline generic staples and searchable personal products, including saved Lidl UK labels
- Recipe ingredient support
- Macro and micronutrient tracking
- Local backup import/export
- Multi-language UI support

## Supported Languages
- English
- Hungarian
- Polish

## Your own foods and basic staples

Name searches in the diary and recipe builder include your saved foods first, followed by 23 built-in generic staples and API results. Staples include lard, raw and cooked chicken, raw 15% fat beef mince, pork mince, dry and cooked pasta, rice, oats and eggs. They work without an API key.

For an exact Lidl UK product, enter its name, brand and per-100-g label values under **Manual**, then choose **Save to my foods**. You can save it without logging a meal and later search by name, brand or both, such as `Lidl pork mince 15%`. Keep different fat percentages as separate products. Manual recipe ingredients are also saved for reuse.

Generic staples are clearly labelled and may differ from a particular package. See [food data sources](docs/food-data-sources.md) for provenance and units.

## Tech Stack
- Kotlin
- Jetpack Compose
- AndroidX
- CameraX + ML Kit barcode scanning
- USDA FoodData Central API
- FatSecret API

## Getting Started
### Prerequisites
- Android Studio (latest stable)
- Android SDK configured
- JDK 17

### Setup
1. Clone the repository.
2. Open the project in Android Studio.
3. Add API keys to your local `~/.gradle/gradle.properties` (or project `gradle.properties` locally, but do not commit secrets):

```properties
USDA_API_KEY=your_usda_api_key
FATSECRET_CLIENT_ID=your_fatsecret_client_id
FATSECRET_CLIENT_SECRET=your_fatsecret_client_secret
```

4. Build and run the app:

```bash
bash ./gradlew assembleDebug
```

## GitHub Actions builds

The [Android build workflow](https://github.com/Atx85/carbomon-tracker/actions/workflows/android-build.yml) runs unit tests and builds a debug APK on pushes to `main` or `feature/**` branches and pull requests targeting `main`. Once the workflow is on the default branch, you can also select **Run workflow** in GitHub Actions to start a build manually.

After tests and the build succeed on `main`, a separate publishing job creates a release for that exact commit and marks it as the latest release. Feature branches and pull requests only produce Actions artifacts. The publishing job uses GitHub's built-in token with `contents: write`; no personal access token is needed. Repository or organisation policies must allow that permission.

Before the first build on `main`, add the repository secret `ANDROID_DEBUG_KEYSTORE_BASE64`. Use the existing debug keystore from the computer that built the installed app (`~/.android/debug.keystore` on macOS/Linux, `%USERPROFILE%\.android\debug.keystore` on Windows). It must use the standard Android debug alias `androiddebugkey` and password `android`. Do not generate a replacement if you need to update an existing installation.

With GitHub CLI authenticated on the original build computer, this macOS/Linux command sends the key directly to the repository secret without printing it:

```bash
base64 < ~/.android/debug.keystore | gh secret set ANDROID_DEBUG_KEYSTORE_BASE64 --repo Atx85/carbomon-tracker
```

Keep a private backup of this keystore and do not commit it. Every published build restores the same key, and `main` fails clearly if the secret is missing. Pull requests do not receive the key; their APKs are disposable test builds and may not install over an existing copy. Feature builds also use temporary keys when the secret is unavailable.

CI builds explicitly leave API credentials blank because credentials embedded in an APK can be extracted. For your own build, use the local Gradle properties described above. Unit-test reports remain available as Actions artifacts for 14 days, including when tests fail.

## Repository
GitHub remote: `git@github.com:Atx85/carbomon-tracker.git`

## License
This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
