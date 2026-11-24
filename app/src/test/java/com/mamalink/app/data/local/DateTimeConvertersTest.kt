package com.mamalink.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.TimeZone

class DateTimeConvertersTest {
    private val converters = DateTimeConverters()

    @Test fun `birth dates and UTC instants are independent of local timezone`() {
        val original = TimeZone.getDefault()
        try {
            for (zone in listOf("Africa/Nairobi", "America/Los_Angeles", "Pacific/Kiritimati")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                val date = LocalDate.of(2024, 2, 29)
                assertEquals("2024-02-29", converters.dateToString(date))
                assertEquals(date, converters.stringToDate("2024-02-29"))
                for (millis in listOf(-1234567890123L, 0L, 1234567890123L)) {
                    val instant = Instant.ofEpochMilli(millis)
                    assertEquals(millis, converters.instantToMillis(instant))
                    assertEquals(instant, converters.millisToInstant(millis))
                }
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
