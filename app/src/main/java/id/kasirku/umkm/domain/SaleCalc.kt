package id.kasirku.umkm.domain

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** Satu baris keranjang. Harga & modal per satuan, diskon dalam Rupiah untuk baris itu. */
data class CartLine(
    val sellPrice: Long,
    val costPrice: Long,
    val qty: Double,
    val discount: Long = 0,
)

data class SaleTotals(
    /** Jumlah harga jual × qty sebelum diskon apa pun. */
    val subtotal: Long,
    /** Total diskon per barang yang benar-benar terpakai. */
    val itemDiscountTotal: Long,
    /** Diskon transaksi yang terpakai (tidak pernah melebihi sisa setelah diskon barang). */
    val transDiscount: Long,
    /** Yang harus dibayar pelanggan = omzet transaksi ini. */
    val total: Long,
    /** Modal barang terjual (HPP) transaksi ini. */
    val costTotal: Long,
)

object SaleCalc {

    fun grossLine(line: CartLine): Long = (line.sellPrice * line.qty).roundToLong()

    /** Diskon baris tidak boleh membuat harga baris di bawah 0. */
    fun lineTotal(line: CartLine): Long = max(0L, grossLine(line) - max(0L, line.discount))

    fun totals(lines: List<CartLine>, transDiscount: Long = 0): SaleTotals {
        val subtotal = lines.sumOf { grossLine(it) }
        val afterItemDiscount = lines.sumOf { lineTotal(it) }
        val itemDiscount = subtotal - afterItemDiscount
        val usedTransDiscount = min(max(0L, transDiscount), afterItemDiscount)
        val cost = lines.sumOf { (it.costPrice * it.qty).roundToLong() }
        return SaleTotals(
            subtotal = subtotal,
            itemDiscountTotal = itemDiscount,
            transDiscount = usedTransDiscount,
            total = afterItemDiscount - usedTransDiscount,
            costTotal = cost,
        )
    }

    /** Kembalian; null jika uang kurang. */
    fun change(total: Long, paid: Long): Long? = if (paid >= total) paid - total else null
}

/**
 * Ringkasan laba untuk satu periode.
 * Modal pembelian barang TIDAK termasuk pengeluaran: ia menjadi stok, dan baru dihitung
 * sebagai HPP saat barangnya terjual. Dengan begitu tiap biaya dikurangkan tepat satu kali.
 */
data class ProfitSummary(
    val omzet: Long,
    val hpp: Long,
    val expenses: Long,
    val trxCount: Int,
) {
    val grossProfit: Long get() = omzet - hpp
    val netProfit: Long get() = grossProfit - expenses

    companion object {
        val EMPTY = ProfitSummary(0, 0, 0, 0)
    }
}
