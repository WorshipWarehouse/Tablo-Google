# Tablo TV Multiview

A native Android TV and Amazon Fire TV client application for Tablo Over-The-Air (OTA) DVR devices, built with Kotlin, Jetpack Compose for TV, ExoPlayer (Media3), Retrofit, and Room.

Inspired by the YouTube TV multiview experience, this application lets you monitor up to four live OTA TV broadcasts simultaneously with 10-foot D-pad navigation, full Electronic Program Guide (EPG), quick channel switching, and custom multiview preset saving.

---

## Key Features

- **YouTube TV-Style Multiview**:
  - Watch up to 4 simultaneous live OTA television streams in a customizable 2x2 grid.
  - Interactive audio switching: D-pad focus routes active audio to the selected tile while keeping secondary tiles live and muted.
  - Dedicated full-screen zoom mode with instant return to grid view.
  - Presets & Quick Saves: Save custom 4-channel multiview layouts to local Room storage for instant recall.

- **Electronic Program Guide (EPG)**:
  - Time-grid channel guide showing live broadcasts, upcoming programming, air times, and descriptions.
  - Channel badges displaying call signs, networks, and broadcast resolutions (1080i, 720p).
  - Quick-tune directly into any channel from the guide.

- **Tablo Device Discovery & Connectivity**:
  - Automatic local network discovery for Tablo DUAL, QUAD, and 4th Gen DVRs via UDP broadcast and the Tablo Association API (`api.tablotv.com`).
  - Manual IP address direct connection for custom subnets.
  - All channels, guide listings, search results, and streams come from the connected device — no demo or placeholder content.

- **TV-First 10-Foot UI**:
  - Designed specifically for Android TV, Google TV, and Amazon Fire TV remotes.
  - Clear visual focus rings, high-contrast Material 3 typography, and dark-canvas ergonomics.
  - Top navigation bar with quick access to **Multiview**, **Guide**, **Search**, **Saved**, and **Connect**.

---

## Tech Stack & Architecture

- **Language**: Kotlin 2.0+
- **UI Framework**: Jetpack Compose for TV / Material 3
- **Media Engine**: AndroidX Media3 (ExoPlayer) with multi-instance decoder management, `TextureView` surface binding, and adaptive track selection
- **Networking**: Retrofit 2 + Moshi (Kotlin reflection adapter) + OkHttp 4
- **Persistence**: Room Database (SQLite) with KSP code generation
- **State Management**: Android Architecture Components `ViewModel` + Kotlin Coroutines & `StateFlow`
- **Testing**: Robolectric & JUnit 4 for local JVM testing without emulators

---

## Project Structure

```text
app/src/main/java/com/example/
├── data/
│   ├── TabloRepository.kt            # Central repository: channels, guide, & watch streams
│   ├── local/
│   │   ├── AppDatabase.kt            # Room database configuration (v2)
│   │   ├── SavedMultiviewDao.kt      # DAO for saved multiview presets
│   │   ├── SavedMultiviewEntity.kt   # Preset entity model
│   │   ├── SavedMultiviewRepository.kt
│   │   ├── TabloDeviceDao.kt         # DAO for the persisted connected device
│   │   ├── TabloDeviceEntity.kt      # Connected-device entity model
│   │   └── TabloDeviceRepository.kt
│   └── remote/
│       ├── TabloApiService.kt        # Retrofit interface for the documented Tablo API
│       ├── TabloApiDto.kt            # Moshi data transfer objects
│       ├── TabloApiMapper.kt         # DTO -> app model mapping
│       ├── TabloTime.kt              # ISO-8601 parsing for guide times
│       └── TabloDiscoveryManager.kt  # UDP broadcast & association-server discovery
├── model/
│   └── TabloModels.kt                # TabloDevice, TabloChannel, TabloAiring, presets
├── playback/
│   └── MultiviewPlayerManager.kt     # Multi-instance Media3 player manager
└── ui/
    ├── TabloTvApp.kt                 # Main app scaffold & top navigation
    ├── TabloViewModel.kt             # Core app state & navigation controller
    ├── components/
    │   ├── TvVideoTile.kt            # TextureView Compose video tile with D-pad focus
    │   ├── TvQuickBar.kt             # Top navigation HUD
    │   └── TvRemoteKeyboard.kt       # On-screen D-pad keyboard
    ├── connect/                      # Device scan, direct IP, & disconnect
    ├── guide/                        # Time-grid Electronic Program Guide (EPG)
    ├── multiview/                    # 4-stream grid & full-screen view
    ├── saved/                        # Saved multiview presets screen
    ├── search/                       # Client-side search over loaded guide data
    └── theme/                        # Material 3 TV typography, colors, and shapes
```

---

## Getting Started

### Quick Testing & Installation (Pre-Built APK)

> **Note**: `outputs/tablo-multiview-debug.apk` is the **latest pre-built APK** generated directly by AI Studio for every iteration. **You do NOT need to build the application yourself or configure Gradle/Android SDK for normal testing.**

Simply pull the latest repository and install the pre-built APK directly onto your Android TV or Amazon Fire TV device:

```bash
cd ~/Tablo-Google
git pull
adb install -r outputs/tablo-multiview-debug.apk
```

If multiple devices are connected or targeting a specific TV over Wi-Fi/Ethernet:

```bash
adb -s DEVICE_IP:5555 install -r outputs/tablo-multiview-debug.apk
```

*(Replace `DEVICE_IP:5555` with your TV's actual IP address.)*

To launch the app immediately via ADB:

```bash
adb shell am start -n com.example/.MainActivity
```

---

### Optional: Manual Building from Source

If you wish to compile or modify the application locally:

- Android Studio Koala / Ladybug or newer
- Android SDK 36 (compileSdk 36, minSdk 24)
- Java 17+

#### Building the Project

Run Gradle to compile and assemble the debug APK:

```bash
gradle assembleDebug
```

The Gradle build automatically runs `copyDebugApkToOutputs`, placing the updated APK at:
```text
outputs/tablo-multiview-debug.apk
```

2. Run unit and Robolectric tests:
   ```bash
   gradle :app:testDebugUnitTest
   ```

#### Deploying & Sideloading via ADB (Android TV / Fire TV)

1. Enable **Developer Options** and **ADB Debugging** on your Fire TV or Android TV device:
   - **Fire TV**: *Settings > My Fire TV > Developer Options > ADB Debugging (ON)*
   - **Android TV / Google TV**: *Settings > Device Preferences > About > click 'Build' 7 times*, then *Settings > Device Preferences > Developer Options > USB Debugging (ON)*
2. Connect to your TV over your local network:
   ```bash
   adb connect <tv-ip-address>:5555
   ```
3. Install the current APK output directly:
   ```bash
   adb install -r outputs/tablo-multiview-debug.apk
   ```
   Or target specifically:
   ```bash
   adb -s <tv-ip-address>:5555 install -r outputs/tablo-multiview-debug.apk
   ```
4. Launch the application immediately via ADB:
   ```bash
   adb shell am start -n com.example/.MainActivity
   ```

---

## TV D-Pad Remote Navigation Model

The application features a 10-foot TV navigation hierarchy optimized for Fire TV and Android TV remotes:

| Context / Screen | Direction / Action | Behavior |
| :--- | :--- | :--- |
| **Multiview Grid** | **D-pad UP** | Moves focus up from top video tiles into the TV navigation bar (`TvQuickBar`) |
| **Multiview Grid** | **D-pad DOWN / LEFT / RIGHT** | Moves focus and active audio between multiview tiles |
| **Multiview Grid** | **D-pad CENTER (OK)** | Enters full-screen single-channel view (Solo mode) |
| **Multiview Grid** | **BACK Button** | Returns from Solo mode to 2x2 grid, or opens top navigation bar |
| **Top Navigation Bar** | **D-pad LEFT / RIGHT** | Slides focus across navigation pills (`MULTIVIEW`, `GUIDE`, `SEARCH`, `SAVED`, `TABLO`) |
| **Top Navigation Bar** | **D-pad DOWN** | Returns focus directly back to the active video tile or screen content |
| **Top Navigation Bar** | **D-pad CENTER (OK)** | Activates selected section or toggles multiview layout presets |
| **Guide Screen** | **D-pad UP on top channel** | Returns focus up to the top navigation bar |
| **Guide Screen** | **D-pad CENTER (OK)** | Opens program details with options to Watch Live or Assign to Multiview Tile |
| **Search Screen** | **On-Screen Keyboard** | Remote-friendly keyboard with quick category filters (Sports, Movies, Series, All) |

---

## License

Licensed under the Apache License, Version 2.0.
