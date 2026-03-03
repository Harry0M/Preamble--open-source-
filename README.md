# Preamble

A beautiful, privacy-first task management app for Android — built entirely with Jetpack Compose and Kotlin.

## ✨ Features

- **Daily Task Tracking** — Add, complete, and manage tasks for today and future dates.
- **Voice Input** — Tap the mic and speak your task. Auto-saves when you stop talking.
- **Smart Reminders** — Set deadline times and get alarm notifications so you never miss a task.
- **Permanent Notification** — Quick Add and Voice Task directly from the notification bar.
- **Wave Progress Bar** — Animated wave-form progress indicator showing daily completion.
- **Overdue Highlighting** — Tasks past their deadline are highlighted in red.
- **Calendar View** — Browse tasks by date on a full calendar screen.
- **Streak Tracking** — Track your daily task completion streak.
- **Full Theme Customization** — Pick any color from the full spectrum or use presets.
- **Professional Onboarding** — Swipeable intro with custom illustrations explaining features.

## 🔒 Privacy

Preamble is **100% on-device**. No data collection, no analytics, no ads, no tracking, no cloud sync. Your tasks stay on your phone.

## 🏗 Architecture

| Layer | Technology |
|-------|-----------|
| UI | Jetpack Compose + Material Design 3 |
| State | ViewModel + Kotlin StateFlow |
| Data | Room Database (SQLite) |
| Async | Kotlin Coroutines |
| Alarms | Android AlarmManager (setAlarmClock) |
| Voice | Android SpeechRecognizer |
| Navigation | Bottom Navigation (Compose) |

## 📁 Project Structure

```
app/src/main/java/com/theblankstate/preamble/
├── data/              # Room entities, DAO, database
├── repository/        # Data access layer
├── viewmodel/         # Business logic & state management
├── notification/      # AlarmReceiver, AlarmDismiss, VoiceTaskService, NotificationManager
├── ui/
│   ├── components/    # TaskItem, AddTaskSheet, ColorPicker
│   ├── screens/       # Home, Calendar, Stats, Settings, Onboarding
│   └── theme/         # Custom theming with full color spectrum
└── MainActivity.kt    # Entry point with floating bottom nav
```

## 🎨 Design

- **Floating Bottom Nav Bar** with 100% rounded corners
- **Monochrome-first** theme (default), fully customizable
- **Wave animations** for voice input and progress tracking
- **Material Design 3** with dynamic color support

## 📋 Permissions

| Permission | Reason |
|-----------|--------|
| `POST_NOTIFICATIONS` | Permanent notification for quick task management |
| `RECORD_AUDIO` | Voice input for hands-free task creation |
| `SCHEDULE_EXACT_ALARM` | Deadline alarm reminders |
| `RECEIVE_BOOT_COMPLETED` | Restore notification after device restart |

## 📄 License

```
Copyright 2024 The Blank State

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## 🤝 Support

- **Email:** theblankstateteam@gmail.com
- **GitHub:** [Harry0M](https://github.com/Harry0M)
- **Website:** [theblankstate.com](https://theblankstate.com)
