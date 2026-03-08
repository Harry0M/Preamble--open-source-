# Preamble — App Documentation

## Overview

Preamble is a privacy-first, fully on-device task management app for Android. Built with modern Android development best practices using Jetpack Compose and Kotlin, following MVVM architecture.

---

## Design Pattern: MVVM (Model-View-ViewModel)

```
┌─────────────┐     ┌──────────────┐     ┌──────────────┐
│    View      │ ──► │  ViewModel   │ ──► │  Repository  │
│  (Compose)   │ ◄── │  (StateFlow) │ ◄── │   (Room)     │
└─────────────┘     └──────────────┘     └──────────────┘
```

- **View Layer**: Jetpack Compose screens
- **ViewModel Layer**: `TaskViewModel` manages UI state via `StateFlow`
- **Data Layer**: `TaskRepository` → `TaskDao` → Room Database (SQLite)

---

## How It Works

### Task Lifecycle
1. User creates a task (typed or voice) → `TaskViewModel.addTask()` → `TaskRepository.addTask()` → Room INSERT
2. If deadline time is set → `TaskAlarmManager.scheduleAlarm()` → `AlarmManager.setAlarmClock()`
3. Task appears in today's list (or future date if scheduled)
4. User completes task → `TaskViewModel.toggleTask()` → Room UPDATE
5. Alarm fires → `AlarmReceiver.onReceive()` → high-priority notification with Dismiss action
6. User taps Dismiss → `AlarmDismissReceiver` stops ringtone and cancels notification

### Voice Input Flow
1. User taps mic FAB (HomeScreen) or Voice Task (notification) or mic button (AddTaskSheet)
2. `SpeechRecognizer` starts listening with 3-second silence timeout
3. Partial results update the UI in real-time
4. Final result auto-saves as a new task

### Notification System
- **Permanent notification**: Always-on (`setOngoing(true)`), shows pending task count, Quick Add (text reply), Voice Task action
- **Alarm notification**: Triggered by `AlarmReceiver`, ongoing with Dismiss button, plays user-selected alarm tone

---

## Color & Theme System

### Default: Monochrome
The app defaults to a monochrome (grayscale) theme, defined in `Theme.kt`:

**Dark Mode (Default):**
| Token | Value | Usage |
|-------|-------|-------|
| `primary` | `Color.White` | Accent text, icons, selected elements |
| `onPrimary` | `Color.Black` | Text on primary buttons |
| `primaryContainer` | `#333333` | Subtle highlight backgrounds |
| `background` | `#121212` | Screen background |
| `surface` | `#121212` | Card/component surface |
| `surfaceVariant` | `#2C2C2C` | Secondary surface (settings cards) |
| `onSurface` | `Color.White` | Body text |
| `onSurfaceVariant` | `Color.LightGray` | Secondary/muted text |
| `outline` | `Color.DarkGray` | Borders/dividers |

**Light Mode:**
| Token | Value | Usage |
|-------|-------|-------|
| `primary` | `Color.Black` | Accent text, icons, selected elements |
| `background` | `Color.White` | Screen background |
| `surfaceVariant` | `#EDEDED` | Card backgrounds |

### Custom Color System
When user picks a custom color via `ColorPickerComponent`:

1. **Hue slider** → draws a horizontal rainbow gradient (0°-360°) using Canvas
2. **Touch/drag** → maps X position to hue value → creates `Color.hsl(hue, 0.7, 0.5)`
3. **Preset grid** → 20 predefined colors in a 5×4 grid
4. **`generateCustomColorScheme()`** in `Theme.kt`:
   - Takes the chosen color as `primary`
   - Auto-detects if color is light/dark via `luminance()` for contrast text
   - `primaryContainer` = primary at 30% (dark) / 20% (light) opacity
   - Background stays `#121212` (dark) / `White` (light)
   - `surfaceVariant` stays `#2C2C2C` / `#EDEDED`
5. **Persistence**: Hue saved in `SharedPreferences` via `ThemePreferences` object
6. **Reset**: "Reset (Monochrome)" button clears preference → falls back to monochrome

### Color Flow
```
User picks color → SharedPreferences → ThemePreferences.themeColor (StateFlow)
    → PreambleTheme composable → generateCustomColorScheme() → MaterialTheme
```

---

## UI Components — Full Detail

### 1. Segmented Progress Bar (`WaveProgressBar`)
**File**: `HomeScreen.kt`  
**Location**: Top of task list  
**Purpose**: Shows daily task completion as a segmented capsule bar

**How it works:**
- Draws **20 rounded rectangle segments** using `Canvas`
- Each segment width = `totalWidth / (count + gaps)`
- Gap ratio = `0.3` (30% of segment width)
- Corner radius = `height / 2` (fully rounded capsules)
- **Active segments** (completed tasks): Solid `primary` color
- **Inactive segments**: `onSurface` at 10% alpha
- Progress = `completedTasks / totalTasks`
- Filled count = `(20 * progress).toInt()`

**Visual:**
```
████ ████ ████ ████ ████ ░░░░ ░░░░ ░░░░ ░░░░ ░░░░
←── completed ──→         ←── remaining ──→
```

**Modifier**: `fillMaxWidth().height(24.dp)`

---

### 2. Bottom Wave Animation (`BottomWaveAnimation`)
**File**: `HomeScreen.kt`  
**Location**: Bottom edge of screen, visible when voice FAB is active  
**Purpose**: Visual feedback during voice recording

**How it works:**
- `rememberInfiniteTransition` animates a `phase` value from `0` to `2π`
- Animation spec: `tween(800ms)`, `RepeatMode.Restart`
- Canvas draws **40 vertical bars** across the screen width
- Each bar's height = `amplitude × |sin(phase + i × 0.4)| + 3dp`
- Bar alpha varies: `0.3 + 0.7 × (barHeight / maxHeight)`
- Color: `primary` from theme
- `StrokeCap.Round` for rounded bar ends

**Trigger**: `AnimatedVisibility` with `slideInVertically`/`slideOutVertically`
**Height**: `60.dp`

---

### 3. Voice FAB (Microphone Button)
**File**: `HomeScreen.kt`  
**Location**: Above "Add Task" FAB, bottom-right  
**Purpose**: One-tap voice task creation

**States:**
| State | Icon | Color | Behavior |
|-------|------|-------|----------|
| Idle | `Icons.Filled.Mic` | `secondaryContainer` | Tap to start listening |
| Listening | `Icons.Filled.Stop` | `error` (red) | Shows wave animation, tap to stop |

**Speech Recognition Config:**
- `LANGUAGE_MODEL_FREE_FORM`
- `EXTRA_PARTIAL_RESULTS = true`
- `SILENCE_LENGTH = 3000ms` (auto-stop after 3s silence)
- On result: Auto-saves task via `onAddTask()`, shows Toast

---

### 4. Task Item (`TaskItem`)
**File**: `ui/components/TaskItem.kt`  
**Purpose**: Single task row in the list

**Layout:** `Row` with `Checkbox` + `Title Text` + optional `Deadline Badge`

**States:**
| State | Alpha | Text Color | Text Style | Background |
|-------|-------|-----------|------------|------------|
| Active | 1.0 | `onSurface` | Normal | Transparent |
| Completed | 0.5 | `onSurfaceVariant` | Strikethrough | Transparent |
| Overdue | 1.0 | `error` | Normal | `errorContainer` at 30% |

**Overdue Detection:**
```kotlin
val deadlineDateStr = "${task.createdDate} ${task.deadlineTime}"  // "2026-03-03 14:00"
val deadlineDate = SimpleDateFormat("yyyy-MM-dd HH:mm").parse(deadlineDateStr)
isOverdue = deadlineDate.before(Date())  // past deadline + not completed
```

**Deadline Badge:**
- Normal: `primaryContainer` background, shows time like `14:00`
- Overdue: `errorContainer` background, shows `⚠ 14:00`
- Shape: `RoundedCornerShape(12.dp)`

---

### 5. Add Task Sheet (`AddTaskSheet`)
**File**: `ui/components/AddTaskSheet.kt`  
**Type**: `ModalBottomSheet`

**Features:**
- Text input field for task title
- **Voice input button** with `SpeechRecognizer` integration
- **Date picker** (optional) — DatePickerDialog, restricts to future dates
- **Time picker** (optional) — TimePickerDialog for deadline
- **WaveAnimation** composable for voice feedback
- Submit button saves task

**Voice Wave Animation** (inside sheet):
- 30 vertical bars, `tween(700ms)` animation
- Height varies with sin wave, `StrokeCap.Round`
- Shows while `isListening = true`

---

### 6. Calendar Heat Map (`CalendarScreen`)
**File**: `ui/screens/CalendarScreen.kt`  
**Purpose**: GitHub-style contribution graph for tasks

**Grid Structure:**
- Static `Column` of `Row`s (not lazy — instant rendering)
- 7 columns (Sun-Sat), variable rows per month
- Each cell: `CircleShape`, `40.dp` size

**Color Coding:**
| Condition | Background | Text Color |
|-----------|-----------|------------|
| No tasks | Transparent | `onSurfaceVariant` |
| Has tasks | `primary` at `0.12 + (completed/total) × 0.55` alpha | `onSurface` |
| Today | `primaryContainer` | `primary` (bold) |
| Selected | `primary` (solid) | `onPrimary` |

**Today Dot:**
- Small `4.dp` `CircleShape` below date number
- Color: `primary`
- Hidden when cell is selected

**Heat Map Legend:**
```
Less  ○ ○ ○ ○  More
      12% 30% 50% 67% opacity
```

**Task Display:**
- Tasks shown inline below calendar (no dialog)
- Each task: `Checkbox` + `Title` + optional `deadlineTime`
- Completed tasks show strikethrough text

**Data Source:** `TaskViewModel.calendarHeatMap` → `TaskRepository.getMonthlyHeatMap(year, month)` which queries Room for every day in the month.

---

### 7. Stats Charts (`StatsScreen`)
**File**: `ui/screens/StatsScreen.kt`  
**Purpose**: Task completion analytics

**Stat Cards (top row):**
| Card | Icon | Value | Label |
|------|------|-------|-------|
| Streak | `Icons.Filled.LocalFireDepartment` | Consecutive days | "Day Streak" |
| Today | `Icons.Filled.CheckCircle` | completed/total | "Today" |
| All Time | `Icons.Filled.EmojiEvents` | Total completed | "All Time" |

All cards: **No background** — just icon + value + label, center-aligned.

**14-Day Wave Chart (`WaveChart`):**
- Canvas composable, `fillMaxWidth().height(140.dp)`
- Data: List of `(dayLabel, completedCount)` for last 14 days
- `maxVal = data.maxOf { it.second }.coerceAtLeast(1)`
- Points mapped: X = proportional position, Y = `height - (value/maxVal) × height`
- **Smooth cubic bezier curves** between points using `cubicTo()`:
  ```kotlin
  val cx1 = (prev.x + curr.x) / 2
  cubicTo(cx1, prev.y, cx1, curr.y, curr.x, curr.y)
  ```
- **Gradient fill** below the curve: `primary` at 30% → 2% vertical gradient
- **Line stroke**: 2dp, `StrokeCap.Round`
- **Dots**: 3dp circles at each data point

**Weekly Bar Chart:**
- 7 vertical bars for each day (Mon-Sun)
- Height = `completionRate × 80.dp`
- Color: Full `primary` if 100%, otherwise `primary` at `0.3 + rate × 0.5` alpha
- `RoundedCornerShape(topStart=6, topEnd=6)`
- Labels: percentage on top, day abbreviation below

**30-Day Wave Chart:**
- Same `WaveChart` composable, `height(100.dp)`
- Shows last 30 days of completed task counts
- Header: "X tasks completed"

---

### 8. Color Picker (`ColorPickerComponent`)
**File**: `ui/components/ColorPickerComponent.kt`  
**Type**: Dialog triggered from Settings

**Components:**
1. **Rainbow Hue Slider**: Canvas drawing horizontal gradient from 0°-360°
   - Touch handler maps X position → hue value
   - Indicator circle at current position
2. **"Apply Custom Color" Button**: Saves hue to `ThemePreferences`
3. **Preset Color Grid**: 20 colors in a 5×4 grid
   - Red, Pink, Purple, Deep Purple, Blue, Cyan, Teal, Green, etc.
   - Each: `36.dp` circle with `clickable` modifier
4. **"Reset (Monochrome)" & "Cancel" buttons**

---

### 9. Onboarding Illustrations
**File**: `ui/screens/OnboardingScreen.kt`  
**All illustrations are Canvas-drawn** — no external assets needed.

**Page 1 — Welcome:** Large emoji text rendering  
**Page 2 — NotificationMockup:** Canvas-drawn notification card showing:
- Rounded rectangle with title "Preamble"
- "Quick Add" and "Voice Task" action mockup buttons
- Shadow/elevation effect

**Page 3 — VoiceIllustration:** Canvas-drawn microphone with sound waves:
- Circle base, rectangle stem
- 3 concentric arc waves radiating outward
- All in `primary` color

**Page 4 — PaletteIllustration:** Canvas-drawn color wheel:
- 12 colored circles arranged in a ring pattern
- Each circle represents a different hue (30° apart)
- Center dot in `primary` color

---

### 10. Floating Bottom Navigation Bar
**File**: `MainActivity.kt`

**Structure:**
```kotlin
Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
    NavigationBar(
        modifier = Modifier.clip(RoundedCornerShape(50))  // 100% rounded
    )
}
```

**Tabs:**
| Tab | Icon | Screen |
|-----|------|--------|
| Tasks | `Icons.Default.Home` | `HomeScreen` |
| Stats | `Icons.Filled.Analytics` | `StatsScreen` |
| Calendar | `Icons.Default.DateRange` | `CalendarScreen` |
| Settings | `Icons.Default.Settings` | `SettingsScreen` |

---

### 11. Settings Components
**File**: `ui/screens/SettingsScreen.kt`

| Section | Components |
|---------|-----------|
| **Appearance** | Theme Color card → opens `ColorPickerComponent` dialog |
| **Notifications** | Switch toggle (synced with system), "Grant Permission" button |
| **Alarms** | Alarm tone card → opens `RingtoneManager.ACTION_RINGTONE_PICKER` |
| **Support** | Email, GitHub (Harry0M), Website links → opens intents |
| **Legal** | Privacy Policy + Terms of Service (inline text) |
| **Open Source** | Library list: Compose, Room, Coroutines, Material3, Core, Navigation |
| **Rate** | Button + auto-popup review `ModalBottomSheet` after 2 days |

**All cards**: `CardDefaults.cardColors(containerColor = surfaceVariant)`

---

### 12. Notification Icon (`ic_notification.xml`)
**File**: `res/drawable/ic_notification.xml`

- Monochrome vector drawable matching the app icon pattern
- 4×3 grid of white rounded rectangles
- White-on-transparent as required for Android notification small icons

---

## Screens Summary

| Screen | File | Key Components |
|--------|------|----------------|
| Onboarding | `OnboardingScreen.kt` | 4 swipeable pages, Canvas illustrations, permission requests |
| Home | `HomeScreen.kt` | TopAppBar, WaveProgressBar, TaskItem list, Voice FAB, Add Task FAB, BottomWaveAnimation |
| Stats | `StatsScreen.kt` | StatCards (Material icons), WaveChart (14-day/30-day), weekly bar chart |
| Calendar | `CalendarScreen.kt` | Heat map grid, today dot, inline task list, legend |
| Settings | `SettingsScreen.kt` | Appearance, Notifications, Alarms, Support, Legal, Open Source, Rate |

---

## Dependencies

| Library | License | Purpose |
|---------|---------|---------|
| Jetpack Compose (BOM) | Apache 2.0 | UI framework |
| Material Design 3 | Apache 2.0 | Design system |
| Material Icons Extended | Apache 2.0 | Full icon set (Analytics, Mic, LocalFireDepartment, etc.) |
| Room Database | Apache 2.0 | Local SQLite persistence |
| Kotlin Coroutines | Apache 2.0 | Async operations |
| AndroidX Core KTX | Apache 2.0 | Kotlin extensions |
| Navigation Compose | Apache 2.0 | Screen navigation |
| AndroidX Lifecycle | Apache 2.0 | ViewModel & lifecycle |

---

## Unique Features

1. **Segmented Capsule Progress Bar** — 20 rounded segments showing task completion
2. **Voice-first Design** — 3 voice entry points (FAB, notification, AddTaskSheet)
3. **Overdue Task Highlighting** — Red background + ⚠ icon for missed deadlines
4. **setAlarmClock() Alarms** — Most reliable alarm method, bypasses Doze
5. **On-device Everything** — Zero network calls, zero analytics, zero data collection
6. **Full 360° Color Customization** — HSL hue wheel, not just presets
7. **Canvas-drawn Onboarding Illustrations** — No external assets needed
8. **GitHub-style Calendar Heat Map** — Color intensity shows task completion
9. **Smooth Cubic Bezier Wave Charts** — Gradient-filled curves for stats
10. **Floating Rounded Nav Bar** — 100% rounded bottom navigation

---

## License

Apache License 2.0 — See [LICENSE](LICENSE) for full text.

**Copyright 2024 The Blank State**

---

## Contact

- **Email:** theblankstateteam@gmail.com
- **GitHub:** [Harry0M](https://github.com/Harry0M)
- **Website:** [theblankstate.com](https://theblankstate.com)
