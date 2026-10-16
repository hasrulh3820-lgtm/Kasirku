package id.kasirku.umkm.domain

import id.kasirku.umkm.data.db.entity.Sale

data class SalesSummary(
    val omzet: Long,
    val hpp: Long,
    val trxCount: Int,
    val voidCount: Int,
    val discountTotal: Long,
) {
    val grossProfit: Long get() = omzet - hpp
    val average: Long get() = if (trxCount == 0) 0 else omzet / trxCount
}

data class DayRow(val dayKey: String, val omzet: Long, val profit: Long, val trxCount: Int)

object ReportCalc {

    /** Transaksi batal tidak dihitung di omzet, modal, maupun jumlah transaksi. */
    fun summarize(sales: List<Sale>): SalesSummary {
        val valid = sales.filter { it.status != SaleState.VOID }
        return SalesSummary(
            omzet = valid.sumOf { it.total },
            hpp = valid.sumOf { it.costTotal },
            trxCount = valid.size,
            voidCount = sales.size - valid.size,
            discountTotal = valid.sumOf { it.itemDiscountTotal + it.transDiscount },
        )
    }

    /** Rekap per hari, terbaru dulu. [dayKeyOf] mengubah waktu jadi kunci hari (mis. "2026-10-16") sesuai zona HP. */
    fun byDay(sales: List<Sale>, dayKeyOf: (Long) -> String): List<DayRow> =
        sales.filter { it.status != SaleState.VOID }
            .groupBy { dayKeyOf(it.createdAt) }
            .map { (day, list) -> DayRow(day, list.sumOf { it.total }, list.sumOf { it.total - it.costTotal }, list.size) }
            .sortedByDescending { it.dayKey }

    /** Gabungkan uang masuk dari beberapa sumber per cara bayar. */
    fun mergeByMethod(vararg parts: List<Pair<String, Long>>): List<Pair<String, Long>> =
        parts.flatMap { it }.groupBy({ it.first }, { it.second })
            .map { (m, v) -> m to v.sum() }
            .filter { it.second != 0L }
            .sortedByDescending { it.second }
}

/** CSV dengan pemisah titik koma (cocok untuk Excel berbahasa Indonesia; Google Sheets juga bisa membaca). */
object Csv {
    const val SEP = ';'

    fun cell(value: Any?): String {
        val s = value?.toString() ?: ""
        val needsQuote = s.any { it == SEP || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuote) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    fun build(header: List<String>, rows: List<List<Any?>>): String = buildString {
        append('﻿') // BOM: agar Excel membaca huruf dengan benar
        append(header.joinToString(SEP.toString()) { cell(it) }).append("\r\n")
        rows.forEach { r -> append(r.joinToString(SEP.toString()) { cell(it) }).append("\r\n") }
    }

    /** Angka desimal dengan koma (format Indonesia), tanpa pemisah ribuan agar mudah dihitung di Excel. */
    fun qty(v: Double): String = Qty.format(v)
}
