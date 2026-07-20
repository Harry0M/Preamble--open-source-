package com.theblankstate.preamble.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theblankstate.preamble.data.PredefinedTags
import com.theblankstate.preamble.data.Task
import com.theblankstate.preamble.ui.components.TaskItem
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class CalendarViewMode(val label: String, val icon: ImageVector) {
    Month("Month", Icons.Default.CalendarMonth),
    Simple("Simple", Icons.Default.Today),
    Week("Week", Icons.Default.ViewWeek),
    Day("Day", Icons.Default.ViewDay)
}

private val PriorityHigh = Color(0xFFEF4444)
private val PriorityMedium = Color(0xFFF97316)
private val PriorityLow = Color(0xFF3B82F6)
private val GoogleBlue = Color(0xFF4285F4)
private val TaskTeal = Color(0xFF039BE5)
private const val PAGER_MID = 5000 // virtual center page

private val PreambleCardColors = listOf(
    Color(0xFFA1C6FF), // Soft Blue
    Color(0xFFEAB3FF), // Soft Purple
    Color(0xFFFFD166), // Soft Yellow
    Color(0xFFFF9E9E), // Soft Coral
    Color(0xFF9EE8FF), // Soft Cyan
    Color(0xFFFFC085)  // Soft Orange
)

@androidx.compose.runtime.Immutable
data class GridDay(val day: Int, val inMonth: Boolean)

/**
 * Helper modifier for physics-based bouncy scale feedback on touch.
 */
@Composable
private fun Modifier.expressivePressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.93f,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    selectedDateTasks: List<Task>,
    heatMap: Map<Int, Pair<Int, Int>>,
    monthTasksByDay: Map<Int, List<Task>>,
    getCachedMonthData: (String) -> com.theblankstate.preamble.viewmodel.TaskViewModel.MonthData?,
    loadMonthData: suspend (Int, Int) -> com.theblankstate.preamble.viewmodel.TaskViewModel.MonthData,
    refreshTick: Int,
    onDateSelected: (String?) -> Unit,
    onMonthChanged: (Int, Int) -> Unit,
    onToggleTask: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit,
    onTaskDetail: (Task) -> Unit,
    onAddTask: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Dynamic scale factor based on screen width
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val scaleFactor = (screenWidthDp / 360f).coerceIn(0.85f, 1.15f)

    var viewMode by remember { mutableStateOf(CalendarViewMode.Month) }
    var currentMonth by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedDay by remember { mutableIntStateOf(-1) }
    var weekStart by remember { mutableStateOf(Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY) }) }
    var activeFilters by remember { mutableStateOf(setOf<String>()) }
    var showFilters by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val sdfDate = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val sdfMonth = remember { SimpleDateFormat("MMMM yyyy", Locale.US) }
    val todayStr = remember { sdfDate.format(Date()) }
    val todayCal = remember { Calendar.getInstance() }

    // ── Pager states (one per view mode) ──
    val monthPager = rememberPagerState(initialPage = PAGER_MID) { PAGER_MID * 2 }
    val simplePager = rememberPagerState(initialPage = PAGER_MID) { PAGER_MID * 2 }
    val weekPager = rememberPagerState(initialPage = PAGER_MID) { PAGER_MID * 2 }
    val dayPager = rememberPagerState(initialPage = PAGER_MID) { PAGER_MID * 2 }

    // Base calendars for offset calculation
    val baseMonth = remember { Calendar.getInstance() }
    val baseWeek = remember { Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY) } }
    val baseDay = remember { Calendar.getInstance() }

    fun monthFromPage(page: Int): Calendar = (baseMonth.clone() as Calendar).apply { add(Calendar.MONTH, page - PAGER_MID) }
    fun weekFromPage(page: Int): Calendar = (baseWeek.clone() as Calendar).apply { add(Calendar.WEEK_OF_YEAR, page - PAGER_MID) }
    fun dayFromPage(page: Int): Calendar = (baseDay.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, page - PAGER_MID) }

    // Sync pager → state
    LaunchedEffect(Unit) {
        snapshotFlow { monthPager.settledPage }.collect { page ->
            val m = monthFromPage(page)
            currentMonth = m; selectedDay = -1; onDateSelected(null)
            onMonthChanged(m.get(Calendar.YEAR), m.get(Calendar.MONTH))
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { simplePager.settledPage }.collect { page ->
            val m = monthFromPage(page)
            currentMonth = m; selectedDay = -1; onDateSelected(null)
            onMonthChanged(m.get(Calendar.YEAR), m.get(Calendar.MONTH))
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { weekPager.settledPage }.collect { page ->
            val w = weekFromPage(page)
            weekStart = w; currentMonth = w.clone() as Calendar; selectedDay = -1; onDateSelected(null)
            onMonthChanged(w.get(Calendar.YEAR), w.get(Calendar.MONTH))
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { dayPager.settledPage }.collect { page ->
            val d = dayFromPage(page)
            currentMonth = d.clone() as Calendar
            selectedDay = d.get(Calendar.DAY_OF_MONTH)
            onDateSelected(sdfDate.format(d.time))
            onMonthChanged(d.get(Calendar.YEAR), d.get(Calendar.MONTH))
        }
    }

    val isCurrentMonth = remember(currentMonth) {
        val now = Calendar.getInstance()
        currentMonth.get(Calendar.YEAR) == now.get(Calendar.YEAR) && currentMonth.get(Calendar.MONTH) == now.get(Calendar.MONTH)
    }

    val filteredTasks = remember(selectedDateTasks, activeFilters) {
        if (activeFilters.isEmpty()) selectedDateTasks
        else selectedDateTasks.filter { task ->
            activeFilters.any { f ->
                when (f) { "high" -> task.priority == 3; "medium" -> task.priority == 2; "low" -> task.priority == 1; "timed" -> task.deadlineTime != null; "google" -> task.isCalendarEvent || task.isGoogleTask; else -> f in task.tagList }
            }
        }
    }
    val activeTags = remember(selectedDateTasks) { selectedDateTasks.flatMap { it.tagList }.distinct().take(8) }

    fun handleDateClick(dateStr: String) {
        try {
            val cal = Calendar.getInstance(); cal.time = sdfDate.parse(dateStr)!!
            if (cal.get(Calendar.MONTH) != currentMonth.get(Calendar.MONTH) || cal.get(Calendar.YEAR) != currentMonth.get(Calendar.YEAR)) currentMonth = cal.clone() as Calendar
            selectedDay = cal.get(Calendar.DAY_OF_MONTH); onDateSelected(dateStr)
        } catch (_: Exception) {}
    }

    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVar = MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ═══ TOP NAVBAR HEADER ═══
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp * scaleFactor, vertical = 6.dp * scaleFactor),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Prev Arrow Button
                val prevInteraction = remember { MutableInteractionSource() }
                Surface(
                    onClick = {
                        scope.launch {
                            when (viewMode) {
                                CalendarViewMode.Month -> monthPager.animateScrollToPage(monthPager.currentPage - 1)
                                CalendarViewMode.Simple -> simplePager.animateScrollToPage(simplePager.currentPage - 1)
                                CalendarViewMode.Week -> weekPager.animateScrollToPage(weekPager.currentPage - 1)
                                CalendarViewMode.Day -> dayPager.animateScrollToPage(dayPager.currentPage - 1)
                            }
                        }
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier.expressivePressScale(prevInteraction)
                ) {
                    Box(modifier = Modifier.size(42.dp * scaleFactor), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp * scaleFactor)
                        )
                    }
                }

                // Centered Month & Subtitle Capsule Title
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier.height(42.dp * scaleFactor)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp * scaleFactor, vertical = 6.dp * scaleFactor),
                        horizontalArrangement = Arrangement.spacedBy(6.dp * scaleFactor),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = sdfMonth.format(currentMonth.time),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = (15 * scaleFactor).sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (viewMode == CalendarViewMode.Day && selectedDay > 0) {
                            val c = (currentMonth.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, selectedDay) }
                            Text(
                                text = "• " + SimpleDateFormat("EEE d", Locale.getDefault()).format(c.time),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = (13 * scaleFactor).sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Action Controls on Right (Today + Filter + Next)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp * scaleFactor),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isCurrentMonth) {
                        val todayInteraction = remember { MutableInteractionSource() }
                        Surface(
                            onClick = {
                                scope.launch {
                                    when (viewMode) {
                                        CalendarViewMode.Month -> monthPager.animateScrollToPage(PAGER_MID)
                                        CalendarViewMode.Simple -> simplePager.animateScrollToPage(PAGER_MID)
                                        CalendarViewMode.Week -> weekPager.animateScrollToPage(PAGER_MID)
                                        CalendarViewMode.Day -> dayPager.animateScrollToPage(PAGER_MID)
                                    }
                                }
                            },
                            shape = CircleShape,
                            color = Color(0xFFA1C6FF).copy(alpha = 0.9f),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            modifier = Modifier.expressivePressScale(todayInteraction)
                        ) {
                            Box(modifier = Modifier.size(42.dp * scaleFactor), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Today,
                                    contentDescription = "Today",
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp * scaleFactor)
                                )
                            }
                        }
                    }

                    val filterInteraction = remember { MutableInteractionSource() }
                    Surface(
                        onClick = { showFilters = !showFilters },
                        shape = CircleShape,
                        color = if (activeFilters.isNotEmpty()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        modifier = Modifier.expressivePressScale(filterInteraction)
                    ) {
                        Box(modifier = Modifier.size(42.dp * scaleFactor), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = "Filters",
                                tint = if (activeFilters.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp * scaleFactor)
                            )
                        }
                    }

                    val nextInteraction = remember { MutableInteractionSource() }
                    Surface(
                        onClick = {
                            scope.launch {
                                when (viewMode) {
                                    CalendarViewMode.Month -> monthPager.animateScrollToPage(monthPager.currentPage + 1)
                                    CalendarViewMode.Simple -> simplePager.animateScrollToPage(simplePager.currentPage + 1)
                                    CalendarViewMode.Week -> weekPager.animateScrollToPage(weekPager.currentPage + 1)
                                    CalendarViewMode.Day -> dayPager.animateScrollToPage(dayPager.currentPage + 1)
                                }
                            }
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        modifier = Modifier.expressivePressScale(nextInteraction)
                    ) {
                        Box(modifier = Modifier.size(42.dp * scaleFactor), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Next",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp * scaleFactor)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp * scaleFactor))

            // ═══ RE-IMAGINED FLOATING PILL VIEW MODE SELECTOR CHIPS ═══
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp * scaleFactor)
                    .height(42.dp * scaleFactor)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(4.dp * scaleFactor),
                    horizontalArrangement = Arrangement.spacedBy(4.dp * scaleFactor),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CalendarViewMode.entries.forEach { mode ->
                        val active = viewMode == mode
                        val bgAnim by animateColorAsState(
                            targetValue = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                            label = "viewModeBg_${mode.name}"
                        )
                        val textTint by animateColorAsState(
                            targetValue = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = tween(200),
                            label = "viewModeText_${mode.name}"
                        )
                        val interaction = remember { MutableInteractionSource() }

                        Surface(
                            onClick = {
                                viewMode = mode
                                if (mode != CalendarViewMode.Month) {
                                    selectedDay = todayCal.get(Calendar.DAY_OF_MONTH)
                                    onDateSelected(todayStr)
                                }
                            },
                            shape = CircleShape,
                            color = bgAnim,
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .expressivePressScale(interaction)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = mode.icon,
                                    contentDescription = mode.label,
                                    tint = textTint,
                                    modifier = Modifier.size(15.dp * scaleFactor)
                                )
                                Spacer(modifier = Modifier.width(4.dp * scaleFactor))
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = (12 * scaleFactor).sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = textTint
                                )
                            }
                        }
                    }
                }
            }

            // ═══ RE-IMAGINED FILTERS BAR WITH RESET PILL ═══
            AnimatedVisibility(
                visible = showFilters,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp * scaleFactor, vertical = 8.dp * scaleFactor),
                    horizontalArrangement = Arrangement.spacedBy(8.dp * scaleFactor),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterPillChip(
                        label = "All",
                        selected = activeFilters.isEmpty(),
                        onClick = { activeFilters = emptySet() },
                        scaleFactor = scaleFactor
                    )

                    listOf(
                        "high" to "High" to PriorityHigh,
                        "medium" to "Med" to PriorityMedium,
                        "low" to "Low" to PriorityLow
                    ).forEach { (pair, color) ->
                        val (key, label) = pair
                        FilterPillChip(
                            label = label,
                            selected = key in activeFilters,
                            colorDot = color,
                            onClick = { activeFilters = if (key in activeFilters) activeFilters - key else activeFilters + key },
                            scaleFactor = scaleFactor
                        )
                    }

                    FilterPillChip(
                        label = "Timed",
                        selected = "timed" in activeFilters,
                        icon = Icons.Default.Schedule,
                        onClick = { activeFilters = if ("timed" in activeFilters) activeFilters - "timed" else activeFilters + "timed" },
                        scaleFactor = scaleFactor
                    )

                    FilterPillChip(
                        label = "Google",
                        selected = "google" in activeFilters,
                        icon = Icons.Default.Event,
                        iconTint = GoogleBlue,
                        onClick = { activeFilters = if ("google" in activeFilters) activeFilters - "google" else activeFilters + "google" },
                        scaleFactor = scaleFactor
                    )

                    activeTags.forEach { tag ->
                        val tc = PredefinedTags.colorForTag(tag)
                        FilterPillChip(
                            label = tag,
                            selected = tag in activeFilters,
                            colorDot = tc,
                            onClick = { activeFilters = if (tag in activeFilters) activeFilters - tag else activeFilters + tag },
                            scaleFactor = scaleFactor
                        )
                    }

                    // Reset Filters Quick Clear Pill Chip
                    if (activeFilters.isNotEmpty()) {
                        FilterPillChip(
                            label = "Clear ✕",
                            selected = false,
                            icon = Icons.Default.Close,
                            iconTint = MaterialTheme.colorScheme.error,
                            onClick = { activeFilters = emptySet() },
                            scaleFactor = scaleFactor
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp * scaleFactor))

            // ═══ MAIN CALENDAR CONTENT AREA ═══
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (viewMode) {
                    // ── MONTH ──
                    CalendarViewMode.Month -> {
                        HorizontalPager(state = monthPager, beyondViewportPageCount = 1, modifier = Modifier.fillMaxSize()) { page ->
                            val pageYear = remember(page) { monthFromPage(page).get(Calendar.YEAR) }
                            val pageMonth = remember(page) { monthFromPage(page).get(Calendar.MONTH) }
                            val pageKey = remember(page) { "$pageYear-$pageMonth" }
                            val pageCal = remember(page) { monthFromPage(page) }
                            val grid = remember(page) {
                                val cal = pageCal.clone() as Calendar; cal.set(Calendar.DAY_OF_MONTH, 1)
                                val firstDow = cal.get(Calendar.DAY_OF_WEEK) - 1; val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                                val prevCal = (pageCal.clone() as Calendar).apply { add(Calendar.MONTH, -1) }; val prevMax = prevCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                                val prevDays = (0 until firstDow).map { GridDay(prevMax - firstDow + 1 + it, false) }
                                val curDays = (1..maxDays).map { GridDay(it, true) }
                                val total = prevDays.size + curDays.size; val needed = if (total <= 35) 35 - total else 42 - total
                                (prevDays + curDays + (1..needed).map { GridDay(it, false) }).chunked(7)
                            }
                            val pageData by produceState<com.theblankstate.preamble.viewmodel.TaskViewModel.MonthData?>(initialValue = getCachedMonthData(pageKey), key1 = page, key2 = refreshTick) {
                                value = loadMonthData(pageYear, pageMonth)
                            }
                            val isSettled by remember(page) { derivedStateOf { monthPager.settledPage == page } }
                            ReimaginedMonthGrid(
                                grid = grid,
                                cal = pageCal,
                                selectedDay = if (isSettled) selectedDay else -1,
                                monthTasks = pageData?.tasksByDay ?: emptyMap(),
                                todayStr = todayStr,
                                sdfDate = sdfDate,
                                primary = primary,
                                onPrimary = onPrimary,
                                onSurface = onSurface,
                                onSurfaceVar = onSurfaceVar,
                                scaleFactor = scaleFactor,
                                onDayClick = { dateStr ->
                                    handleDateClick(dateStr)
                                    viewMode = CalendarViewMode.Day
                                    scope.launch { dayPager.scrollToPage(PAGER_MID) }
                                }
                            )
                        }
                    }

                    // ── SIMPLE ──
                    CalendarViewMode.Simple -> {
                        HorizontalPager(state = simplePager, beyondViewportPageCount = 1, modifier = Modifier.fillMaxSize()) { page ->
                            val pageYear = remember(page) { monthFromPage(page).get(Calendar.YEAR) }
                            val pageMonth = remember(page) { monthFromPage(page).get(Calendar.MONTH) }
                            val pageKey = remember(page) { "$pageYear-$pageMonth" }
                            val pageCal = remember(page) { monthFromPage(page) }
                            val pageRows = remember(page) {
                                val cal = pageCal.clone() as Calendar; cal.set(Calendar.DAY_OF_MONTH, 1)
                                val firstDow = cal.get(Calendar.DAY_OF_WEEK) - 1; val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                                (MutableList<Int?>(firstDow) { null } + (1..maxDays).toList()).chunked(7)
                            }
                            val pageData by produceState<com.theblankstate.preamble.viewmodel.TaskViewModel.MonthData?>(initialValue = getCachedMonthData(pageKey), key1 = page, key2 = refreshTick) {
                                value = loadMonthData(pageYear, pageMonth)
                            }
                            val isSettled by remember(page) { derivedStateOf { simplePager.settledPage == page } }
                            CollapsibleSimplePage(
                                rows = pageRows,
                                cal = pageCal,
                                selectedDay = if (isSettled) selectedDay else -1,
                                heatMap = pageData?.heatMap ?: emptyMap(),
                                todayStr = todayStr,
                                sdfDate = sdfDate,
                                primary = primary,
                                onPrimary = onPrimary,
                                onSurface = onSurface,
                                onSurfaceVar = onSurfaceVar,
                                tasks = if (isSettled) filteredTasks else emptyList(),
                                scaleFactor = scaleFactor,
                                onDateClick = { handleDateClick(it) },
                                onToggle = onToggleTask,
                                onDelete = onDeleteTask,
                                onDetail = onTaskDetail,
                                onAddTask = onAddTask
                            )
                        }
                    }

                    // ── WEEK ──
                    CalendarViewMode.Week -> {
                        HorizontalPager(state = weekPager, beyondViewportPageCount = 1, modifier = Modifier.fillMaxSize()) { page ->
                            val pageWeek = remember(page) { weekFromPage(page) }
                            val pageYear = remember(page) { pageWeek.get(Calendar.YEAR) }
                            val pageMonth = remember(page) { pageWeek.get(Calendar.MONTH) }
                            val pageKey = remember(page) { "$pageYear-$pageMonth" }
                            val pageData by produceState<com.theblankstate.preamble.viewmodel.TaskViewModel.MonthData?>(initialValue = getCachedMonthData(pageKey), key1 = page, key2 = refreshTick) {
                                value = loadMonthData(pageYear, pageMonth)
                            }
                            val isSettled by remember(page) { derivedStateOf { weekPager.settledPage == page } }
                            ReimaginedWeekPage(
                                weekStart = pageWeek,
                                currentMonth = currentMonth,
                                selectedDay = if (isSettled) selectedDay else -1,
                                heatMap = pageData?.heatMap ?: emptyMap(),
                                todayStr = todayStr,
                                sdfDate = sdfDate,
                                primary = primary,
                                onPrimary = onPrimary,
                                onSurface = onSurface,
                                onSurfaceVar = onSurfaceVar,
                                tasks = if (isSettled) filteredTasks else emptyList(),
                                scaleFactor = scaleFactor,
                                onDateClick = { handleDateClick(it) },
                                onToggle = onToggleTask,
                                onDelete = onDeleteTask,
                                onDetail = onTaskDetail,
                                onAddTask = onAddTask
                            )
                        }
                    }

                    // ── DAY ──
                    CalendarViewMode.Day -> {
                        HorizontalPager(state = dayPager, beyondViewportPageCount = 1, modifier = Modifier.fillMaxSize()) { page ->
                            val pageDayCal = remember(page) { dayFromPage(page) }
                            val isSettled by remember(page) { derivedStateOf { dayPager.settledPage == page } }
                            CollapsibleDayPage(
                                dayCal = pageDayCal,
                                tasks = if (isSettled) filteredTasks else emptyList(),
                                onToggle = onToggleTask,
                                onDelete = onDeleteTask,
                                onDetail = onTaskDetail,
                                onSurfaceVar = onSurfaceVar,
                                todayStr = todayStr,
                                scaleFactor = scaleFactor,
                                onAddTask = onAddTask
                            )
                        }
                    }
                }
            }
        }

        // ═══ RE-IMAGINED FAB — Add Task ═══
        val fabInteraction = remember { MutableInteractionSource() }
        FloatingActionButton(
            onClick = onAddTask,
            containerColor = primary,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp * scaleFactor)
                .navigationBarsPadding()
                .expressivePressScale(fabInteraction, pressedScale = 0.88f)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add Task",
                tint = onPrimary,
                modifier = Modifier.size(24.dp * scaleFactor)
            )
        }
    }
}

/**
 * Filter Pill Chip with Visual Anchor Color Dot or Icon.
 */
@Composable
private fun FilterPillChip(
    label: String,
    selected: Boolean,
    colorDot: Color? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
    scaleFactor: Float = 1f
) {
    val interaction = remember { MutableInteractionSource() }
    val bgAnim by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "filterBg_$label"
    )
    val textTint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(200),
        label = "filterText_$label"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = bgAnim,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.expressivePressScale(interaction)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp * scaleFactor, vertical = 6.dp * scaleFactor),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp * scaleFactor)
        ) {
            if (colorDot != null) {
                Box(
                    modifier = Modifier
                        .size(8.dp * scaleFactor)
                        .clip(CircleShape)
                        .background(colorDot)
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) textTint else iconTint,
                    modifier = Modifier.size(13.dp * scaleFactor)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = (12 * scaleFactor).sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = textTint
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// RE-IMAGINED MONTH GRID
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ReimaginedMonthGrid(
    grid: List<List<GridDay>>, cal: Calendar, selectedDay: Int,
    monthTasks: Map<Int, List<Task>>, todayStr: String, sdfDate: SimpleDateFormat,
    primary: Color, onPrimary: Color, onSurface: Color, onSurfaceVar: Color,
    scaleFactor: Float = 1f,
    onDayClick: (String) -> Unit
) {
    val dayLabels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val surfaceHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val dimColor = onSurfaceVar.copy(alpha = 0.25f)

    val dateStrings = remember(cal) {
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val c = cal.clone() as Calendar
        (1..maxDays).associate { day ->
            c.set(Calendar.DAY_OF_MONTH, day)
            day to sdfDate.format(c.time)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp * scaleFactor)) {
            dayLabels.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = (11 * scaleFactor).sp, fontWeight = FontWeight.Bold),
                    color = if (label == "Sun" || label == "Sat") primary.copy(alpha = 0.7f) else onSurfaceVar
                )
            }
        }
        grid.forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 1.dp)) {
                row.forEach { gridDay ->
                    val isInMonth = gridDay.inMonth; val day = gridDay.day
                    val dateStr = if (isInMonth) dateStrings[day] ?: "" else ""
                    val isToday = isInMonth && dateStr == todayStr; val isSelected = isInMonth && day == selectedDay
                    val tasks = if (isInMonth) monthTasks[day] ?: emptyList() else emptyList()
                    val dayInteraction = remember { MutableInteractionSource() }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(1.5.dp * scaleFactor)
                            .clip(RoundedCornerShape(12.dp * scaleFactor))
                            .background(if (isInMonth) surfaceHigh.copy(alpha = 0.45f) else surfaceHigh.copy(alpha = 0.12f))
                            .then(if (isInMonth) Modifier.clickable(interactionSource = dayInteraction, indication = null) { onDayClick(dateStr) }.expressivePressScale(dayInteraction) else Modifier)
                            .padding(3.dp * scaleFactor)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .then(if (isToday) Modifier.size(22.dp * scaleFactor).clip(CircleShape).background(primary) else Modifier)
                                .padding(1.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = (11 * scaleFactor).sp,
                                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = when { isToday -> onPrimary; !isInMonth -> dimColor; isSelected -> primary; else -> onSurface }
                            )
                        }
                        if (isInMonth) {
                            tasks.take(3).forEach { task ->
                                val tc = when {
                                    task.priority == 3 -> PriorityHigh
                                    task.priority == 2 -> PriorityMedium
                                    task.priority == 1 -> PriorityLow
                                    task.isCalendarEvent || task.isGoogleTask -> TaskTeal
                                    else -> primary
                                }
                                val icon = when {
                                    task.deadlineTime != null -> Icons.Default.Schedule
                                    task.isCalendarEvent || task.isGoogleTask -> Icons.Default.Event
                                    else -> null
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 0.5.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(tc.copy(alpha = 0.15f))
                                        .padding(horizontal = 3.dp * scaleFactor, vertical = 1.5.dp)
                                ) {
                                    if (icon != null) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = tc,
                                            modifier = Modifier.size(9.dp * scaleFactor)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp * scaleFactor))
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(4.dp * scaleFactor)
                                                .clip(CircleShape)
                                                .background(tc)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp * scaleFactor))
                                    }
                                    Text(
                                        text = task.title.removePrefix("📅 ").trim(),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = (9.5 * scaleFactor).sp, lineHeight = 12.sp),
                                        color = tc,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (tasks.size > 3) {
                                Text(
                                    "•••",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = onSurfaceVar.copy(alpha = 0.5f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// COLLAPSIBLE SIMPLE PAGE — Height-based collapse with week strip
// ═══════════════════════════════════════════════════════════════

@Composable
private fun CollapsibleSimplePage(
    rows: List<List<Int?>>, cal: Calendar, selectedDay: Int,
    heatMap: Map<Int, Pair<Int, Int>>, todayStr: String, sdfDate: SimpleDateFormat,
    primary: Color, onPrimary: Color, onSurface: Color, onSurfaceVar: Color,
    tasks: List<Task>, scaleFactor: Float = 1f,
    onDateClick: (String) -> Unit,
    onToggle: (Task) -> Unit, onDelete: (Task) -> Unit, onDetail: (Task) -> Unit,
    onAddTask: () -> Unit
) {
    val density = LocalDensity.current
    val calendarExpandedPx = with(density) { 340.dp.toPx() }
    val calendarCollapsedPx = with(density) { 56.dp.toPx() }
    var calendarHeightPx by remember { mutableFloatStateOf(calendarExpandedPx) }

    val dateStrings = remember(cal) {
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val c = cal.clone() as Calendar
        (1..maxDays).associate { day ->
            c.set(Calendar.DAY_OF_MONTH, day)
            day to sdfDate.format(c.time)
        }
    }

    val nestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta < 0 && calendarHeightPx > calendarCollapsedPx) {
                    val old = calendarHeightPx
                    calendarHeightPx = (calendarHeightPx + delta).coerceIn(calendarCollapsedPx, calendarExpandedPx)
                    return Offset(0f, calendarHeightPx - old)
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta > 0 && calendarHeightPx < calendarExpandedPx) {
                    val old = calendarHeightPx
                    calendarHeightPx = (calendarHeightPx + delta).coerceIn(calendarCollapsedPx, calendarExpandedPx)
                    return Offset(0f, calendarHeightPx - old)
                }
                return Offset.Zero
            }
        }
    }

    val collapseProgress = (1f - (calendarHeightPx - calendarCollapsedPx) / (calendarExpandedPx - calendarCollapsedPx)).coerceIn(0f, 1f)

    val selectedWeekIdx = remember(rows, selectedDay) {
        val target = if (selectedDay > 0) selectedDay else Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        rows.indexOfFirst { week -> week.any { it == target } }.coerceAtLeast(0)
    }

    val weekStripPager = rememberPagerState(initialPage = selectedWeekIdx) { rows.size }

    LaunchedEffect(selectedWeekIdx, collapseProgress) {
        if (collapseProgress > 0.5f) weekStripPager.scrollToPage(selectedWeekIdx)
    }

    Column(Modifier.fillMaxSize().nestedScroll(nestedScroll)) {
        val calendarHeightDp = with(density) { (calendarHeightPx / density.density).dp }
        Box(Modifier.fillMaxWidth().height(calendarHeightDp).clip(RoundedCornerShape(0.dp))) {
            if (collapseProgress < 1f) {
                Column(Modifier.fillMaxWidth().graphicsLayer { alpha = (1f - collapseProgress * 1.5f).coerceIn(0f, 1f) }) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                            Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = onSurfaceVar)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        rows.forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                for (col in 0 until 7) {
                                    val day = row.getOrNull(col)
                                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                                        if (day != null) {
                                            val dateStr = dateStrings[day] ?: ""
                                            val isToday = dateStr == todayStr; val isSelected = day == selectedDay
                                            val taskData = heatMap[day]
                                            val bg = when { isSelected -> primary; taskData != null -> { val r = taskData.first.toFloat() / taskData.second; primary.copy(alpha = 0.12f + r * 0.55f) }; else -> Color.Transparent }
                                            val cellInteraction = remember { MutableInteractionSource() }
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(bg)
                                                    .clickable(interactionSource = cellInteraction, indication = null) { onDateClick(dateStr) }
                                                    .expressivePressScale(cellInteraction),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(day.toString(), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = when { isSelected -> onPrimary; isToday -> primary; taskData != null -> onSurface; else -> onSurfaceVar })
                                                    if (isToday && !isSelected) Box(Modifier.size(4.dp).clip(CircleShape).background(primary))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // Re-imagined Monthly Productivity Summary Card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp * scaleFactor, vertical = 6.dp * scaleFactor),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = primary.copy(alpha = 0.12f),
                            tonalElevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp * scaleFactor, vertical = 4.dp * scaleFactor),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = primary, modifier = Modifier.size(12.dp * scaleFactor))
                                Spacer(modifier = Modifier.width(4.dp * scaleFactor))
                                Text("Monthly Streak", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = (11 * scaleFactor).sp), color = primary)
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Less", style = MaterialTheme.typography.labelSmall.copy(fontSize = (11 * scaleFactor).sp), color = onSurfaceVar)
                            Spacer(Modifier.width(6.dp))
                            listOf(0.12f, 0.3f, 0.5f, 0.7f).forEach { a -> Box(Modifier.size(10.dp * scaleFactor).padding(1.dp).clip(CircleShape).background(primary.copy(alpha = a))) }
                            Spacer(Modifier.width(6.dp))
                            Text("More", style = MaterialTheme.typography.labelSmall.copy(fontSize = (11 * scaleFactor).sp), color = onSurfaceVar)
                        }
                    }
                }
            }

            if (collapseProgress > 0f) {
                HorizontalPager(
                    state = weekStripPager,
                    userScrollEnabled = collapseProgress > 0.7f,
                    modifier = Modifier.fillMaxWidth()
                        .graphicsLayer { alpha = (collapseProgress * 2f).coerceIn(0f, 1f) }
                        .align(Alignment.TopCenter)
                        .padding(vertical = 6.dp)
                ) { weekIdx ->
                    val weekDays = rows.getOrElse(weekIdx) { emptyList() }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                        for (col in 0 until 7) {
                            val day = weekDays.getOrNull(col)
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                if (day != null) {
                                    val dateStr = dateStrings[day] ?: ""
                                    val isToday = dateStr == todayStr
                                    val isSelected = day == selectedDay
                                    val hasTasks = heatMap.containsKey(day)
                                    val cellInteraction = remember { MutableInteractionSource() }
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(when { isSelected -> primary; isToday -> primary.copy(alpha = 0.12f); else -> Color.Transparent })
                                            .clickable(interactionSource = cellInteraction, indication = null) { onDateClick(dateStr) }
                                            .expressivePressScale(cellInteraction),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(day.toString(), style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 13.sp, fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal),
                                                color = when { isSelected -> onPrimary; isToday -> primary; else -> onSurfaceVar })
                                            if (hasTasks && !isSelected) Box(Modifier.size(4.dp).clip(CircleShape).background(primary.copy(alpha = 0.6f)))
                                        }
                                    }
                                } else Spacer(Modifier.size(40.dp))
                            }
                        }
                    }
                }
            }
        }

        if (selectedDay > 0) {
            val lbl = remember(cal, selectedDay) {
                SimpleDateFormat("EEE, dd MMM", Locale.getDefault()).format(
                    (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, selectedDay) }.time)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(lbl, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = primary)
                if (tasks.isNotEmpty()) Text("${tasks.count { it.isCompleted }}/${tasks.size}", style = MaterialTheme.typography.labelSmall, color = onSurfaceVar)
            }
            if (tasks.isEmpty()) ReimaginedEmptyState(message = "No tasks planned for this day", icon = Icons.Default.Event, onAddTask = onAddTask, scaleFactor = scaleFactor)
            else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 8.dp)) {
                items(tasks, key = { it.id }) { task -> val isPast = task.createdDate < todayStr; TaskItem(task = task, onToggle = { if (!isPast) onToggle(task) }, onDelete = { onDelete(task) }, onDetail = { onDetail(task) }, isEditable = !isPast) }
            }
        } else ReimaginedEmptyState(message = "Select a date to view tasks", icon = Icons.Default.CalendarMonth, onAddTask = onAddTask, scaleFactor = scaleFactor)
    }
}

// ═══════════════════════════════════════════════════════════════
// RE-IMAGINED WEEK PAGE
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ReimaginedWeekPage(
    weekStart: Calendar, currentMonth: Calendar, selectedDay: Int,
    heatMap: Map<Int, Pair<Int, Int>>, todayStr: String, sdfDate: SimpleDateFormat,
    primary: Color, onPrimary: Color, onSurface: Color, onSurfaceVar: Color,
    tasks: List<Task>, scaleFactor: Float = 1f,
    onDateClick: (String) -> Unit,
    onToggle: (Task) -> Unit, onDelete: (Task) -> Unit, onDetail: (Task) -> Unit,
    onAddTask: () -> Unit
) {
    val dayLabels = listOf("S", "M", "T", "W", "T", "F", "S")
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp * scaleFactor, vertical = 4.dp * scaleFactor),
            horizontalArrangement = Arrangement.spacedBy(4.dp * scaleFactor)
        ) {
            for (i in 0..6) {
                val dayCal = (weekStart.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }
                val dom = dayCal.get(Calendar.DAY_OF_MONTH); val dateStr = sdfDate.format(dayCal.time)
                val isToday = dateStr == todayStr; val inMonth = dayCal.get(Calendar.MONTH) == currentMonth.get(Calendar.MONTH)
                val isSelected = inMonth && dom == selectedDay; val taskData = if (inMonth) heatMap[dom] else null
                val heatAlpha = if (taskData != null) { val r = taskData.first.toFloat() / taskData.second; 0.12f + r * 0.55f } else 0f
                val dayInteraction = remember { MutableInteractionSource() }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp * scaleFactor))
                        .background(when {
                            isSelected -> primary; isToday -> primary.copy(alpha = 0.14f); taskData != null -> primary.copy(alpha = heatAlpha * 0.5f)
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f)
                        })
                        .clickable(interactionSource = dayInteraction, indication = null) { onDateClick(dateStr) }
                        .expressivePressScale(dayInteraction)
                        .padding(vertical = 8.dp * scaleFactor),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(dayLabels[i], style = MaterialTheme.typography.labelSmall.copy(fontSize = (11 * scaleFactor).sp), color = if (isSelected) onPrimary else onSurfaceVar)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        dom.toString(),
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = (14 * scaleFactor).sp, fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium),
                        color = when { isSelected -> onPrimary; isToday -> primary; !inMonth -> onSurfaceVar.copy(alpha = 0.3f); else -> onSurface }
                    )
                    if (taskData != null) {
                        Spacer(Modifier.height(2.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isSelected) onPrimary.copy(alpha = 0.25f) else primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${taskData.second}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = (9 * scaleFactor).sp, fontWeight = FontWeight.Bold),
                                color = if (isSelected) onPrimary else primary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.5.dp)
                            )
                        }
                    }
                }
            }
        }
        TaskListSection(selectedDay, currentMonth, tasks, onToggle, onDelete, onDetail, todayStr, onAddTask = onAddTask, scaleFactor = scaleFactor)
    }
}

// ═══════════════════════════════════════════════════════════════
// RE-IMAGINED DAY PAGE — Live Time Indicator Line & Smart Empty Hour Collapse
// ═══════════════════════════════════════════════════════════════

@Composable
private fun CollapsibleDayPage(
    dayCal: Calendar, tasks: List<Task>,
    onToggle: (Task) -> Unit, onDelete: (Task) -> Unit, onDetail: (Task) -> Unit,
    onSurfaceVar: Color, todayStr: String,
    scaleFactor: Float = 1f,
    onAddTask: () -> Unit
) {
    val allDayTasks = remember(tasks) { tasks.filter { it.deadlineTime == null } }
    val timedTasks = remember(tasks) { tasks.filter { it.deadlineTime != null }.sortedBy { it.deadlineTime } }
    val hourTasks = remember(timedTasks) {
        val map = mutableMapOf<Int, MutableList<Task>>()
        timedTasks.forEach { t -> val h = t.deadlineTime?.split(":")?.firstOrNull()?.toIntOrNull() ?: 0; map.getOrPut(h) { mutableListOf() }.add(t) }; map
    }

    val primary = MaterialTheme.colorScheme.primary
    var allDayExpanded by remember { mutableStateOf(false) }
    val maxPreview = 2

    val density = LocalDensity.current
    val previewMaxPx = with(density) { (52.dp * allDayTasks.size.coerceAtMost(maxPreview).coerceAtLeast(1) + if (allDayTasks.size > maxPreview) 28.dp else 0.dp).toPx() }
    var previewOffsetPx by remember { mutableFloatStateOf(0f) }
    var scrollCollapsed by remember { mutableStateOf(false) }

    // Calculate current live time for live timeline pulse indicator
    val nowCal = remember { Calendar.getInstance() }
    val currentHour = nowCal.get(Calendar.HOUR_OF_DAY)
    val currentMinute = nowCal.get(Calendar.MINUTE)
    val sdfDate = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val isPageToday = sdfDate.format(dayCal.time) == todayStr

    val infiniteTransition = rememberInfiniteTransition(label = "pulseLiveTime")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    val nestedScroll = remember(allDayTasks.size) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (allDayTasks.isEmpty() || allDayExpanded) return Offset.Zero
                val delta = available.y
                if (delta < 0 && !scrollCollapsed) {
                    val old = previewOffsetPx
                    previewOffsetPx = (previewOffsetPx + delta).coerceIn(-previewMaxPx, 0f)
                    if (previewOffsetPx <= -previewMaxPx + 1f) scrollCollapsed = true
                    return Offset(0f, previewOffsetPx - old)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (allDayTasks.isEmpty() || allDayExpanded) return Offset.Zero
                val delta = available.y
                if (delta > 0 && (scrollCollapsed || previewOffsetPx < 0f)) {
                    val old = previewOffsetPx
                    previewOffsetPx = (previewOffsetPx + delta).coerceIn(-previewMaxPx, 0f)
                    if (previewOffsetPx >= -1f) { scrollCollapsed = false; previewOffsetPx = 0f }
                    return Offset(0f, previewOffsetPx - old)
                }
                return Offset.Zero
            }
        }
    }

    Column(Modifier.fillMaxSize().nestedScroll(nestedScroll)) {
        if (allDayTasks.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth()
                    .clickable {
                        allDayExpanded = !allDayExpanded
                        if (allDayExpanded) { scrollCollapsed = false; previewOffsetPx = 0f }
                        else { scrollCollapsed = false; previewOffsetPx = 0f }
                    }
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                Arrangement.SpaceBetween, Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("All day", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = onSurfaceVar)
                    Surface(shape = RoundedCornerShape(50), color = primary.copy(alpha = 0.1f)) {
                        Text("${allDayTasks.size}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = primary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                }
                Icon(if (allDayExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    "Toggle", modifier = Modifier.size(20.dp), tint = onSurfaceVar)
            }

            if (!scrollCollapsed || allDayExpanded) {
                if (allDayExpanded) {
                    val expandedScrollState = rememberScrollState()
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(expandedScrollState)
                    ) {
                        allDayTasks.forEach { task ->
                            TaskItem(task = task, onToggle = { if (task.createdDate >= todayStr) onToggle(task) }, onDelete = { onDelete(task) }, onDetail = { onDetail(task) }, isEditable = task.createdDate >= todayStr)
                        }
                    }
                } else {
                    val visibleHeightDp = if (previewOffsetPx < 0f) {
                        with(density) { ((previewMaxPx + previewOffsetPx).coerceAtLeast(0f) / density.density).dp }
                    } else null

                    val columnMod = if (visibleHeightDp != null) {
                        Modifier.fillMaxWidth().height(visibleHeightDp).clip(RoundedCornerShape(0.dp))
                    } else Modifier.fillMaxWidth()

                    Box(columnMod) {
                        Column(if (previewOffsetPx < 0f) Modifier.graphicsLayer { translationY = previewOffsetPx } else Modifier) {
                            allDayTasks.take(maxPreview).forEach { task ->
                                TaskItem(task = task, onToggle = { if (task.createdDate >= todayStr) onToggle(task) }, onDelete = { onDelete(task) }, onDetail = { onDetail(task) }, isEditable = task.createdDate >= todayStr)
                            }
                            if (allDayTasks.size > maxPreview) {
                                Text("+${allDayTasks.size - maxPreview} more",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = primary,
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable { allDayExpanded = true; scrollCollapsed = false; previewOffsetPx = 0f }
                                        .padding(horizontal = 16.dp, vertical = 6.dp))
                            }
                        }
                    }
                }
            }
        }

        // Timeline — compact slots with Live Time Indicator Line
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(24) { hour ->
                val label = when { hour == 0 -> "12 AM"; hour < 12 -> "$hour AM"; hour == 12 -> "12 PM"; else -> "${hour - 12} PM" }
                val tasksHere = hourTasks[hour]
                val isCurrentHour = isPageToday && hour == currentHour

                Column {
                    // Live Current Time Pulse Line Indicator if this is current hour
                    if (isCurrentHour) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp * scaleFactor)
                                    .clip(CircleShape)
                                    .background(PriorityHigh.copy(alpha = pulseAlpha))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            HorizontalDivider(
                                color = PriorityHigh.copy(alpha = pulseAlpha),
                                thickness = 1.5.dp
                            )
                        }
                    }

                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.Top) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = if (isCurrentHour) FontWeight.Bold else FontWeight.Normal),
                            color = if (isCurrentHour) PriorityHigh else onSurfaceVar.copy(alpha = 0.5f),
                            modifier = Modifier.width(44.dp).padding(top = 2.dp),
                            textAlign = TextAlign.End
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(
                            Modifier
                                .weight(1f)
                                .drawBehind { drawLine(if (isCurrentHour) PriorityHigh.copy(alpha = 0.3f) else onSurfaceVar.copy(alpha = 0.08f), Offset(0f, 0f), Offset(size.width, 0f), 1f) }
                                .padding(vertical = 1.dp)
                        ) {
                            if (tasksHere != null) {
                                tasksHere.forEach { task ->
                                    TaskItem(task = task, onToggle = { if (task.createdDate >= todayStr) onToggle(task) }, onDelete = { onDelete(task) }, onDetail = { onDetail(task) }, isEditable = task.createdDate >= todayStr)
                                }
                            } else {
                                Spacer(Modifier.height(36.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// SHARED TASK LIST SECTION & TACTILE HERO EMPTY STATE
// ═══════════════════════════════════════════════════════════════

@Composable
private fun TaskListSection(
    selectedDay: Int, currentMonth: Calendar, tasks: List<Task>,
    onToggle: (Task) -> Unit, onDelete: (Task) -> Unit, onDetail: (Task) -> Unit,
    todayStr: String,
    onAddTask: () -> Unit = {},
    scaleFactor: Float = 1f
) {
    val primary = MaterialTheme.colorScheme.primary; val onSurfaceVar = MaterialTheme.colorScheme.onSurfaceVariant
    if (selectedDay > 0) {
        val cal = (currentMonth.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, selectedDay) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(SimpleDateFormat("EEE, dd MMM", Locale.getDefault()).format(cal.time), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = primary)
            if (tasks.isNotEmpty()) Text("${tasks.count { it.isCompleted }}/${tasks.size}", style = MaterialTheme.typography.labelSmall, color = onSurfaceVar)
        }
        if (tasks.isEmpty()) ReimaginedEmptyState(message = "No tasks planned for this day", icon = Icons.Default.Event, onAddTask = onAddTask, scaleFactor = scaleFactor)
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 8.dp)) {
            items(tasks, key = { it.id }) { task -> val isPast = task.createdDate < todayStr; TaskItem(task = task, onToggle = { if (!isPast) onToggle(task) }, onDelete = { onDelete(task) }, onDetail = { onDetail(task) }, isEditable = !isPast) }
        }
    } else ReimaginedEmptyState(message = "Select a date to view tasks", icon = Icons.Default.CalendarMonth, onAddTask = onAddTask, scaleFactor = scaleFactor)
}

/**
 * Tactile Hero Card Empty State with Soft Pastel Accent & Quick Action Button.
 */
@Composable
private fun ReimaginedEmptyState(
    message: String,
    icon: ImageVector,
    onAddTask: () -> Unit,
    scaleFactor: Float = 1f
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp * scaleFactor),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp * scaleFactor),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp * scaleFactor),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Visual Anchor Icon Pill with Soft Pastel Background
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFA1C6FF).copy(alpha = 0.7f),
                    modifier = Modifier.size(54.dp * scaleFactor)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(26.dp * scaleFactor)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp * scaleFactor))

                Text(
                    text = message,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = (15 * scaleFactor).sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp * scaleFactor))

                Text(
                    text = "Tap below to add a new task or event to your schedule.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = (12 * scaleFactor).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp * scaleFactor))

                val addBtnInteraction = remember { MutableInteractionSource() }
                Surface(
                    onClick = onAddTask,
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .height(40.dp * scaleFactor)
                        .expressivePressScale(addBtnInteraction)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp * scaleFactor),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp * scaleFactor)
                        )
                        Spacer(modifier = Modifier.width(6.dp * scaleFactor))
                        Text(
                            text = "Add Task",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = (13 * scaleFactor).sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}
