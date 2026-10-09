package com.ascon.core.data.room

import androidx.room.TypeConverter
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/** Column types Room does not know. */
internal class Converters {
    @TypeConverter
    fun fromDecimal(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()

    @TypeConverter
    fun toDecimal(value: String): BigDecimal = BigDecimal(value)

    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun fromDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    // Titles never hold a line break, so one separates them.
    @TypeConverter
    fun fromTitles(value: List<String>): String = value.joinToString("\n")

    @TypeConverter
    fun toTitles(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split("\n")
}
