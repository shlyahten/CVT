# CVT Temperature Monitor for Mitsubishi Lancer X

<p align="center">
  <a href="README.md"><b>English</b></a> •
  <a href="README.ru.md"><b>Русский</b></a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.x-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin 2.x" />
  <img src="https://img.shields.io/badge/Android-8.0+_(API_26--36)-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android 8.0+" />
  <img src="https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Architecture-Clean_/_MVI-FF6F00?style=for-the-badge" alt="Clean Architecture" />
  <a href="https://github.com/shlyahten/CVT/actions/workflows/android.yml"><img src="https://img.shields.io/github/actions/workflow/status/shlyahten/CVT/android.yml?branch=main&style=for-the-badge&label=Android%20CI" alt="Build Status" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue?style=for-the-badge" alt="MIT License" /></a>
</p>

---

## 📌 Overview

**CVT Temperature Monitor** is a modern, high-precision Android application written in **Kotlin** and **Jetpack Compose** (Material 3). It monitors the Continuously Variable Transmission (**Jatco CVT JF011E / F1CJA / W1CJA**) fluid temperature and degradation counters in real-time via an **ELM327 OBD-II adapter** over Bluetooth Classic (SPP).

While fully compatible with Android smartphones and tablets, the application is **specifically engineered for automotive Android Head Units (Teyes CC2 / CC2+ / CC3 / CC3 2K, Kingbeats, Joying, DUDU, Dasaita, and other Android radios)**. It features a persistent background foreground service, ignition autostart, auto-reconnect, and a movable floating overlay widget that sits smoothly on top of navigation apps like Yandex Navigator, 2GIS, Waze, and Google Maps.

---

## 🚀 Key Features

### 🌡️ Real-Time CVT Fluid Temperature
- **Direct Transmission ECU Communication**: Queries ECU diagnostic Mode/PID `2103` using header `7E1` on CAN bus.
- **Multiple Temperature Calculation Formulas**:
  - **Temp 1 (PIDs.csv)**: 5th-degree polynomial formula providing high precision across the entire thermal curve.
  - **Temp 2 (Cubic)**: Smooth cubic polynomial approximation.
  - **Raw Count (N)**: Raw integer counter value directly from the ECU (byte 13) without mathematical transformation.
- **Dynamic Color Zones**:
  - 🔵 **Cold / Warm-up (< 50°C)**: Reminds the driver to avoid hard acceleration while transmission fluid is cold.
  - 🟢 **Normal Operating Range (50°C – 89°C)**: Ideal operating temperature window.
  - 🟡 **Elevated Load (90°C – 99°C)**: Transmission under heavier thermal stress (traffic jams, high ambient temperatures, mountain climbs).
  - 🔴 **Overheating / Critical (≥ 100°C)**: Critical thermal stress warning to prevent fluid oxidation and transmission limp mode.

### 🪟 Floating Overlay Widget
- **Over-the-App Monitoring**: Compact, semi-transparent draggable widget floating over any running application (Yandex.Navigator, 2GIS, Google Maps, Spotify, etc.).
- **Smart Memory**: Remembers its exact screen coordinates $(X, Y)$ across reboots and app launches.
- **Live State Indication**: Mirrors the live temperature and updates its background color dynamically to match the current thermal zone.
- **Interactive**: Single-tap immediately brings the main monitor screen to the foreground.

### 🚗 Designed for Automotive Android Head Units (Teyes, Kingbeats, etc.)
- **Persistent Background Service**: `CvtOverlayService` runs as an official Android Foreground Service (`connectedDevice` type), preventing background termination by aggressive OEM task managers.
- **Automatic Connection**: Seamlessly connects to your last-used OBD adapter on service start.
- **Ignition & Boot Autostart (`BootReceiver`)**: Listens to system broadcasts for cold boots, fast wakeups, and ACC key turn events:
  - `com.glsx.boot.ACCON` *(Teyes GLSX launcher ACC ON broadcast)*
  - `com.fyt.boot.ACCON` *(FYT platform ACC ON broadcast)*
  - `android.intent.action.BOOT_COMPLETED`
  - `android.intent.action.LOCKED_BOOT_COMPLETED`
  - `android.intent.action.QUICKBOOT_POWERON`
  - `com.htc.intent.action.QUICKBOOT_POWERON`
  - `com.ts.headunit.power.on` *(proprietary Teyes wake intent)*
- **Automatic Bluetooth (Bluetooth 2) Activation**: Teyes head units frequently disable the secondary Android Bluetooth adapter ("Bluetooth 2" in settings) during standby or sleep, causing an empty device list. The app automatically detects this, activates Bluetooth 2 on launch, wake, and in the background service, and immediately repopulates the device list.
- **Adaptive Responsive Layout**: Automatically switches to an ergonomic two-column landscape view on widescreen displays (≥ 600dp) and a sleek single-column layout on portrait phones.

### 🧪 Widget Demo Mode
- Test overlay positioning, visual layout, and color transitions directly from your desk without needing to connect to a car or adapter:
  - **Auto Cycle**: Automatically simulates transmission warm-up through all thermal phases.
  - **Manual Presets**: Instantly trigger Cold (40°C), Normal (75°C), Warm (94°C), and Overheat (106°C).

### 🛢️ CVT Oil Degradation Counter (PID `2110`)
- Reads the internal Jatco transmission oil degradation points counter stored in the ECU on demand.
- **Weekly Delta Analysis**: Tracks degradation history and calculates the rolling 7-day point increase (`+N points`), highlighting high degradation rates in amber or green.

### 📡 Advanced Bluetooth Management
- **Auto-Activation of Bluetooth 2**: Eliminates empty device lists by automatically powering on Bluetooth 2 when the app opens or the car wakes into ACC mode.
- **Device Prioritizer**: Automatically highlights and prioritizes OBD-II adapters (`OBDII`, `OBD2`, etc.) over audio devices and phones.
- **In-App Bluetooth Discovery**: Scan and pair nearby devices without leaving the application.
- **Manual MAC Address Input**: Solve the notorious Teyes issue where custom Bluetooth stacks hide paired OBD adapters from standard Android system APIs by manually entering the adapter's MAC address (e.g. `00:1D:A5:68:98:8B`).
- **Device Aliasing**: Assign custom display names and remove obsolete devices.

### 📋 Live Diagnostic Terminal
- Real-time timestamped scrollable log displaying sent AT commands, raw hex responses, frame parsing states, and connection diagnostics.
- One-tap clipboard copy and clear actions for quick debugging and troubleshooting.

---

## 🚘 Vehicle & Hardware Compatibility

### Supported Vehicles
Equipped with **Jatco CVT (JF011E / F1CJA / W1CJA)**:
- **Mitsubishi Lancer X** (CY4A: 1.8L 4B10, 2.0L 4B11, 2.4L 4B12)
- **Mitsubishi Outlander XL** (CW5W / CW6W)
- **Mitsubishi ASX / RVR / Outlander Sport**
- **Mitsubishi Delica D:5**
- Other platform vehicles sharing the same Mitsubishi CVT ECU protocol.

### Supported Android Devices
| Category | Requirement |
|:---|:---|
| **OS Version** | Android 8.0 (Oreo, API 26) through Android 16 (API 36) |
| **Bluetooth** | Bluetooth Classic with SPP (Serial Port Profile) support |
| **Head Units** | Teyes (CC2, CC2+, CC3, CC3 2K, SPRO), Kingbeats, Joying, DUDU, Dasaita, and any Android-based multimedia system |

### Recommended OBD-II Adapters
- **Recommended**: Quality **ELM327 v1.5** Bluetooth adapter with **Microchip PIC18F25K80** microcontroller.
- ⚠️ **Notice regarding ELM327 v2.1 clones**: Cheap "v2.1" clones with truncated firmware typically do not support custom ISO 15765-4 CAN 11-bit headers (`ATSH 7E1`) or multi-frame responses, resulting in `NO DATA` or connection errors.

---

## 🔑 Permissions Breakdown

The app includes a dedicated first-launch onboarding screen to configure the necessary permissions:

| Permission | API Level | Purpose |
|:---|:---|:---|
| `BLUETOOTH_CONNECT` | Android 12+ (API 31+) | Connect to paired Bluetooth OBD-II adapters. |
| `BLUETOOTH_SCAN` | Android 12+ (API 31+) | Discover nearby Bluetooth adapters (`neverForLocation` flag). |
| `ACCESS_FINE_LOCATION` | Android 8–11 (API 26–30) | Required by Android OS to perform Bluetooth discovery on older versions. |
| `SYSTEM_ALERT_WINDOW` | All versions | Display the floating temperature widget over other apps. |
| `POST_NOTIFICATIONS` | Android 13+ (API 33+) | Display ongoing foreground service status in the notification drawer. |
| `RECEIVE_BOOT_COMPLETED`| All versions | Automatically start monitoring when the device boots or head unit powers on. |

> 💡 **Tip for Android Head Units**: In your head unit's Android Settings, disable battery optimization (**Don't optimize / No restrictions**) for CVT to prevent the OS from killing the background service during sleep cycles.

---

## 📥 Installation

### Method 1: Pre-built APK from GitHub Actions CI (Recommended)
1. Go to the project's [GitHub Actions tab](https://github.com/shlyahten/CVT/actions).
2. Select the latest successful run of the **Android CI** workflow.
3. Scroll down to the **Artifacts** section and download:
   - `release-apk` — Signed, optimized release build.
   - `debug-apk` — Debug build with extended logging.
4. Transfer the APK to your phone or head unit via USB drive or browser, and install (allow installation from unknown sources).

### Method 2: Build from Source
Ensure you have **JDK 17+** and **Android SDK** installed:

```bash
# Clone the repository
git clone https://github.com/shlyahten/CVT.git
cd CVT

# On Linux / macOS
./gradlew assembleDebug

# On Windows (PowerShell)
.\gradlew.bat assembleDebug
```

Compiled APK locations:
- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk`
- **Release APK**: `app/build/outputs/apk/release/app-release.apk`
- **Release AAB**: `app/build/outputs/bundle/release/app-release.aab`

---

## 🚦 Quick Start Guide

1. **Pair your ELM327 adapter**:
   - Insert the adapter into your vehicle's OBD-II diagnostic port (located beneath the steering column).
   - Turn on vehicle ignition.
   - In Android **Bluetooth Settings**, search for devices and pair with `OBDII` (standard PIN is usually `1234` or `0000`).
2. **Launch CVT App**:
   - Grant Bluetooth and Notification permissions on the onboarding screen.
   - Grant "Display over other apps" permission if you plan to use the floating overlay.
3. **Select your OBD Adapter**:
   - Select your paired adapter from the device list.
   - *If your head unit hides paired devices*: Tap **"Enter MAC"** and input the adapter's MAC address directly.
4. **Start Monitoring**:
   - Tap **START MONITOR**.
   - Watch live temperature readings update once per second.
5. **Setup Head Unit Automation**:
   - Toggle **Floating Widget** ON to keep the temperature gauge visible over your navigation app.
   - Toggle **Autostart on Head Unit** and **Auto-connect** ON to have monitoring start seamlessly every time you turn the key.

---

## 🔬 Technical Deep-Dive: OBD-II Protocol & Formulas

### ELM327 Initialization Sequence
Communication is handled through [`Elm327Session.kt`](app/src/main/java/ru/shlyahten/cvt/elm/Elm327Session.kt) using standard AT commands:
```text
ATZ         -> Reset ELM327 chip
ATE0        -> Echo off
ATL0        -> Linefeeds off
ATS0        -> Spaces off
ATH1        -> Headers on
ATSP6       -> Set protocol to ISO 15765-4 CAN (11-bit ID, 500 kbaud)
ATSH7E1     -> Set CAN transmit header to 7E1 (CVT Transmission ECU)
```

### PID `2103`: CVT Fluid Temperature
- **Mode & PID**: `2103`
- **ECU Request Header**: `7E1`
- **ECU Response Header**: `7E9` (with response payload starting with `61 03`)
- **Data Index**: The raw temperature count byte $N$ is located at zero-based index `13` (`CVT_2103_TEMP_COUNT_BYTE_INDEX`) in the merged ISO-TP payload following `61 03`.

```
Example Response:
7E9 10 12 61 03 02 02 00 B4 ... [Byte 13 = N]
```

#### Calculation Formulas:
- **Temp 1 (5th order polynomial from Mitsubishi PIDs specification)**:
  $$T_1(N) = 2.344 \cdot 10^{-9} N^5 - 1.387 \cdot 10^{-6} N^4 + 3.193 \cdot 10^{-4} N^3 - 0.03501 N^2 + 2.302 N - 36.6$$
- **Temp 2 (Cubic approximation)**:
  $$T_2(N) = 0.0000286 N^3 - 0.00951 N^2 + 1.46 N - 30.1$$
- **Raw Count**:
  $$T_{\text{raw}} = N$$

### PID `2110`: CVT Oil Degradation
- **Mode & PID**: `2110`
- **ECU Request Header**: `7E1`
- **Formula**:
  $$\text{Degradation} = AC \cdot 256 + AD$$
  *(where $AC$ and $AD$ are the respective response data bytes)*

---

## 🏛️ Project Architecture

The project adheres to modern Android Clean Architecture and unidirectional data flow:

```
app/src/main/java/ru/shlyahten/cvt/
├── BootReceiver.kt              # Handles system wake & ignition broadcasts
├── CvtApp.kt                    # Application setup & notification channels
├── CvtOverlayService.kt         # Foreground Service & draggable WindowManager overlay
├── MainActivity.kt              # Compose UI entry point & permissions flow
├── bluetooth/
│   └── BluetoothSppClient.kt    # Bluetooth Classic (RFCOMM/SPP) client
├── config/
│   └── VehicleConfigs.kt        # Vehicle & PID registry (declarative configurations)
├── data/
│   ├── AppSettings.kt           # SharedPreferences storage & degradation math
│   └── repository/              # OBD repository implementation
├── domain/
│   ├── model/ObdModels.kt       # Domain models & entities
│   └── usecase/                 # ReadCvtTemperature & ReadOilDegradation use cases
├── elm/
│   ├── Elm327Session.kt         # ELM327 AT protocol engine
│   └── ElmResponseParser.kt     # Low-level ELM response parsing
├── obd/
│   ├── CvtPid2103.kt            # PID 2103 specifications & byte offset constants
│   ├── CvtTempParser.kt         # Temperature payload decoder
│   ├── ExpressionEvaluator.kt   # Mathematical formula parser (infix-to-postfix evaluator)
│   └── ObdPayloadDecoder.kt     # Multi-frame ISO-TP byte assembler
├── ui/
│   ├── MainViewModel.kt         # UI StateFlow & orchestration
│   ├── PermissionsScreen.kt     # First-run permissions onboarding UI
│   └── theme/                   # Automotive dark theme (Colors, Shapes, Typography)
└── model/
    └── ErrorType.kt             # Typed error categorization
```

### Adding New PIDs or Vehicle Profiles
Adding a new PID or vehicle model is fully declarative. Simply edit [`VehicleConfigs.kt`](app/src/main/java/ru/shlyahten/cvt/config/VehicleConfigs.kt):

```kotlin
val CustomVehicle = VehicleConfig(
    name = "My Vehicle CVT",
    tempPid = PidConfig(
        modeAndPid = "21XX",
        headerHex = "7E1",
        formulas = mapOf(
            "Temp1" to "A * 256 + B - 40"
        ),
        valueIndex = 0
    ),
    oilDegradationPid = PidConfig(
        modeAndPid = "2110",
        headerHex = "7E1",
        formulas = mapOf(
            "Default" to "AC * 256 + AD"
        )
    )
)
```

---

## 🛠️ Testing & Verification

Run automated test suites and linters directly from the command line:

```bash
# Run local JVM unit tests
./gradlew test

# Run Android lint checks
./gradlew lint

# Run instrumented tests on connected device / emulator
./gradlew connectedAndroidTest
```

*(On Windows, use `.\gradlew.bat`)*

---

## ❓ Troubleshooting & FAQ

| Problem | Cause | Solution |
|:---|:---|:---|
| **No devices shown in list** | Bluetooth (Bluetooth 2) is disabled by Teyes power management, or adapter is not paired. | The app auto-enables Bluetooth 2, or tap **"Turn on Bluetooth"** on the banner. Ensure adapter is paired in system settings. |
| **Adapter not in list on Teyes head unit** | Teyes firmware isolates the Bluetooth phone stack, or adapter is asleep. | Tap **"Turn on Bluetooth"** if disabled, or click **"Enter MAC"** to input the adapter's MAC address directly. |
| **Floating widget does not appear** | Missing `SYSTEM_ALERT_WINDOW` permission. | Open Android Settings → Apps → Special app access → **Display over other apps** → Enable for **CVT**. |
| **Service stops when screen turns off** | OS battery optimization kills the process. | Disable battery optimization for the app (Settings → Battery → Unrestricted). |
| **Connection Error / Timeout** | Another app is holding the adapter connection (e.g. Torque, Car Scanner, CVTz50). | Close other OBD apps and turn the car ignition off and on. |
| **NO DATA / Bus Init Error** | The adapter is a faulty ELM327 v2.1 clone, or the vehicle ignition is OFF. | Verify vehicle ignition is ON. Ensure you are using an authentic ELM327 v1.5 with PIC18F25K80. |
| **Temp reads -36.6°C or 0** | ECU returned all zeros or connection was interrupted. | Check the live diagnostic log for exact responses to PID `2103`. |

---

## 🤝 Contributing

Contributions, bug reports, and pull requests are warmly welcome! Please check out [CONTRIBUTING.md](CONTRIBUTING.md) for full development guidelines.

1. Fork the repository.
2. Create your feature branch (`git checkout -b feature/amazing-feature`).
3. Commit your changes (`git commit -m 'feat: add amazing feature'`).
4. Push to the branch (`git push origin feature/amazing-feature`).
5. Open a Pull Request.

---

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.

---

## 📬 Contact & Credits

- **Author**: [shlyahten](https://github.com/shlyahten)
- **Repository**: [https://github.com/shlyahten/CVT](https://github.com/shlyahten/CVT)
- **Reference PIDs & Community Research**: Mitsubishi Lancer X Out-Club & Drive2 automotive research communities.
