package com.projectkaka.inventory.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.projectkaka.inventory.data.local.entity.BasketEntity
import com.projectkaka.inventory.data.local.entity.BasketItemCrossRef
import com.projectkaka.inventory.data.local.entity.ItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BasketDao {

    @Query("SELECT * FROM baskets ORDER BY created_at DESC")
    fun observeAllBaskets(): Flow<List<BasketEntity>>

    @Query("SELECT * FROM baskets WHERE id = :id")
    suspend fun getBasketById(id: Int): BasketEntity?

    @Query("SELECT * FROM baskets WHERE id = :id")
    fun observeBasketById(id: Int): Flow<BasketEntity?>

    @Query("SELECT * FROM baskets WHERE LOWER(code) = LOWER(:code)")
    suspend fun getBasketByCode(code: String): BasketEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBasket(basket: BasketEntity): Long

    @Update
    suspend fun updateBasket(basket: BasketEntity)

    @Delete
    suspend fun deleteBasket(basket: BasketEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addItemToBasket(crossRef: BasketItemCrossRef)

    @Query("DELETE FROM basket_items WHERE basket_id = :basketId AND item_id = :itemId")
    suspend fun removeItemFromBasket(basketId: Int, itemId: Int)

    @Query("DELETE FROM basket_items WHERE item_id = :itemId")
    suspend fun removeItemFromAllBaskets(itemId: Int)

    @Query("""
        SELECT items.* FROM items
        INNER JOIN basket_items ON items.id = basket_items.item_id
        WHERE basket_items.basket_id = :basketId
        ORDER BY items.name ASC
    """)
    fun observeItemsForBasket(basketId: Int): Flow<List<ItemEntity>>

    @Query("""
        SELECT baskets.* FROM baskets
        INNER JOIN basket_items ON baskets.id = basket_items.basket_id
        WHERE basket_items.item_id = :itemId
        LIMIT 1
    """)
    fun observeBasketForItem(itemId: Int): Flow<BasketEntity?>

    @Query("SELECT COUNT(*) FROM basket_items WHERE basket_id = :basketId")
    fun observeBasketItemCount(basketId: Int): Flow<Int>

    @Transaction
    suspend fun moveItemToBasket(itemId: Int, targetBasketId: Int) {
        removeItemFromAllBaskets(itemId)
        addItemToBasket(BasketItemCrossRef(basketId = targetBasketId, itemId = itemId))
    }
}
