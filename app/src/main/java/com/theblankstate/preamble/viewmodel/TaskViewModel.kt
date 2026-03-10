package com.theblankstate.preamble.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.theblankstate.preamble.data.Task
import com.theblankstate.preamble.notification.TaskNotificationManager
import com.theblankstate.preamble.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class StatsState(
    val totalCompleted: Int = 0,
    val totalTasks: Int = 0,
    val todayCompleted: Int = 0,
    val todayTotal: Int = 0,
    val streak: Int = 0,
    val weeklyStats: List<Pair<String, Float>> = emptyList(),
    val dailyCompleted: List<Pair<String, Int>> = emptyList(),
    val monthlyCompleted: List<Pair<String, Int>> = emptyList()
)

class TaskViewModel(
    private val repository: TaskRepository,
    private val appContext: Context
) : ViewModel() {

    private val today = TaskRepository.todayString()

    val todayTasks: StateFlow<List<Task>> = repository.getTasksForDate(today)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val past10Dates = (1..10).map { i ->
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -i)
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
    }

    val pastTasks: StateFlow<Map<String, List<Task>>> = repository.getTasksForDates(past10Dates)
        .map { tasks: List<Task> -> tasks.groupBy { it.createdDate } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _selectedDate = MutableStateFlow<String?>(null)
    
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedDateTasks: StateFlow<List<Task>> = _selectedDate
        .flatMapLatest { dateStr: String? -> 
            if (dateStr == null) flowOf(emptyList()) else repository.getTasksForDate(dateStr) 
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectDate(date: String?) {
        _selectedDate.value = date
    }

    // Calendar heat map
    private val _calendarHeatMap = MutableStateFlow<Map<Int, Pair<Int, Int>>>(emptyMap())
    val calendarHeatMap: StateFlow<Map<Int, Pair<Int, Int>>> = _calendarHeatMap.asStateFlow()

    fun loadHeatMap(year: Int, month: Int) {
        viewModelScope.launch {
            _calendarHeatMap.value = repository.getMonthlyHeatMap(year, month)
        }
    }

    private val _statsState = MutableStateFlow(StatsState())
    val statsState: StateFlow<StatsState> = _statsState.asStateFlow()

    // Debounce stats refresh to avoid 730+ queries on rapid task changes
    private var statsRefreshJob: Job? = null

    init {
        viewModelScope.launch {
            combine(
                repository.getCompletedCount(),
                repository.getTotalCount()
            ) { completed, total ->
                completed to total
            }.collect { (completed, total) ->
                _statsState.update { it.copy(totalCompleted = completed, totalTasks = total) }
            }
        }

        viewModelScope.launch {
            todayTasks.collect { tasks ->
                val completed = tasks.count { it.isCompleted }
                val total = tasks.size
                _statsState.update { it.copy(todayCompleted = completed, todayTotal = total) }
            }
        }

        refreshStats()
    }

    fun refreshStats() {
        // Debounce: cancel previous job, wait 500ms before running
        statsRefreshJob?.cancel()
        statsRefreshJob = viewModelScope.launch {
            delay(500)
            val streak = repository.calculateStreak()
            val weekly = repository.getWeeklyStats()
            val daily = repository.getDailyStatsForRange(14)
            val monthly = repository.getDailyStatsForRange(30)
            _statsState.update {
                it.copy(
                    streak = streak,
                    weeklyStats = weekly,
                    dailyCompleted = daily,
                    monthlyCompleted = monthly
                )
            }
        }
    }

    fun addTask(title: String, date: String? = null, deadlineTime: String? = null) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.addTask(title, date, deadlineTime)
            if (deadlineTime != null) {
                val taskDate = date ?: TaskRepository.todayString()
                com.theblankstate.preamble.notification.TaskAlarmManager.scheduleAlarm(
                    appContext, title, taskDate, deadlineTime
                )
            }
            refreshStats()
        }
    }

    fun toggleTask(task: Task) {
        viewModelScope.launch {
            repository.toggleTask(task)
            refreshStats()
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
            refreshStats()
        }
    }

    private fun updateNotification() {
        // Notification auto-updates via TaskNotificationService
    }

    class Factory(
        private val repository: TaskRepository,
        private val appContext: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TaskViewModel(repository, appContext) as T
        }
    }
}
