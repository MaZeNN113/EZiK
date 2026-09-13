# EZiK

Version 1.1.0 (versionCode 23)

EZiK is an Android system-assistant MVP focused on local-first commands and generic in-app control through Accessibility.

## Current capabilities
- System assistant / VoiceInteractionSession surface.
- Fast on-device/system speech recognition when available.
- Optional Groq reasoning fallback. The app remains usable without a network key.
- Launch installed apps with fuzzy name matching.
- Generic in-app search through the Accessibility tree.
- Generic tap, type, scroll and back actions in visible app UIs.
- Shizuku connection hook for future privileged operations.
- Local encrypted storage for the optional Groq key using Android Keystore.
- User controls for automatic listening, in-app actions, sensitive-action confirmation, and cloud reasoning.

## Important limitation
Android does not provide one universal API that lets a third-party assistant silently read arbitrary historical messages from every app without opening the app. Accessibility can inspect the UI that an app exposes, and app-specific APIs can provide deeper access where an app supports them. Notification access only covers notification data and depends on notifications being delivered. EZiK therefore does not pretend it can universally summarize private chat history offline today.

## Next architecture step
The in-app control layer should evolve into:
1. generic Accessibility agent for broad compatibility;
2. app adapters for apps with stable APIs or predictable UI semantics;
3. a message-source layer for supported apps;
4. Observe -> Think -> Act -> Observe execution with confirmation gates for sensitive actions.

## Naming
- App label: EZiK
- Application ID: viz.EZiK
- Namespace: viz.EZiK
- Root project: EZiK
- Version: 1.1.0 / versionCode 23
