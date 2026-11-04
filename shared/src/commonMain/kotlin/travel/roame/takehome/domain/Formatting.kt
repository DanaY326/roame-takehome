package travel.roame.takehome.domain

/** Shared display formatting so both apps render numbers identically. */
object Formatting {
    /** 39000 -> "39,000" */
    fun groupedNumber(value: Int): String {
        val digits = kotlin.math.abs(value.toLong()).toString()
        val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
        return if (value < 0) "-$grouped" else grouped
    }

    /** 39000 -> "39,000 pts" */
    fun points(value: Int): String = "${groupedNumber(value)} pts"

    /** 39000 -> "39k", 101500 -> "101.5k", 900 -> "900" */
    fun compactPoints(value: Int): String {
        if (value < 1000) return value.toString()
        val whole = value / 1000
        val tenth = (value % 1000) / 100
        return if (tenth == 0) "${whole}k" else "$whole.${tenth}k"
    }

    /** 1 -> "1 flight", 3 -> "3 flights" */
    fun flightCount(count: Int): String = if (count == 1) "1 flight" else "${groupedNumber(count)} flights"
}
