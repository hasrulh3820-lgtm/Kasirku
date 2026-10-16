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

    private val invoiceFmt = DateTimeFormatter.ofPattern("yyMMdd", locale)
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", locale)

    fun invoiceDate(epochMs: Long): String = invoiceFmt.format(Instant.ofEpochMilli(epochMs).atZone(zone))
    fun formatTime(epochMs: Long): String = timeFmt.format(Instant.ofEpochMilli(epochMs).atZone(zone))

    /** Rentang [from, to) untuk n hari terakhir termasuk hari ini; offsetDays=1 berarti kemarin saja. */
    fun dayRange(offsetDays: Long): Pair<Long, Long> = todayRange(LocalDate.now(zone).minusDays(offsetDays))
    fun lastDaysRange(days: Long): Pair<Long, Long> {
        val today = LocalDate.now(zone)
        return today.minusDays(days - 1).atStartOfDay(zone).toInstant().toEpochMilli() to
            today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    private val dayKeyFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd", locale)
    private val dayLabelFmt = DateTimeFormatter.ofPattern("EEE, d MMM", locale)
    private val fileDateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm", locale)

    fun dayKey(epochMs: Long): String = dayKeyFmt.format(Instant.ofEpochMilli(epochMs).atZone(zone))
    fun dayLabel(dayKey: String): String = dayLabelFmt.format(LocalDate.parse(dayKey))
    fun fileStamp(): String = fileDateFmt.format(Instant.now().atZone(zone))

    fun lastMonthRange(): Pair<Long, Long> = monthRange(LocalDate.now(zone).minusMonths(1))

    fun formatDate(epochMs: Long): String = DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(Instant.ofEpochMilli(epochMs).atZone(zone))

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
