# LLMBalance

[English](README.md) | [中文](README_zh.md)

An Android app to manage LLM API keys, query account balances, and receive push notifications when Claude completes long-running tasks.

## Features

- **API Key Management** — Add, save, and manage multiple LLM provider API keys (DeepSeek, OpenAI, etc.)
- **Balance Query** — Check account balance and status for each key, with auto-refresh every 5 seconds
- **Push Notifications** — Connect to an [ntfy](https://ntfy.sh/) server via WebSocket to receive instant push notifications when Claude finishes a task
- **Multi-language** — Supports English and Chinese (switch in Settings)
- **Background Service** — Persistent foreground service for reliable push delivery

## Screenshots

| Main Page | Settings |
|---|---|
| API key list with balance | Language, notifications, server config |

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Material Components, ViewBinding, RecyclerView |
| Architecture | MVVM (ViewModel + LiveData) |
| HTTP | OkHttp 4 |
| Push | WebSocket foreground service |
| Build | Gradle KTS, Android SDK 34, minSdk 26 |

## Project Structure

```
app/src/main/java/com/llmbalance/app/
├── MainActivity.kt              # Main screen: key list & balance
├── data/
│   ├── BalanceApi.kt            # HTTP client for balance queries
│   ├── KeyStore.kt              # SharedPreferences-backed key storage
│   ├── ProviderConfig.kt        # LLM provider definitions
│   └── SettingsStore.kt         # App settings persistence
├── ntfy/
│   ├── NtfyService.kt           # WebSocket foreground service
│   └── NotificationHelper.kt    # Notification channel & test
└── ui/
    ├── BaseActivity.kt          # Locale-aware base activity
    ├── BalanceAdapter.kt        # RecyclerView adapter
    ├── MainViewModel.kt         # ViewModel for main screen
    ├── settings/
    │   └── SettingsActivity.kt   # Settings screen
    └── util/
        └── LocaleHelper.kt      # Language switching helper
```

## Build & Install

### Prerequisites

- Android SDK 34
- JDK 17
- Gradle (wrapper included)

### Build

```bash
# Debug APK (for testing)
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease

# Sign the release APK (replace with your keystore)
apksigner sign --ks your.keystore \
  --out app-release.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk
```

### Install

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Ntfy Push Setup

1. Set up an [ntfy server](https://ntfy.sh/) (self-hosted or use ntfy.sh)
2. Create a topic for your notifications
3. In the app Settings, enter your server URL and topic
4. Enable notifications and set up a Claude Code **Stop hook**:

```json
{
  "stop": [
    {
      "type": "command",
      "command": "curl -H \"Title: Claude Complete\" -H \"Priority: 4\" -d \"Task finished!\" https://your-ntfy-server/your-topic"
    }
  ]
}
```

## License

MIT
