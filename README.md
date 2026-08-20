# 🎲 Neo Ludo — Production-Ready Android Multiplayer Game (Ad-Free)

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.0-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Android Gradle Plugin](https://img.shields.io/badge/AGP-8.5.0-green.svg?logo=android)](https://developer.android.com/studio/releases/gradle-plugin)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.06.00-4285F4.svg?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![MinSdk](https://img.shields.io/badge/MinSdk-24-orange.svg)](https://developer.android.com)
[![TargetSdk](https://img.shields.io/badge/TargetSdk-34-brightgreen.svg)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-purple.svg)](LICENSE)
[![Ad-Free](https://img.shields.io/badge/Ads-Zero%20%2F%20100%25%20Free-red.svg)](#)

An original, production-ready, ad-free Android multiplayer Ludo game engineered with **Kotlin 2.0** and **Jetpack Compose** for Android 14+ (minSdk 24, targetSdk 34/35). 

Featuring a **100% deterministic pure Kotlin rule engine**, custom high-performance **60 FPS Canvas-rendered Neo-Ludo board graphics**, dynamic **3D-styled animated dice with spring physics**, low-latency **SoundPool audio & tactile haptics**, **DataStore persistence**, and **real-time synchronized online multiplayer** with automatic AI proxy reconnection handling.

---

## 📥 Download Direct APK

Get the pre-built, ready-to-install debug APK directly from the repository:

🔗 **[Download NeoLudo-v1.0.0.apk (Direct from Repo)](./release-apk/NeoLudo-v1.0.0.apk)**

---

## ✨ Features & Game Modes

### 🎮 Game Modes
1. **Play Online**: Real-time room matchmaking & quick join with real players worldwide.
2. **Play with Friends**: Create private rooms with custom 6-character room codes (`NL-XXXX`), real-time waiting lobby, 1-tap copy/share intent, and player ready states.
3. **Pass & Play (Local)**: 100% offline match for 2, 3, or 4 players on a single device screen with zero latency.
4. **Vs Computer (AI)**: Solo play against 1–3 intelligent bots across 3 difficulty tiers (`Easy`, `Normal`, `Hard`).

### 🧠 Pure Kotlin Deterministic Rule Engine
- Standard **15x15 Ludo coordinate grid** with 52 perimeter path cells.
- **8 Designated Safe Zones**: 4 color starting tiles (`0, 13, 26, 39`) and 4 laser-cut star cells (`8, 21, 34, 47`) where pieces peacefully coexist without capture.
- **Capture Mechanics**: Landing on an opponent piece on an unsafe tile sends them back to their corner Yard and awards the attacking player an immediate **Extra Bonus Turn**.
- **Bonus Turns**: Awarded upon rolling a `6`, capturing an enemy piece, or scoring a token into Home.
- **3x Consecutive Sixes Penalty**: Official tournament rule forfeiting the turn on 3 consecutive 6s (customizable in rules).
- **Exact Roll Home Entry**: Private 5-step colored home stretch requiring an exact dice roll to reach center Home (`step 56`).

### 🤖 3-Tier AI Opponent Engine
- **Easy AI**: Release priority on 6 with random exploration.
- **Normal AI (Tactical Heuristics)**: Weighted tactical decision tree (`Score Home > Capture Enemy > Release from Yard > Safe Zone Entry > Escape Threat > Advance`).
- **Hard AI (Threat Probability & Danger Heatmap)**: Computes enemy strike zones, risk ratios, vulnerability penalties, and positional safety scoring.

### 🎨 Neo-Ludo "Velvet & Obsidian" Design System
- **Obsidian Dark OLED** (`#0B0E14`, `#151B26`) and **Titanium Light** (`#F0F4F8`) themes.
- Saturated player accents: **Ruby Red** (`#FF3366`), **Emerald Green** (`#00E676`), **Amber Yellow** (`#FFD600`), and **Cobalt Blue** (`#2979FF`).
- **60 FPS Custom Canvas Board**: 100% vector geometry with concentric pawn slots, glowing piece halos, and clustered radial multi-piece layouts when multiple tokens share a single cell.
- **3D Animated Dice**: Isometric beveled cube shading, drop shadow, specular reflections, and physics tumble/bounce animations.

### ⚡ Networking & Sync Engine
- Unified `MultiplayerClient` interface cleanly decoupling game presentation from networking.
- Real-time Firebase Realtime Database sync with monotonic sequence validation via `StateReconciler`.
- **Connection Resilience**: 30-second disconnect grace period with automatic `DisconnectAiProxy` takeover so matches never freeze for remaining players.
- **Floating Emotes & Quick Chat**: Ephemeral floating reaction emojis (`🔥`, `😂`, `👏`, `😭`, `🎯`, `👑`) and preset quick chats.

---

## 🏛️ Project Architecture

```text
com.neoludo.game/
├── core/
│   ├── audio/           # SoundController (SoundPool), HapticController (Vibrator)
│   ├── designsystem/    # Theme, Colors, Typography, Glowing Buttons, Player Plates
│   └── model/           # UserProfile, GameSettings, UserStats, Friend, ThemeMode
├── engine/              # Pure Kotlin Deterministic Engine (0 Android Dependencies)
│   ├── coordinate/      # BoardCoordinates, GridCoord, 15x15 Grid Math, 52-path mapping
│   ├── model/           # GameState, PlayerState, Piece, PiecePosition, DiceState, TurnPhase
│   ├── rules/           # MoveValidator, MoveCalculation, Capture Logic
│   └── ai/              # LudoBotEngine, Difficulty (Easy, Normal, Hard), Danger Heatmaps
├── multiplayer/         # Multiplayer Client Abstraction Layer
│   ├── MultiplayerClient.kt         # Unified Game Session Interface
│   ├── LocalMultiplayerClient.kt    # Offline Pass & Play Controller
│   ├── BotMultiplayerClient.kt      # Offline Human vs AI Bot Controller
│   ├── FirebaseMultiplayerClient.kt # Real-time Synchronized Network Multiplayer
│   ├── model/           # RoomMetadata, PlayerPresence, NetworkAction, ChatEvent
│   └── sync/            # StateReconciler, ReconnectManager, DisconnectAiProxy
├── data/
│   ├── datastore/       # PreferencesDataStore (Theme, Audio, Timer, Rules)
│   └── repository/      # SettingsRepository, ProfileRepository, StatsRepository, FriendRepository
└── ui/                  # Jetpack Compose Presentation Layer
    ├── navigation/      # NeoLudoNavHost, Typed Routes
    ├── splash/          # Fast Vector Morph Splash
    ├── home/            # Main Dashboard & Game Mode Selection Cards
    ├── room/            # CreateRoomScreen, JoinRoomScreen, LobbyWaitingRoomScreen
    ├── game/            # GameScreen, CanvasLudoBoard, Dice3DRenderer, EmoteOverlay
    ├── result/          # GameResultScreen, Podium Rankings & Match Highlights
    ├── profile/         # ProfileScreen, 16 Avatar Selectors, Lifetime Stats
    ├── friends/         # FriendsScreen, Live Online Status, Add Friend Dialog
    ├── settings/        # SettingsScreen, Theme Picker, Volume Sliders, Rule Defaults
    └── rules/           # RulesGuideScreen, Visual Handbook
```

---

## 🛠️ Building & Running Locally

### Prerequisites
- Android Studio Iguana / Jellyfish / Koala or newer
- JDK 17
- Android SDK (compileSdk 34)

### Build Commands
```bash
# Clone the repository
git clone https://github.com/soupashh-ship-it/Neo-Ludo.git
cd Neo-Ludo

# Run all pure Kotlin unit tests (Rules, AI, Multiplayer Sync)
./gradlew testDebugUnitTest

# Assemble the debug APK
./gradlew assembleDebug
```

The generated APK will be available at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 🧪 Automated Test Suite

The project includes unit test coverage for the core game mechanics:
- **`LudoGameEngineTest`**:
  - `testDiceRollUpdatesStateAndLegalMoves`
  - `testYardPieceEntersBoardOnSix`
  - `testStandardPathAdvancement`
  - `testSafeZonesPreventCaptures`
  - `testCaptureOnUnsafeCellSendsEnemyToYardAndGrantsBonusTurn`
  - `testExactRollRequiredForHome`
  - `testReachingHomeGrantsBonusTurn`
  - `testThreeConsecutiveSixesForfeitsTurn`
  - `testMultiplayerRankingAndGameOver`
- **`LudoBotEngineTest`**:
  - `testEasyBotReleasesPieceOnSix`
  - `testNormalBotPrefersWinningOverCapturing`
  - `testNormalBotPrefersCapturingOverSimpleMove`
  - `testAutomatedAiGameSimulationPlaysOnlyLegalMoves`
- **`MultiplayerSyncTest`**:
  - `testStateReconcilerRejectsDuplicatesAndOutOfOrderPackets`
  - `testDisconnectAiProxyExecutesLegalMoveWhenWaitingForRoll`
  - `testDisconnectAiProxyExecutesLegalMoveWhenWaitingForMove`
  - `testLocalMultiplayerClientTurnCycle`
  - `testBotMultiplayerClientInitialization`

---

## 📄 License

```text
MIT License

Copyright (c) 2026 Neo Ludo Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
```
