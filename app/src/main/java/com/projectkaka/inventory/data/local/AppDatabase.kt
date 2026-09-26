package com.projectkaka.inventory.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.projectkaka.inventory.data.local.dao.CareTaskDao
import com.projectkaka.inventory.data.local.dao.FinanceDao
import com.projectkaka.inventory.data.local.dao.ItemDao
import com.projectkaka.inventory.data.local.entity.AccountEntity
import com.projectkaka.inventory.data.local.entity.CareTaskEntity
import com.projectkaka.inventory.data.local.entity.FinancialCategoryEntity
import com.projectkaka.inventory.data.local.entity.ItemEntity
import com.projectkaka.inventory.data.local.entity.LedgerEntryEntity
import com.projectkaka.inventory.data.local.entity.TransactionEntity

@Database(
    entities = [
        ItemEntity::class,
        CareTaskEntity::class,
        AccountEntity::class,
        FinancialCategoryEntity::class,
        TransactionEntity::class,
        LedgerEntryEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun itemDao(): ItemDao
    abstract fun careTaskDao(): CareTaskDao
    abstract fun financeDao(): FinanceDao

    companion object {
        private const val DATABASE_NAME = "kaka_inventory.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    // Fast sequential writes for burst photo ingestion.
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }

        /**
         * v1 → v2: Create the four financial tables and seed default data.
         *
         * This migration is purely additive — no existing columns or tables
         * are touched, so all inventory items and care tasks survive intact.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {

                // ── accounts ────────────────────────────────────────────
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `accounts` (
                        `id`              INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name`            TEXT    NOT NULL,
                        `type`            TEXT    NOT NULL,
                        `aliases`         TEXT    NOT NULL DEFAULT '',
                        `opening_balance` REAL    NOT NULL DEFAULT 0.0,
                        `is_active`       INTEGER NOT NULL DEFAULT 1,
                        `created_at`      INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_accounts_name` ON `accounts` (`name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_type` ON `accounts` (`type`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_is_active` ON `accounts` (`is_active`)")

                // ── financial_categories ─────────────────────────────────
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `financial_categories` (
                        `id`         INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name`       TEXT    NOT NULL,
                        `type`       TEXT    NOT NULL,
                        `aliases`    TEXT    NOT NULL DEFAULT '',
                        `created_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_financial_categories_name` ON `financial_categories` (`name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_financial_categories_type` ON `financial_categories` (`type`)")

                // ── financial_transactions ───────────────────────────────
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `financial_transactions` (
                        `id`          INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `amount`      REAL    NOT NULL,
                        `account_id`  INTEGER NOT NULL,
                        `category_id` INTEGER NOT NULL,
                        `is_credit`   INTEGER NOT NULL,
                        `note`        TEXT    NOT NULL DEFAULT '',
                        `timestamp`   INTEGER NOT NULL,
                        FOREIGN KEY (`account_id`)  REFERENCES `accounts`(`id`)              ON DELETE CASCADE,
                        FOREIGN KEY (`category_id`) REFERENCES `financial_categories`(`id`)   ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_financial_transactions_account_id`  ON `financial_transactions` (`account_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_financial_transactions_category_id` ON `financial_transactions` (`category_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_financial_transactions_timestamp`   ON `financial_transactions` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_financial_transactions_is_credit`   ON `financial_transactions` (`is_credit`)")

                // ── ledger_entries ───────────────────────────────────────
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ledger_entries` (
                        `id`           INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `contact_name` TEXT    NOT NULL,
                        `contact_phone` TEXT   NOT NULL DEFAULT '',
                        `amount`       REAL    NOT NULL,
                        `type`         TEXT    NOT NULL,
                        `is_settled`   INTEGER NOT NULL DEFAULT 0,
                        `note`         TEXT    NOT NULL DEFAULT '',
                        `due_date`     INTEGER,
                        `created_at`   INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_entries_is_settled` ON `ledger_entries` (`is_settled`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_entries_type`       ON `ledger_entries` (`type`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_entries_due_date`   ON `ledger_entries` (`due_date`)")

                // ── Seed default accounts + categories ──────────────────
                DatabaseSeeder.seed(db)
            }
        }
    }
}

