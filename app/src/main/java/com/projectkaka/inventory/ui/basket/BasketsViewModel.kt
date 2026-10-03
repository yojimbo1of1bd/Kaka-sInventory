package com.projectkaka.inventory.ui.basket

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.projectkaka.inventory.KakaApplication
import com.projectkaka.inventory.data.local.entity.BasketEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BasketsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = getApplication<KakaApplication>().repository

    val baskets: StateFlow<List<BasketEntity>> = repository.observeAllBaskets()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    fun createBasket(name: String, code: String, description: String, colorHex: String) {
        viewModelScope.launch {
            val safeCode = code.trim().uppercase().ifEmpty {
                "BOX-${System.currentTimeMillis() % 10000}"
            }
            repository.saveBasket(
                BasketEntity(
                    name = name.trim().ifEmpty { "Moving Box" },
                    code = safeCode,
                    description = description.trim(),
                    colorHex = colorHex.ifEmpty { "#3B82F6" },
                    isPacked = false
                )
            )
        }
    }

    fun togglePacked(basket: BasketEntity) {
        viewModelScope.launch {
            repository.updateBasket(basket.copy(isPacked = !basket.isPacked))
        }
    }

    fun deleteBasket(basket: BasketEntity) {
        viewModelScope.launch {
            repository.deleteBasket(basket)
        }
    }

    suspend fun getBasketByCode(code: String): BasketEntity? {
        val trimmed = code.trim().uppercase()
        val cleanCode = if (trimmed.startsWith("KAKA-BOX:")) {
            trimmed.removePrefix("KAKA-BOX:")
        } else {
            trimmed
        }
        return repository.getBasketByCode(cleanCode)
    }
}
