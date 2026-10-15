package id.kasirku.umkm.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Waktu memakai zona waktu HP (mis. WITA). Rentang = [from, to) dalam epoch milidetik. */
object Waktu {
    private val locale: Locale = Locale.forLanguageTag("id-ID")
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val dateTimeFmt = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", locale)
    private val dateFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale)
    private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", locale)

    fun now(): Long = System.currentTimeMillis()

    fun todayRange(today: LocalDate = LocalDate.now(zone)): Pair<Long, Long> {
        val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return from to to
    }

    fun monthRange(today: LocalDate = LocalDate.now(zone)): Pair<Long, Long> {
        val first = today.withDayOfMonth(1)
        val from = first.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = first.plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return from to to
    }

    fun formatDateTime(epochMs: Long): String =
        dateTimeFmt.format(Instant.ofEpochMilli(epochMs).atZone(zone))

    fun todayLabel(): String = dateFmt.format(LocalDate.now(zone))

    fun monthLabel(): String = monthFmt.format(LocalDate.now(zone))
}
