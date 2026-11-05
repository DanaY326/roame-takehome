package travel.roame.takehome

import travel.roame.takehome.domain.ActiveFilters
import travel.roame.takehome.domain.FilterKind
import travel.roame.takehome.domain.FilterOptions
import travel.roame.takehome.domain.FlightDates
import travel.roame.takehome.domain.FlightFilter
import travel.roame.takehome.domain.Formatting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FormattingTest {
    @Test
    fun datesRoundTripThroughEpochDays() {
        assertEquals(0L, FlightDates.epochDay("1970-01-01"))
        for (iso in listOf("2027-02-01", "2027-02-28", "2027-03-01", "2027-04-01", "2028-02-29", "1969-12-31")) {
            assertEquals(iso, FlightDates.fromEpochDay(FlightDates.epochDay(iso)!!))
            assertEquals(iso, FlightDates.fromUtcEpochMillis(FlightDates.toUtcEpochMillis(iso)!!))
        }
        assertEquals(1L, FlightDates.epochDay("2027-03-01")!! - FlightDates.epochDay("2027-02-28")!!)
    }

    @Test
    fun invalidDatesAreRejected() {
        assertFalse(FlightDates.isValid("2027-02-29"))
        assertFalse(FlightDates.isValid("2027-13-01"))
        assertFalse(FlightDates.isValid("27-02-01"))
        assertNull(FlightDates.epochDay("nope"))
    }

    @Test
    fun formatsDates() {
        assertEquals("Mon, Feb 1, 2027", FlightDates.formatLong("2027-02-01"))
        assertEquals("Thu, Apr 1, 2027", FlightDates.formatLong("2027-04-01"))
        assertEquals("Sun, Feb 28", FlightDates.formatMedium("2027-02-28"))
        assertEquals("Feb 1 – Apr 1, 2027", FlightDates.formatRange("2027-02-01", "2027-04-01"))
    }

    @Test
    fun formatsPoints() {
        assertEquals("39,000 pts", Formatting.points(39_000))
        assertEquals("1,000,000", Formatting.groupedNumber(1_000_000))
        assertEquals("101.5k", Formatting.compactPoints(101_500))
        assertEquals("9k", Formatting.compactPoints(9_000))
        assertEquals("1 flight", Formatting.flightCount(1))
    }

    @Test
    fun fullCatalogDateRangeIsNotAnActiveFilter() {
        val options = FilterOptions.from(listOf(flight("a", date = "2027-02-01"), flight("b", date = "2027-04-01")))
        assertFalse(ActiveFilters.isDateRangeActive(FlightFilter(), options))
        assertFalse(ActiveFilters.isDateRangeActive(FlightFilter(startDate = "2027-02-01", endDate = "2027-04-01"), options))
        assertTrue(ActiveFilters.isDateRangeActive(FlightFilter(startDate = "2027-02-02"), options))

        val active = ActiveFilters.describe(
            FlightFilter(originQuery = " bos ", endDate = "2027-03-01", cabin = "Business", maxPoints = 50_000, programs = setOf("Avios")),
            options,
        )
        assertEquals(
            listOf(FilterKind.ORIGIN, FilterKind.DATES, FilterKind.CABIN, FilterKind.MAX_POINTS, FilterKind.PROGRAMS),
            active.map { it.kind },
        )
        assertEquals("From: bos", active[0].label)
        assertEquals("Feb 1 – Mar 1, 2027", active[1].label)
        assertEquals("≤ 50,000 pts", active[3].label)
    }
}
