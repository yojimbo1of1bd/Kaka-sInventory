package com.projectkaka.inventory.data.triage

import com.projectkaka.inventory.data.local.entity.CareTaskEntity
import java.util.concurrent.TimeUnit

/** A care task joined with the item it belongs to, plus computed overdue info. */
data class DueTask(
    val task: CareTaskEntity,
    val itemName: String,
    val itemImagePath: String,
    val daysOverdue: Int
) {
    companion object {
        /**
         * Triage math, mirrored from the SQL so the UI can label each row:
         * dueDate = last_completed_date + frequency_days * 86_400_000
         * daysOverdue = (now - dueDate) / 86_400_000, floored at 0
         */
        fun overdueDays(task: CareTaskEntity, now: Long): Int {
            val dueAt = task.lastCompletedDate +
                TimeUnit.DAYS.toMillis(task.frequencyDays.toLong())
            if (now <= dueAt) return 0
            return TimeUnit.MILLISECONDS.toDays(now - dueAt).toInt()
        }
    }
}
