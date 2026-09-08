# 🎲 Neo Ludo — Production-Ready Android Multiplayer Game (Ad-Free)

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.0-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Android Gradle Plugin](https://img.shields.io/badge/AGP-8.5.0-green.svg?logo=android)](https://developer.android.com/studio/releases/gradle-plugin)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.06.00-4285F4.svg?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![MinSdk](https://img.shields.io/badge/MinSdk-24-orange.svg)](https://developer.android.com)
[![TargetSdk](https://img.shields.io/badge/TargetSdk-34-brightgreen.svg)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-purple.svg)](LICENSE)
[![Ad-Free](https://img.shields.io/badge/Ads-Zero%20%2F%20100%25%20Free-red.svg)](#)

An original, production-ready, ad-free Android multiplayer Ludo game engineered with **Kotlin 2.0** and **Jetpack Compose** for Android 14+ (minSdk 24, targetSdk 34/35). 

Featuring a **100% deterministic pure Kotlin rule engine**, custom high-performance **60 FPS Canvas-rendered Neo-Ludo board graphics**, dynamic **3D-styled animated dice with spring physics**, low-latency **SoundPool audio & tactile haptics**, **DataStore persistence**, and **real-time synchronized online multiplayer** backed by Firebase Authentication and Firebase Realtime Database with action-based canonical state authority and automatic AI proxy reconnection handling.

---

## ✨ Features & Game Modes

### 🎮 Game Modes
1. **Play with Friends**: Create private rooms with custom 6-character room codes (`NL-XXXXXX`), real-time waiting lobby, 1-tap copy/share intent, host bot filling, and atomic player presence.
2. **Play Online**: Real-time room matchmaking & quick join with real players worldwide.
3. **Pass & Play (Local)**: 100% offline match for 2, 3, or 4 players on a single device screen with zero latency.
4. **Vs Computer (AI)**: Solo play against 1–3 intelligent bots across 3 difficulty tiers (`Easy`, `Normal`, `Hard`).

### 🌐 Online Play — Zero Setup (Free Public Relay)

Private online rooms **just work after install — no account, no server, no config file.**
The app syncs over a free public MQTT relay (HiveMQ / EMQX, TLS-first) with the
same host-authoritative engine as Firebase mode: the room host's phone computes
canonical turns, late joiners catch up from the retained snapshot, and presence
(explicit leave + crash detection) drives host migration and AFK AI takeover.

**To play with friends (up to 4, anywhere):**
1. Everyone installs the **same APK** and opens the game (internet required).
2. Host: **Play with Friends → Create Room** → pick player count → share the
   `NL-XXXXXX` code (Copy / Share buttons in the lobby).
3. Friends: **Join Room** → enter the code → **Ready Up**.
4. Host taps **Start Game** (can fill empty seats with bots).

Notes & limits:
- Everyone in a room converges on one relay automatically (sticky server +
  multi-relay join scan), and reconnects return to the room's own relay — if
  the lobby shows "Reconnecting…", stay on the screen or tap RETRY.
- Public relays are best-effort (no SLA) and obscurity-private: room traffic is
  unlisted but guessable from the code, so don't share personal info in names/chat.
- If the host's app dies mid-match, the lowest-joined connected player takes
  over automatically; AFK players are auto-played after the turn timer.
- Prefer your own backend? Add `app/google-services.json` from your Firebase
  project (enable Anonymous Auth + Realtime Database, deploy
  `database.rules.json`) and rebuild — the app switches to Firebase
  automatically with zero code changes.

### 🧠 Pure Kotlin Deterministic Rule Engine
- Standard **15x15 Ludo coordinate grid** with 52 perimeter path cells.
- **8 Designated Safe Zones**: 4 color starting tiles (`0, 13, 26, 39`) and 4 laser-cut star cells (`8, 21, 34, 47`) where pieces peacefully coexist without capture.
- **Capture Mechanics**: Landing on an opponent piece on an unsafe tile sends them back to their corner Yard and awards the attacking player an immediate **Extra Bonus Turn**.
- **Bonus Turns**: Awarded upon rolling a `6`, capturing an enemy piece, or scoring a token into Home.
- **3x Consecutive Sixes Penalty**: Official tournament rule forfeiting the turn on 3 consecutive 6s (customizable in rules).
- **Exact Roll Home Entry**: Private 5-step colored home stretch requiring an exact dice roll to reach center Home (`step 56`).

### ⚡ Realtime Multiplayer Architecture
- **Action-Based Authority:** Clients submit actions (`ROLL_DICE`, `MOVE_PIECE`, `PASS_TURN`, `SET_READY`), and the designated host/authority deterministically computes canonical `GameState` updates and monotonically increments `version`.
- **Zero Polling & Zero Fake Fallbacks:** 100% reactive listeners via Firebase Realtime Database SDK (`ValueEventListener` & `ChildEventListener`).
- **Atomic Joins:** Firebase Realtime Database transactions prevent race conditions, seat collisions, and color conflicts.
- **Presence & Auto Reconnection:** Real-time `.info/connected` status tracking with automatic state reconciliation upon reconnection.
- **Deterministic Timeout & AI Takeover:** Authoritative turn timer resolution prevents competing client actions when a player disconnects or is AFK.
- **Host Migration:** If the room host disconnects, the lowest connected UID is automatically elected as the new authoritative host with incremented `hostEpoch`.

---

## 🏛️ Project Structure

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
├── multiplayer/         # Multiplayer Architecture
│   ├── MultiplayerClient.kt         # Unified Game Session Interface
│   ├── LocalMultiplayerClient.kt    # Offline Pass & Play Controller
│   ├── BotMultiplayerClient.kt      # Offline Human vs AI Bot Controller
│   ├── FirebaseMultiplayerClient.kt # Real-time Synchronized Network Multiplayer
│   ├── backend/         # FirebaseAuthDataSource, FirebaseRoomDataSource
│   ├── model/           # RoomMetadata, PlayerPresence, NetworkAction, NetworkEvent, ChatEvent
│   ├── repository/      # RoomRepository, ActionRepository, PresenceRepository, ChatRepository
│   └── sync/            # ActionDeduplicator, AuthoritativeGameProcessor, HostElectionManager
├── data/
│   ├── datastore/       # PreferencesDataStore (Theme, Audio, Timer, Rules)
│   └── repository/      # SettingsRepository, ProfileRepository, StatsRepository, FriendRepository
└── ui/                  # Jetpack Compose Presentation Layer
    ├── navigation/      # NeoLudoNavHost, Typed Routes
    ├── home/            # HomeHeader, PlayWithFriendsCard, GameModeGrid, DailyReward
    ├── room/            # CreateRoomScreen, JoinRoomScreen, LobbyWaitingRoomScreen
    ├── game/            # GameScreen, CanvasLudoBoard, TwoPlayerArcadeBottomBar, EmoteOverlay
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

# Run all unit and integration tests
./gradlew test

# Assemble the debug APK
./gradlew assembleDebug

# Assemble the release APK
./gradlew assembleRelease
```

---

## 🧪 Automated Test Suite

- **`LudoGameEngineTest`**: Engine rules, exact home rolls, bonus turns, 3x sixes penalty, multi-player rankings.
- **`LudoBotEngineTest`**: AI decision heuristics, safe cell threat evaluation, full game simulation.
- **`MultiplayerSyncTest`**: Action deduplication, host election on disconnect, authoritative game processor, turn flow, piece captures, room code normalization, and local turn cycles.
- **`EconomyAndStatsTest`**: Lifetime stats, win rate caps, currency spending, cosmetics unlocks.

---

## 📄 License

```text
MIT License

Copyright (c) 2026 Neo Ludo Contributors
```
