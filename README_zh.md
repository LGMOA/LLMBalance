# LLMBalance

一款用于管理 LLM API 密钥、查询账户余额，并在 Claude 完成长时间任务时接收推送通知的 Android 应用。

## 功能特性

- **API 密钥管理** — 添加、保存和管理多个 LLM 提供商的 API 密钥（DeepSeek、OpenAI 等）
- **余额查询** — 查询每个密钥的账户余额和状态，支持每 5 秒自动刷新
- **推送通知** — 通过 WebSocket 连接到 [ntfy](https://ntfy.sh/) 服务器，在 Claude 完成任务时即时接收推送通知
- **多语言支持** — 支持英文和中文（可在设置中切换）
- **后台服务** — 持久化的前台服务，确保推送消息可靠送达

## 截图

| 主页 | 设置 |
|---|---|
| API 密钥列表及余额 | 语言、通知、服务器配置 |

## 技术栈

| 层级 | 技术 |
|---|---|
| 语言 | Kotlin |
| UI | Material Components、ViewBinding、RecyclerView |
| 架构 | MVVM（ViewModel + LiveData） |
| HTTP | OkHttp 4 |
| 推送 | WebSocket 前台服务 |
| 构建 | Gradle KTS、Android SDK 34、minSdk 26 |

## 项目结构

```
app/src/main/java/com/llmbalance/app/
├── MainActivity.kt              # 主界面：密钥列表与余额
├── data/
│   ├── BalanceApi.kt            # 余额查询 HTTP 客户端
│   ├── KeyStore.kt              # 基于 SharedPreferences 的密钥存储
│   ├── ProviderConfig.kt        # LLM 提供商配置
│   └── SettingsStore.kt         # 应用设置持久化
├── ntfy/
│   ├── NtfyService.kt           # WebSocket 前台服务
│   └── NotificationHelper.kt    # 通知渠道与测试
└── ui/
    ├── BaseActivity.kt          # 支持多语言的基类 Activity
    ├── BalanceAdapter.kt        # RecyclerView 适配器
    ├── MainViewModel.kt         # 主界面 ViewModel
    ├── settings/
    │   └── SettingsActivity.kt   # 设置界面
    └── util/
        └── LocaleHelper.kt      # 语言切换辅助类
```

## 构建与安装

### 前提条件

- Android SDK 34
- JDK 17
- Gradle（项目已包含 wrapper）

### 构建

```bash
# 调试版 APK（用于测试）
./gradlew assembleDebug

# 发布版 APK
./gradlew assembleRelease

# 对发布版 APK 签名（替换为你的 keystore）
apksigner sign --ks your.keystore \
  --out app-release.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk
```

### 安装

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Ntfy 推送设置

1. 搭建 [ntfy 服务器](https://ntfy.sh/)（可自托管或使用 ntfy.sh）
2. 为你的通知创建一个主题（topic）
3. 在应用的设置中，输入服务器地址和主题名称
4. 启用通知，并配置 Claude Code 的 **Stop 钩子**：

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

## 许可证

MIT
