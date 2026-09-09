package com.example.util

import java.text.Normalizer
import java.util.Locale

object SearchUtils {

    private val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val NON_ALPHANUMERIC_SPACE_REGEX = Regex("[^a-z0-9\\s]")
    private val MULTI_SPACE_REGEX = Regex("\\s+")

    /**
     * Normalizes a string for search comparisons:
     * - Removes accents and diacritics (e.g., "São Paulo" -> "sao paulo", "Ação" -> "acao")
     * - Converts to lowercase
     * - Replaces special symbols and punctuation with spaces
     * - Trims and reduces consecutive spaces
     */
    fun normalize(text: String?): String {
        if (text.isNullOrBlank()) return ""
        val nfd = Normalizer.normalize(text, Normalizer.Form.NFD)
        val withoutDiacritics = DIACRITICS_REGEX.replace(nfd, "").lowercase(Locale.ROOT)
        val cleaned = NON_ALPHANUMERIC_SPACE_REGEX.replace(withoutDiacritics, " ")
        return MULTI_SPACE_REGEX.replace(cleaned, " ").trim()
    }

    /**
     * Compact representation without spaces or special characters (e.g., "Spider-Man" -> "spiderman", "ESPN 4" -> "espn4").
     */
    fun toCompact(text: String?): String {
        return normalize(text).replace(" ", "")
    }

    /**
     * Evaluates if [candidate] matches the [query]:
     * 1. Direct normalized substring match
     * 2. Compact match (ignoring spaces & punctuation)
     * 3. Multi-token association (all words from the query match anywhere in the candidate)
     */
    fun matches(candidate: String?, query: String?): Boolean {
        if (query.isNullOrBlank()) return true
        if (candidate.isNullOrBlank()) return false

        val normCandidate = normalize(candidate)
        val normQuery = normalize(query)
        if (normQuery.isEmpty()) return true

        // 1. Direct normalized match
        if (normCandidate.contains(normQuery)) return true

        // 2. Compact match without spaces (e.g., "espn4" matches "ESPN 4")
        val compactCandidate = normCandidate.replace(" ", "")
        val compactQuery = normQuery.replace(" ", "")
        if (compactCandidate.isNotEmpty() && compactCandidate.contains(compactQuery)) return true

        // 3. Multi-token association: all words in query are present in the candidate
        val tokens = normQuery.split(" ").filter { it.isNotBlank() }
        if (tokens.size > 1) {
            val allTokensFound = tokens.all { token ->
                normCandidate.contains(token) || compactCandidate.contains(token)
            }
            if (allTokensFound) return true
        }

        return false
    }

    /**
     * Checks if any of the provided candidate fields match the query,
     * OR if the combined fields contain all query tokens (association across fields,
     * e.g., "batman acao" matching title="Batman" and category="Ação").
     */
    fun matchesCombined(query: String?, vararg fields: String?): Boolean {
        if (query.isNullOrBlank()) return true
        val validFields = fields.filterNotNull().filter { it.isNotBlank() }
        if (validFields.isEmpty()) return false

        // Individual field match
        if (validFields.any { matches(it, query) }) return true

        // Cross-field token association (e.g. searching "Globo SP" across title & category/subtitle)
        val combined = validFields.joinToString(" ")
        return matches(combined, query)
    }
}
