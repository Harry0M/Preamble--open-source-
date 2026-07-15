---
name: custom-ui-design-guidelines
description: Apply the Preamble project's premium minimalist UI design guidelines, including borderless list items, responsive scale factor adjustments, tactile press animations, and avatar stacks.
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

## 5. Morphing & Moving Floating Buttons
When implementing layout actions that collapse on scroll (e.g. hiding the top header navbar on swipe up), morph the primary action button and translate its position dynamically to fit next to the floating top row controls:

* **Position Translation**: Animate the $x$ and $y$ offsets between its local header position and the top floating row (e.g., transitioning $x$ from `24.dp` to the scaled button row offset).
* **Shape Morphing**: Animate the container shape, width, and height between a rounded pill (with custom width/height when fully open) and a compact circle (matching the diameter of the other circular buttons in the floating row).
* **Content Transitions**: Animate text opacity (`textAlpha`) to fade out the label completely as it collapses, leaving only the central icon scaled and centered.

---

## How to use this Skill
When prompting me to design or modify a screen, you can guide me to use this skill by explicitly stating:
> *"Design the new features using the `custom-ui-design-guidelines` skill"* or
> *"Implement the UI following the Preamble minimalist layout design rules"*
