package id.kasirku.umkm.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckoutTest {

    private fun item(unit: String, factor: Double, price: Long, costBase: Long, qty: Double, discount: Long = 0) = CartItem(
        productId = 1, name = "Indomie Goreng", baseUnit = "pcs", unitName = unit, factor = factor,
        sellPrice = price, costPerUnit = UnitCalc.costPerUnit(costBase, factor), qty = qty, discount = discount,
    )

    @Test
    fun mixedUnitsTotalsAndProfit() {
        // 3 pcs @3.500 + 1 dus (40 pcs) @125.000, modal 3.000/pcs
        val cart = listOf(item("pcs", 1.0, 3_500, 3_000, 3.0), item("dus", 40.0, 125_000, 3_000, 1.0))
        val t = SaleCalc.totals(cart.map { it.toLine() })
        assertEquals(135_500L, t.total)
        assertEquals(129_000L, t.costTotal) // 9.000 + 120.000
        assertEquals(6_500L, t.total - t.costTotal)
        assertEquals(43.0, cart.sumOf { it.baseQty }, 0.0)
    }

    @Test
    fun shortageSumsAllUnitsOfSameProduct() {
        val cart = listOf(item("pcs", 1.0, 3_500, 3_000, 5.0), item("dus", 40.0, 125_000, 3_000, 1.0))
        val s = CheckoutCalc.shortages(cart, mapOf(1L to 44.0))
        assertEquals(1, s.size)
        assertEquals(45.0, s[0].need, 0.0)
        assertTrue(CheckoutCalc.shortages(cart, mapOf(1L to 45.0)).isEmpty())
    }

    @Test
    fun cashPayment() {
        val r = (CheckoutCalc.resolvePayment(19_000, PaymentInput(PayMethod.TUNAI, cashReceived = 20_000)) as PaymentCheck.Ok).result
        assertEquals(19_000L, r.paidAmount)
        assertEquals(1_000L, r.changeAmount)
        assertEquals(SaleState.PAID, r.status)
        assertFalse(r.isDebt)
        assertTrue(CheckoutCalc.resolvePayment(19_000, PaymentInput(PayMethod.TUNAI, cashReceived = 10_000)) is PaymentCheck.Error)
    }

    @Test
    fun qrisIsExact() {
        val r = (CheckoutCalc.resolvePayment(19_000, PaymentInput(PayMethod.QRIS)) as PaymentCheck.Ok).result
        assertEquals(19_000L, r.paidAmount)
        assertEquals(0L, r.changeAmount)
    }

    @Test
    fun debtWithDownPayment() {
        val r = (CheckoutCalc.resolvePayment(100_000, PaymentInput(PayMethod.UTANG, downPayment = 30_000)) as PaymentCheck.Ok).result
        assertEquals(SaleState.PARTIAL, r.status)
        assertEquals(30_000L, r.paidAmount)
        assertEquals(30_000L, r.debtPayment)
        assertTrue(r.isDebt)
        val none = (CheckoutCalc.resolvePayment(100_000, PaymentInput(PayMethod.UTANG)) as PaymentCheck.Ok).result
        assertEquals(SaleState.UNPAID, none.status)
        assertEquals(0L, none.debtPayment)
    }

    @Test
    fun cashShortBecomesDebt() {
        val r = (CheckoutCalc.resolvePayment(100_000, PaymentInput(PayMethod.TUNAI, cashReceived = 50_000, asDebt = true)) as PaymentCheck.Ok).result
        assertEquals(SaleState.PARTIAL, r.status)
        assertEquals(50_000L, r.debtPayment)
    }

    @Test
    fun debtFullyPaidIsNotDebt() {
        val r = (CheckoutCalc.resolvePayment(100_000, PaymentInput(PayMethod.UTANG, downPayment = 100_000)) as PaymentCheck.Ok).result
        assertEquals(SaleState.PAID, r.status)
        assertFalse(r.isDebt)
    }

    @Test
    fun cashSuggestions() {
        assertEquals(listOf(19_000L, 20_000L, 50_000L, 100_000L), CheckoutCalc.cashSuggestions(19_000))
        assertEquals(listOf(50_000L, 60_000L, 100_000L), CheckoutCalc.cashSuggestions(50_000))
        assertTrue(CheckoutCalc.cashSuggestions(0).isEmpty())
    }

    @Test
    fun invoiceNumber() {
        assertEquals("INV-261016-007", CheckoutCalc.invoiceNo("261016", 7))
    }

    @Test
    fun receiptLayout() {
        val text = ReceiptFormatter.build(
            ReceiptFormatter.Header("Toko Berkah", "Jl. Poros", "0812", "Terima kasih"),
            ReceiptFormatter.Body(
                invoiceNo = "INV-261016-001", dateTime = "16 Okt 2026 08:30", customer = null,
                lines = listOf(ReceiptFormatter.Line("Indomie Goreng", 2.0, "pcs", 3_500, 500, 6_500)),
                subtotal = 7_000, itemDiscount = 500, transDiscount = 0, total = 6_500,
                method = PayMethod.TUNAI, paidNow = 6_500, change = 3_500, status = SaleState.PAID, remainingDebt = 0,
            ),
        )
        assertTrue(text.contains("TOKO BERKAH"))
        assertTrue(text.contains("Kembalian"))
        assertTrue(text.lines().all { it.length <= ReceiptFormatter.WIDTH })
        assertTrue(text.contains("TOTAL") && text.contains("Rp 6.500"))
    }
}
