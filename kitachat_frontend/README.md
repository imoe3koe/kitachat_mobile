# Kitachat Android — Jetpack Compose starter

Starter native Android project adapted from the supplied `index.html` and `style.css`.

## Features included
- Material 3 light/dark themes mapped from CSS variables.
- Login/register demo screen, with create-family/join-family choice and basic local validation.
- Responsive navigation: bottom navigation on compact screens and navigation rail on medium/expanded screens via `WindowWidthSizeClass`.
- Chat screen with incoming/outgoing bubbles, reply preview, local message sending and attachment/call placeholders.
- Album screen with responsive grid and upload placeholder.
- Agenda screen with birthday grid, locally addable agenda items.
- Family directory and settings/profile editing placeholders.

## Build
1. Open this directory in Android Studio (recent version supporting AGP 8.9.x).
2. Use JDK 17.
3. Allow Gradle sync, then run the `app` configuration on an emulator/device.

## Important implementation notes
- The login/register flow is **demo-only** and does not authenticate users. Connect `AuthScreen` to a secure backend/repository before production.
- Chat messages and agenda additions are held in Compose state only; they are not persisted or synchronized across family members.
- Photo picker/upload, profile image changes, password reset, email recovery, voice messages, calling, push notifications and update checks are TODOs.
- For production, add a repository/data layer (e.g. ViewModel + StateFlow), secure auth/session handling, backend API, persistence, file upload permissions/pickers, validation, error/loading states, and tests.
- Date input is deliberately a text placeholder in this UI starter; use a Material date picker or platform date picker for production.

## Main source layout
```
app/src/main/java/id/kitachat/
  MainActivity.kt
  ui/theme/Color.kt
  ui/theme/Theme.kt
  ui/screens/KitachatApp.kt
```

The screen implementations are grouped in `KitachatApp.kt` for ease of trying the prototype. As the project grows, split `AuthScreen`, `ChatTab`, `AlbumTab`, `AgendaTab`, `FamilyTab`, and `SettingsTab` into separate files under `ui/screens/` and reusable elements into `ui/components/`.
