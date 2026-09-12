# EZiK 1.0.0

EZiK is an Android system-assistant prototype built around Android's `VoiceInteractionService`, a compact assistant surface, Groq Whisper voice transcription, a safe typed-action planner, optional Groq LLM reasoning, Accessibility, and optional Shizuku.

## What this version fixes

- No API key is compiled into the APK. The personal Groq key is entered in EZiK and encrypted locally with Android Keystore.
- Groq Whisper uses `whisper-large-v3-turbo` for voice commands.
- Added a Groq LLM action router using `openai/gpt-oss-120b` for requests the deterministic planner does not understand.
- LLM output is converted into a small allow-list of typed actions. Arbitrary shell commands, coordinates, intents, permissions, or destructive operations are rejected.
- Added robust voice recording cleanup, silence detection, recording caps, and detach-time cancellation.
- Added Android assistant role handling with a settings fallback.
- Added a real AccessibilityService declaration with window-content retrieval and gesture capability.
- Improved app matching with normalized labels, package matching, and bounded Levenshtein matching.
- Added direct Wi-Fi, Bluetooth, and app-settings commands.
- Reworked the launcher and assistant surface into a compact dark UI instead of a setup-form prototype.
- Updated the Gradle toolchain and GitHub Actions workflow.

## Important limitation

This is a practical Android assistant MVP, not a literal replacement for Gemini. Gemini has privileged system integrations, multimodal models, large-scale backend infrastructure, browser/tool integrations, and years of platform work. EZiK now has the correct foundation for an agent, but capabilities must still be implemented as explicit, reviewable actions.

## Setup

1. Install EZiK and open it once.
2. Grant microphone permission.
3. Create a Groq API key and paste it into **Voice setup**. It is encrypted locally and is never written into the repository.
4. Tap **Set as default assistant**.
5. On Xiaomi/MIUI/HyperOS, use **Assistant settings** if the role picker is not exposed normally.
6. Enable EZiK Accessibility only when you want screen-aware actions. It is optional for the current core commands.
7. Start Shizuku and grant EZiK permission only if you intend to add privileged actions later.

## Example commands

- `EZiK, open Goodreads`
- `open Telegram`
- `search for Project Hail Mary`
- `open Goodreads and search for Project Hail Mary`
- `open Wi-Fi settings`
- `open Bluetooth settings`
- `https://example.com`
- General questions can be answered by the optional Groq LLM when a key is configured.

## Security model

The assistant never executes raw LLM output. The LLM may choose only:

- launch_app
- search_web
- open_url
- open_settings
- answer

Accessibility and Shizuku are bridges, not unrestricted command shells. New actions should be added as explicit Kotlin types and validated before execution.

For a public product, do not ship a long-lived provider API key in the APK. The local encrypted key is appropriate for a personal sideloaded build, not a public multi-user release. A production service should use an authenticated backend or short-lived credentials.

## Build

GitHub Actions builds the debug APK without requiring a Groq secret. This is intentional: the key is configured on the device instead of embedded in every APK.

Local build:

```bash
gradle assembleDebug
```

## Roadmap to a serious Gemini-class assistant

1. Replace the single-turn action router with an agent loop: Observe → Plan → Act → Observe.
2. Add a structured action registry with confirmation policies.
3. Add Accessibility screen snapshots and safe UI targeting.
4. Add browser/search integration.
5. Add persistent conversation memory and user preferences.
6. Add streaming model output and text-to-speech.
7. Add task history, cancellation, retries, and crash recovery.
8. Add a backend for public distribution so provider credentials never live in APKs.
