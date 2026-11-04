package travel.roame.takehome.domain

/**
 * Tiny, dependency-free helpers for ISO `yyyy-MM-dd` dates.
 *
 * All conversions are in UTC calendar days, which is what Android's date pickers use and
 * what the fixture's departure dates represent (local calendar dates without a time zone).
 */
object FlightDates {
    private const val MILLIS_PER_DAY = 86_400_000L
    private val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val weekdayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    fun isValid(iso: String): Boolean = parse(iso) != null

    /** Days since 1970-01-01, or null if [iso] is not a valid calendar date. */
    fun epochDay(iso: String): Long? {
        val (y, m, d) = parse(iso) ?: return null
        return daysFromCivil(y, m, d)
    }

    fun fromEpochDay(epochDay: Long): String {
        val (y, m, d) = civilFromDays(epochDay)
        return "${y.toString().padStart(4, '0')}-${m.pad2()}-${d.pad2()}"
    }

    /** UTC midnight in epoch millis — the representation Material date pickers use. */
    fun toUtcEpochMillis(iso: String): Long? = epochDay(iso)?.times(MILLIS_PER_DAY)

    fun fromUtcEpochMillis(millis: Long): String = fromEpochDay(floorDiv(millis, MILLIS_PER_DAY))

    /** "Mon, Feb 1, 2027" */
    fun formatLong(iso: String): String {
        val (y, m, d) = parse(iso) ?: return iso
        val weekday = weekdayNames[floorMod(daysFromCivil(y, m, d) + 3, 7).toInt()] // 1970-01-01 was a Thursday
        return "$weekday, ${monthNames[m - 1]} $d, $y"
    }

    /** "Mon, Feb 1" */
    fun formatMedium(iso: String): String {
        val (y, m, d) = parse(iso) ?: return iso
        val weekday = weekdayNames[floorMod(daysFromCivil(y, m, d) + 3, 7).toInt()]
        return "$weekday, ${monthNames[m - 1]} $d"
    }

    /** "Feb 1" */
    fun formatShort(iso: String): String {
        val (_, m, d) = parse(iso) ?: return iso
        return "${monthNames[m - 1]} $d"
    }

    /** "Feb 1 – Mar 3, 2027", or "Dec 30, 2026 – Jan 2, 2027" across years. */
    fun formatRange(startIso: String, endIso: String): String {
        val start = parse(startIso) ?: return "$startIso – $endIso"
        val end = parse(endIso) ?: return "$startIso – $endIso"
        return if (start.first == end.first) {
            "${formatShort(startIso)} – ${formatShort(endIso)}, ${end.first}"
        } else {
            "${formatShort(startIso)}, ${start.first} – ${formatShort(endIso)}, ${end.first}"
        }
    }

    private fun parse(iso: String): Triple<Int, Int, Int>? {
        if (iso.length != 10 || iso[4] != '-' || iso[7] != '-') return null
        val y = iso.substring(0, 4).toIntOrNull() ?: return null
        val m = iso.substring(5, 7).toIntOrNull() ?: return null
        val d = iso.substring(8, 10).toIntOrNull() ?: return null
        if (m !in 1..12 || d < 1 || d > daysInMonth(y, m)) return null
        return Triple(y, m, d)
    }

    private fun daysInMonth(y: Int, m: Int): Int = when (m) {
        2 -> if ((y % 4 == 0 && y % 100 != 0) || y % 400 == 0) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    // Howard Hinnant's civil-date algorithms.
    private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = (if (month <= 2) year - 1 else year).toLong()
        val era = floorDiv(y, 400)
        val yoe = y - era * 400
        val mp = (month + 9) % 12
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097 + doe - 719_468
    }

    private fun civilFromDays(days: Long): Triple<Int, Int, Int> {
        val z = days + 719_468
        val era = floorDiv(z, 146_097)
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
        val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
        val y = (yoe + era * 400 + if (m <= 2) 1 else 0).toInt()
        return Triple(y, m, d)
    }

    private fun floorDiv(a: Long, b: Long): Long {
        val q = a / b
        return if (a % b != 0L && (a < 0) != (b < 0)) q - 1 else q
    }
    private fun floorMod(a: Long, b: Long): Long = a - floorDiv(a, b) * b
    private fun Int.pad2() = toString().padStart(2, '0')
}
