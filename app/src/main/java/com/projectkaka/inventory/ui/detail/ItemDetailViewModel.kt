package com.projectkaka.inventory.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.projectkaka.inventory.KakaApplication
import com.projectkaka.inventory.data.local.entity.CareTaskEntity
import com.projectkaka.inventory.data.local.entity.ItemEntity
import com.projectkaka.inventory.data.local.entity.ItemStatus
import com.projectkaka.inventory.data.triage.DueTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ItemDetailUiState(
    val item: ItemEntity? = null,
    val tasks: List<CareTaskEntity> = emptyList(),
    val loading: Boolean = true
)

class ItemDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = getApplication<KakaApplication>().repository

    private val itemId = MutableStateFlow<Int?>(null)

    private val item = itemId.flatMapLatest { id ->
        if (id == null) kotlinx.coroutines.flow.flowOf(null)
        else repository.observeItem(id)
    }

    private val tasks = itemId.flatMapLatest { id ->
        if (id == null) kotlinx.coroutines.flow.flowOf(emptyList())
        else repository.getTasksForItem(id)
    }

    val uiState: StateFlow<ItemDetailUiState> =
        combine(item, tasks) { item, taskList ->
            ItemDetailUiState(item = item, tasks = taskList, loading = false)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ItemDetailUiState()
        )

    fun load(id: Int) {
        itemId.value = id
    }

    /** Days until / past due for display next to each task. */
    fun overdueDays(task: CareTaskEntity, now: Long = System.currentTimeMillis()): Int =
        DueTask.overdueDays(task, now)

    fun addTask(taskName: String, frequencyDays: Int) {
        val id = itemId.value ?: return
        viewModelScope.launch {
            repository.saveCareTask(
                CareTaskEntity(
                    itemId = id,
                    taskName = taskName.trim(),
                    frequencyDays = frequencyDays.coerceAtLeast(1)
                )
            )
        }
    }

    fun completeTask(task: CareTaskEntity) {
        viewModelScope.launch { repository.completeCareTask(task) }
    }

    fun deleteTask(task: CareTaskEntity) {
        viewModelScope.launch { repository.deleteCareTask(task) }
    }

    fun liquidate(item: ItemEntity, status: ItemStatus, recovered: Double) {
        viewModelScope.launch { repository.liquidateItem(item.id, status, recovered) }
    }

    fun deleteItem() {
        val item = uiState.value.item ?: return
        viewModelScope.launch { repository.deleteItem(item) }
    }
}
