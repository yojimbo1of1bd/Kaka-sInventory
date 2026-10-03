package com.projectkaka.inventory.util

object SearchHelper {
    /**
     * Unified fuzzy-matching logic across all entities.
     * Returns true if [input] (lowercased) is a substring of [primaryName] (lowercased),
     * or if it exactly matches any comma-separated token in [aliases].
     */
    fun matchesAlias(input: String, primaryName: String, aliases: String): Boolean {
        val lower = input.lowercase().trim()
        if (primaryName.lowercase().contains(lower)) return true
        return aliases.split(",")
            .map { it.trim().lowercase() }
            .any { it.isNotEmpty() && it == lower }
    }

    /**
     * Strict matching for the terminal.
     * Requires the input to exactly match either the primary name or one of the aliases.
     */
    fun matchesExact(input: String, primaryName: String, aliases: String): Boolean {
        val lower = input.lowercase().trim()
        if (primaryName.lowercase().trim() == lower) return true
        return aliases.split(",")
            .map { it.trim().lowercase() }
            .any { it.isNotEmpty() && it == lower }
    }

    /** Escapes SQLite LIKE metacharacters so user text cannot act as a wildcard. */
    fun escapeLike(value: String): String = value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
}
