# EZiK — Android personal assistant

EZiK is an Android 12+ voice assistant prototype using `VoiceInteractionService`, `VoiceInteractionSessionService`, Groq Whisper transcription, typed command planning, and optional Shizuku/Accessibility bridges.

## Current release

- Application ID: `viz.EZiK`
- Version: `0.2.3` / versionCode `5`
- Voice Interaction metadata includes a session service, recognition service, settings activity, and assist support.
- `ACTION_ASSIST` fallback renders the command surface directly on MIUI devices that do not open a normal voice session.
- Voice recording stop failures are handled so short recordings do not crash the session.
- Shizuku provider is packaged and declared with the exported mode required by Shizuku.
- The launcher activity now contains the same command surface as the assistant session: text input, voice recording, and command execution. It is not limited to setup buttons.

## Build

GitHub Actions builds the debug APK. Add a repository secret named `GROQ_API_KEY` under **Settings → Secrets and variables → Actions**, then run the **Build APK** workflow on the feature branch. The workflow passes the secret only to Gradle during the build.

For local builds:

```bash
gradle assembleDebug -PGROQ_API_KEY=your_key_here
```

Never commit the key. A debug APK contains the configured key and is for personal testing only. Before public distribution, move Groq calls behind an authenticated backend or short-lived token service.

## Device setup

1. Uninstall older EZiK builds if Android reports a package/version conflict.
2. Install the new APK and open it from the launcher once.
3. Grant microphone permission.
4. Select EZiK under **Settings → Apps → Default apps → Digital assistant app**. On MIUI, use the in-app **Open assistant settings** button if needed.
5. For the fallback action path, enable EZiK under **Accessibility settings**. This permission remains optional.
6. If using Shizuku, install and start the Shizuku app first, then grant EZiK access from the Shizuku application list.

The exact corner gesture is controlled by the device launcher and system settings. EZiK can receive the system assistant invocation once selected, but it cannot force Xiaomi/MIUI to map a gesture that the launcher does not expose. The current wake-word recognition is command-prefix recognition after recording starts; it is not an always-listening background hotword. A true wake word requires an explicit foreground microphone service and a hotword engine, plus a persistent Android notification and device-specific battery settings. Shizuku permission does not bypass these Android microphone/privacy rules.

## Supported prototype actions

The planner recognizes EZiK wake-word prefixes such as `EZiK`, `Hey EZiK`, `إيزيك`, and `يا إيزيك`, then supports launching matching installed apps, web search, URL opening, and app search when the target app supports Android's `ACTION_SEARCH` intent. Saying only the name produces an acknowledgement.

Groq transcription uses the official multilingual `whisper-large-v3-turbo` endpoint. The project also includes the shared command UI binder from the latest Claude update so the normal voice session and the MIUI assist fallback use the same recording/execution path.

## Safety note

Model output must be converted to allow-listed typed actions. Do not execute arbitrary model-generated shell commands. Shizuku and Accessibility should only be used for explicitly implemented, reviewable actions.
