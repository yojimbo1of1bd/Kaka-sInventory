package com.projectkaka.inventory.data.triage

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.TimeUnit

/**
 * Emits "now" immediately, then once per hour (configurable for tests).
 *
 * The triage query is time-sensitive, so a stale timestamp would leave the
 * Red Ring showing yesterday's state. An hourly tick is plenty for a task
 * whose granularity is whole days, and it costs nothing while the screen is
 * not being observed (the flow is only collected while the UI is subscribed).
 */
object TriageClock {

    fun ticks(intervalMs: Long = TimeUnit.HOURS.toMillis(1)): Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(intervalMs)
        }
    }
}
