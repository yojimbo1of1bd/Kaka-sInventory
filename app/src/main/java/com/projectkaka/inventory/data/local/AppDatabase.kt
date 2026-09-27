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
    version = 7,
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Fresh installs get everything directly into the v6 schema.
                            DatabaseSeeder.seedV6(db)
                        }
                    })
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

        /**
         * v2 → v3: Money refactor (Double -> INTEGER minor units).
         * Rebuilds tables to multiply existing money by 100 and changes types.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Rebuild accounts
                db.execSQL("CREATE TABLE `accounts_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `aliases` TEXT NOT NULL, `opening_balance` INTEGER NOT NULL, `is_active` INTEGER NOT NULL, `created_at` INTEGER NOT NULL)")
                db.execSQL("INSERT INTO `accounts_new` (`id`, `name`, `type`, `aliases`, `opening_balance`, `is_active`, `created_at`) SELECT `id`, `name`, `type`, `aliases`, CAST(`opening_balance` * 100 AS INTEGER), `is_active`, `created_at` FROM `accounts`")
                db.execSQL("DROP TABLE `accounts`")
                db.execSQL("ALTER TABLE `accounts_new` RENAME TO `accounts`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_accounts_name` ON `accounts` (`name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_type` ON `accounts` (`type`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_is_active` ON `accounts` (`is_active`)")

                // 2. Rebuild financial_transactions
                db.execSQL("CREATE TABLE `financial_transactions_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amount` INTEGER NOT NULL, `account_id` INTEGER NOT NULL, `category_id` INTEGER, `transfer_id` TEXT, `counter_account_id` INTEGER, `type` TEXT NOT NULL, `is_credit` INTEGER NOT NULL, `note` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, FOREIGN KEY(`account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`category_id`) REFERENCES `financial_categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("INSERT INTO `financial_transactions_new` (`id`, `amount`, `account_id`, `category_id`, `transfer_id`, `counter_account_id`, `type`, `is_credit`, `note`, `timestamp`) SELECT `id`, CAST(`amount` * 100 AS INTEGER), `account_id`, `category_id`, NULL, NULL, CASE WHEN `is_credit` THEN 'INCOME' ELSE 'EXPENSE' END, `is_credit`, `note`, `timestamp` FROM `financial_transactions`")
                db.execSQL("DROP TABLE `financial_transactions`")
                db.execSQL("ALTER TABLE `financial_transactions_new` RENAME TO `financial_transactions`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_financial_transactions_account_id` ON `financial_transactions` (`account_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_financial_transactions_category_id` ON `financial_transactions` (`category_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_financial_transactions_timestamp` ON `financial_transactions` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_financial_transactions_is_credit` ON `financial_transactions` (`is_credit`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_financial_transactions_type` ON `financial_transactions` (`type`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_financial_transactions_transfer_id` ON `financial_transactions` (`transfer_id`)")

                // 3. Rebuild ledger_entries
                db.execSQL("CREATE TABLE `ledger_entries_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `contact_name` TEXT NOT NULL, `contact_phone` TEXT NOT NULL, `amount` INTEGER NOT NULL, `type` TEXT NOT NULL, `is_settled` INTEGER NOT NULL, `note` TEXT NOT NULL, `due_date` INTEGER, `created_at` INTEGER NOT NULL)")
                db.execSQL("INSERT INTO `ledger_entries_new` (`id`, `contact_name`, `contact_phone`, `amount`, `type`, `is_settled`, `note`, `due_date`, `created_at`) SELECT `id`, `contact_name`, `contact_phone`, CAST(`amount` * 100 AS INTEGER), `type`, `is_settled`, `note`, `due_date`, `created_at` FROM `ledger_entries`")
                db.execSQL("DROP TABLE `ledger_entries`")
                db.execSQL("ALTER TABLE `ledger_entries_new` RENAME TO `ledger_entries`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_entries_is_settled` ON `ledger_entries` (`is_settled`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_entries_type` ON `ledger_entries` (`type`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_entries_due_date` ON `ledger_entries` (`due_date`)")

                // 4. Rebuild items
                db.execSQL("CREATE TABLE `items_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `location_tag` TEXT NOT NULL, `estimated_value` INTEGER NOT NULL, `image_path` TEXT NOT NULL, `is_draft` INTEGER NOT NULL, `status` TEXT NOT NULL, `date_added` INTEGER NOT NULL)")
                db.execSQL("INSERT INTO `items_new` (`id`, `name`, `category`, `location_tag`, `estimated_value`, `image_path`, `is_draft`, `status`, `date_added`) SELECT `id`, `name`, `category`, `location_tag`, CAST(`estimated_value` * 100 AS INTEGER), `image_path`, `is_draft`, `status`, `date_added` FROM `items`")
                db.execSQL("DROP TABLE `items`")
                db.execSQL("ALTER TABLE `items_new` RENAME TO `items`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_items_status` ON `items` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_items_category` ON `items` (`category`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_items_is_draft` ON `items` (`is_draft`)")
            }
        }

        /**
         * v3 → v4: Phase 2 Ledger enhancements.
         * Adds account_id and linked_transaction_id to ledger_entries.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `ledger_entries` ADD COLUMN `account_id` INTEGER")
                db.execSQL("ALTER TABLE `ledger_entries` ADD COLUMN `linked_transaction_id` INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_entries_account_id` ON `ledger_entries` (`account_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_entries_linked_transaction_id` ON `ledger_entries` (`linked_transaction_id`)")
            }
        }

        /**
         * v4 → v5: Phase 3 Balance performance.
         * Denormalises account balances into a balance_minor column on accounts table.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `balance_minor` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    UPDATE `accounts`
                    SET `balance_minor` = `opening_balance` + 
                        COALESCE((SELECT SUM(`amount`) FROM `financial_transactions` WHERE `account_id` = `accounts`.`id` AND `is_credit` = 1), 0) - 
                        COALESCE((SELECT SUM(`amount`) FROM `financial_transactions` WHERE `account_id` = `accounts`.`id` AND `is_credit` = 0), 0)
                    """.trimIndent()
                )
            }
        }

        /**
         * v5 → v6: Phase 3 Account Management defaults.
         * Ensures Assets, Liabilities, Capital, and Cash exist idempotently.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                DatabaseSeeder.seedPhase3Defaults(db)
            }
        }

        /**
         * v6 → v7: Phase 4 Ledger Contacts
         * Adds 'aliases' column to ledger_entries and backfills it with lowercase contact_name.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `ledger_entries` ADD COLUMN `aliases` TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE `ledger_entries` SET `aliases` = LOWER(TRIM(`contact_name`))")
            }
        }
    }
}

