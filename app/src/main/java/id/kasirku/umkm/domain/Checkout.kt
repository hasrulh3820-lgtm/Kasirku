package id.kasirku.umkm.domain

import kotlin.math.max

/** Satu baris keranjang kasir. Barang yang sama dengan satuan berbeda (pcs vs dus) = baris berbeda. */
data class CartItem(
    val productId: Long,
    val name: String,
    val baseUnit: String,
    /** Satuan yang dijual (pcs / renteng / dus) dan isinya dalam satuan dasar. */
    val unitName: String,
    val factor: Double,
    /** Harga jual & modal per satuan yang dijual. */
    val sellPrice: Long,
    val costPerUnit: Long,
    val qty: Double,
    val discount: Long = 0,
) {
    val key: String get() = "$productId:$unitName"
    val baseQty: Double get() = UnitCalc.toBase(qty, factor)
    fun toLine() = CartLine(sellPrice = sellPrice, costPrice = costPerUnit, qty = qty, discount = discount)
    val lineTotal: Long get() = SaleCalc.lineTotal(toLine())
}

data class Shortage(val productId: Long, val name: String, val baseUnit: String, val need: Double, val stock: Double)

object PayMethod {
    const val TUNAI = "TUNAI"
    const val QRIS = "QRIS"
    const val TRANSFER = "TRANSFER"
    const val UTANG = "UTANG"

    fun label(m: String) = when (m) {
        TUNAI -> "Tunai"
        QRIS -> "QRIS"
        TRANSFER -> "Transfer"
        UTANG -> "Utang"
        else -> m
    }
}

object SaleState {
    const val PAID = "PAID"
    const val PARTIAL = "PARTIAL"
    const val UNPAID = "UNPAID"
    const val VOID = "VOID"

    fun label(s: String) = when (s) {
        PAID -> "Lunas"
        PARTIAL -> "Bayar sebagian"
        UNPAID -> "Belum bayar"
        VOID -> "Dibatalkan"
        else -> s
    }
}

/**
 * Cara bayar yang dipilih kasir.
 * - Tunai / QRIS / Transfer tanpa utang: harus lunas.
 * - Utang (atau tunai kurang lalu "sisanya jadi utang"): [downPayment] = uang yang dibayar sekarang (boleh 0).
 */
data class PaymentInput(
    val method: String,
    val cashReceived: Long = 0,
    val asDebt: Boolean = false,
    val downPayment: Long = 0,
)

data class PaymentResult(
    /** Uang yang masuk ke tagihan ini sekarang (tanpa kembalian). */
    val paidAmount: Long,
    val changeAmount: Long,
    val status: String,
    /** Pembayaran awal yang dicatat sebagai cicilan pertama utang. */
    val debtPayment: Long,
    val isDebt: Boolean,
)

sealed interface PaymentCheck {
    data class Ok(val result: PaymentResult) : PaymentCheck
    data class Error(val message: String) : PaymentCheck
}

object CheckoutCalc {

    /** Barang yang stoknya tidak cukup. Satu barang bisa ada di beberapa baris (pcs + dus), jadi dijumlahkan. */
    fun shortages(items: List<CartItem>, stockByProduct: Map<Long, Double>): List<Shortage> =
        items.groupBy { it.productId }.mapNotNull { (id, lines) ->
            val need = Qty.round(lines.sumOf { it.baseQty })
            val stock = stockByProduct[id] ?: 0.0
            if (need > stock + 1e-9) Shortage(id, lines.first().name, lines.first().baseUnit, need, stock) else null
        }

    fun resolvePayment(total: Long, input: PaymentInput): PaymentCheck {
        if (total < 0) return PaymentCheck.Error("Total tidak valid.")
        val debt = input.method == PayMethod.UTANG || input.asDebt
        if (debt) {
            val dp = if (input.method == PayMethod.UTANG) input.downPayment else input.cashReceived
            if (dp < 0) return PaymentCheck.Error("Uang muka tidak boleh minus.")
            if (dp >= total) {
                // Ternyata lunas: perlakukan sebagai pembayaran tunai biasa.
                return PaymentCheck.Ok(PaymentResult(total, dp - total, SaleState.PAID, 0, isDebt = false))
            }
            val status = if (dp > 0) SaleState.PARTIAL else SaleState.UNPAID
            return PaymentCheck.Ok(PaymentResult(dp, 0, status, dp, isDebt = true))
        }
        return when (input.method) {
            PayMethod.TUNAI -> {
                if (input.cashReceived < total) {
                    PaymentCheck.Error("Uang diterima kurang ${Money.format(total - input.cashReceived)}.")
                } else {
                    PaymentCheck.Ok(PaymentResult(total, input.cashReceived - total, SaleState.PAID, 0, isDebt = false))
                }
            }
            PayMethod.QRIS, PayMethod.TRANSFER -> PaymentCheck.Ok(PaymentResult(total, 0, SaleState.PAID, 0, isDebt = false))
            else -> PaymentCheck.Error("Metode pembayaran tidak dikenal.")
        }
    }

    /** Usulan nominal uang tunai: uang pas lalu pecahan umum di atas total. */
    fun cashSuggestions(total: Long): List<Long> {
        if (total <= 0) return emptyList()
        val out = linkedSetOf(total)
        for (step in listOf(5_000L, 10_000L, 20_000L, 50_000L, 100_000L)) {
            val up = ((total + step - 1) / step) * step
            if (up > total) out += up
        }
        return out.sorted().take(5)
    }

    fun invoiceNo(datePart: String, seq: Int): String = "INV-$datePart-${seq.toString().padStart(3, '0')}"
}

/** Struk teks lebar 32 karakter (cocok untuk WhatsApp dan printer thermal 58 mm). */
object ReceiptFormatter {
    const val WIDTH = 32

    data class Header(val storeName: String, val address: String, val phone: String, val footer: String)
    data class Line(val name: String, val qty: Double, val unit: String, val price: Long, val discount: Long, val total: Long)
    data class Body(
        val invoiceNo: String,
        val dateTime: String,
        val customer: String?,
        val lines: List<Line>,
        val subtotal: Long,
        val itemDiscount: Long,
        val transDiscount: Long,
        val total: Long,
        val method: String,
        val paidNow: Long,
        val change: Long,
        val status: String,
        val remainingDebt: Long,
    )

    private fun num(v: Long) = Money.format(v).replace("Rp ", "")

    fun lr(left: String, right: String, width: Int = WIDTH): String {
        val space = width - left.length - right.length
        return if (space >= 1) left + " ".repeat(space) + right else left + "\n" + " ".repeat(max(0, width - right.length)) + right
    }

    fun center(text: String, width: Int = WIDTH): String =
        text.chunked(width).joinToString("\n") { " ".repeat(max(0, (width - it.length) / 2)) + it }

    fun build(h: Header, b: Body): String {
        val sep = "-".repeat(WIDTH)
        val sb = StringBuilder()
        sb.appendLine(center(h.storeName.uppercase()))
        if (h.address.isNotBlank()) sb.appendLine(center(h.address))
        if (h.phone.isNotBlank()) sb.appendLine(center(h.phone))
        sb.appendLine(sep)
        sb.appendLine(b.invoiceNo)
        sb.appendLine(b.dateTime)
        if (!b.customer.isNullOrBlank()) sb.appendLine("Pelanggan: ${b.customer}")
        if (b.status == SaleState.VOID) sb.appendLine(center("*** DIBATALKAN ***"))
        sb.appendLine(sep)
        for (l in b.lines) {
            l.name.chunked(WIDTH).forEach { sb.appendLine(it) }
            sb.appendLine(lr("  ${Qty.format(l.qty)} ${l.unit} x ${num(l.price)}", num(l.total + l.discount)))
            if (l.discount > 0) sb.appendLine(lr("  Diskon", "-" + num(l.discount)))
        }
        sb.appendLine(sep)
        if (b.itemDiscount > 0 || b.transDiscount > 0) {
            sb.appendLine(lr("Subtotal", num(b.subtotal)))
            val disc = b.itemDiscount + b.transDiscount
            sb.appendLine(lr("Diskon", "-" + num(disc)))
        }
        sb.appendLine(lr("TOTAL", Money.format(b.total)))
        when {
            b.method == PayMethod.UTANG || b.status == SaleState.PARTIAL || b.status == SaleState.UNPAID -> {
                sb.appendLine(lr("Dibayar", num(b.paidNow)))
                sb.appendLine(lr("Sisa utang", num(b.remainingDebt)))
            }
            b.method == PayMethod.TUNAI -> {
                sb.appendLine(lr("Tunai", num(b.paidNow + b.change)))
                sb.appendLine(lr("Kembalian", num(b.change)))
            }
            else -> sb.appendLine(lr(PayMethod.label(b.method), num(b.paidNow)))
        }
        sb.appendLine(sep)
        if (h.footer.isNotBlank()) sb.appendLine(center(h.footer))
        return sb.toString().trimEnd()
    }
}
