# CarboMon Tracker

[![Android build](https://github.com/Atx85/carbomon-tracker/actions/workflows/android-build.yml/badge.svg?branch=main)](https://github.com/Atx85/carbomon-tracker/actions/workflows/android-build.yml)

CarboMon Tracker is an Android app for logging meals, tracking daily carbohydrates, and monitoring key nutrition metrics.

## Download Android app

[**Download latest APK**](https://github.com/Atx85/carbomon-tracker/releases/latest/download/carbomon-latest.apk)

Download `carbomon-latest.apk` directly and open it on an Android 10 or newer device. No GitHub account or ZIP extraction is required. This link becomes available after the first successful release-enabled build on `main` and automatically follows subsequent releases.

Downloads are signed release builds with debugging disabled. Each successful build on `main` publishes a GitHub Release with the APK attached; release downloads do not have the 30-day Actions artifact expiry. [View release details and previous builds](https://github.com/Atx85/carbomon-tracker/releases).

Public APKs omit embedded API credentials. Saved foods, generic staples and Open Food Facts barcode lookup are available; USDA name search and FatSecret lookups require a personal build with your own API credentials.

### Updating an existing installation

Android requires an update to use the same signing key as the installed app. The release workflow must therefore use the existing release keystore. A keystore is held by whoever built the original release; it cannot be recovered from the phone or APK.

Earlier versions of this workflow built debug APKs with temporary keys. Publishing an APK on a GitHub Release page does not change its Android build type. The workflow now builds the release variant and signs it separately before publication. Switching to release mode alone does not fix a mismatch with an installed app's key.

Keep the existing app installed and export a backup from its settings before any migration. Uninstalling deletes local data. If the original signing key is unavailable, first verify that your backup is saved before considering a reinstall and restore.

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

4. For local development in Android Studio, build a debug APK (public downloads use the separate release workflow below):

```bash
bash ./gradlew assembleDebug
```

## GitHub Actions builds

The [Android build workflow](https://github.com/Atx85/carbomon-tracker/actions/workflows/android-build.yml) tests and builds the **release** variant on pushes to `main` or `feature/**` and pull requests targeting `main`. Once the workflow is on the default branch, **Run workflow** also starts a build manually.

Only a successful build on `main` proceeds to signing and publication. A separate job signs the APK with the persistent release key, verifies its signature, then publishes it as `carbomon-latest.apk`. The README's download link follows the latest release. Pull requests and feature branches produce unsigned build artifacts for development; these are not phone downloads. Release signing secrets are available only to the publication job on `main`.

### One-time release signing setup

App users do not need signing keys or GitHub secrets. The repository owner configures these once, using the keystore that signed the previous release. The former `ANDROID_DEBUG_KEYSTORE_BASE64` setting is no longer used.

On the computer holding the original release keystore, install Python 3 and [GitHub CLI](https://cli.github.com/), sign in with `gh auth login`, then run:

```bash
python3 scripts/configure-release-signing.py
```

The helper asks for the keystore file, key alias and passwords, then encodes and uploads the secrets directly to GitHub. Passwords and the encoded key are not printed or saved to a separate file. If the previous APK was generated in Android Studio, use the keystore selected in **Build → Generate Signed Bundle / APK**. Do not create a replacement key if you need to update an existing installation.

The helper sets `ANDROID_RELEASE_KEYSTORE_BASE64`, `ANDROID_RELEASE_STORE_PASSWORD`, `ANDROID_RELEASE_KEY_ALIAS` and `ANDROID_RELEASE_KEY_PASSWORD`. Keep a private backup of the keystore; never commit it. Publication fails if signing is missing or invalid, so an unsigned APK is never offered through the public download link.

The publication job uses GitHub's built-in token with `contents: write`; repository policies must allow that permission. No personal access token is needed for the workflow. The helper needs a signed-in repository administrator to configure secrets once.

CI builds explicitly leave API credentials blank because credentials embedded in an APK can be extracted. For your own build, use the local Gradle properties described above. Unit-test reports remain available as Actions artifacts for 14 days, including when tests fail.

## Repository
GitHub remote: `git@github.com:Atx85/carbomon-tracker.git`

## License
This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
