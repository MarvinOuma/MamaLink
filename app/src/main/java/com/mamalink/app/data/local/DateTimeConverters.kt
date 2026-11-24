package com.mamalink.app.data.local

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

/** Birth dates have no timezone; appointment instants are stored as UTC milliseconds. */
class DateTimeConverters {
    @TypeConverter fun dateToString(value: LocalDate): String = value.toString()
    @TypeConverter fun stringToDate(value: String): LocalDate = LocalDate.parse(value)
    @TypeConverter fun instantToMillis(value: Instant): Long = value.toEpochMilli()
    @TypeConverter fun millisToInstant(value: Long): Instant = Instant.ofEpochMilli(value)
}
