package id.kasirku.umkm.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UnitCalcTest {

    private val renteng = UnitOption("renteng", 10.0, 33_000)
    private val dus = UnitOption("dus", 40.0, 125_000)

    @Test
    fun breakdownThreeLevels() {
        assertEquals("4 dus + 1 renteng + 2 pcs", UnitCalc.breakdown(172.0, "pcs", listOf(renteng, dus)))
        assertEquals("1 dus", UnitCalc.breakdown(40.0, "pcs", listOf(renteng, dus)))
        assertEquals("3 renteng", UnitCalc.breakdown(30.0, "pcs", listOf(dus, renteng)))
        assertEquals("7 pcs", UnitCalc.breakdown(7.0, "pcs", listOf(renteng, dus)))
        assertEquals("0 pcs", UnitCalc.breakdown(0.0, "pcs", listOf(renteng, dus)))
        assertEquals("-3 pcs", UnitCalc.breakdown(-3.0, "pcs", listOf(dus)))
        assertEquals("12 pcs", UnitCalc.breakdown(12.0, "pcs", emptyList()))
    }

    @Test
    fun breakdownWithKg() {
        val karung = UnitOption("karung", 25.0, 300_000)
        assertEquals("2 karung + 3,5 kg", UnitCalc.breakdown(53.5, "kg", listOf(karung)))
    }

    @Test
    fun conversions() {
        assertEquals(80.0, UnitCalc.toBase(2.0, 40.0), 0.0)
        assertEquals(2_750L, UnitCalc.baseCostFrom(110_000, 40.0))
        assertEquals(110_000L, UnitCalc.costPerUnit(2_750, 40.0))
        // pembelian 5 dus @110.000 di atas stok 10 pcs @3.000 -> modal rata-rata per pcs
        val baseQtyIn = UnitCalc.toBase(5.0, 40.0)
        val baseCostIn = UnitCalc.baseCostFrom(110_000, 40.0)
        assertEquals(2_762L, Money.weightedAverageCost(10.0, 3_000, baseQtyIn, baseCostIn))
    }

    @Test
    fun validation() {
        assertNull(UnitCalc.validate("pcs", listOf(renteng, dus)))
        assertNotNull(UnitCalc.validate("pcs", listOf(UnitOption("pcs", 10.0, 1))))
        assertNotNull(UnitCalc.validate("pcs", listOf(UnitOption("dus", 1.0, 1))))
        assertNotNull(UnitCalc.validate("pcs", listOf(UnitOption("dus", 40.0, 0))))
        assertNotNull(UnitCalc.validate("pcs", listOf(UnitOption("dus", 40.0, 1), UnitOption("karton", 40.0, 1))))
        assertNotNull(UnitCalc.validate("pcs", listOf(UnitOption("", 40.0, 1))))
    }

    @Test
    fun sumPhysicalCount() {
        assertEquals(144.0, UnitCalc.sumParts(listOf(3.0 to 40.0, 2.0 to 10.0, 4.0 to 1.0)), 0.0)
        assertEquals(0.0, UnitCalc.sumParts(emptyList()), 0.0)
        assertEquals(53.5, UnitCalc.sumParts(listOf(2.0 to 25.0, 3.5 to 1.0)), 0.0)
        // hitung fisik dibanding stok sistem 172 pcs -> selisih -28
        assertEquals(-28.0, UnitCalc.sumParts(listOf(3.0 to 40.0, 2.0 to 10.0, 4.0 to 1.0)) - 172.0, 0.0)
    }
}
