package id.kasirku.umkm.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DebtCalcTest {

    private val open = listOf(
        OpenDebt(saleId = 1, total = 100_000, paid = 30_000), // sisa 70.000 (paling lama)
        OpenDebt(saleId = 2, total = 50_000, paid = 0), // sisa 50.000
    )

    @Test
    fun paysOldestFirst() {
        val a = DebtCalc.allocate(open, 80_000)
        assertEquals(2, a.size)
        assertEquals(Allocation(1, 70_000, 100_000, SaleState.PAID), a[0])
        assertEquals(Allocation(2, 10_000, 10_000, SaleState.PARTIAL), a[1])
    }

    @Test
    fun partialOnFirstNote() {
        val a = DebtCalc.allocate(open, 20_000)
        assertEquals(listOf(Allocation(1, 20_000, 50_000, SaleState.PARTIAL)), a)
    }

    @Test
    fun payAllSettlesEverything() {
        val a = DebtCalc.allocate(open, 120_000)
        assertEquals(listOf(SaleState.PAID, SaleState.PAID), a.map { it.newStatus })
        assertEquals(120_000L, a.sumOf { it.amount })
    }

    @Test
    fun overpaymentRejected() {
        assertThrows(IllegalArgumentException::class.java) { DebtCalc.allocate(open, 120_001) }
        assertThrows(IllegalArgumentException::class.java) { DebtCalc.allocate(open, 0) }
    }

    @Test
    fun statusRules() {
        assertEquals(SaleState.UNPAID, DebtCalc.status(10_000, 0))
        assertEquals(SaleState.PARTIAL, DebtCalc.status(10_000, 1))
        assertEquals(SaleState.PAID, DebtCalc.status(10_000, 10_000))
    }
}
