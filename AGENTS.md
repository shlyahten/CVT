# CVT Temperature Monitor - Agent Guidance

## Essential Commands
- Build debug: `./gradlew assembleDebug`
- Build release: `./gradlew assembleRelease`
- Unit tests: `./gradlew test`
- Instrumented tests: `./gradlew connectedAndroidTest`
- Lint: `./gradlew lint`
- Clean: `./gradlew clean`

## Project Structure
- **App module**: `app/` contains all source code
- **Source**: `app/src/main/java/ru/shlyahten/cvt/`
  - Architecture: Repository/UseCase pattern
  - Key packages: `bluetooth`, `config`, `data/repository`, `domain`, `elm`, `obd`, `ui`, `model`
- **Configuration**: `app/src/main/java/ru/shlyahten/cvt/config/VehicleConfigs.kt` (for PID definitions)

## Important Notes
- **minSdkVersion**: 33 (Android 13) per `app/build.gradle.kts` (README claims 8.0+ but build takes precedence)
- **Language**: Kotlin with Jetpack Compose (UI in `ui/` package)
- **Build system**: Gradle Kotlin DSL (`build.gradle.kts` files)
- **CI**: GitHub Actions builds debug APK/bundle and runs unit tests (see `.github/workflows/android.yml`)
- **Adding new PID**: Edit `VehicleConfigs.kt` - add `PidConfig` with `modeAndPid`, `headerHex`, and formulas

## Testing
- Unit tests: `src/test/`
- Instrumented tests: `src/androidTest/`
- Test runner: `AndroidJUnitRunner`
- Compose UI testing: Includes `androidx.compose.ui.test.junit4`

## Development Workflow
1. Make changes to source
2. Verify with lint: `./gradlew lint`
3. Run unit tests: `./gradlew test`
4. For UI changes, run instrumented tests on device/emulator: `./gradlew connectedAndroidTest`
5. Build APK: `./gradlew assembleDebug` (or `assembleRelease`)