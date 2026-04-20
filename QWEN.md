# CVT (ELM327) — Mitsubishi Lancer X

## Project Overview
This is an Android application built with **Jetpack Compose** designed specifically for **Mitsubishi Lancer X** owners. The app connects to an **ELM327** Bluetooth adapter (via Bluetooth Classic/SPP) to monitor the temperature of the **CVT** (Continuously Variable Transmission) and check for oil degradation.

### Core Functionality
- **CVT Temperature Monitoring**: Reads PID `2103` from the ECU and applies complex polynomial formulas to calculate two different temperature readings.
- **Oil Degradation Tracking**: Optionally reads PID `2110` to estimate transmission fluid health.
- **Real-time Updates**: Data is polled approximately once per second.

## Building and Running

### Prerequisites
- **Android Studio** (latest version recommended)
- **JDK 11+**
- An **ELM327 Bluetooth adapter** paired with your Android device.

### Build Instructions
The project uses the Gradle build system.

1.  **Clone/Open the project** in Android Studio.
2.  **Sync Gradle**: Android Studio will automatically sync the project and download necessary dependencies.
3.  **Build APK**: 
    - Use the command line: `./gradlew assembleDebug`
    - Or use **Build > Build Bundle(s) / APK(s) > Build APK(s)** in Android Studio.

### Running the App
1.  **Pair your ELM327 adapter** in the Android system Bluetooth settings before launching the app.
2.  Ensure the device has **Bluetooth permissions** (especially for Android 12+).
3.  Launch the application on an Android device or emulator.
4.  Select your paired ELMM327 device from the list and tap **Connect**.

## Development Conventions

### Tech Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Asynchronous Programming**: Kotlin Coroutines
- **Dependency Management**: Gradle with Version Catalogs (`gradle/libs.versions.toml`)
- **Architecture**: Modern Android architecture (ViewModel, Compose)

### Key Files and Directories
- `app/`: The main module containing the application source code and resources.
- `app/build.gradle.kts`: Module-level build configuration.
- `build.gradle.kts`: Project-level build configuration.
- `gradle/libs.versions.toml`: Centralized dependency and plugin version management.
- `README.md`: High-level project documentation and usage instructions.

### Coding Standards
- Use **Version Catalogs** (`libs.versions.toml`) for managing all dependencies and plugins.
- Adhere to **Jetpack Compose** best practices for UI development.
    