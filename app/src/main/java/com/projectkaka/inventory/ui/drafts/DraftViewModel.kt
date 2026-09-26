package com.projectkaka.inventory.ui.drafts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.projectkaka.inventory.KakaApplication
import com.projectkaka.inventory.data.local.entity.ItemEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Phase 2: turns raw capture drafts (is_draft = true, name "Draft - ...")
 * into fully described active items. Same repository, no new data path.
 */
class DraftViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = getApplication<KakaApplication>().repository

    val drafts: StateFlow<List<ItemEntity>> = repository.getDraftItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Promote a draft to an active item with user-supplied metadata. */
    fun commitDraft(
        draft: ItemEntity,
        name: String,
        category: String,
        locationTag: String,
        estimatedValue: Double
    ) {
        viewModelScope.launch {
            repository.updateItem(
                draft.copy(
                    name = name.ifBlank { "Item ${draft.id}" },
                    category = category.ifBlank { "Uncategorized" },
                    locationTag = locationTag.trim(),
                    estimatedValue = com.projectkaka.inventory.model.Money((estimatedValue.coerceAtLeast(0.0) * 100).toLong()),
                    isDraft = false
                )
            )
        }
    }

    /** Discard a draft and remove its orphaned WebP file. */
    fun discardDraft(draft: ItemEntity) {
        viewModelScope.launch {
            runCatching { java.io.File(draft.imagePath).delete() }
            repository.deleteItem(draft)
        }
    }
}
