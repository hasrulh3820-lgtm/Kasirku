package id.kasirku.umkm.domain

import kotlin.math.min

/** Satu nota utang yang belum lunas. Daftar diurutkan dari yang paling lama. */
data class OpenDebt(val saleId: Long, val total: Long, val paid: Long) {
    val remaining: Long get() = (total - paid).coerceAtLeast(0)
}

data class Allocation(val saleId: Long, val amount: Long, val newPaid: Long, val newStatus: String)

object DebtCalc {

    fun status(total: Long, paid: Long): String = when {
        paid >= total -> SaleState.PAID
        paid > 0 -> SaleState.PARTIAL
        else -> SaleState.UNPAID
    }

    /**
     * Bagi uang cicilan ke nota paling lama dulu (FIFO).
     * Uang tidak boleh melebihi total sisa utang; tiap nota tidak pernah dibayar melebihi sisanya.
     */
    fun allocate(openOldestFirst: List<OpenDebt>, amount: Long): List<Allocation> {
        require(amount > 0) { "Jumlah bayar harus lebih dari 0." }
        val totalRemaining = openOldestFirst.sumOf { it.remaining }
        require(amount <= totalRemaining) { "Jumlah bayar melebihi sisa utang ${Money.format(totalRemaining)}." }
        var left = amount
        val out = mutableListOf<Allocation>()
        for (d in openOldestFirst) {
            if (left == 0L) break
            if (d.remaining == 0L) continue
            val pay = min(left, d.remaining)
            val newPaid = d.paid + pay
            out += Allocation(d.saleId, pay, newPaid, status(d.total, newPaid))
            left -= pay
        }
        return out
    }
}
