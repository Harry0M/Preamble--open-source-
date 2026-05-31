package com.theblankstate.preamble.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
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
import kotlin.math.roundToInt

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

@androidx.compose.runtime.Immutable
data class GridDay(val day: Int, val inMonth: Boolean)

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
            android.util.Log.d("CAL_UI", "MonthPager SETTLED on page=$page → ${m.get(Calendar.YEAR)}-${m.get(Calendar.MONTH)}")
            currentMonth = m; selectedDay = -1; onDateSelected(null)
            onMonthChanged(m.get(Calendar.YEAR), m.get(Calendar.MONTH))
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { simplePager.settledPage }.collect { page ->
            val m = monthFromPage(page)
            android.util.Log.d("CAL_UI", "SimplePager SETTLED on page=$page → ${m.get(Calendar.YEAR)}-${m.get(Calendar.MONTH)}")
            currentMonth = m; selectedDay = -1; onDateSelected(null)
            onMonthChanged(m.get(Calendar.YEAR), m.get(Calendar.MONTH))
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { weekPager.settledPage }.collect { page ->
            val w = weekFromPage(page)
            android.util.Log.d("CAL_UI", "WeekPager SETTLED on page=$page → ${w.get(Calendar.YEAR)}-${w.get(Calendar.MONTH)}")
            weekStart = w; currentMonth = w.clone() as Calendar; selectedDay = -1; onDateSelected(null)
            onMonthChanged(w.get(Calendar.YEAR), w.get(Calendar.MONTH))
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { dayPager.settledPage }.collect { page ->
            val d = dayFromPage(page)
            android.util.Log.d("CAL_UI", "DayPager SETTLED on page=$page → ${sdfDate.format(d.time)}")
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

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        // ═══ HEADER ═══
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            IconButton(onClick = {
                scope.launch {
                    when (viewMode) {
                        CalendarViewMode.Month -> monthPager.animateScrollToPage(monthPager.currentPage - 1)
                        CalendarViewMode.Simple -> simplePager.animateScrollToPage(simplePager.currentPage - 1)
                        CalendarViewMode.Week -> weekPager.animateScrollToPage(weekPager.currentPage - 1)
                        CalendarViewMode.Day -> dayPager.animateScrollToPage(dayPager.currentPage - 1)
                    }
                }
            }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous") }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(50), color = primary.copy(alpha = 0.1f)) {
                    Text(sdfMonth.format(currentMonth.time), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = primary, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
                if (viewMode == CalendarViewMode.Day && selectedDay > 0) {
                    val c = (currentMonth.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, selectedDay) }
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)) {
                        Text(SimpleDateFormat("EEEE, d", Locale.getDefault()).format(c.time), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isCurrentMonth) {
                    IconButton(onClick = {
                        scope.launch {
                            when (viewMode) {
                                CalendarViewMode.Month -> monthPager.animateScrollToPage(PAGER_MID)
                                CalendarViewMode.Simple -> simplePager.animateScrollToPage(PAGER_MID)
                                CalendarViewMode.Week -> weekPager.animateScrollToPage(PAGER_MID)
                                CalendarViewMode.Day -> dayPager.animateScrollToPage(PAGER_MID)
                            }
                        }
                    }) { Icon(Icons.Default.Today, "Today", tint = primary, modifier = Modifier.size(22.dp)) }
                }
                IconButton(onClick = { showFilters = !showFilters }) {
                    Icon(Icons.Default.FilterList, "Filters", tint = if (activeFilters.isNotEmpty()) primary else onSurfaceVar, modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = {
                    scope.launch {
                        when (viewMode) {
                            CalendarViewMode.Month -> monthPager.animateScrollToPage(monthPager.currentPage + 1)
                            CalendarViewMode.Simple -> simplePager.animateScrollToPage(simplePager.currentPage + 1)
                            CalendarViewMode.Week -> weekPager.animateScrollToPage(weekPager.currentPage + 1)
                            CalendarViewMode.Day -> dayPager.animateScrollToPage(dayPager.currentPage + 1)
                        }
                    }
                }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next") }
            }
        }

        // ═══ VIEW MODE ═══
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            CalendarViewMode.entries.forEachIndexed { idx, mode ->
                SegmentedButton(selected = viewMode == mode, onClick = {
                    viewMode = mode
                    // Always select today when entering Simple/Week/Day so tasks show immediately
                    if (mode != CalendarViewMode.Month) {
                        selectedDay = todayCal.get(Calendar.DAY_OF_MONTH)
                        onDateSelected(todayStr)
                    }
                }, shape = SegmentedButtonDefaults.itemShape(idx, CalendarViewMode.entries.size), icon = {}) {
                    Text(mode.label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // ═══ FILTERS ═══
        AnimatedVisibility(showFilters, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(activeFilters.isEmpty(), { activeFilters = emptySet() }, label = { Text("All", style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = primary.copy(alpha = 0.15f)))
                listOf("high" to "High" to PriorityHigh, "medium" to "Med" to PriorityMedium, "low" to "Low" to PriorityLow).forEach { (pair, color) ->
                    val (key, label) = pair
                    FilterChip(key in activeFilters, { activeFilters = if (key in activeFilters) activeFilters - key else activeFilters + key },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }, leadingIcon = { Box(Modifier.size(8.dp).clip(CircleShape).background(color)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color.copy(alpha = 0.15f)))
                }
                FilterChip("timed" in activeFilters, { activeFilters = if ("timed" in activeFilters) activeFilters - "timed" else activeFilters + "timed" },
                    label = { Text("Timed", style = MaterialTheme.typography.labelSmall) }, leadingIcon = { Icon(Icons.Default.Schedule, null, Modifier.size(14.dp)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = primary.copy(alpha = 0.15f)))
                FilterChip("google" in activeFilters, { activeFilters = if ("google" in activeFilters) activeFilters - "google" else activeFilters + "google" },
                    label = { Text("Google", style = MaterialTheme.typography.labelSmall) }, leadingIcon = { Icon(Icons.Default.Event, null, Modifier.size(14.dp), tint = GoogleBlue) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GoogleBlue.copy(alpha = 0.1f)))
                activeTags.forEach { tag ->
                    val tc = PredefinedTags.colorForTag(tag)
                    FilterChip(tag in activeFilters, { activeFilters = if (tag in activeFilters) activeFilters - tag else activeFilters + tag },
                        label = { Text(tag, style = MaterialTheme.typography.labelSmall) }, leadingIcon = { Box(Modifier.size(8.dp).clip(CircleShape).background(tc)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = tc.copy(alpha = 0.12f)))
                }
            }
        }
        Spacer(Modifier.height(2.dp))

        // ═══ CONTENT ═══
        when (viewMode) {
            // ── MONTH: each page loads its OWN data via produceState ──
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
                    // produceState: instant from cache, then async load if miss
                    // Each page is INDEPENDENT — no shared state, no cascade
                    val pageData by produceState<com.theblankstate.preamble.viewmodel.TaskViewModel.MonthData?>(initialValue = getCachedMonthData(pageKey), key1 = page, key2 = refreshTick) {
                        value = loadMonthData(pageYear, pageMonth)
                    }
                    val isSettled by remember(page) { androidx.compose.runtime.derivedStateOf { monthPager.settledPage == page } }
                    GoogleMonthGrid(grid, pageCal, if (isSettled) selectedDay else -1, pageData?.tasksByDay ?: emptyMap(), todayStr, sdfDate, primary, onPrimary, onSurface, onSurfaceVar,
                        onDayClick = { dateStr -> handleDateClick(dateStr); viewMode = CalendarViewMode.Day; scope.launch { dayPager.scrollToPage(PAGER_MID) } })
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
                    val isSettled by remember(page) { androidx.compose.runtime.derivedStateOf { simplePager.settledPage == page } }
                    CollapsibleSimplePage(pageRows, pageCal, if (isSettled) selectedDay else -1, pageData?.heatMap ?: emptyMap(),
                        todayStr, sdfDate, primary, onPrimary, onSurface, onSurfaceVar, if (isSettled) filteredTasks else emptyList(),
                        onDateClick = { handleDateClick(it) }, onToggle = onToggleTask, onDelete = onDeleteTask, onDetail = onTaskDetail)
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
                    val isSettled by remember(page) { androidx.compose.runtime.derivedStateOf { weekPager.settledPage == page } }
                    WeekPage(pageWeek, currentMonth, if (isSettled) selectedDay else -1, pageData?.heatMap ?: emptyMap(),
                        todayStr, sdfDate, primary, onPrimary, onSurface, onSurfaceVar, if (isSettled) filteredTasks else emptyList(),
                        onDateClick = { handleDateClick(it) }, onToggle = onToggleTask, onDelete = onDeleteTask, onDetail = onTaskDetail)
                }
            }

            // ── DAY ──
            CalendarViewMode.Day -> {
                HorizontalPager(state = dayPager, beyondViewportPageCount = 1, modifier = Modifier.fillMaxSize()) { page ->
                    val pageDayCal = remember(page) { dayFromPage(page) }
                    val isSettled by remember(page) { androidx.compose.runtime.derivedStateOf { dayPager.settledPage == page } }
                    CollapsibleDayPage(pageDayCal, if (isSettled) filteredTasks else emptyList(),
                        onToggle = onToggleTask, onDelete = onDeleteTask, onDetail = onTaskDetail, onSurfaceVar = onSurfaceVar, todayStr = todayStr)
                }
            }
        }
    }

    // ═══ FAB — Add Task ═══
    FloatingActionButton(
        onClick = onAddTask,
        containerColor = primary,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(16.dp)
    ) {
        Icon(Icons.Default.Add, contentDescription = "Add Task", tint = onPrimary)
    }
    } // end Box
}

// ═══════════════════════════════════════════════════════════════
// MONTH GRID
// ═══════════════════════════════════════════════════════════════

@Composable
private fun GoogleMonthGrid(
    grid: List<List<GridDay>>, cal: Calendar, selectedDay: Int,
    monthTasks: Map<Int, List<Task>>, todayStr: String, sdfDate: SimpleDateFormat,
    primary: Color, onPrimary: Color, onSurface: Color, onSurfaceVar: Color,
    onDayClick: (String) -> Unit
) {
    val dayLabels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val surfaceHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val dimColor = onSurfaceVar.copy(alpha = 0.25f)

    // Pre-compute ALL date strings once (Fix #6: eliminates 42 Calendar.clone() per recomposition)
    val dateStrings = remember(cal) {
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val c = cal.clone() as Calendar
        (1..maxDays).associate { day ->
            c.set(Calendar.DAY_OF_MONTH, day)
            day to sdfDate.format(c.time)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
            dayLabels.forEach { label ->
                Text(label, Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                    color = if (label == "Sun" || label == "Sat") primary.copy(alpha = 0.6f) else onSurfaceVar)
            }
        }
        grid.forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 1.dp)) {
                row.forEach { gridDay ->
                    val isInMonth = gridDay.inMonth; val day = gridDay.day
                    val dateStr = if (isInMonth) dateStrings[day] ?: "" else ""
                    val isToday = isInMonth && dateStr == todayStr; val isSelected = isInMonth && day == selectedDay
                    val tasks = if (isInMonth) monthTasks[day] ?: emptyList() else emptyList()

                    Column(Modifier.weight(1f).fillMaxHeight().padding(1.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (isInMonth) surfaceHigh.copy(alpha = 0.4f) else surfaceHigh.copy(alpha = 0.12f))
                        .clickable { if (isInMonth) onDayClick(dateStr) }.padding(3.dp)) {
                        Box(Modifier.align(Alignment.CenterHorizontally).then(if (isToday) Modifier.size(22.dp).clip(CircleShape).background(primary) else Modifier).padding(1.dp), contentAlignment = Alignment.Center) {
                            Text(day.toString(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal),
                                color = when { isToday -> onPrimary; !isInMonth -> dimColor; isSelected -> primary; else -> onSurface })
                        }
                        if (isInMonth) {
                            val tertiary = MaterialTheme.colorScheme.tertiary
                            tasks.take(3).forEach { task ->
                                val tc = when { task.priority == 3 -> PriorityHigh; task.priority == 2 -> PriorityMedium; task.priority == 1 -> PriorityLow; task.isCalendarEvent || task.isGoogleTask -> TaskTeal; else -> primary }
                                val displayTitle = task.title.removePrefix("📅 ").trim()
                                val prefix = when {
                                    task.isHabit -> "● "
                                    task.isMultiDay -> "↔ "
                                    else -> ""
                                }
                                val annotatedTitle = androidx.compose.ui.text.buildAnnotatedString {
                                    if (prefix.isNotEmpty()) {
                                        val prefixColor = if (task.isHabit) tertiary else tc
                                        pushStyle(androidx.compose.ui.text.SpanStyle(color = prefixColor, fontWeight = FontWeight.Bold))
                                        append(prefix)
                                        pop()
                                    }
                                    append(displayTitle)
                                }
                                Text(annotatedTitle, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, lineHeight = 12.sp),
                                    color = tc, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 0.5.dp).clip(RoundedCornerShape(6.dp)).background(tc.copy(alpha = 0.12f)).padding(horizontal = 4.dp, vertical = 1.5.dp))
                            }
                            if (tasks.size > 3) Text("•••", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = onSurfaceVar.copy(alpha = 0.5f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
    tasks: List<Task>, onDateClick: (String) -> Unit,
    onToggle: (Task) -> Unit, onDelete: (Task) -> Unit, onDetail: (Task) -> Unit
) {
    val density = LocalDensity.current
    val calendarExpandedPx = with(density) { 340.dp.toPx() }
    val calendarCollapsedPx = with(density) { 56.dp.toPx() }
    var calendarHeightPx by remember { mutableFloatStateOf(calendarExpandedPx) }

    // Pre-compute date strings once (same optimization as MonthGrid)
    val dateStrings = remember(cal) {
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val c = cal.clone() as Calendar
        (1..maxDays).associate { day ->
            c.set(Calendar.DAY_OF_MONTH, day)
            day to sdfDate.format(c.time)
        }
    }

    // Google Calendar pattern: preScroll collapses, postScroll expands
    val nestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta < 0 && calendarHeightPx > calendarCollapsedPx) {
                    // Scrolling up → collapse calendar first
                    val old = calendarHeightPx
                    calendarHeightPx = (calendarHeightPx + delta).coerceIn(calendarCollapsedPx, calendarExpandedPx)
                    return Offset(0f, calendarHeightPx - old)
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta > 0 && calendarHeightPx < calendarExpandedPx) {
                    // Scrolling down + child didn't consume → list at top → expand calendar
                    val old = calendarHeightPx
                    calendarHeightPx = (calendarHeightPx + delta).coerceIn(calendarCollapsedPx, calendarExpandedPx)
                    return Offset(0f, calendarHeightPx - old)
                }
                return Offset.Zero
            }
        }
    }

    val collapseProgress = (1f - (calendarHeightPx - calendarCollapsedPx) / (calendarExpandedPx - calendarCollapsedPx)).coerceIn(0f, 1f)

    // Find which week row contains selectedDay (or today)
    val selectedWeekIdx = remember(rows, selectedDay) {
        val target = if (selectedDay > 0) selectedDay else Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        rows.indexOfFirst { week -> week.any { it == target } }.coerceAtLeast(0)
    }

    // Week strip pager — each page = 1 week of the month
    val weekStripPager = rememberPagerState(initialPage = selectedWeekIdx) { rows.size }

    // Sync strip pager to selected week when collapsing
    LaunchedEffect(selectedWeekIdx, collapseProgress) {
        if (collapseProgress > 0.5f) weekStripPager.scrollToPage(selectedWeekIdx)
    }

    Column(Modifier.fillMaxSize().nestedScroll(nestedScroll)) {
        // Calendar area — height smoothly transitions from 340dp → 56dp
        val calendarHeightDp = with(density) { (calendarHeightPx / density.density).dp }
        Box(Modifier.fillMaxWidth().height(calendarHeightDp).clip(RoundedCornerShape(0.dp))) {
            // Full calendar grid (visible when not fully collapsed)
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
                                            Box(Modifier.size(40.dp).clip(CircleShape).background(bg).clickable { onDateClick(dateStr) }, contentAlignment = Alignment.Center) {
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
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), Arrangement.Center, Alignment.CenterVertically) {
                        Text("Less", style = MaterialTheme.typography.labelSmall, color = onSurfaceVar); Spacer(Modifier.width(6.dp))
                        listOf(0.12f, 0.3f, 0.5f, 0.67f).forEach { a -> Box(Modifier.size(12.dp).padding(1.dp).clip(CircleShape).background(primary.copy(alpha = a))) }
                        Spacer(Modifier.width(6.dp)); Text("More", style = MaterialTheme.typography.labelSmall, color = onSurfaceVar)
                    }
                }
            }

            // Week strip pager (fades in when collapsing)
            if (collapseProgress > 0f) {
                HorizontalPager(
                    state = weekStripPager,
                    userScrollEnabled = collapseProgress > 0.7f, // Only swipable when mostly collapsed
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
                                    Box(Modifier.size(40.dp).clip(CircleShape)
                                        .background(when { isSelected -> primary; isToday -> primary.copy(alpha = 0.12f); else -> Color.Transparent })
                                        .clickable { onDateClick(dateStr) },
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

        // Task list
        if (selectedDay > 0) {
            val lbl = remember(cal, selectedDay) {
                SimpleDateFormat("EEE, dd MMM", Locale.getDefault()).format(
                    (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, selectedDay) }.time)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(lbl, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = primary)
                if (tasks.isNotEmpty()) Text("${tasks.count { it.isCompleted }}/${tasks.size}", style = MaterialTheme.typography.labelSmall, color = onSurfaceVar)
            }
            if (tasks.isEmpty()) EmptyState("No tasks", Icons.Default.Event)
            else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 8.dp)) {
                items(tasks, key = { it.id }) { task -> val isPast = task.createdDate < todayStr; TaskItem(task = task, onToggle = { if (!isPast) onToggle(task) }, onDelete = { onDelete(task) }, onDetail = { onDetail(task) }, isEditable = !isPast) }
            }
        } else EmptyState("Select a date", Icons.Default.CalendarMonth)
    }
}

// ═══════════════════════════════════════════════════════════════
// WEEK PAGE
// ═══════════════════════════════════════════════════════════════

@Composable
private fun WeekPage(
    weekStart: Calendar, currentMonth: Calendar, selectedDay: Int,
    heatMap: Map<Int, Pair<Int, Int>>, todayStr: String, sdfDate: SimpleDateFormat,
    primary: Color, onPrimary: Color, onSurface: Color, onSurfaceVar: Color,
    tasks: List<Task>, onDateClick: (String) -> Unit,
    onToggle: (Task) -> Unit, onDelete: (Task) -> Unit, onDetail: (Task) -> Unit
) {
    val dayLabels = listOf("S", "M", "T", "W", "T", "F", "S")
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 0..6) {
                val dayCal = (weekStart.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }
                val dom = dayCal.get(Calendar.DAY_OF_MONTH); val dateStr = sdfDate.format(dayCal.time)
                val isToday = dateStr == todayStr; val inMonth = dayCal.get(Calendar.MONTH) == currentMonth.get(Calendar.MONTH)
                val isSelected = inMonth && dom == selectedDay; val taskData = if (inMonth) heatMap[dom] else null
                val heatAlpha = if (taskData != null) { val r = taskData.first.toFloat() / taskData.second; 0.12f + r * 0.55f } else 0f
                Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(when {
                    isSelected -> primary; isToday -> primary.copy(alpha = 0.12f); taskData != null -> primary.copy(alpha = heatAlpha * 0.5f)
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.3f) }).clickable { onDateClick(dateStr) }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(dayLabels[i], style = MaterialTheme.typography.labelSmall, color = if (isSelected) onPrimary else onSurfaceVar)
                    Spacer(Modifier.height(2.dp))
                    Text(dom.toString(), style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium),
                        color = when { isSelected -> onPrimary; isToday -> primary; !inMonth -> onSurfaceVar.copy(alpha = 0.3f); else -> onSurface })
                    if (taskData != null) { Spacer(Modifier.height(2.dp)); Box(Modifier.size(6.dp).clip(CircleShape).background(if (isSelected) onPrimary.copy(alpha = 0.7f) else primary.copy(alpha = 0.6f))) }
                }
            }
        }
        TaskListSection(selectedDay, currentMonth, tasks, onToggle, onDelete, onDetail, todayStr)
    }
}

// ═══════════════════════════════════════════════════════════════
// DAY PAGE — Header-only expand, scroll-collapse preview, 2-task limit
// ═══════════════════════════════════════════════════════════════

@Composable
private fun CollapsibleDayPage(
    dayCal: Calendar, tasks: List<Task>,
    onToggle: (Task) -> Unit, onDelete: (Task) -> Unit, onDetail: (Task) -> Unit,
    onSurfaceVar: Color, todayStr: String
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

    // Scroll-driven collapse: only collapses on scroll up, expand via header tap only
    val density = LocalDensity.current
    val previewMaxPx = with(density) { (52.dp * allDayTasks.size.coerceAtMost(maxPreview).coerceAtLeast(1) + if (allDayTasks.size > maxPreview) 28.dp else 0.dp).toPx() }
    var previewOffsetPx by remember { mutableFloatStateOf(0f) }
    var scrollCollapsed by remember { mutableStateOf(false) }

    val nestedScroll = remember(allDayTasks.size) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (allDayTasks.isEmpty() || allDayExpanded) return Offset.Zero
                val delta = available.y
                if (delta < 0 && !scrollCollapsed) {
                    // Scrolling up → collapse preview smoothly
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
                // Scrolling down + list is at top (leftover delta > 0) → expand preview back
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
        // All-day header
        if (allDayTasks.isNotEmpty()) {
            Row(Modifier.fillMaxWidth()
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

            // All-day tasks with scroll-driven collapse
            if (!scrollCollapsed || allDayExpanded) {
                if (allDayExpanded) {
                    // Expanded: cap height at 240dp + internal scroll so timeline stays reachable
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
                    // Collapsed preview with scroll-driven animation
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

        // Timeline — compact slots
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(24) { hour ->
                val label = when { hour == 0 -> "12 AM"; hour < 12 -> "$hour AM"; hour == 12 -> "12 PM"; else -> "${hour - 12} PM" }
                val tasksHere = hourTasks[hour]
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.Top) {
                    Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = onSurfaceVar.copy(alpha = 0.5f),
                        modifier = Modifier.width(44.dp).padding(top = 2.dp), textAlign = TextAlign.End)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f).drawBehind { drawLine(onSurfaceVar.copy(alpha = 0.08f), Offset(0f, 0f), Offset(size.width, 0f), 1f) }.padding(vertical = 1.dp)) {
                        if (tasksHere != null) tasksHere.forEach { task -> TaskItem(task = task, onToggle = { if (task.createdDate >= todayStr) onToggle(task) }, onDelete = { onDelete(task) }, onDetail = { onDetail(task) }, isEditable = task.createdDate >= todayStr) }
                        else Spacer(Modifier.height(36.dp))
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// SHARED
// ═══════════════════════════════════════════════════════════════

@Composable
private fun TaskListSection(
    selectedDay: Int, currentMonth: Calendar, tasks: List<Task>,
    onToggle: (Task) -> Unit, onDelete: (Task) -> Unit, onDetail: (Task) -> Unit,
    todayStr: String
) {
    val primary = MaterialTheme.colorScheme.primary; val onSurfaceVar = MaterialTheme.colorScheme.onSurfaceVariant
    if (selectedDay > 0) {
        val cal = (currentMonth.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, selectedDay) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(SimpleDateFormat("EEE, dd MMM", Locale.getDefault()).format(cal.time), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = primary)
            if (tasks.isNotEmpty()) Text("${tasks.count { it.isCompleted }}/${tasks.size}", style = MaterialTheme.typography.labelSmall, color = onSurfaceVar)
        }
        if (tasks.isEmpty()) EmptyState("No tasks", Icons.Default.Event)
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 8.dp)) {
            items(tasks, key = { it.id }) { task -> val isPast = task.createdDate < todayStr; TaskItem(task = task, onToggle = { if (!isPast) onToggle(task) }, onDelete = { onDelete(task) }, onDetail = { onDetail(task) }, isEditable = !isPast) }
        }
    } else EmptyState("Select a date", Icons.Default.CalendarMonth)
}

@Composable
private fun EmptyState(message: String, icon: ImageVector) {
    Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        }
    }
}
