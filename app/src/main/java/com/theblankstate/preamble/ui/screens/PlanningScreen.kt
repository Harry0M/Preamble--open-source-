package com.theblankstate.preamble.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import com.theblankstate.preamble.viewmodel.DayPlanState

// Solid Vibrant Preamble Friends-Screen Palette
private val PreambleCardColors = listOf(
    Color(0xFFA1C6FF), // Soft Blue
    Color(0xFFEAB3FF), // Soft Purple
    Color(0xFFFFD166), // Soft Yellow
    Color(0xFFFF9E9E), // Soft Coral
    Color(0xFF9EE8FF), // Soft Cyan
    Color(0xFFFFC085)  // Soft Orange
)

private data class MotivationalPreset(val title: String, val description: String)

private val MOTIVATIONAL_PRESETS = listOf(
    MotivationalPreset("Conquer Your Day", "Turn your goals into momentum with an AI-crafted schedule."),
    MotivationalPreset("Own the Clock", "Transform remaining hours into pure focus and achievement."),
    MotivationalPreset("Make It Happen", "Streamline your tasks and unleash your highest productivity."),
    MotivationalPreset("Focus & Flourish", "Let AI balance your workload so you stay ahead of the day."),
    MotivationalPreset("Unstoppable Today", "Structure your remaining time for peak performance."),
    MotivationalPreset("Master Your Workflow", "Align your energy with the right tasks at the right time."),
    MotivationalPreset("Fuel Your Ambition", "Organize today's actions with clarity and confidence."),
    MotivationalPreset("Seize Every Hour", "Turn open time slots into completed milestones."),
    MotivationalPreset("Zero Distractions", "Build a high-focus daily path tailored just for you."),
    MotivationalPreset("Daily Brilliance", "Optimize your task list and crush your priorities effortlessly."),
    MotivationalPreset("Clarity & Power", "Transform chaos into a structured, calm schedule."),
    MotivationalPreset("Peak Efficiency", "Let AI calculate your optimal task flow for remaining hours."),
    MotivationalPreset("Ready for Impact", "Focus on what matters most and finish strong today."),
    MotivationalPreset("Smart Daily Flow", "Balance priority tasks and quick wins in perfect rhythm."),
    MotivationalPreset("Elevate Your Output", "Your daily plan, optimized for focus, energy, and success."),
    MotivationalPreset("Execute with Ease", "Clear execution steps tailored to your working window."),
    MotivationalPreset("Unlock Your Potential", "Every minute planned for maximum satisfaction and progress."),
    MotivationalPreset("Drive Success", "Structure your day to achieve more with less friction."),
    MotivationalPreset("Action Meets Purpose", "Align your tasks with realistic remaining time slots."),
    MotivationalPreset("Finish Strong", "Turn remaining hours into your most productive streak.")
)

private val PLACEHOLDER_PRESETS = listOf(
    "e.g. Feeling super energetic, heavy coding until 5 PM",
    "e.g. Go outside earliest, bahar jaana pehle kar do",
    "e.g. Tired today, focus on quick light tasks first",
    "e.g. Important gym session at 6 PM, leave time free",
    "e.g. Deep work block before 4 PM, light calls later",
    "e.g. Finish client presentation first, then errands",
    "e.g. Take short breaks between heavy tasks",
    "e.g. Focus on high-priority items before evening",
    "e.g. Clear remaining uncompleted tasks by 9 PM",
    "e.g. Schedule outdoor walk at 5 PM",
    "e.g. Finish code review first, study later tonight",
    "e.g. Need 30 min quiet time around 6 PM",
    "e.g. Group all small quick-win tasks together",
    "e.g. Work late until 11:30 PM if needed",
    "e.g. Push heavy tasks earlier, relax later",
    "e.g. Finish admin work before focus session",
    "e.g. Bahar jaana pehle, baaki kaam baad mein",
    "e.g. Clear inbox first then deep focus block",
    "e.g. Balance meetings and development tasks",
    "e.g. Keep 7 PM to 8 PM completely free"
)

private data class DayEndTimePreset(val label: String, val minuteOfDay: Int)

private val DAY_END_PRESETS = listOf(
    DayEndTimePreset("8:00 PM (20:00)", 20 * 60),
    DayEndTimePreset("9:00 PM (21:00)", 21 * 60),
    DayEndTimePreset("10:00 PM (22:00) • Default", 22 * 60),
    DayEndTimePreset("11:00 PM (23:00)", 23 * 60),
    DayEndTimePreset("Midnight (23:59)", 23 * 60 + 59)
)

/** Custom Half-Dotted Circle AI Logo. */
@Composable
fun HalfDottedCircleAiLogo(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 2.5.dp
) {
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { strokeWidth.toPx() }
    Canvas(modifier = modifier) {
        val sweepAngle = 155f
        drawArc(
            color = color,
            startAngle = 190f,
            sweepAngle = sweepAngle,
            useCenter = false,
            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
        )
        drawArc(
            color = color,
            startAngle = 10f,
            sweepAngle = sweepAngle,
            useCenter = false,
            style = Stroke(
                width = strokeWidthPx,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f), 0f)
            )
        )
    }
}

/**
 * Plan-My-Day Screen Redesign adhering to custom-ui-design-guidelines:
 * 1. 20+ Motivational & Energetic variations on setup screen.
 * 2. 20+ Short trial placeholder prompt variations.
 * 3. Removed redundant "Target Day Ending Time" header text.
 * 4. Single lightning bolt emoji inside sliding thumb toggle pill.
 * 5. Friends-Screen solid vibrant colors for top floating header FABs.
 * 6. Interactive Day-End Selector ModalBottomSheet with Time Wheel Slider & Haptics.
 * 7. Extra top clearance spacing below FABs on result screen.
 */
@Composable
fun PlanningScreen(
    state: DayPlanState,
    onRequestPlan: (customDayEndMinute: Int?, userContextPrompt: String?, allowRescheduleFixed: Boolean) -> Unit,
    onAccept: () -> Unit,
    onDiscard: () -> Unit,
    onRetry: () -> Unit,
    onSubmitAdjustment: (String, Boolean) -> Unit,
    onClose: () -> Unit,
    aiChatScreenViewModel: com.theblankstate.preamble.ai.AiChatScreenViewModel? = null,
    modifier: Modifier = Modifier,
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val scaleFactor = (screenWidth.value / 360f).coerceIn(0.85f, 1.15f)

    var isSetupPhase by remember { mutableStateOf(true) }
    var selectedDayEndMinute by remember { mutableIntStateOf(22 * 60) }
    var preAnalysisPrompt by remember { mutableStateOf("") }
    var allowRescheduleFixed by remember { mutableStateOf(false) }

    var isChatMode by remember { mutableStateOf(false) }
    var showExternalChatSheet by remember { mutableStateOf(false) }

    val showSetup = isSetupPhase && (state is DayPlanState.Idle)

    // Scroll state & scroll-aware header visibility tracking
    val scrollState = rememberScrollState()
    var isHeaderVisible by remember { mutableStateOf(true) }
    var lastScrollPosition by remember { mutableIntStateOf(0) }

    LaunchedEffect(scrollState.value) {
        val currentScroll = scrollState.value
        val diff = currentScroll - lastScrollPosition
        if (diff > 12) {
            isHeaderVisible = false
        } else if (diff < -12 || currentScroll < 30) {
            isHeaderVisible = true
        }
        lastScrollPosition = currentScroll
    }

    val handleDiscard = {
        onDiscard()
        isSetupPhase = true
    }

    androidx.activity.compose.BackHandler(enabled = true) {
        if (isChatMode) {
            isChatMode = false
        } else if (!showSetup) {
            handleDiscard()
        } else {
            onClose()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            // Main Content Area
            if (showSetup) {
                if (isChatMode && aiChatScreenViewModel != null) {
                    AiChatScreen(
                        viewModel = aiChatScreenViewModel,
                        showInternalHeader = false,
                        externalShowChatSheet = showExternalChatSheet,
                        onDismissChatSheet = { showExternalChatSheet = false },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 56.dp * scaleFactor)
                    )
                } else {
                    DayPlanSetupContent(
                        selectedDayEndMinute = selectedDayEndMinute,
                        preAnalysisPrompt = preAnalysisPrompt,
                        allowRescheduleFixed = allowRescheduleFixed,
                        onAllowRescheduleFixedChange = { allowRescheduleFixed = it },
                        onPreAnalysisPromptChange = { preAnalysisPrompt = it },
                        onSelectDayEndMinute = { minute -> selectedDayEndMinute = minute },
                        onStartAnalysis = {
                            isSetupPhase = false
                            onRequestPlan(selectedDayEndMinute, preAnalysisPrompt.trim().ifEmpty { null }, allowRescheduleFixed)
                        },
                        scaleFactor = scaleFactor
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        when (state) {
                            DayPlanState.Idle -> Unit

                            DayPlanState.Loading ->
                                AlivePlanningProgress(message = "Analyzing tasks & building schedule…", scaleFactor = scaleFactor)

                            DayPlanState.Applying ->
                                AlivePlanningProgress(message = "Applying your plan…", scaleFactor = scaleFactor)

                            is DayPlanState.Review ->
                                ReviewContent(
                                    review = state,
                                    scrollState = scrollState,
                                    allowRescheduleFixed = allowRescheduleFixed,
                                    onAllowRescheduleFixedChange = { allowRescheduleFixed = it },
                                    onAccept = onAccept,
                                    onDiscard = handleDiscard,
                                    onSubmitAdjustment = onSubmitAdjustment,
                                    scaleFactor = scaleFactor
                                )

                            DayPlanState.Applied ->
                                AppliedContent(
                                    onClose = onClose,
                                    onSubmitAdjustment = { text -> onSubmitAdjustment(text, allowRescheduleFixed) },
                                    scaleFactor = scaleFactor
                                )

                            DayPlanState.Error ->
                                ErrorContent(
                                    onRetry = { onRequestPlan(selectedDayEndMinute, preAnalysisPrompt.trim().ifEmpty { null }, allowRescheduleFixed) },
                                    onClose = onClose,
                                    scaleFactor = scaleFactor
                                )

                            DayPlanState.Failed ->
                                MessageContent(
                                    icon = Icons.Filled.ErrorOutline,
                                    iconTint = MaterialTheme.colorScheme.error,
                                    title = "Couldn't apply the plan",
                                    body = "Something went wrong while applying your schedule. Your tasks were left unchanged.",
                                    primaryLabel = "Done",
                                    onPrimary = onClose,
                                    scaleFactor = scaleFactor
                                )

                            DayPlanState.NoSchedulableTasks ->
                                MessageContent(
                                    icon = Icons.Filled.Info,
                                    iconTint = MaterialTheme.colorScheme.primary,
                                    title = "No uncompleted tasks",
                                    body = "All your tasks for today are already completed! Add a new task to plan your day.",
                                    primaryLabel = "Done",
                                    onPrimary = onClose,
                                    scaleFactor = scaleFactor
                                )

                            DayPlanState.NoRemainingTimeToday ->
                                MessageContent(
                                    icon = Icons.Filled.Schedule,
                                    iconTint = MaterialTheme.colorScheme.primary,
                                    title = "No time left today",
                                    body = "There isn't enough time remaining before your selected day-end time. Try adjusting your day-end time above.",
                                    primaryLabel = "Done",
                                    onPrimary = onClose,
                                    scaleFactor = scaleFactor
                                )

                            DayPlanState.CouldNotGenerate ->
                                MessageContent(
                                    icon = Icons.Filled.Info,
                                    iconTint = MaterialTheme.colorScheme.primary,
                                    title = "Couldn't build a plan",
                                    body = "We couldn't put together a legal schedule from today's tasks. Try adjusting your day-end time or task priorities.",
                                    primaryLabel = "Done",
                                    onPrimary = onClose,
                                    scaleFactor = scaleFactor
                                )

                            DayPlanState.InsufficientCredits ->
                                MessageContent(
                                    icon = Icons.Filled.Info,
                                    iconTint = MaterialTheme.colorScheme.primary,
                                    title = "Out of AI credits",
                                    body = "You need more AI credits to plan your day. Top up and try again.",
                                    primaryLabel = "Done",
                                    onPrimary = onClose,
                                    scaleFactor = scaleFactor
                                )
                        }
                    }
                }
            }

            // Floating Header Top Bar with Friends-Screen Colorized FAB Buttons
            AnimatedVisibility(
                visible = isHeaderVisible,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp * scaleFactor, vertical = 6.dp * scaleFactor)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back FAB Badge (Soft Blue Accent)
                    val backInteraction = remember { MutableInteractionSource() }
                    Surface(
                        onClick = {
                            if (isChatMode) {
                                isChatMode = false
                            } else if (!showSetup) {
                                handleDiscard()
                            } else {
                                onClose()
                            }
                        },
                        shape = CircleShape,
                        color = Color(0xFFA1C6FF),
                        modifier = Modifier.expressivePressScale(backInteraction)
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp * scaleFactor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp * scaleFactor)
                            )
                        }
                    }

                    // Morphing Header Badge: Setup shows Title Badge with mode switcher, Morphs to Day End Chip on analysis/review
                    AnimatedContent(
                        targetState = showSetup to isChatMode,
                        transitionSpec = { (fadeIn() + slideInVertically { -it }) togetherWith (fadeOut() + slideOutVertically { it }) },
                        label = "headerBadgeMorph"
                    ) { (isSetup, chatMode) ->
                        if (isSetup) {
                            val modeBadgeInteraction = remember { MutableInteractionSource() }
                            Surface(
                                onClick = { isChatMode = !isChatMode },
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .padding(horizontal = 4.dp * scaleFactor)
                                    .expressivePressScale(modeBadgeInteraction)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp * scaleFactor, vertical = 8.dp * scaleFactor),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    HalfDottedCircleAiLogo(
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp * scaleFactor)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp * scaleFactor))
                                    Text(
                                        text = if (chatMode) "AI Chat ❯" else "AI Day Planner ❯",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp * scaleFactor,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            PreambleDayEndCapsuleChip(
                                selectedDayEndMinute = selectedDayEndMinute,
                                onSelectDayEndMinute = { minute ->
                                    selectedDayEndMinute = minute
                                    onRequestPlan(minute, preAnalysisPrompt.trim().ifEmpty { null }, allowRescheduleFixed)
                                },
                                scaleFactor = scaleFactor
                            )
                        }
                    }

                    // Close / History FAB Badge
                    val closeInteraction = remember { MutableInteractionSource() }
                    Surface(
                        onClick = {
                            if (isChatMode && showSetup) {
                                showExternalChatSheet = true
                            } else {
                                onClose()
                            }
                        },
                        shape = CircleShape,
                        color = Color(0xFFFF9E9E),
                        modifier = Modifier.expressivePressScale(closeInteraction)
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp * scaleFactor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isChatMode && showSetup) Icons.Filled.History else Icons.Filled.Close,
                                contentDescription = if (isChatMode && showSetup) "Chat History" else "Close",
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp * scaleFactor)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Pre-Planning Setup Surface with 20+ motivational & placeholder variations. */
@Composable
private fun DayPlanSetupContent(
    selectedDayEndMinute: Int,
    preAnalysisPrompt: String,
    allowRescheduleFixed: Boolean,
    onAllowRescheduleFixedChange: (Boolean) -> Unit,
    onPreAnalysisPromptChange: (String) -> Unit,
    onSelectDayEndMinute: (Int) -> Unit,
    onStartAnalysis: () -> Unit,
    scaleFactor: Float
) {
    // 20+ Motivational & Placeholder variations generated once per screen display
    val motivational = remember { MOTIVATIONAL_PRESETS.random() }
    val randomPlaceholder = remember { PLACEHOLDER_PRESETS.random() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp * scaleFactor)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(58.dp * scaleFactor))

        WigglingMaterialShapeHeader(scaleFactor = scaleFactor)

        Spacer(modifier = Modifier.height(20.dp * scaleFactor))

        // Dynamic Energetic Title
        Text(
            text = motivational.title,
            fontWeight = FontWeight.Black,
            fontSize = 22.sp * scaleFactor,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp * scaleFactor))

        // Dynamic Motivational Description
        Text(
            text = motivational.description,
            fontSize = 12.sp * scaleFactor,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp * scaleFactor))

        // Pre-Analysis Prompt Input Field with 20+ trial placeholder variations
        OutlinedTextField(
            value = preAnalysisPrompt,
            onValueChange = onPreAnalysisPromptChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    randomPlaceholder,
                    fontSize = 11.5.sp * scaleFactor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            maxLines = 2,
            singleLine = false,
            shape = RoundedCornerShape(18.dp * scaleFactor),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            )
        )

        Spacer(modifier = Modifier.height(16.dp * scaleFactor))

        UniqueReschedulePillToggle(
            checked = allowRescheduleFixed,
            onCheckedChange = onAllowRescheduleFixedChange,
            compact = false,
            scaleFactor = scaleFactor
        )

        Spacer(modifier = Modifier.height(20.dp * scaleFactor))

        // Day End Time Capsule Chip Selector (Uncolored on setup screen)
        PreambleDayEndCapsuleChip(
            selectedDayEndMinute = selectedDayEndMinute,
            onSelectDayEndMinute = onSelectDayEndMinute,
            isColored = false,
            scaleFactor = scaleFactor
        )

        Spacer(modifier = Modifier.height(24.dp * scaleFactor))

        val analyzeInteraction = remember { MutableInteractionSource() }
        Button(
            onClick = onStartAnalysis,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(48.dp * scaleFactor)
                .expressivePressScale(analyzeInteraction)
        ) {
            HalfDottedCircleAiLogo(
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp * scaleFactor)
            )
            Spacer(modifier = Modifier.width(8.dp * scaleFactor))
            Text(
                text = "Analyze & Plan Day",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp * scaleFactor
            )
        }

        Spacer(modifier = Modifier.height(40.dp * scaleFactor))
    }
}

/** Unique Animated Sliding Pill Toggle with Single Lightning Bolt Emoji in Thumb Circle. */
@Composable
private fun UniqueReschedulePillToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    compact: Boolean = false,
    scaleFactor: Float
) {
    val toggleInteraction = remember { MutableInteractionSource() }
    val maxOffset = if (compact) 16.dp * scaleFactor else 22.dp * scaleFactor
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) maxOffset else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "thumbOffset"
    )

    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(50),
        color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.expressivePressScale(toggleInteraction)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 10.dp * scaleFactor else 14.dp * scaleFactor,
                vertical = if (compact) 5.dp * scaleFactor else 8.dp * scaleFactor
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(if (compact) 34.dp * scaleFactor else 44.dp * scaleFactor)
                    .height(if (compact) 18.dp * scaleFactor else 22.dp * scaleFactor)
                    .clip(CircleShape)
                    .background(if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    .padding(2.dp * scaleFactor),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .padding(start = thumbOffset.coerceAtLeast(0.dp))
                        .size(if (compact) 14.dp * scaleFactor else 18.dp * scaleFactor)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    if (checked) {
                        Text("⚡", fontSize = if (compact) 8.sp * scaleFactor else 10.sp * scaleFactor)
                    }
                }
            }

            Spacer(modifier = Modifier.width(if (compact) 6.dp * scaleFactor else 10.dp * scaleFactor))

            // Single lightning bolt emoji used ONLY in thumb circle above; text is clean
            Text(
                text = if (compact) {
                    if (checked) "Re-time ON" else "Re-time Fixed"
                } else {
                    if (checked) "Re-time Fixed Tasks Allowed" else "Allow Rescheduling Fixed Tasks"
                },
                fontSize = if (compact) 10.5.sp * scaleFactor else 11.5.sp * scaleFactor,
                fontWeight = FontWeight.Bold,
                color = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Floating Day-End Time Capsule Chip FAB opening custom Time Selector ModalBottomSheet with Haptics. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreambleDayEndCapsuleChip(
    selectedDayEndMinute: Int,
    onSelectDayEndMinute: (Int) -> Unit,
    scaleFactor: Float,
    isColored: Boolean = true,
    modifier: Modifier = Modifier
) {
    var isSheetOpen by remember { mutableStateOf(false) }

    val formattedTime = remember(selectedDayEndMinute) {
        val h = selectedDayEndMinute / 60
        val m = selectedDayEndMinute % 60
        val amPm = if (h >= 12) "PM" else "AM"
        val displayH = if (h % 12 == 0) 12 else h % 12
        val displayM = if (m < 10) "0$m" else "$m"
        "$displayH:$displayM $amPm"
    }

    Box(modifier = modifier) {
        val chipInteraction = remember { MutableInteractionSource() }
        Surface(
            onClick = { isSheetOpen = true },
            shape = RoundedCornerShape(50),
            color = if (isColored) Color(0xFFFFD166) else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.expressivePressScale(chipInteraction)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp * scaleFactor, vertical = 8.dp * scaleFactor),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "🌙", fontSize = 13.sp * scaleFactor)
                Spacer(modifier = Modifier.width(5.dp * scaleFactor))
                Text(
                    text = "Day Ends at $formattedTime",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp * scaleFactor,
                    color = if (isColored) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp * scaleFactor))
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Select Time",
                    tint = if (isColored) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp * scaleFactor)
                )
            }
        }

        // Custom ModalBottomSheet for Day End Time Selection with Haptic Time Slider
        if (isSheetOpen) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val haptic = LocalHapticFeedback.current

            ModalBottomSheet(
                onDismissRequest = { isSheetOpen = false },
                sheetState = sheetState,
                shape = RoundedCornerShape(topStart = 28.dp * scaleFactor, topEnd = 28.dp * scaleFactor),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp * scaleFactor, vertical = 12.dp * scaleFactor),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🌙 Set Target Day Ending Time",
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp * scaleFactor,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(16.dp * scaleFactor))

                    // Preset Chips Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp * scaleFactor)
                    ) {
                        DAY_END_PRESETS.forEach { preset ->
                            val presetInteraction = remember { MutableInteractionSource() }
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectDayEndMinute(preset.minuteOfDay)
                                },
                                shape = RoundedCornerShape(50),
                                color = if (preset.minuteOfDay == selectedDayEndMinute) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.expressivePressScale(presetInteraction)
                            ) {
                                Text(
                                    text = preset.label,
                                    modifier = Modifier.padding(horizontal = 12.dp * scaleFactor, vertical = 6.dp * scaleFactor),
                                    fontSize = 11.5.sp * scaleFactor,
                                    fontWeight = FontWeight.Bold,
                                    color = if (preset.minuteOfDay == selectedDayEndMinute) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp * scaleFactor))

                    // Time Slider (18:00 to 23:59) with Haptics on change
                    var sliderPos by remember { mutableFloatStateOf(selectedDayEndMinute.toFloat()) }

                    Text(
                        text = formattedTime,
                        fontSize = 28.sp * scaleFactor,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(12.dp * scaleFactor))

                    Slider(
                        value = sliderPos,
                        onValueChange = { newVal ->
                            val rounded = (newVal.toInt() / 15) * 15 // Snap to 15-min intervals
                            if (rounded != selectedDayEndMinute) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectDayEndMinute(rounded.coerceIn(18 * 60, 23 * 60 + 59))
                            }
                            sliderPos = newVal
                        },
                        valueRange = (18 * 60).toFloat()..(23 * 60 + 59).toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp * scaleFactor))

                    Button(
                        onClick = { isSheetOpen = false },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(44.dp * scaleFactor)
                    ) {
                        Text("Confirm Time", fontWeight = FontWeight.Bold, fontSize = 13.sp * scaleFactor)
                    }

                    Spacer(modifier = Modifier.height(30.dp * scaleFactor))
                }
            }
        }
    }
}

/** Animated Wiggling Material 3 Expressive Shape Header. */
@Composable
private fun WigglingMaterialShapeHeader(scaleFactor: Float) {
    val shapeColor = MaterialTheme.colorScheme.secondaryContainer

    val morph = remember {
        Morph(
            start = RoundedPolygon.star(
                numVerticesPerRadius = 6,
                innerRadius = 0.65f,
                rounding = CornerRounding(0.25f),
            ),
            end = RoundedPolygon(
                numVertices = 8,
                rounding = CornerRounding(0.35f),
            ),
        )
    }
    val transition = rememberInfiniteTransition(label = "setupWiggle")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "setupMorphProgress",
    )
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000),
            repeatMode = RepeatMode.Restart,
        ),
        label = "setupRotation",
    )

    Box(
        modifier = Modifier
            .size(110.dp * scaleFactor)
            .clip(MorphPolygonShape(morph, progress, rotation))
            .background(shapeColor),
        contentAlignment = Alignment.Center,
    ) {
        HalfDottedCircleAiLogo(
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(44.dp * scaleFactor)
        )
    }
}

/** Loading surface. */
@Composable
private fun AlivePlanningProgress(
    message: String,
    scaleFactor: Float
) {
    val containerColor = MaterialTheme.colorScheme.primaryContainer

    val morph = remember {
        Morph(
            start = RoundedPolygon.star(
                numVerticesPerRadius = 8,
                innerRadius = 0.7f,
                rounding = CornerRounding(0.2f),
            ),
            end = RoundedPolygon(
                numVertices = 6,
                rounding = CornerRounding(0.3f),
            ),
        )
    }
    val transition = rememberInfiniteTransition(label = "planLoading")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "planLoadingMorph",
    )
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000),
            repeatMode = RepeatMode.Restart,
        ),
        label = "planLoadingRotation",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp * scaleFactor),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(100.dp * scaleFactor)
                    .clip(MorphPolygonShape(morph, progress, rotation))
                    .background(containerColor),
                contentAlignment = Alignment.Center,
            ) {
                HalfDottedCircleAiLogo(
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp * scaleFactor)
                )
            }
            CircularProgressIndicator(
                modifier = Modifier.size(118.dp * scaleFactor),
                strokeWidth = 3.5.dp * scaleFactor,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = message,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp * scaleFactor,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

/** Review Content with extra clearance spacing below top floating FABs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewContent(
    review: DayPlanState.Review,
    scrollState: androidx.compose.foundation.ScrollState,
    allowRescheduleFixed: Boolean,
    onAllowRescheduleFixedChange: (Boolean) -> Unit,
    onAccept: () -> Unit,
    onDiscard: () -> Unit,
    onSubmitAdjustment: (String, Boolean) -> Unit,
    scaleFactor: Float
) {
    var adjustment by remember { mutableStateOf("") }
    val assignments = review.schedule.assignments
    val taskCount = assignments.size

    var showDetailSheet by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp * scaleFactor, vertical = 2.dp * scaleFactor),
        ) {
            // Extra top clearance spacer so content starts cleanly below header FABs
            Spacer(modifier = Modifier.height(80.dp * scaleFactor))

            // ORDER 1: TASK LIST
            Text(
                text = "Proposed Schedule ($taskCount tasks)",
                fontWeight = FontWeight.Black,
                fontSize = 15.sp * scaleFactor,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp * scaleFactor))

            if (review.isRefining) {
                SkeletonTaskList(count = assignments.size.coerceAtLeast(3), scaleFactor = scaleFactor)
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    assignments.forEachIndexed { index, assignment ->
                        val title = review.tasksById[assignment.taskId]?.title ?: "Untitled task"
                        val reason = review.taskReasons[assignment.taskId]
                        val anchorColor = PreambleCardColors[index % PreambleCardColors.size]

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp * scaleFactor),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp * scaleFactor)
                                    .clip(CircleShape)
                                    .background(anchorColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Schedule,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp * scaleFactor)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp * scaleFactor))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp * scaleFactor,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(1.dp * scaleFactor))
                                Text(
                                    text = reason ?: "Scheduled slot",
                                    fontSize = 11.5.sp * scaleFactor,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = assignment.time,
                                    modifier = Modifier.padding(horizontal = 12.dp * scaleFactor, vertical = 5.dp * scaleFactor),
                                    fontSize = 12.sp * scaleFactor,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        if (index < assignments.size - 1) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                thickness = 1.dp,
                                modifier = Modifier.padding(start = 56.dp * scaleFactor)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp * scaleFactor))

            // HORIZONTAL SWIPEABLE CARDS CAROUSEL
            Text(
                text = "AI Insights & Briefings (Tap for Full Detail 🔍)",
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp * scaleFactor,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp * scaleFactor))

            if (review.isRefining) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp * scaleFactor)
                ) {
                    SkeletonCard(width = 285.dp * scaleFactor, height = 145.dp * scaleFactor, scaleFactor = scaleFactor)
                    SkeletonCard(width = 285.dp * scaleFactor, height = 145.dp * scaleFactor, scaleFactor = scaleFactor)
                }
            } else {
                val cardWidth = 285.dp * scaleFactor
                val cardHeight = 145.dp * scaleFactor
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp * scaleFactor)
                ) {
                    // Card 1: Executive Briefing
                    val briefingText = review.briefing
                        ?: "AI analyzed your $taskCount uncompleted tasks for today and structured an optimal schedule window."

                    val card1Interaction = remember { MutableInteractionSource() }
                    Surface(
                        onClick = { showDetailSheet = true },
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(22.dp * scaleFactor),
                        modifier = Modifier
                            .width(cardWidth)
                            .height(cardHeight)
                            .expressivePressScale(card1Interaction)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp * scaleFactor)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                HalfDottedCircleAiLogo(
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp * scaleFactor)
                                )
                                Spacer(modifier = Modifier.width(6.dp * scaleFactor))
                                Text(
                                    text = "Executive Briefing",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp * scaleFactor,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp * scaleFactor))
                            FormattedBulletText(
                                rawText = briefingText,
                                textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                scaleFactor = scaleFactor
                            )
                        }
                    }

                    // Card 2: Recommendation
                    if (!review.recommendation.isNullOrBlank()) {
                        val card2Interaction = remember { MutableInteractionSource() }
                        Surface(
                            onClick = { showDetailSheet = true },
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(22.dp * scaleFactor),
                            modifier = Modifier
                                .width(cardWidth)
                                .height(cardHeight)
                                .expressivePressScale(card2Interaction)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp * scaleFactor)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Lightbulb,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(18.dp * scaleFactor)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp * scaleFactor))
                                    Text(
                                        text = "Recommendation",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp * scaleFactor,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp * scaleFactor))
                                FormattedBulletText(
                                    rawText = review.recommendation,
                                    textColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    scaleFactor = scaleFactor
                                )
                            }
                        }
                    }

                    // Card 3: Unplaced Notices
                    val unplacedTitles = review.schedule.unplaced.map { it.title }
                    if (review.advisory != null || unplacedTitles.isNotEmpty()) {
                        val card3Interaction = remember { MutableInteractionSource() }
                        Surface(
                            onClick = { showDetailSheet = true },
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(22.dp * scaleFactor),
                            modifier = Modifier
                                .width(cardWidth)
                                .height(cardHeight)
                                .expressivePressScale(card3Interaction)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp * scaleFactor)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp * scaleFactor)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp * scaleFactor))
                                    Text(
                                        text = "Unplaced Items Notice",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp * scaleFactor,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp * scaleFactor))
                                unplacedTitles.forEach { tTitle ->
                                    Text(
                                        text = "• $tTitle",
                                        fontSize = 11.5.sp * scaleFactor,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp * scaleFactor))

            // BUTTONS TO DISCARD AND ACCEPT PLAN
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp * scaleFactor)
            ) {
                val discardInteraction = remember { MutableInteractionSource() }
                Button(
                    onClick = onDiscard,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp * scaleFactor)
                        .expressivePressScale(discardInteraction),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text("Discard", fontSize = 13.sp * scaleFactor, fontWeight = FontWeight.Bold)
                }

                val acceptInteraction = remember { MutableInteractionSource() }
                Button(
                    onClick = onAccept,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp * scaleFactor)
                        .expressivePressScale(acceptInteraction),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Accept Plan", fontSize = 13.sp * scaleFactor, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(18.dp * scaleFactor))

            // REFINE SCHEDULE INPUT ROW & COMPACT TOGGLE
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Refine Schedule",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp * scaleFactor,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Compact Pill Toggle on Review screen
                UniqueReschedulePillToggle(
                    checked = allowRescheduleFixed,
                    onCheckedChange = onAllowRescheduleFixedChange,
                    compact = true,
                    scaleFactor = scaleFactor
                )
            }

            Spacer(modifier = Modifier.height(8.dp * scaleFactor))

            // Input field matching Revise button height exactly
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp * scaleFactor)
            ) {
                OutlinedTextField(
                    value = adjustment,
                    onValueChange = { adjustment = it },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp * scaleFactor),
                    placeholder = {
                        Text(
                            "e.g. go outside earliest",
                            fontSize = 11.5.sp * scaleFactor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    maxLines = 1,
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    )
                )

                val reviseInteraction = remember { MutableInteractionSource() }
                Button(
                    onClick = {
                        val text = adjustment.trim()
                        if (text.isNotEmpty()) {
                            onSubmitAdjustment(text, allowRescheduleFixed)
                            adjustment = ""
                        }
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 16.dp * scaleFactor),
                    modifier = Modifier
                        .height(46.dp * scaleFactor)
                        .expressivePressScale(reviseInteraction)
                ) {
                    HalfDottedCircleAiLogo(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp * scaleFactor)
                    )
                    Spacer(modifier = Modifier.width(4.dp * scaleFactor))
                    Text(text = "Revise", fontSize = 12.sp * scaleFactor, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(100.dp * scaleFactor))
        }

        // Full Insight Card Detail ModalBottomSheet
        if (showDetailSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showDetailSheet = false },
                sheetState = sheetState,
                shape = RoundedCornerShape(topStart = 28.dp * scaleFactor, topEnd = 28.dp * scaleFactor),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp * scaleFactor, vertical = 10.dp * scaleFactor)
                ) {
                    Text(
                        text = "Full AI Insight Cards (Swipe 👉)",
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp * scaleFactor,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp * scaleFactor))

                    val detailWidth = 310.dp * scaleFactor
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(14.dp * scaleFactor)
                    ) {
                        // Executive Briefing Detail
                        val briefingText = review.briefing
                            ?: "AI analyzed your $taskCount uncompleted tasks for today and structured an optimal schedule window."

                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(24.dp * scaleFactor),
                            modifier = Modifier
                                .width(detailWidth)
                                .fillMaxHeight(0.6f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp * scaleFactor)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    HalfDottedCircleAiLogo(
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(22.dp * scaleFactor)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp * scaleFactor))
                                    Text(
                                        text = "Executive Briefing",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 16.sp * scaleFactor,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp * scaleFactor))
                                FormattedBulletText(
                                    rawText = briefingText,
                                    textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    scaleFactor = scaleFactor
                                )
                            }
                        }

                        // Recommendation Detail
                        if (!review.recommendation.isNullOrBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(24.dp * scaleFactor),
                                modifier = Modifier
                                    .width(detailWidth)
                                    .fillMaxHeight(0.6f)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(20.dp * scaleFactor)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.Lightbulb,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.size(22.dp * scaleFactor)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp * scaleFactor))
                                        Text(
                                            text = "Recommendation",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp * scaleFactor,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp * scaleFactor))
                                    FormattedBulletText(
                                        rawText = review.recommendation,
                                        textColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                        scaleFactor = scaleFactor
                                    )
                                }
                            }
                        }

                        // Unplaced Notices Detail
                        val unplacedTitles = review.schedule.unplaced.map { it.title }
                        if (review.advisory != null || unplacedTitles.isNotEmpty()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(24.dp * scaleFactor),
                                modifier = Modifier
                                    .width(detailWidth)
                                    .fillMaxHeight(0.6f)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(20.dp * scaleFactor)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(22.dp * scaleFactor)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp * scaleFactor))
                                        Text(
                                            text = "Unplaced Tasks Notice",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp * scaleFactor,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp * scaleFactor))
                                    if (unplacedTitles.isNotEmpty()) {
                                        Text(
                                            text = "The following tasks could not fit in today's remaining time window:",
                                            fontSize = 12.sp * scaleFactor,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(8.dp * scaleFactor))
                                        unplacedTitles.forEach { tTitle ->
                                            Text(
                                                text = "• $tTitle",
                                                fontSize = 12.sp * scaleFactor,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else if (review.advisory != null) {
                                        Text(
                                            text = review.advisory,
                                            fontSize = 12.sp * scaleFactor,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(30.dp * scaleFactor))
                }
            }
        }
    }
}

/** Skeleton Card helper for card shimmers. */
@Composable
private fun SkeletonCard(width: Dp, height: Dp, scaleFactor: Float) {
    val transition = rememberInfiniteTransition(label = "skeletonCard")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonCardAlpha"
    )

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(20.dp * scaleFactor))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.15f))
    )
}

/** Formatted text helper for bullet points. */
@Composable
private fun FormattedBulletText(
    rawText: String,
    textColor: Color,
    scaleFactor: Float
) {
    val lines = remember(rawText) {
        rawText.split("\n", ". ")
            .map { it.trim().removePrefix("•").removePrefix("-").trim() }
            .filter { it.isNotEmpty() }
    }

    if (lines.size > 1) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp * scaleFactor)) {
            lines.forEach { line ->
                val displayText = if (line.endsWith(".")) line else "$line."
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "• ",
                        fontSize = 11.5.sp * scaleFactor,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Text(
                        text = displayText,
                        fontSize = 11.5.sp * scaleFactor,
                        lineHeight = 15.sp * scaleFactor,
                        color = textColor
                    )
                }
            }
        }
    } else {
        Text(
            text = rawText,
            fontSize = 11.5.sp * scaleFactor,
            lineHeight = 15.sp * scaleFactor,
            color = textColor
        )
    }
}

/** Skeleton Loading Shimmer for Refine Schedule. */
@Composable
private fun SkeletonTaskList(count: Int, scaleFactor: Float) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp * scaleFactor)) {
        repeat(count) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp * scaleFactor)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.2f))
                )
                Spacer(modifier = Modifier.width(12.dp * scaleFactor))
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(14.dp * scaleFactor)
                            .clip(RoundedCornerShape(4.dp * scaleFactor))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.2f))
                    )
                    Spacer(modifier = Modifier.height(4.dp * scaleFactor))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(10.dp * scaleFactor)
                            .clip(RoundedCornerShape(4.dp * scaleFactor))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.15f))
                    )
                }
                Box(
                    modifier = Modifier
                        .width(50.dp * scaleFactor)
                        .height(24.dp * scaleFactor)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.2f))
                )
            }
        }
    }
}

/** Post-apply success surface. */
@Composable
private fun AppliedContent(
    onClose: () -> Unit,
    onSubmitAdjustment: (String) -> Unit,
    scaleFactor: Float
) {
    var adjustment by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp * scaleFactor)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp * scaleFactor),
    ) {
        Spacer(modifier = Modifier.height(80.dp * scaleFactor))
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp * scaleFactor),
        )
        Text(
            text = "Day Plan Applied!",
            fontWeight = FontWeight.Black,
            fontSize = 18.sp * scaleFactor,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Your tasks now have their proposed times. You can request further adjustments anytime.",
            fontSize = 12.sp * scaleFactor,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(6.dp * scaleFactor))

        OutlinedTextField(
            value = adjustment,
            onValueChange = { adjustment = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp * scaleFactor),
            placeholder = { Text("e.g. push everything 30 minutes later", fontSize = 12.sp * scaleFactor) },
            maxLines = 1,
            singleLine = true,
            shape = RoundedCornerShape(50),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp * scaleFactor)
        ) {
            val reviseInteraction = remember { MutableInteractionSource() }
            Button(
                onClick = {
                    val text = adjustment.trim()
                    if (text.isNotEmpty()) {
                        onSubmitAdjustment(text)
                        adjustment = ""
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp * scaleFactor)
                    .expressivePressScale(reviseInteraction),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                HalfDottedCircleAiLogo(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp * scaleFactor)
                )
                Spacer(modifier = Modifier.width(4.dp * scaleFactor))
                Text("Adjust Again", fontSize = 12.sp * scaleFactor, fontWeight = FontWeight.Bold)
            }

            val doneInteraction = remember { MutableInteractionSource() }
            Button(
                onClick = onClose,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp * scaleFactor)
                    .expressivePressScale(doneInteraction),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Done", fontSize = 13.sp * scaleFactor, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(40.dp * scaleFactor))
    }
}

/** Error surface. */
@Composable
private fun ErrorContent(
    onRetry: () -> Unit,
    onClose: () -> Unit,
    scaleFactor: Float
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp * scaleFactor),
        modifier = Modifier.padding(horizontal = 20.dp * scaleFactor)
    ) {
        Spacer(modifier = Modifier.height(80.dp * scaleFactor))
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp * scaleFactor),
        )
        Text(
            text = "We couldn't reach the planner",
            fontWeight = FontWeight.Black,
            fontSize = 18.sp * scaleFactor,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Check your connection and try again.",
            fontSize = 12.sp * scaleFactor,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp * scaleFactor),
        ) {
            val closeInteraction = remember { MutableInteractionSource() }
            Button(
                onClick = onClose,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp * scaleFactor)
                    .expressivePressScale(closeInteraction),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text("Close", fontSize = 13.sp * scaleFactor)
            }

            val retryInteraction = remember { MutableInteractionSource() }
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp * scaleFactor)
                    .expressivePressScale(retryInteraction),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Retry", fontSize = 13.sp * scaleFactor, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(40.dp * scaleFactor))
    }
}

/** Reusable terminal-message surface. */
@Composable
private fun MessageContent(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    scaleFactor: Float
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp * scaleFactor),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp * scaleFactor),
    ) {
        Spacer(modifier = Modifier.height(80.dp * scaleFactor))
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(48.dp * scaleFactor),
        )
        Text(
            text = title,
            fontWeight = FontWeight.Black,
            fontSize = 18.sp * scaleFactor,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = body,
            fontSize = 12.sp * scaleFactor,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        val buttonInteraction = remember { MutableInteractionSource() }
        Button(
            onClick = onPrimary,
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(42.dp * scaleFactor)
                .expressivePressScale(buttonInteraction),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(primaryLabel, fontSize = 13.sp * scaleFactor, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(40.dp * scaleFactor))
    }
}

/** Expressive Press Scale Modifier. */
@Composable
private fun Modifier.expressivePressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.92f
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "expressivePressScale"
    )
    return this.scale(scale)
}
