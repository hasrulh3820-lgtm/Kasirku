package id.kasirku.umkm.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun formatRupiah() {
        assertEquals("Rp 0", Money.format(0))
        assertEquals("Rp 500", Money.format(500))
        assertEquals("Rp 15.000", Money.format(15_000))
        assertEquals("Rp 1.250.000", Money.format(1_250_000))
        assertEquals("-Rp 2.500", Money.format(-2_500))
    }

    @Test
    fun parseRupiah() {
        assertEquals(15_000L, Money.parse("15.000"))
        assertEquals(15_000L, Money.parse("Rp 15.000"))
        assertEquals(7L, Money.parse("7"))
        assertNull(Money.parse(""))
        assertNull(Money.parse("abc"))
    }

    @Test
    fun weightedAverageCost() {
        // 10 pcs @3.000 + 10 pcs @4.000 -> 3.500
        assertEquals(3_500L, Money.weightedAverageCost(10.0, 3_000, 10.0, 4_000))
        // stok kosong -> pakai harga beli baru
        assertEquals(4_000L, Money.weightedAverageCost(0.0, 3_000, 5.0, 4_000))
        // stok negatif tidak ikut dihitung
        assertEquals(4_000L, Money.weightedAverageCost(-2.0, 3_000, 5.0, 4_000))
        // 2 pembelian berturut-turut: 10@3.000, +5@6.000 -> 4.000; +5@2.000 -> 3.500
        val c1 = Money.weightedAverageCost(10.0, 3_000, 5.0, 6_000)
        assertEquals(4_000L, c1)
        assertEquals(3_500L, Money.weightedAverageCost(15.0, c1, 5.0, 2_000))
    }

    @Test
    fun qtyFormatAndParse() {
        assertEquals("2", Qty.format(2.0))
        assertEquals("1,5", Qty.format(1.5))
        assertEquals("0,25", Qty.format(0.25))
        assertEquals(1.5, Qty.parse("1,5")!!, 0.0)
        assertEquals(0.333, Qty.parse("0.3333")!!, 0.0)
        assertNull(Qty.parse("x"))
    }
}

class SaleCalcTest {

    @Test
    fun noDiscount() {
        val t = SaleCalc.totals(
            listOf(
                CartLine(sellPrice = 3_500, costPrice = 3_000, qty = 2.0),
                CartLine(sellPrice = 12_000, costPrice = 10_000, qty = 1.0),
            ),
        )
        assertEquals(19_000L, t.subtotal)
        assertEquals(19_000L, t.total)
        assertEquals(16_000L, t.costTotal)
    }

    @Test
    fun itemAndTransactionDiscount() {
        val t = SaleCalc.totals(
            listOf(
                CartLine(sellPrice = 10_000, costPrice = 7_000, qty = 3.0, discount = 2_000),
                CartLine(sellPrice = 5_000, costPrice = 4_000, qty = 1.0),
            ),
            transDiscount = 1_000,
        )
        assertEquals(35_000L, t.subtotal)
        assertEquals(2_000L, t.itemDiscountTotal)
        assertEquals(1_000L, t.transDiscount)
        assertEquals(32_000L, t.total)
        assertEquals(25_000L, t.costTotal)
    }

    @Test
    fun discountNeverMakesTotalNegative() {
        val t = SaleCalc.totals(
            listOf(CartLine(sellPrice = 5_000, costPrice = 4_000, qty = 1.0, discount = 9_000)),
            transDiscount = 3_000,
        )
        assertEquals(0L, t.total)
        assertEquals(0L, t.transDiscount)
    }

    @Test
    fun weightedQtyRoundsToRupiah() {
        // 0,75 kg @ 13.333 = 9.999,75 -> 10.000
        val t = SaleCalc.totals(listOf(CartLine(sellPrice = 13_333, costPrice = 12_000, qty = 0.75)))
        assertEquals(10_000L, t.total)
        assertEquals(9_000L, t.costTotal)
    }

    @Test
    fun change() {
        assertEquals(1_000L, SaleCalc.change(19_000, 20_000))
        assertEquals(0L, SaleCalc.change(19_000, 19_000))
        assertNull(SaleCalc.change(19_000, 10_000))
    }

    @Test
    fun profitSubtractsEachCostOnce() {
        val p = ProfitSummary(omzet = 32_000, hpp = 25_000, expenses = 2_000, trxCount = 1)
        assertEquals(7_000L, p.grossProfit)
        assertEquals(5_000L, p.netProfit)
    }
}
