package com.projectkaka.inventory.triage

import com.projectkaka.inventory.data.local.entity.CareTaskEntity
import com.projectkaka.inventory.data.triage.DueTask
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Verifies the triage formula used by both the SQL query and the UI label:
 *   dueAt = last_completed_date + frequency_days * 86_400_000
 *   overdue when now >= dueAt
 */
class DueTaskTest {

    private val day = TimeUnit.DAYS.toMillis(1)

    private fun task(frequencyDays: Int, lastCompleted: Long) = CareTaskEntity(
        id = 1,
        itemId = 1,
        taskName = "Water the plant",
        frequencyDays = frequencyDays,
        lastCompletedDate = lastCompleted
    )

    @Test
    fun notDue_beforeIntervalElapses() {
        val now = 1_000L * day
        // Completed today, repeats every 7 days -> not due.
        assertEquals(0, DueTask.overdueDays(task(7, now), now))
    }

    @Test
    fun dueExactlyAtBoundary_countsAsOverdue() {
        val last = 1_000L * day
        val now = last + 7 * day
        assertEquals(0, DueTask.overdueDays(task(7, last), now))
    }

    @Test
    fun oneDayPastDue_reportsOneDay() {
        val last = 1_000L * day
        val now = last + 8 * day
        assertEquals(1, DueTask.overdueDays(task(7, last), now))
    }

    @Test
    fun longOverdue_reportsWholeDays() {
        val last = 1_000L * day
        val now = last + 30 * day
        // 30 days elapsed - 7 day interval = 23 days overdue.
        assertEquals(23, DueTask.overdueDays(task(7, last), now))
    }

    @Test
    fun singleDayInterval_overdueAfterOneDay() {
        val last = 500L * day
        val now = last + 2 * day
        assertEquals(1, DueTask.overdueDays(task(1, last), now))
    }
}
