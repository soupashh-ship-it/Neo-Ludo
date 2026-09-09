# Neo Ludo — Ad-Free Android Ludo

Neo Ludo is a Kotlin + Jetpack Compose Ludo app for Android (minSdk 24, targetSdk 34) with offline play, bots, and private online rooms for up to four friends.

## What works immediately after install

The default online mode needs **no server address, no same Wi-Fi, no Firebase file, and no account setup on the players' phones**. It uses public HiveMQ/EMQX MQTT relays (TLS first, TCP fallback) for private room-code games.

A typical four-friend flow is:

1. Install the same app version on all phones and connect each phone to the internet.
2. One player opens **Play With Friends → Create Private Room**.
3. Share the six-character room code.
4. The other players choose **Join With Room Code**, enter the code, and ready up.
5. The host starts the match.

The phones may be on different ISPs/mobile networks and in different parts of India. No LAN or port-forwarding is required.

### Important production distinction

The built-in MQTT path is deliberately zero-configuration and internet reachable, but HiveMQ/EMQX public brokers are third-party, best-effort relay infrastructure with no application-specific SLA or authentication guarantee. Neo Ludo therefore hardens the protocol itself with deterministic admission, canonical snapshots, version/authority fencing, duplicate/stale action rejection, reconnect recovery, host migration, and room tombstones, but an app cannot turn a public broker into an owned production service.

For an owned, authenticated backend, add a Firebase project as described below. The app automatically chooses Firebase when a valid `google-services.json` is present; otherwise it uses the zero-setup MQTT mode.

## Game modes

- **Play With Friends** — private online room-code games for up to four real players.
- **Pass & Play** — 2–4 players on one device, fully offline.
- **Vs Computer** — play against bots with multiple difficulty levels.

There is no fake worldwide matchmaking service in this release. The Friends entry is a room-code play hub, not a fabricated online-presence directory.

## Canonical Ludo rules

The game uses one deterministic Kotlin engine for move legality and state transitions. The configured standard rules include:

- four pieces per player;
- a six is required to leave the yard;
- safe/start cells cannot be captured on;
- landing on an opponent on an unsafe path cell captures it;
- exact movement is required to reach Home;
- overshooting Home is illegal;
- a six grants a bonus roll;
- captures and reaching Home grant bonus turns when enabled by the room rules;
- three consecutive sixes forfeit the turn when that option is enabled;
- rankings/game completion are determined from finished pieces/players.

## Multiplayer synchronization

### Shared protocol safety

Every online game state carries:

- a monotonically increasing `version`;
- an authority generation (`authorityEpoch`);
- the authoritative host id (`authorityHostId`).

Actions carry the state version and host generation they were based on. The authority rejects stale-version, stale-host, wrong-player, illegal-phase, duplicate, and illegal-move requests. Dice values for online games are produced by the authoritative processor; normal clients do not choose their dice result.

A full canonical snapshot is retained so reconnecting clients can recover without replaying an unbounded event stream. Older snapshots and same-generation host forks are ignored.

### Zero-setup MQTT mode

- Scans HiveMQ and EMQX only while locating a room, then pins that room to the broker network where it was found.
- Uses retained room metadata, player presence, and canonical game state for recovery.
- Uses crash-disconnect wills plus heartbeats and explicit leave handling.
- Existing logical players may reclaim their seat after process death/background termination, including after the match has started.
- Host migration advances the authority epoch so old-host frames cannot fork the match.
- Simultaneous joins converge through deterministic post-join admission; at most the configured four live human seats survive.
- Old room codes are tombstoned rather than recycled, avoiding retained-state resurrection.

### Optional Firebase mode

Firebase gives the project an owned authenticated data plane when you provide your own Firebase project:

1. Create a Firebase Android app for package `com.neoludo.game`.
2. Put its `google-services.json` at `app/google-services.json`.
3. Enable **Anonymous Authentication**.
4. Create a **Realtime Database**.
5. Deploy the included `database.rules.json`.

Example with the Firebase CLI from the repository root:

```bash
firebase login
firebase use <your-project-id>
firebase deploy --only database
```

The database rules restrict player writes to the player's own presence record and canonical state mutation to the current room host/authority, while validating room/action fields.

## Project structure

```text
app/src/main/java/com/neoludo/game/
├── core/                  # audio, haptics, design system, shared models
├── engine/                # deterministic rules, board coordinates, bots
├── multiplayer/
│   ├── backend/           # Firebase + MQTT transport data sources
│   ├── model/             # rooms, presence, actions, events
│   ├── repository/        # multiplayer repositories
│   └── sync/              # authority, dedupe, election, protocol guards
├── data/                  # DataStore/profile/settings/stat repositories
└── ui/                    # Compose screens and navigation
```

## Build

### Prerequisites

- JDK 17 or newer supported by the configured Android Gradle Plugin
- Android SDK / compileSdk 34
- Android Studio or a working Gradle wrapper environment

### Commands

Windows:

```powershell
.\gradlew.bat clean test assembleDebug lint
.\gradlew.bat assembleRelease
```

macOS/Linux:

```bash
./gradlew clean test assembleDebug lint
./gradlew assembleRelease
```

A release APK intended for distribution should be signed with your own Android signing keystore. Do not commit private signing keys or service credentials to source control.

## Validation and audit

See `PROJECT_AUDIT_AND_FIX_REPORT.md` for the exact fixes applied, checks actually run, environment limitations, and any remaining external deployment requirements. Do not treat historical APKs or historical test reports as proof that the current source was freshly built.

## License

MIT License. See `LICENSE`.
