# CarboMon Tracker

CarboMon Tracker is an Android app for logging meals, tracking daily carbohydrates, and monitoring key nutrition metrics.

## Features
- Daily and weekly diary views
- Food lookup by name and barcode
- Manual food entries
- Recipe ingredient support
- Macro and micronutrient tracking
- Local backup import/export
- Multi-language UI support

## Supported Languages
- English
- Hungarian
- Polish

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
- JDK 11+

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
./gradlew assembleDebug
```

## Repository
GitHub remote: `git@github.com:Atx85/carbomon-tracker.git`

## License
This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
