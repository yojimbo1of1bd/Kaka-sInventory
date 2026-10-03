package com.projectkaka.inventory.data.local.entity

/** Direction of a ledger entry for tracking personal debts and dues. */
enum class LedgerType {
    /** Someone owes me money. */
    RECEIVABLE,

    /** I owe someone money. */
    PAYABLE
}
