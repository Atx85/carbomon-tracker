# CarboMon Tracker

[![Android build](https://github.com/Atx85/carbomon-tracker/actions/workflows/android-build.yml/badge.svg?branch=main)](https://github.com/Atx85/carbomon-tracker/actions/workflows/android-build.yml)

CarboMon Tracker is an Android app for logging meals, tracking daily carbohydrates, and monitoring key nutrition metrics.

## Download Android app

[Open successful builds to download the APK](https://github.com/Atx85/carbomon-tracker/actions/workflows/android-build.yml?query=is%3Asuccess)

After the first successful build, open the newest successful run for your desired branch and select **Download the APK** in its summary, or **carbomon-debug-apk** under **Artifacts**. Sign in to GitHub to download, extract the archive, then open `app-debug.apk` on an Android 10 or newer device. Downloads are test builds and are kept for 30 days; run the workflow again if a download has expired.

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

Optional repository secrets under **Settings → Secrets and variables → Actions** enable the food search services in the APK:

- `USDA_API_KEY`
- `FATSECRET_CLIENT_ID`
- `FATSECRET_CLIENT_SECRET`

The build can run without these secrets; the corresponding online food searches need credentials to work. The workflow also uploads unit-test reports for 14 days, including when tests fail.

## Repository
GitHub remote: `git@github.com:Atx85/carbomon-tracker.git`

## License
This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
