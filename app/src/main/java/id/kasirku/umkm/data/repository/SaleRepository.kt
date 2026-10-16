package id.kasirku.umkm.data.repository

import androidx.room.withTransaction
import id.kasirku.umkm.data.db.AppDatabase
import id.kasirku.umkm.data.db.dao.SaleRow
import id.kasirku.umkm.data.db.entity.Customer
import id.kasirku.umkm.data.db.entity.DebtPayment
import id.kasirku.umkm.data.db.entity.MovementType
import id.kasirku.umkm.data.db.entity.Sale
import id.kasirku.umkm.data.db.entity.SaleItem
import id.kasirku.umkm.data.db.entity.StockMovement
import id.kasirku.umkm.data.db.entity.StoreProfile
import id.kasirku.umkm.domain.CartItem
import id.kasirku.umkm.domain.CheckoutCalc
import id.kasirku.umkm.domain.PaymentCheck
import id.kasirku.umkm.domain.PaymentInput
import id.kasirku.umkm.domain.Qty
import id.kasirku.umkm.domain.SaleCalc
import id.kasirku.umkm.domain.SaleState
import id.kasirku.umkm.domain.Shortage
import id.kasirku.umkm.domain.UnitCalc
import id.kasirku.umkm.util.Waktu
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class CheckoutRequest(
    /** Kunci unik per keranjang. Simpan berulang dengan kunci yang sama tidak membuat transaksi ganda. */
    val requestId: String,
    val items: List<CartItem>,
    val transDiscount: Long,
    val payment: PaymentInput,
    val customerName: String,
    val customerPhone: String,
    /** true jika pemilik sudah setuju menjual walau stok di aplikasi kurang. */
    val allowNegativeStock: Boolean,
)

sealed interface CheckoutResult {
    data class Success(val saleId: Long, val alreadySaved: Boolean = false) : CheckoutResult
    data class NeedStockConfirm(val shortages: List<Shortage>) : CheckoutResult
    data class Error(val message: String) : CheckoutResult
}

class SaleRepository(private val db: AppDatabase) {

    private val saleDao = db.saleDao()
    private val productDao = db.productDao()
    private val stockDao = db.stockDao()
    private val storeDao = db.storeDao()

    fun observeProfile(): Flow<StoreProfile> = storeDao.observe().map { it ?: StoreProfile() }
    suspend fun saveProfile(p: StoreProfile) = storeDao.save(p.copy(id = 1))

    fun observeSale(id: Long) = saleDao.observeSale(id)
    fun observeItems(id: Long) = saleDao.observeItems(id)
    fun observePaidOnDebt(id: Long) = saleDao.observePaidOnDebt(id)
    fun observeCustomer(id: Long) = saleDao.observeCustomer(id)
    fun observeSales(range: Pair<Long, Long>): Flow<List<SaleRow>> = saleDao.observeSales(range.first, range.second)
    fun observeCustomers() = saleDao.observeCustomers()

    suspend fun checkout(req: CheckoutRequest): CheckoutResult {
        if (req.items.isEmpty()) return CheckoutResult.Error("Keranjang masih kosong.")
        if (req.items.any { it.qty <= 0 }) return CheckoutResult.Error("Jumlah barang harus lebih dari 0.")
        if (req.transDiscount < 0) return CheckoutResult.Error("Diskon tidak boleh minus.")

        return db.withTransaction<CheckoutResult> {
            // 1. Sudah pernah tersimpan (tombol ditekan dua kali)? Kembalikan transaksi yang sama.
            saleDao.findByRequestId(req.requestId)?.let { return@withTransaction CheckoutResult.Success(it, alreadySaved = true) }

            // 2. Ambil data barang terbaru (stok & modal saat ini).
            val products = req.items.map { it.productId }.distinct().associateWith { productDao.getById(it) }
            products.entries.firstOrNull { it.value == null }?.let {
                return@withTransaction CheckoutResult.Error("Ada barang di keranjang yang sudah dihapus. Hapus dari keranjang lalu coba lagi.")
            }
            val items = req.items.map { item ->
                val p = products.getValue(item.productId)!!
                item.copy(name = p.name, baseUnit = p.unit, costPerUnit = UnitCalc.costPerUnit(p.costPrice, item.factor))
            }

            // 3. Cek stok.
            val shortages = CheckoutCalc.shortages(items, products.mapValues { it.value!!.stockQty })
            if (shortages.isNotEmpty() && !req.allowNegativeStock) {
                return@withTransaction CheckoutResult.NeedStockConfirm(shortages)
            }

            // 4. Hitung total & pembayaran.
            val totals = SaleCalc.totals(items.map { it.toLine() }, req.transDiscount)
            val pay = when (val c = CheckoutCalc.resolvePayment(totals.total, req.payment)) {
                is PaymentCheck.Ok -> c.result
                is PaymentCheck.Error -> return@withTransaction CheckoutResult.Error(c.message)
            }
            val customerName = req.customerName.trim()
            if (pay.isDebt && customerName.isEmpty()) {
                return@withTransaction CheckoutResult.Error("Transaksi utang wajib diisi nama pelanggan.")
            }

            val now = Waktu.now()
            val customerId = if (customerName.isEmpty()) null else {
                val existing = saleDao.findCustomer(customerName)
                when {
                    existing == null -> saleDao.insertCustomer(Customer(name = customerName, phone = req.customerPhone.trim(), createdAt = now))
                    req.customerPhone.isNotBlank() && existing.phone.isBlank() -> {
                        saleDao.updateCustomer(existing.copy(phone = req.customerPhone.trim())); existing.id
                    }
                    else -> existing.id
                }
            }

            // 5. Nomor nota: INV-yyMMdd-urutan hari ini.
            val day = Waktu.todayRange()
            var seq = saleDao.countInRange(day.first, day.second) + 1
            var invoice = CheckoutCalc.invoiceNo(Waktu.invoiceDate(now), seq)
            while (saleDao.countInvoice(invoice) > 0) {
                seq++
                invoice = CheckoutCalc.invoiceNo(Waktu.invoiceDate(now), seq)
            }

            val saleId = saleDao.insertSale(
                Sale(
                    invoiceNo = invoice, clientRequestId = req.requestId, customerId = customerId, createdAt = now,
                    subtotal = totals.subtotal, itemDiscountTotal = totals.itemDiscountTotal, transDiscount = totals.transDiscount,
                    total = totals.total, costTotal = totals.costTotal, paidAmount = pay.paidAmount, changeAmount = pay.changeAmount,
                    paymentMethod = req.payment.method, status = pay.status,
                ),
            )
            saleDao.insertItems(
                items.map {
                    SaleItem(
                        saleId = saleId, productId = it.productId, productName = it.name, unit = it.unitName, qty = it.qty,
                        sellPrice = it.sellPrice, costPrice = it.costPerUnit, discount = it.discount,
                        lineTotal = it.lineTotal, unitFactor = it.factor,
                    )
                },
            )

            // 6. Kurangi stok (dalam satuan dasar) + catat riwayat.
            for ((productId, lines) in items.groupBy { it.productId }) {
                val p = productDao.getById(productId)!!
                val out = Qty.round(lines.sumOf { it.baseQty })
                val after = Qty.round(p.stockQty - out)
                productDao.update(p.copy(stockQty = after, updatedAt = now))
                val note = buildList {
                    add(invoice)
                    if (after < 0) add("stok minus diizinkan")
                }.joinToString(" · ")
                stockDao.insertMovement(
                    StockMovement(
                        productId = productId, type = MovementType.SALE, qtyChange = -out, qtyAfter = after,
                        unitCost = p.costPrice, refType = "SALE", refId = saleId, note = note, createdAt = now,
                    ),
                )
            }

            // 7. Uang muka utang dicatat sebagai cicilan pertama.
            if (pay.isDebt && pay.debtPayment > 0) {
                saleDao.insertDebtPayment(
                    DebtPayment(saleId = saleId, customerId = customerId, amount = pay.debtPayment, method = "TUNAI", note = "Uang muka", createdAt = now),
                )
            }

            CheckoutResult.Success(saleId)
        }
    }

    /** Batalkan transaksi: stok dikembalikan, transaksi tidak dihitung di omzet/laba. Tidak bisa dibatalkan dua kali. */
    suspend fun voidSale(saleId: Long, reason: String): RepoResult {
        if (reason.isBlank()) return RepoResult.Error("Tuliskan alasan pembatalan.")
        return db.withTransaction<RepoResult> {
            val sale = saleDao.getSale(saleId) ?: return@withTransaction RepoResult.Error("Transaksi tidak ditemukan.")
            if (sale.status == SaleState.VOID) return@withTransaction RepoResult.Error("Transaksi ini sudah dibatalkan.")
            val now = Waktu.now()
            for ((productId, lines) in saleDao.getItems(saleId).groupBy { it.productId }) {
                val p = productDao.getById(productId) ?: continue
                val back = Qty.round(lines.sumOf { it.qty * it.unitFactor })
                val after = Qty.round(p.stockQty + back)
                productDao.update(p.copy(stockQty = after, updatedAt = now))
                stockDao.insertMovement(
                    StockMovement(
                        productId = productId, type = MovementType.VOID, qtyChange = back, qtyAfter = after,
                        unitCost = p.costPrice, refType = "SALE", refId = saleId,
                        note = "${sale.invoiceNo} · ${reason.trim()}", createdAt = now,
                    ),
                )
            }
            saleDao.updateSale(sale.copy(status = SaleState.VOID, voidReason = reason.trim()))
            RepoResult.Ok(saleId, "Transaksi ${sale.invoiceNo} dibatalkan. Stok sudah dikembalikan.")
        }
    }
}
