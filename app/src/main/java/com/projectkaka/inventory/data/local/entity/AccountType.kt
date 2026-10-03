package com.projectkaka.inventory.data.local.entity

/**
 * Classifies an account for balance-sheet grouping and daily-budget filtering.
 *
 * The daily budget engine only sums [CASH] accounts; [ASSET] and [CAPITAL]
 * are explicitly excluded, matching the project requirement.
 */
enum class AccountType {
    /** Liquid spending money: Cash, bKash, Nagad.  Included in daily budget. */
    CASH,

    /** Physical assets or investments.  Excluded from daily budget. */
    ASSET,

    /** Debts: credit cards, loans.  Excluded from daily budget. */
    LIABILITY,

    /** Owner equity / savings set aside.  Excluded from daily budget. */
    CAPITAL,

    /** Income / Revenue. Excluded from daily budget. */
    REVENUE,

    /** Expenses / Costs. Excluded from daily budget. */
    EXPENSE
}
