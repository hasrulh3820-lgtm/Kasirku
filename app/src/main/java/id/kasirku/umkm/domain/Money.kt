package id.kasirku.umkm.domain

import kotlin.math.abs
import kotlin.math.roundToLong

/** Semua perhitungan uang memakai Long (Rupiah utuh) agar tidak ada selisih pembulatan desimal. */
object Money {

    /** 15000 -> "Rp 15.000", -2500 -> "-Rp 2.500" */
    fun format(amount: Long): String {
        val digits = abs(amount).toString()
        val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
        return if (amount < 0) "-Rp $grouped" else "Rp $grouped"
    }

    /** Menerima "15.000", "15000", "Rp 15.000". Kosong atau tidak masuk akal -> null. */
    fun parse(text: String): Long? {
        val digits = text.filter { it.isDigit() }
        if (digits.isEmpty() || digits.length > 13) return null
        return digits.toLong()
    }

    /** Modal rata-rata tertimbang setelah barang masuk. */
    fun weightedAverageCost(oldQty: Double, oldCost: Long, inQty: Double, inCost: Long): Long {
        if (inQty <= 0.0) return oldCost
        if (oldQty <= 0.0) return inCost
        return ((oldQty * oldCost + inQty * inCost) / (oldQty + inQty)).roundToLong()
    }
}

object Qty {

    /** Bulatkan ke 3 angka di belakang koma (cukup untuk kg/liter). */
    fun round(value: Double): Double = (value * 1000.0).roundToLong() / 1000.0

    /** 2.0 -> "2", 1.5 -> "1,5", 0.25 -> "0,25" */
    fun format(value: Double): String {
        val r = round(value)
        if (r == r.toLong().toDouble()) return r.toLong().toString()
        return r.toString().trimEnd('0').trimEnd('.').replace('.', ',')
    }

    /** Menerima "2", "1,5", "1.5". Tidak valid -> null. */
    fun parse(text: String): Double? {
        val cleaned = text.trim().replace(',', '.')
        if (cleaned.isEmpty()) return null
        val v = cleaned.toDoubleOrNull() ?: return null
        if (v.isNaN() || v.isInfinite()) return null
        return round(v)
    }
}
