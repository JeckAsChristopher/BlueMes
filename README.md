# BlueMes — Offline Bluetooth Messenger

A fully offline Android messaging app that lets nearby users chat using only Bluetooth Classic — no internet, no accounts, no cloud.

---

## Features

- **Nearby Discovery** — Automatically scans for and lists nearby devices running BlueMes
- **Real-time Chat** — Full duplex Bluetooth Classic RFCOMM communication
- **Chat History** — All messages persisted locally with Room (SQLite)
- **Typing Indicators** — See when the other person is typing
- **Settings** — Change username, toggle dark mode, clear chat history
- **Dark Mode** — Full Material3 dark theme support
- **Offline-only** — No internet, no Firebase, no accounts required

---

## Requirements

- Android 8.0+ (API 26+)
- Bluetooth-enabled Android device
- Android 12+ devices need `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT` permissions

---

## Building

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34

### Steps

1. Clone the repository
2. Open `BlueMes/` in Android Studio
3. Sync Gradle
4. Build → Run on a physical Android device

> **Note:** Bluetooth Classic discovery does NOT work on emulators. You need two real Android devices to test peer-to-peer messaging.

---

## Architecture

```
app/src/main/java/com/bluemes/app/
├── bluetooth/
│   ├── BluetoothService.kt          # RFCOMM server/client, per-peer read threads
│   └── BluetoothDiscoveryManager.kt # BroadcastReceiver for device discovery
├── data/
│   ├── local/
│   │   ├── BlueMesDatabase.kt       # Room database
│   │   ├── dao/                     # ConversationDao, MessageDao
│   │   └── entities/                # Room entity data classes
│   └── repository/
│       └── ChatRepository.kt        # Single source of truth for chat data
├── models/
│   ├── BluetoothDevice.kt           # NearbyUser, ConnectionState
│   └── Message.kt                   # MessagePacket, PacketType (wire format)
├── ui/
│   ├── main/    MainActivity.kt
│   ├── splash/  SplashFragment + (no ViewModel needed)
│   ├── setup/   SetupFragment + SetupViewModel
│   ├── nearby/  NearbyFragment + NearbyViewModel + NearbyUserAdapter
│   ├── chat/    ChatFragment + ChatViewModel + MessageAdapter
│   ├── history/ HistoryFragment + HistoryViewModel + ConversationAdapter
│   └── settings/ SettingsFragment + SettingsViewModel
└── utils/
    ├── UserPreferences.kt            # DataStore wrapper
    └── Constants.kt                  # Service UUID, timeouts, buffer sizes
```

---

## Messaging Protocol

Each message is a JSON-serialized `MessagePacket` terminated with `\n`:

```json
{
  "id": "uuid-v4",
  "type": "TEXT_MESSAGE",
  "senderAddress": "AA:BB:CC:DD:EE:FF",
  "senderName": "Alice",
  "content": "Hello!",
  "timestamp": 1700000000000,
  "protocolVersion": 1
}
```

**Packet types:** `HANDSHAKE`, `HANDSHAKE_ACK`, `TEXT_MESSAGE`, `TYPING_START`, `TYPING_STOP`, `READ_RECEIPT`, `DISCONNECT`

---

## GitHub Actions

The included `.github/workflows/android.yml` workflow:
- Triggers on push to `main`/`master`/`develop` and all pull requests
- Builds a debug APK using JDK 17
- Uploads the APK as a build artifact (retained 30 days)
- Runs unit tests

---

## Known Limitations

- Bluetooth Classic discovery requires Location permission on Android < 12
- Discovery visibility depends on device discoverability settings
- Background discovery is limited by Android Doze mode
- Message delivery is best-effort — no end-to-end encryption by default (can be added)

---

## License

MIT
