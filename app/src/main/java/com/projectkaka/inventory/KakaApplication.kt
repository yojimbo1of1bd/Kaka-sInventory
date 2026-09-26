package com.projectkaka.inventory

import android.app.Application
import com.projectkaka.inventory.data.local.AppDatabase
import com.projectkaka.inventory.data.repository.FinanceRepository
import com.projectkaka.inventory.data.repository.FinanceRepositoryImpl
import com.projectkaka.inventory.data.repository.InventoryRepository
import com.projectkaka.inventory.data.repository.InventoryRepositoryImpl
import com.projectkaka.inventory.data.settings.UserPreferences
import kotlinx.coroutines.Dispatchers

/**
 * Manual dependency container. No DI framework needed for an MVP,
 * and no third-party libraries means the app stays trivially offline.
 */
class KakaApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    val preferences: UserPreferences by lazy { UserPreferences(this) }

    val repository: InventoryRepository by lazy {
        InventoryRepositoryImpl(
            itemDao = database.itemDao(),
            careTaskDao = database.careTaskDao(),
            ioDispatcher = Dispatchers.IO
        )
    }

    val financeRepository: FinanceRepository by lazy {
        FinanceRepositoryImpl(
            db = database,
            financeDao = database.financeDao(),
            ioDispatcher = Dispatchers.IO
        )
    }
}
