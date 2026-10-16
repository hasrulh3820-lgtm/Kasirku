package id.kasirku.umkm.domain

import id.kasirku.umkm.data.db.entity.Sale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportCalcTest {

    private fun sale(id: Long, at: Long, total: Long, cost: Long, status: String = SaleState.PAID, disc: Long = 0) = Sale(
        id = id, invoiceNo = "INV-$id", clientRequestId = "r$id", createdAt = at, subtotal = total + disc,
        itemDiscountTotal = disc, transDiscount = 0, total = total, costTotal = cost, paidAmount = total,
        changeAmount = 0, paymentMethod = PayMethod.TUNAI, status = status,
    )

    private val day = 86_400_000L
    private val sales = listOf(
        sale(1, 0, 10_000, 8_000),
        sale(2, 1_000, 20_000, 15_000, disc = 500),
        sale(3, day, 5_000, 4_000),
        sale(4, day + 10, 99_000, 1, status = SaleState.VOID),
    )

    @Test
    fun summaryExcludesVoid() {
        val s = ReportCalc.summarize(sales)
        assertEquals(35_000L, s.omzet)
        assertEquals(27_000L, s.hpp)
        assertEquals(8_000L, s.grossProfit)
        assertEquals(3, s.trxCount)
        assertEquals(1, s.voidCount)
        assertEquals(500L, s.discountTotal)
        assertEquals(11_666L, s.average)
    }

    @Test
    fun byDayNewestFirst() {
        val rows = ReportCalc.byDay(sales) { if (it < day) "2026-10-15" else "2026-10-16" }
        assertEquals(listOf("2026-10-16", "2026-10-15"), rows.map { it.dayKey })
        assertEquals(DayRow("2026-10-16", 5_000, 1_000, 1), rows[0])
        assertEquals(DayRow("2026-10-15", 30_000, 7_000, 2), rows[1])
    }

    @Test
    fun mergeMethods() {
        val m = ReportCalc.mergeByMethod(listOf("TUNAI" to 30_000L, "QRIS" to 5_000L), listOf("TUNAI" to 10_000L))
        assertEquals(listOf("TUNAI" to 40_000L, "QRIS" to 5_000L), m)
    }

    @Test
    fun csvEscaping() {
        assertEquals("abc", Csv.cell("abc"))
        assertEquals("\"a;b\"", Csv.cell("a;b"))
        assertEquals("\"say \"\"hi\"\"\"", Csv.cell("say \"hi\""))
        val text = Csv.build(listOf("Nama", "Harga"), listOf(listOf("Kopi; Sachet", 1500L)))
        assertTrue(text.startsWith("﻿Nama;Harga\r\n"))
        assertTrue(text.contains("\"Kopi; Sachet\";1500\r\n"))
    }
}
