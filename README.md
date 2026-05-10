# CoreAI Client

An Android test harness for the **CoreAI** on-device LLM service (`com.stridetech.coreai`). It connects over AIDL, exposes every service API through a sectioned UI, and streams logs to an in-app console.

---

## What it does

| Zone | Feature |
|------|---------|
| 1 — Connection | Bind/unbind to the CoreAI service with an API key |
| 2 — Engine Status | Query active model, downloaded models, loaded models, catalog; validate API key |
| 3 — Model Acquisition | Download a model by ID + URL (defaults to `gemma-3-1b-it-Q4_K_M.gguf`) |
| 4 — RAM & Disk Lifecycle | Load / unload / delete a model; promote a model to the active inference slot |
| 5 — Inference | Run a prompt against the active model; streams tokens live |
| 6 — Context & Session | Switch between `FULL_PROMPT` / `PER_CLIENT` context modes; reset chat history |
| 7 — Custom Chat Template | Inject or clear a JSON chat template override for any model |
| Console Log | Scrollable, selectable, timestamped log of every AIDL call and callback |

---

## Download

<div align="center">

### 📥 [Download APK](https://github.com/akshay-baiplawat/Core-Ai-Client/releases/download/CoreAIClient/CoreAiClient.apk)

**Min Android Version:** 12.0 (API 26) | **Size:** ~21 MB

[![Download APK](https://img.shields.io/badge/Download-APK-brightgreen?style=for-the-badge&logo=android)](https://github.com/akshay-baiplawat/Core-Ai-Client/releases/download/CoreAIClient/CoreAiClient.apk)

</div>

---

## Prerequisites

| Requirement | Detail |
|------------|--------|
| Android Studio | Ladybug or newer |
| Android SDK | API 26+ (minSdk) |
| CoreAI service | `com.stridetech.coreai` installed on the same device |
| API key | Issued by the CoreAI service |

---

## Getting started

```bash
# Clone
git clone https://github.com/akshay-baiplawat/Core-Ai-Client.git
cd Core-Ai-Client

# Open in Android Studio, let Gradle sync, then run on a device/emulator
# that has the CoreAI service installed.
```

1. Launch the app.
2. Enter your **API Key** in the Connection section.
3. Tap **Bind** — the status chip turns green when connected.
4. Use any zone to exercise the service.

---

## Project structure

```
app/src/main/
├── aidl/com/stridetech/coreai/
│   ├── ICoreAiInterface.aidl   # Full service API
│   └── ICoreAiCallback.aidl    # Async callback interface
└── java/com/stridetech/coreaiclient/
    ├── MainActivity.kt          # Jetpack Compose UI (7 zones + console)
    └── CoreAiViewModel.kt       # AIDL binding, state, all service calls
```

---

## Tech stack

- **Kotlin 2.0** + **Jetpack Compose** (Material 3)
- **AndroidViewModel** + **StateFlow** for UI state
- **AIDL** for IPC with the CoreAI background service
- AGP 9.2 / Gradle version catalog

---

## Context modes

| Mode | Behaviour |
|------|-----------|
| `PER_CLIENT` | Service tracks conversation history per caller UID. Send only the latest user turn. *(Default on bind)* |
| `FULL_PROMPT` | Service is stateless. Client must include full conversation history in every prompt. |

---

## Default model

The download URL pre-filled in Zone 3 points to:

```
https://huggingface.co/bartowski/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf
```

Any HTTPS URL accepted by the CoreAI service can be used instead.

---

## License

This project is proprietary to Stridetech. All rights reserved.
