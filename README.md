# EZiK — Android 12+ personal assistant

EZiK is registered as an Android `VoiceInteractionService`, so after the user grants the assistant role it can be invoked through the system assistant gesture (including the configured corner gesture on supported launchers/devices).

## What is implemented in this revision

- Package identity is now `com.vizmazen.EZiK`.
- `VoiceInteractionService` and `VoiceInteractionSessionService` are registered with the required `BIND_VOICE_INTERACTION` permission.
- The app requests the Android assistant role through `RoleManager`.
- Android 12+ is the current minimum supported version (`minSdk 31`).
- Shizuku is an optional bridge: EZiK detects whether Shizuku is running, requests its permission, and reports readiness. Accessibility remains an independent fallback.
- Voice commands are recorded locally as m4a, transcribed through Groq's multilingual `whisper-large-v3-turbo`, and then passed to a typed command planner.
- The planner currently supports app launch, web search, URL opening, and app search when the target app exposes Android's search intent.
- The planner recognizes `EZiK`, `E-Zik`, `إيزيك`, `ايزيك`, `Hey EZiK`, and `يا إيزيك` at the beginning of a transcript. Saying only the name produces an acknowledgement instead of searching for an app.
- The Shizuku provider artifact is included explicitly so Android can instantiate the declared provider without crashing at process startup.
- The setup screen provides direct buttons for assistant settings, Accessibility settings, and EZiK app settings.

This is a command-prefix wake word, not an always-listening hotword. The system assistant gesture must first open EZiK and then the voice recording button records the command. A true background wake word requires a separate on-device hotword engine and a foreground microphone service, which is intentionally not enabled yet for privacy and battery reasons.

## Groq configuration

For a local debug build, provide `GROQ_API_KEY` as a Gradle property or environment variable; do not commit it:

```bash
gradle assembleDebug -PGROQ_API_KEY=your_key_here
```

For GitHub Actions, add the key once under **Repository Settings → Secrets and variables → Actions → New repository secret**, name it `GROQ_API_KEY`, and keep the value private. The workflow reads that secret during the build; the key is not written to the repository.

The current prototype injects the key into the debug build for testing. Before publishing, move transcription behind a small authenticated backend or use a short-lived token flow; a permanent Groq key embedded in any APK can be extracted.

Groq's official endpoint is `https://api.groq.com/openai/v1/audio/transcriptions`; the implementation uses the documented multipart `file`, `model`, `language`-agnostic multilingual transcription, `prompt`, `temperature`, and JSON response fields. See [Groq Speech to Text documentation](https://console.groq.com/docs/speech-to-text).

## Important platform limitation

The system/launcher owns the exact gesture mapping. EZiK can become the selected digital assistant and receive the system assistant invocation, but an app cannot force every OEM (especially Xiaomi/MIUI variants) to expose or map the gesture identically. The gesture must be enabled by the device's system settings.

## Shizuku design note

The current revision only establishes the permission/status bridge. A command planner and executor must be added next. Commands should be allow-listed and use Android intents first; Shizuku should be used only for operations that require elevated shell APIs. Do not execute arbitrary model-generated shell text directly.

## Build

GitHub Actions builds the debug APK with Gradle 8.7. The local sandbox used for this revision did not include the `gradle` executable or Android SDK, so a local APK build could not be run here. The workflow remains the authoritative build check.

## Next implementation phases

1. Add a command planner that converts natural-language requests into typed, reviewable actions.
2. Implement safe app launch/search actions using package visibility and Android intents.
3. Add a Shizuku user-service executor for a small allow-listed set of actions.
4. Keep Accessibility as an optional fallback and expose clear per-device setup instructions.
5. Add instrumented tests on Android 12+ devices, including Xiaomi/MIUI behavior.
