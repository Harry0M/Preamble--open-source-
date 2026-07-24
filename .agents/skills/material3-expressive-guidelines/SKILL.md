---
name: material3-expressive-guidelines
description: Complete Google Material 3 Expressive Design System guidelines and Jetpack Compose component implementations including morphing LoadingIndicator, ContainedLoadingIndicator, CircularWavyProgressIndicator, LinearWavyProgressIndicator, ButtonGroup, SplitButton, FloatingToolbar, DockedToolbar, FabMenu, and 35 Abstract Material Shapes.
---

# Google Material 3 Expressive Design System Guidelines

This skill provides the authoritative reference, API mapping, and Jetpack Compose implementations for **Google Material 3 Expressive Design System**.

---

## 1. Overview & Core Component API Mapping

| Component Name | Exact Compose Function / Class | Companion Defaults Class | Key Parameters / Tokens To Target | Availability & Audit Note |
| :--- | :--- | :--- | :--- | :--- |
| **"Alive" Morphing Indicator** | `LoadingIndicator()` | `LoadingIndicatorDefaults` | `polygons: List<RoundedPolygon>` | High-performance custom implementation using `androidx.graphics:graphics-shapes:1.0.0-rc01` (`RoundedPolygon`, `Morph`) |
| **Contained Alive Indicator** | `ContainedLoadingIndicator()` | `LoadingIndicatorDefaults` | `containerShape: Shape`, `containerColor: Color` | Built with `Surface` container wrapping morphing polygon |
| **Progressive Wavy Circle** | `CircularWavyProgressIndicator()` | `WavyProgressIndicatorDefaults` | `amplitude: Float`, `wavelength: Dp`, `strokeWidth: Dp` | Canvas path mathematical sine-wave arc renderer |
| **Progressive Wavy Line** | `LinearWavyProgressIndicator()` | `WavyProgressIndicatorDefaults` | `amplitude: Float`, `height: Dp` | Canvas linear active path wave renderer |
| **Expressive Button Layout** | `ButtonGroup()` | `ButtonGroupDefaults` | Toggleable item shape-shifting layout | `Surface` capsule row with bouncy press scaling |
| **Two-Part Action Button** | `SplitButton()` | `SplitButtonDefaults` | `primaryText`, `leadingIcon`, `onMenuClick` | Integrated dual-action capsule button with vertical divider |
| **Floating Surface Bar** | `FloatingToolbar()` | `FloatingToolbarDefaults` | `scrollBehavior`, `orientation` | Floating capsule surface with spatial spring transitions |
| **Docked Bottom Anchor Bar** | `DockedToolbar()` | `DockedToolbarDefaults` | `containerColor`, `contentPadding` | Bottom-anchored bar replacement |
| **Dynamic FAB Overlay** | `FabMenu()` | `FabMenuDefaults` | `expanded: Boolean`, `fab: @Composable` | Expanding floating action button menu overlay |

---

## 2. Dynamic "Alive Shapes" System Foundations

1. **Polygon Morphing**: Transitions smoothly across abstract shapes (stars, squircles, florals, geometric polygons) using `androidx.graphics.shapes.RoundedPolygon` and `androidx.graphics.shapes.Morph`.
2. **Spring Physics Motion**: Dynamic spring damping (`Spring.DampingRatioMediumBouncy`, `Spring.StiffnessLow`) replaces flat linear or fixed-duration easing curves.
3. **Active Wave Tracking**: `CircularWavyProgressIndicator()` and `LinearWavyProgressIndicator()` replace static 1.4 circular progress indicators to reduce visual fatigue during active progress.

---

## 3. Jetpack Compose Code Implementation Reference

### Opt-In Requirement
```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
```

### Component Code File Location
The project's Material 3 Expressive components are saved in:
[Material3ExpressiveComponents.kt](file:///c:/Users/palha/preamble/app/src/main/java/com/theblankstate/preamble/ui/components/Material3ExpressiveComponents.kt)

### Component Snippets

#### 1. "Alive" Morphing Loading Indicator
```kotlin
@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 48.dp
) { ... }
```

#### 2. Circular Wavy Progress Indicator
```kotlin
@Composable
fun CircularWavyProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    strokeWidth: Dp = 8.dp,
    amplitude: Float = 4f,
    wavelength: Dp = 12.dp
) { ... }
```

#### 3. Linear Wavy Progress Indicator
```kotlin
@Composable
fun LinearWavyProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    height: Dp = 10.dp,
    amplitude: Float = 3f
) { ... }
```

#### 4. ButtonGroup & SplitButton
```kotlin
@Composable
fun ButtonGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) { ... }

@Composable
fun SplitButton(
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    primaryText: String,
    leadingIcon: @Composable (() -> Unit)? = null
) { ... }
```

---

## 4. Component Availability & Replacement Audit Report

When utilizing this skill, if an explicit Material 3 Expressive class is updated or moved by Google in future M3 releases:
- **`LoadingIndicator` & `ContainedLoadingIndicator`**: Provided by `com.theblankstate.preamble.ui.components.LoadingIndicator` backed by `androidx.graphics:graphics-shapes:1.0.0-rc01`.
- **`CircularWavyProgressIndicator` & `LinearWavyProgressIndicator`**: Provided by `com.theblankstate.preamble.ui.components.CircularWavyProgressIndicator` backed by custom Canvas sine-wave math.
- **`ButtonGroup` & `SplitButton`**: Provided by `com.theblankstate.preamble.ui.components.ButtonGroup` & `SplitButton`.
