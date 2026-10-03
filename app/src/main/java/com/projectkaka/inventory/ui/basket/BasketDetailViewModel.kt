package com.projectkaka.inventory.ui.basket

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.projectkaka.inventory.KakaApplication
import com.projectkaka.inventory.data.local.entity.BasketEntity
import com.projectkaka.inventory.data.local.entity.ItemEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BasketDetailUiState(
    val basket: BasketEntity? = null,
    val packedItems: List<ItemEntity> = emptyList(),
    val allItems: List<ItemEntity> = emptyList(),
    val totalEstimatedMinorUnits: Long = 0L,
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
class BasketDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = getApplication<KakaApplication>().repository

    private val basketIdState = MutableStateFlow<Int?>(null)

    private val basketFlow = basketIdState.flatMapLatest { id ->
        if (id == null) flowOf(null)
        else repository.observeBasketById(id)
    }

    private val packedItemsFlow = basketIdState.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else repository.observeItemsForBasket(id)
    }

    private val allItemsFlow = repository.getAllInventoryItems()

    val uiState: StateFlow<BasketDetailUiState> = combine(
        basketFlow,
        packedItemsFlow,
        allItemsFlow
    ) { basket, packed, all ->
        val totalMinorUnits = packed.sumOf { it.estimatedValue.minorUnits }
        BasketDetailUiState(
            basket = basket,
            packedItems = packed,
            allItems = all,
            totalEstimatedMinorUnits = totalMinorUnits,
            isLoading = false
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        BasketDetailUiState()
    )

    fun load(basketId: Int) {
        basketIdState.value = basketId
    }

    fun packItem(itemId: Int) {
        val bId = basketIdState.value ?: return
        viewModelScope.launch {
            repository.moveItemToBasket(itemId, bId)
        }
    }

    fun unpackItem(itemId: Int) {
        val bId = basketIdState.value ?: return
        viewModelScope.launch {
            repository.removeItemFromBasket(bId, itemId)
        }
    }

    fun togglePacked() {
        val currentBasket = uiState.value.basket ?: return
        viewModelScope.launch {
            repository.updateBasket(currentBasket.copy(isPacked = !currentBasket.isPacked))
        }
    }

    fun updateBasket(name: String, code: String, description: String, colorHex: String) {
        val currentBasket = uiState.value.basket ?: return
        viewModelScope.launch {
            repository.updateBasket(
                currentBasket.copy(
                    name = name.trim().ifEmpty { currentBasket.name },
                    code = code.trim().uppercase().ifEmpty { currentBasket.code },
                    description = description.trim(),
                    colorHex = colorHex
                )
            )
        }
    }
}
