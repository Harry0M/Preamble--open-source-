---
name: custom-ui-design-guidelines
description: Apply the Preamble project's premium minimalist UI design guidelines, including borderless list items, responsive scale factor adjustments, tactile press animations, avatar stacks, FAB-style floating headers, scroll-to-hide bars, and floating bottom navbars.
---

# Preamble UI Design Guidelines

This skill defines the visual identity, interaction design, and responsive layout rules developed for the Preamble project. Activate or reference this skill when building or styling screens in the Preamble mobile app.

---

## 1. Clean, Minimalist List Layouts
Instead of using heavy, solid card containers that clutter the screen, default to a borderless list layout that sits directly on the screen's background (similar to the Activity Notification Center).

* **Visual Anchor on Left**: Use a circular background of size `44.dp` filled with a soft accent color from the project's `CardColors` array, hosting a clean dark icon.
* **Metadata in Middle**: Use bold typography for the title (around `16.sp`) and a secondary label below for subtitle/details with slightly reduced opacity.
* **Actions/Metadata on Right**: Align secondary badges, timestamps, or horizontal avatar stacks on the right side of the row.

---

## 2. Dynamic Responsive Scaling
Ensure the UI automatically adapts to different screen sizes. Avoid static hardcoded dimensions for primary layout sections:

```kotlin
// Define a scaling factor based on screen width
val screenWidth = LocalConfiguration.current.screenWidthDp.dp
val scaleFactor = (screenWidth / 360f).coerceIn(0.85f, 1.15f)

// Apply scaleFactor to dimensions, paddings, and font sizes
val iconSize = 24.dp * scaleFactor
val buttonWidth = 84.dp * scaleFactor
val fontSize = 14.sp * scaleFactor
```

---

## 3. Tactile Press Animations
Make controls feel "alive" and responsive to touch instead of static. Apply physics-based bouncy scale transitions to clickable targets:

```kotlin
@Composable
fun Modifier.expressivePressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.92f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "expressivePressScale",
    )
    return this.scale(scale)
}
```

---

## 4. Overlapping Avatar Stacks
When representing members or collaborators, use a horizontal stack of overlapping circular avatars:

* Use negative spacing to create overlap: `Arrangement.spacedBy((-12).dp)`.
* Wrap each avatar with a thin white border: `padding(2.dp)`.
* Limit visible avatars to a maximum of 3, and add a `+N` indicator for any remaining.

---

## 5. Floating Action Bar Headers (FAB-Style Top Bar)
Replace standard heavy top app bars with a floating row of rounded FAB components:

* **Floating Action Row**: Arrange Back FAB (e.g. `Color(0xFFA1C6FF)` soft blue), Center Title/Capsule FAB (e.g. `Color(0xFFFFD166)` soft amber), and Close/Action FAB (e.g. `Color(0xFFFF9E9E)` soft coral) floating over content.
* **Friends Palette Accents**: Use soft vibrant colors from `PreambleCardColors` for FAB backgrounds and icons.
* **Top Clearance Spacer**: Provide explicit top clearance (`Spacer(modifier = Modifier.height(80.dp * scaleFactor))`) in scrollable views so content starts cleanly below the floating header FABs without being hidden behind them.

---

## 6. Scroll / Swipe-to-Hide Floating Bars (Header & Bottom Nav)
Floating header and bottom navigation bars should dynamically respond to scroll gestures to maximize screen real estate:

```kotlin
val scrollState = rememberScrollState()
var isBarVisible by remember { mutableStateOf(true) }
var lastScrollPosition by remember { mutableIntStateOf(0) }

LaunchedEffect(scrollState.value) {
    val currentScroll = scrollState.value
    val diff = currentScroll - lastScrollPosition
    if (diff > 12) {
        isBarVisible = false // Hide on swipe up / scroll down
    } else if (diff < -12 || currentScroll < 30) {
        isBarVisible = true  // Show on swipe down / scroll up / near top
    }
    lastScrollPosition = currentScroll
}

AnimatedVisibility(
    visible = isBarVisible,
    enter = fadeIn() + slideInVertically { -it },
    exit = fadeOut() + slideOutVertically { -it },
) {
    // Render Floating Header or Bottom Nav Bar
}
```

---

## 7. Floating FAB-Style Bottom Navigation Bars
Bottom navigation bars should mirror the floating top FAB aesthetic:

* **Floating Pill Surface**: Wrap navigation items inside a rounded capsule container (`shape = RoundedCornerShape(50)`) floating above the screen bottom.
* **Gesture & Insets Clearance**: Wrap floating bottom bars in `navigationBarsPadding()` and `imePadding()` to prevent overlaps with system gesture bars and soft keyboards.

---

## 8. Morphing & Moving Floating Buttons
When implementing layout actions that collapse on scroll, morph the primary action button and translate its position dynamically to fit next to the floating top row controls:

* **Position Translation**: Animate $x$ and $y$ offsets between its local header position and the top floating row.
* **Shape Morphing**: Animate container shape, width, and height between a rounded pill and a compact circle.
* **Content Transitions**: Animate text opacity (`textAlpha`) to fade out the label completely as it collapses, leaving only the central icon scaled and centered.

---

## How to use this Skill
When prompting me to design or modify a screen, you can guide me to use this skill by explicitly stating:
> *"Design the new features using the `custom-ui-design-guidelines` skill"* or
> *"Implement the UI following the Preamble minimalist layout design rules"*
