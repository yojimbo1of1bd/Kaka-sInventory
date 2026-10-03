package com.projectkaka.inventory.data.local

import androidx.room.TypeConverter
import com.projectkaka.inventory.data.local.entity.AccountType
import com.projectkaka.inventory.data.local.entity.CategoryType
import com.projectkaka.inventory.data.local.entity.JournalStatus
import com.projectkaka.inventory.data.local.entity.ApprovalStatus
import com.projectkaka.inventory.data.local.entity.ItemStatus
import com.projectkaka.inventory.data.local.entity.LedgerType

class Converters {

    // ── JournalStatus ───────────────────────────────────────────────────

    @TypeConverter
    fun fromJournalStatus(status: JournalStatus): String = status.name

    @TypeConverter
    fun toJournalStatus(value: String): JournalStatus =
        runCatching { JournalStatus.valueOf(value) }.getOrDefault(JournalStatus.POSTED)

    // ── ApprovalStatus ──────────────────────────────────────────────────

    @TypeConverter
    fun fromApprovalStatus(status: ApprovalStatus): String = status.name

    @TypeConverter
    fun toApprovalStatus(value: String): ApprovalStatus =
        runCatching { ApprovalStatus.valueOf(value) }.getOrDefault(ApprovalStatus.APPROVED)

    // ── ItemStatus (existing) ───────────────────────────────────────────

    @TypeConverter
    fun fromStatus(status: ItemStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): ItemStatus =
        runCatching { ItemStatus.valueOf(value) }.getOrDefault(ItemStatus.ACTIVE)

    // ── AccountType ─────────────────────────────────────────────────────

    @TypeConverter
    fun fromAccountType(type: AccountType): String = type.name

    @TypeConverter
    fun toAccountType(value: String): AccountType =
        try {
            AccountType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Unknown AccountType: $value")
        }

    // ── CategoryType ────────────────────────────────────────────────────

    @TypeConverter
    fun fromCategoryType(type: CategoryType): String = type.name

    @TypeConverter
    fun toCategoryType(value: String): CategoryType =
        runCatching { CategoryType.valueOf(value) }.getOrDefault(CategoryType.EXPENSE)

    // ── LedgerType ──────────────────────────────────────────────────────

    @TypeConverter
    fun fromLedgerType(type: LedgerType): String = type.name

    @TypeConverter
    fun toLedgerType(value: String): LedgerType =
        runCatching { LedgerType.valueOf(value) }.getOrDefault(LedgerType.PAYABLE)

    // ── Money ───────────────────────────────────────────────────────────

    @TypeConverter
    fun fromMoney(money: com.projectkaka.inventory.model.Money): Long = money.minorUnits

    @TypeConverter
    fun toMoney(value: Long): com.projectkaka.inventory.model.Money = com.projectkaka.inventory.model.Money(value)
}
