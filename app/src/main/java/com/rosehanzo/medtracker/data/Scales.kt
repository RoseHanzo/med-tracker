package com.rosehanzo.medtracker.data

/**
 * Check-ins are saved as the bar position you picked (1 = left end).
 * Trends always charts "impact", where higher means worse, so some scales flip.
 */
object Scales {

    /** Number of positions on the bar. */
    fun steps(def: SymptomDef): Int = if (def.scale == SCALE_MOOD) 7 else 10

    /** Highest impact value (the top of a Trends chart). */
    fun impactMax(def: SymptomDef): Int = steps(def)

    /** Higher = worse, for charts and comparisons. */
    fun impact(def: SymptomDef, raw: Int): Double = when (def.scale) {
        SCALE_GOOD -> (11 - raw).toDouble()
        SCALE_MOOD -> (8 - raw).toDouble()
        else -> raw.toDouble()
    }

    /** 0 = fine, 1 = as bad as it gets. Drives the colour. */
    fun severity(def: SymptomDef, raw: Int): Float = when (def.scale) {
        SCALE_GOOD -> (10 - raw) / 9f
        SCALE_MOOD -> if (raw >= 4) 0f else (4 - raw) / 3f
        else -> (raw - 1) / 9f
    }.coerceIn(0f, 1f)

    private val MOOD_WORDS = listOf("Very bad", "Bad", "Low", "Normal", "Good", "Very good", "Excellent")

    /** The word shown instead of a number. */
    fun word(def: SymptomDef, raw: Int): String = when (def.scale) {
        SCALE_MOOD -> MOOD_WORDS[(raw - 1).coerceIn(0, 6)]
        SCALE_GOOD -> when (raw) {
            in 1..2 -> "Very low"
            in 3..4 -> "Low"
            in 5..6 -> "Fair"
            in 7..8 -> "Good"
            else -> "Great"
        }
        else -> when (raw) {
            1 -> "None"
            in 2..3 -> "Slight"
            in 4..5 -> "Mild"
            in 6..7 -> "Moderate"
            in 8..9 -> "Strong"
            else -> "Severe"
        }
    }

    /** Name used in Trends, where higher always means worse. */
    fun chartName(def: SymptomDef): String = if (def.scale == SCALE_IMPACT) def.name else "${def.name} (low)"

    /** Whether a change in average impact is big enough to flag in red. */
    fun bigRise(def: SymptomDef, base: Double, now: Double): Boolean =
        now - base >= if (def.scale == SCALE_MOOD) 1.0 else 1.5
}
