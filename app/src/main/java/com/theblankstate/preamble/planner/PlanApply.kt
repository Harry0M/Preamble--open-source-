package com.theblankstate.preamble.planner

import com.theblankstate.preamble.data.Task

object PlanApply {
    /** PURE. Returns a copy of [task] with only deadlineTime replaced (Req 4.2 safety guarantee). */
    fun withDeadlineTime(task: Task, time: String): Task = task.copy(deadlineTime = time)
}
