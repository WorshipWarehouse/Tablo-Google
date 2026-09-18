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

### Prerequisites

- Android Studio Koala / Ladybug or newer
- Android SDK 36 (compileSdk 36, minSdk 24)
- Java 17+

### Building the Project

1. Open the project in Android Studio or use the Gradle wrapper:
   ```bash
   gradle :app:assembleDebug
   ```
2. Run unit and Robolectric tests:
   ```bash
   gradle :app:testDebugUnitTest
   ```

### Deploying to Android TV / Fire TV

1. Enable **Developer Options** and **ADB Debugging** on your Android TV or Amazon Fire TV device.
2. Connect via ADB:
   ```bash
   adb connect <device-ip-address>:5555
   ```
3. Install and run:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## Remote Control Navigation

| Action | Fire TV / Android TV Remote |
| :--- | :--- |
| **Move Focus** | D-pad Up / Down / Left / Right |
| **Select / Focus Audio** | D-pad Center (OK / Select) |
| **Full-Screen Video** | Press OK on active multiview tile or click Expand |
| **Return to Grid** | Back button |
| **Channel Quick Switch** | D-pad while in Multiview or Guide |

---

## License

Licensed under the Apache License, Version 2.0.
