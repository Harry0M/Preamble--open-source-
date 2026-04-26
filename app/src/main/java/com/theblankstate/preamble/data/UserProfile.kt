package com.theblankstate.preamble.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

enum class UserRole(val key: String, val label: String) {
    STUDENT("student", "Student"),
    WORKING("working", "Working professional"),
    OTHER("other", "Other");

    companion object {
        fun fromKey(k: String?): UserRole? = values().firstOrNull { it.key == k }
    }
}

enum class TasksPerDay(val key: String, val label: String, val hint: String) {
    LIGHT("light", "1–3", "Just the essentials"),
    MODERATE("moderate", "4–8", "Busy but balanced"),
    HEAVY("heavy", "9+", "Packed schedule");

    companion object {
        fun fromKey(k: String?): TasksPerDay? = values().firstOrNull { it.key == k }
    }
}

enum class PrimaryGoal(val key: String, val label: String, val iconName: String) {
    STUDY("study", "Study", "menu_book"),
    WORK("work", "Work", "business_center"),
    HEALTH("health", "Health", "fitness_center"),
    HABIT("habit", "Build a habit", "loop");

    companion object {
        fun fromKey(k: String?): PrimaryGoal? = values().firstOrNull { it.key == k }
    }
}

data class UserProfile(
    val name: String? = null,
    val age: Int? = null,
    val gender: String? = null,
    val role: UserRole? = null,
    val tasksPerDay: TasksPerDay? = null,
    val goal: PrimaryGoal? = null,
    val goals: Set<PrimaryGoal> = emptySet(),
    val baselineScore: Int = 0,
    val percentile: Int = 0,
    val onboardingCompletedAt: Long = 0L,
) {
    val discountEligible: Boolean
        get() = role == UserRole.STUDENT || (age != null && age < 25)

    val effectiveGoals: Set<PrimaryGoal>
        get() = if (goals.isNotEmpty()) goals else goal?.let { setOf(it) } ?: emptySet()
}

/**
 * Computes a baseline productivity score 0–100 from onboarding answers.
 * Score is intentionally generous (peak-end reveal moment) but driven by real inputs.
 */
fun computeBaselineScore(p: UserProfile): Int {
    var score = 45
    score += when (p.tasksPerDay) {
        TasksPerDay.LIGHT -> 5
        TasksPerDay.MODERATE -> 12
        TasksPerDay.HEAVY -> 18
        null -> 0
    }
    score += when (p.role) {
        UserRole.STUDENT -> 8
        UserRole.WORKING -> 12
        UserRole.OTHER -> 6
        null -> 0
    }
    score += if (p.effectiveGoals.isNotEmpty()) 8 else 0
    score += if (!p.name.isNullOrBlank()) 3 else 0
    score += if (p.age != null) 3 else 0
    score += if (p.gender != null) 3 else 0
    return score.coerceIn(55, 96)
}

fun computePercentile(score: Int): Int = (100 - score).coerceIn(3, 40)

object UserProfileStore {
    private const val PREFS = "preamble_prefs"
    private const val K_NAME = "profile_name"
    private const val K_AGE = "profile_age"
    private const val K_GENDER = "profile_gender"
    private const val K_ROLE = "profile_role"
    private const val K_TASKS = "profile_tasks_per_day"
    private const val K_GOAL = "profile_goal"
    private const val K_GOALS = "profile_goals"
    private const val K_SCORE = "profile_baseline_score"
    private const val K_PERCENTILE = "profile_percentile"
    private const val K_COMPLETED_AT = "profile_completed_at"

    fun save(ctx: Context, p: UserProfile) {
        val score = if (p.baselineScore > 0) p.baselineScore else computeBaselineScore(p)
        val pct = if (p.percentile > 0) p.percentile else computePercentile(score)
        val completedAt = if (p.onboardingCompletedAt > 0) p.onboardingCompletedAt else System.currentTimeMillis()
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            p.name?.let { putString(K_NAME, it) }
            p.age?.let { putInt(K_AGE, it) }
            p.gender?.let { putString(K_GENDER, it) }
            p.role?.let { putString(K_ROLE, it.key) }
            p.tasksPerDay?.let { putString(K_TASKS, it.key) }
            val goalsToSave = p.effectiveGoals
            if (goalsToSave.isNotEmpty()) {
                putString(K_GOALS, goalsToSave.joinToString(",") { it.key })
                putString(K_GOAL, goalsToSave.first().key)
            }
            putInt(K_SCORE, score)
            putInt(K_PERCENTILE, pct)
            putLong(K_COMPLETED_AT, completedAt)
            apply()
        }

        // Bridge profile fields into long-term memory so AI sees them via memory snapshot.
        bridgeToMemoryAsync(ctx, p)
    }

    private fun bridgeToMemoryAsync(ctx: Context, p: UserProfile) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val repo = com.theblankstate.preamble.ai.AiMemoryRepository.get(ctx)
                p.name?.takeIf { it.isNotBlank() }?.let {
                    repo.save("name", it, "identity", 1.0f, source = "onboarding")
                }
                p.age?.let {
                    repo.save("age", it.toString(), "identity", 1.0f, source = "onboarding")
                }
                p.gender?.takeIf { it.isNotBlank() }?.let {
                    repo.save("gender", it, "identity", 1.0f, source = "onboarding")
                }
                p.role?.let {
                    repo.save("role", it.label, "identity", 1.0f, source = "onboarding")
                }
                p.tasksPerDay?.let {
                    repo.save("daily_load", "${it.label} tasks/day (${it.hint})", "context", 1.0f, source = "onboarding")
                }
                val goalsToSave = p.effectiveGoals
                if (goalsToSave.isNotEmpty()) {
                    repo.save("primary_goals", goalsToSave.joinToString(", ") { it.label }, "goal", 1.0f, source = "onboarding")
                }
            } catch (e: Exception) {
                android.util.Log.w("UserProfileStore", "Memory bridge failed", e)
            }
        }
    }

    fun load(ctx: Context): UserProfile {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val goalsRaw = sp.getString(K_GOALS, null)
        val goalsSet = goalsRaw?.split(',')
            ?.mapNotNull { PrimaryGoal.fromKey(it.trim()) }
            ?.toSet()
            ?: emptySet()
        return UserProfile(
            name = sp.getString(K_NAME, null),
            age = sp.getInt(K_AGE, -1).takeIf { it > 0 },
            gender = sp.getString(K_GENDER, null),
            role = UserRole.fromKey(sp.getString(K_ROLE, null)),
            tasksPerDay = TasksPerDay.fromKey(sp.getString(K_TASKS, null)),
            goal = PrimaryGoal.fromKey(sp.getString(K_GOAL, null)),
            goals = goalsSet,
            baselineScore = sp.getInt(K_SCORE, 0),
            percentile = sp.getInt(K_PERCENTILE, 0),
            onboardingCompletedAt = sp.getLong(K_COMPLETED_AT, 0L),
        )
    }

    /**
     * Pushes profile to Firestore users/{uid}. No-ops if not signed in.
     */
    fun syncToFirestore(profile: UserProfile, onResult: (Boolean) -> Unit = {}) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) { onResult(false); return }
        val data = mutableMapOf<String, Any>()
        profile.name?.let { data["name"] = it }
        profile.age?.let { data["age"] = it }
        profile.gender?.let { data["gender"] = it }
        profile.role?.let { data["role"] = it.key }
        profile.tasksPerDay?.let { data["tasksPerDay"] = it.key }
        val goalsToSync = profile.effectiveGoals
        if (goalsToSync.isNotEmpty()) {
            data["goals"] = goalsToSync.map { it.key }
            data["goal"] = goalsToSync.first().key
        }
        data["baselineScore"] = profile.baselineScore
        data["percentile"] = profile.percentile
        data["discountEligible"] = profile.discountEligible
        data["onboardingCompletedAt"] = profile.onboardingCompletedAt
        if (data.isEmpty()) { onResult(false); return }
        try {
            FirebaseFirestore.getInstance("preamble")
                .collection("users").document(uid)
                .set(data, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener { onResult(true) }
                .addOnFailureListener { onResult(false) }
        } catch (e: Exception) {
            onResult(false)
        }
    }
}
