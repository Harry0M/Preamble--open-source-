# Preamble — App Documentation

## Overview

Preamble is a privacy-first, fully on-device task management app for Android. It is built with modern Android development best practices using Jetpack Compose and follows the MVVM architecture pattern.

---

## Design Pattern: MVVM (Model-View-ViewModel)

```
┌─────────────┐     ┌──────────────┐     ┌──────────────┐
│    View      │ ──► │  ViewModel   │ ──► │  Repository  │
│  (Compose)   │ ◄── │  (StateFlow) │ ◄── │   (Room)     │
└─────────────┘     └──────────────┘     └──────────────┘
```

- **View Layer**: Jetpack Compose screens (`HomeScreen`, `CalendarScreen`, `StatsScreen`, `SettingsScreen`, `OnboardingScreen`)
- **ViewModel Layer**: `TaskViewModel` manages UI state via `StateFlow`, handles business logic
- **Data Layer**: `TaskRepository` → `TaskDao` → Room Database (SQLite)

---

## How It Works

### Task Lifecycle
1. User creates a task (typed or voice) → `TaskViewModel.addTask()` → `TaskRepository.addTask()` → Room INSERT
2. If deadline time is set → `TaskAlarmManager.scheduleAlarm()` → sets `AlarmManager.setAlarmClock()`
3. Task appears in today's list (or future date if scheduled)
4. User completes task → `TaskViewModel.toggleTask()` → Room UPDATE
5. Alarm fires → `AlarmReceiver.onReceive()` → creates high-priority notification with Dismiss action
6. User taps Dismiss → `AlarmDismissReceiver` stops ringtone and cancels notification

### Voice Input Flow
1. User taps mic FAB (HomeScreen) or Voice Task (notification)
2. `SpeechRecognizer` starts listening with 3-second silence timeout
3. Partial results update the UI in real-time
4. Final result auto-saves as a new task via `addTask()`

### Notification System
- **Permanent notification**: Always-on, shows pending task count, Quick Add (text reply), Voice Task (starts VoiceTaskService)
- **Alarm notification**: Triggered by `AlarmReceiver`, ongoing with Dismiss button, plays selected alarm tone

---

## Color System

### Default: Monochrome
The app defaults to a monochrome (grayscale) theme. All Material3 accent colors are overridden:
- Primary: Gray tones
- Secondary: Lighter grays
- Tertiary: Darker grays
- No purple/blue Material defaults leak through

### Custom Colors
Users can pick any hue from a full color wheel or use preset colors:
- Red, Orange, Yellow, Green, Teal, Blue, Purple, Pink
- Custom HSL hue value (0-360°) saved in `SharedPreferences`
- Both light and dark color schemes generated from the hue

---

## UI Components

| Component | File | Description |
|----------|------|-------------|
| `TaskItem` | `TaskItem.kt` | Single task row with checkbox, title, deadline badge, overdue highlighting |
| `AddTaskSheet` | `AddTaskSheet.kt` | Bottom sheet for creating tasks with text, voice, time picker, date picker |
| `ColorPickerComponent` | `ColorPickerComponent.kt` | Full color wheel + preset colors for theme customization |
| `WaveProgressBar` | `HomeScreen.kt` | Animated wave-form progress indicator |
| `BottomWaveAnimation` | `HomeScreen.kt` | Voice input wave animation at screen bottom |
| `NotificationMockup` | `OnboardingScreen.kt` | Canvas-drawn notification illustration for onboarding |
| `VoiceIllustration` | `OnboardingScreen.kt` | Canvas-drawn microphone with sound waves |
| `PaletteIllustration` | `OnboardingScreen.kt` | Canvas-drawn color palette wheel |

---

## Screens

| Screen | Purpose |
|--------|---------|
| `OnboardingScreen` | First-launch intro with feature explanations and permission requests |
| `HomeScreen` | Today's tasks, progress wave bar, voice FAB, add task FAB |
| `CalendarScreen` | Browse tasks by date |
| `StatsScreen` | Completion statistics and streaks |
| `SettingsScreen` | Theme, notification toggle, alarm tone, support, legal, open source |

---

## Unique Features

1. **Wave-form Progress Bar** — Animated bars show task completion progress
2. **Voice-first Design** — Multiple voice entry points (in-app FAB, notification, AddTaskSheet)
3. **Overdue Task Highlighting** — Red background + ⚠ icon for missed deadlines
4. **setAlarmClock() Alarms** — Most reliable alarm method, bypasses Doze and battery optimization
5. **On-device Everything** — Zero network calls, zero analytics, zero data collection
6. **Full Color Customization** — 360° hue wheel, not just presets
7. **Professional Onboarding** — Canvas-drawn illustrations, no external assets needed
8. **Floating Nav Bar** — 100% rounded nav bar floating above content

---

## Dependencies

| Library | Version | License | Purpose |
|---------|---------|---------|---------|
| Jetpack Compose | BOM-managed | Apache 2.0 | UI framework |
| Material Design 3 | BOM-managed | Apache 2.0 | Design system |
| Room Database | 2.x | Apache 2.0 | Local SQLite persistence |
| Kotlin Coroutines | 1.x | Apache 2.0 | Async operations |
| AndroidX Core KTX | 1.x | Apache 2.0 | Android Kotlin extensions |
| Navigation Compose | 2.x | Apache 2.0 | Screen navigation |
| AndroidX Lifecycle | 2.x | Apache 2.0 | ViewModel & lifecycle |

---

## License

Apache License 2.0 — See [LICENSE](../LICENSE) for full text.

**Copyright 2024 The Blank State**

---

## Contact

- **Email:** theblankstateteam@gmail.com
- **GitHub:** [Harry0M](https://github.com/Harry0M)
- **Website:** [theblankstate.com](https://theblankstate.com)
