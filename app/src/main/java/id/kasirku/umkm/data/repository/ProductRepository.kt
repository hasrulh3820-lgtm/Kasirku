package id.kasirku.umkm.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import id.kasirku.umkm.data.db.AppDatabase
import id.kasirku.umkm.data.db.dao.ProductWithCategory
import id.kasirku.umkm.data.db.entity.Category
import id.kasirku.umkm.data.db.entity.MovementType
import id.kasirku.umkm.data.db.entity.Product
import id.kasirku.umkm.data.db.entity.ProductUnit
import id.kasirku.umkm.data.db.entity.Purchase
import id.kasirku.umkm.data.db.entity.PurchaseItem
import id.kasirku.umkm.data.db.entity.StockMovement
import id.kasirku.umkm.domain.Money
import id.kasirku.umkm.domain.Qty
import id.kasirku.umkm.domain.UnitCalc
import id.kasirku.umkm.domain.UnitOption
import id.kasirku.umkm.util.Waktu
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.roundToLong

data class ProductInput(
    val id: Long?,
    val name: String,
    val sku: String,
    val barcode: String,
    val categoryName: String,
    val unit: String,
    val costPrice: Long,
    val sellPrice: Long,
    /** Hanya dipakai saat produk baru. Stok produk lama diubah lewat Barang Masuk / Koreksi Stok. */
    val initialStock: Double,
    val minStock: Double,
    /** Satuan besar (maks. 2), mis. renteng & dus. Boleh kosong. */
    val bigUnits: List<UnitOption> = emptyList(),
)

sealed interface RepoResult {
    data class Ok(val id: Long, val message: String = "") : RepoResult
    data class Error(val message: String) : RepoResult
}

class ProductRepository(private val db: AppDatabase) {

    private val productDao = db.productDao()
    private val stockDao = db.stockDao()

    fun observeList(query: String, lowOnly: Boolean): Flow<List<ProductWithCategory>> =
        productDao.observeList(query.trim(), lowOnly)

    fun observeLowStock(limit: Int = 20): Flow<List<ProductWithCategory>> = productDao.observeLowStock(limit)

    fun observeProduct(id: Long): Flow<Product?> = productDao.observeById(id)

    fun observeMovements(productId: Long): Flow<List<StockMovement>> = stockDao.observeMovements(productId)

    fun observeCategories(): Flow<List<Category>> = productDao.observeCategories()

    suspend fun getProduct(id: Long): Product? = productDao.getById(id)

    fun observeUnits(productId: Long): Flow<List<UnitOption>> =
        productDao.observeUnits(productId).map { list -> list.map { it.toOption() } }

    suspend fun getUnits(productId: Long): List<UnitOption> = productDao.getUnits(productId).map { it.toOption() }

    /** Satuan besar semua barang, dikelompokkan per productId (untuk daftar barang). */
    fun observeAllUnits(): Flow<Map<Long, List<UnitOption>>> =
        productDao.observeAllUnits().map { list -> list.groupBy({ it.productId }, { it.toOption() }) }

    private fun ProductUnit.toOption() = UnitOption(name = name, factor = factor, sellPrice = sellPrice)

    private suspend fun replaceUnits(productId: Long, units: List<UnitOption>) {
        productDao.deleteUnits(productId)
        if (units.isNotEmpty()) {
            productDao.insertUnits(
                units.sortedBy { it.factor }.mapIndexed { i, u ->
                    ProductUnit(productId = productId, name = u.name.trim(), factor = Qty.round(u.factor), sellPrice = u.sellPrice, sortOrder = i)
                },
            )
        }
    }

    suspend fun categoryName(id: Long?): String = id?.let { productDao.categoryName(it) } ?: ""

    suspend fun saveProduct(input: ProductInput): RepoResult {
        val name = input.name.trim()
        if (name.isEmpty()) return RepoResult.Error("Nama barang wajib diisi.")
        if (input.sellPrice <= 0) return RepoResult.Error("Harga jual wajib diisi dan lebih dari 0.")
        if (input.costPrice < 0) return RepoResult.Error("Harga modal tidak boleh minus.")
        if (input.initialStock < 0) return RepoResult.Error("Stok awal tidak boleh minus.")
        if (input.minStock < 0) return RepoResult.Error("Batas stok menipis tidak boleh minus.")
        if (input.bigUnits.size > 2) return RepoResult.Error("Maksimal 2 satuan besar.")
        UnitCalc.validate(input.unit, input.bigUnits)?.let { return RepoResult.Error(it) }

        val now = Waktu.now()
        return try {
            db.withTransaction<RepoResult> {
                val categoryId = categoryIdFor(input.categoryName)
                val sku = input.sku.trim().ifEmpty { null }
                val barcode = input.barcode.trim().ifEmpty { null }
                if (input.id == null) {
                    val stock = Qty.round(input.initialStock)
                    val id = productDao.insert(
                        Product(
                            name = name, sku = sku, barcode = barcode, categoryId = categoryId,
                            unit = input.unit, costPrice = input.costPrice, sellPrice = input.sellPrice,
                            stockQty = stock, minStock = Qty.round(input.minStock),
                            createdAt = now, updatedAt = now,
                        ),
                    )
                    replaceUnits(id, input.bigUnits)
                    if (stock > 0) {
                        stockDao.insertMovement(
                            StockMovement(
                                productId = id, type = MovementType.INITIAL, qtyChange = stock, qtyAfter = stock,
                                unitCost = input.costPrice, note = "Stok awal", createdAt = now,
                            ),
                        )
                    }
                    RepoResult.Ok(id, "Barang \"$name\" disimpan.")
                } else {
                    val old = productDao.getById(input.id) ?: return@withTransaction RepoResult.Error("Barang tidak ditemukan.")
                    productDao.update(
                        old.copy(
                            name = name, sku = sku, barcode = barcode, categoryId = categoryId,
                            unit = input.unit, costPrice = input.costPrice, sellPrice = input.sellPrice,
                            minStock = Qty.round(input.minStock), updatedAt = now,
                        ),
                    )
                    replaceUnits(old.id, input.bigUnits)
                    RepoResult.Ok(old.id, "Perubahan disimpan.")
                }
            }
        } catch (e: SQLiteConstraintException) {
            RepoResult.Error("SKU atau barcode sudah dipakai barang lain.")
        }
    }

    /**
     * Barang masuk dari pemasok: tambah stok, catat pembelian, perbarui modal rata-rata.
     * [qty] dan [unitCost] dalam satuan yang dipilih (mis. 5 dus @ Rp 110.000);
     * [factor] = isi satuan itu dalam satuan dasar (dus = 40). Satuan dasar: factor 1.
     */
    suspend fun stockIn(
        productId: Long,
        qty: Double,
        unitCost: Long,
        supplier: String,
        note: String,
        unitName: String? = null,
        factor: Double = 1.0,
    ): RepoResult {
        val q = Qty.round(qty)
        if (q <= 0) return RepoResult.Error("Jumlah barang masuk harus lebih dari 0.")
        if (unitCost < 0) return RepoResult.Error("Harga beli tidak boleh minus.")
        if (factor <= 0.0) return RepoResult.Error("Isi satuan tidak valid.")
        val baseQty = UnitCalc.toBase(q, factor)
        val baseCost = UnitCalc.baseCostFrom(unitCost, factor)
        val now = Waktu.now()
        return db.withTransaction<RepoResult> {
            val p = productDao.getById(productId) ?: return@withTransaction RepoResult.Error("Barang tidak ditemukan.")
            val newQty = Qty.round(p.stockQty + baseQty)
            val newCost = Money.weightedAverageCost(p.stockQty, p.costPrice, baseQty, baseCost)
            val purchaseId = stockDao.insertPurchase(
                Purchase(supplierName = supplier.trim(), total = (q * unitCost).roundToLong(), note = note.trim(), createdAt = now),
            )
            stockDao.insertPurchaseItem(PurchaseItem(purchaseId = purchaseId, productId = productId, qty = baseQty, unitCost = baseCost))
            productDao.update(p.copy(stockQty = newQty, costPrice = newCost, updatedAt = now))
            val label = if (factor != 1.0 && unitName != null) "${Qty.format(q)} $unitName @ ${Money.format(unitCost)}" else ""
            stockDao.insertMovement(
                StockMovement(
                    productId = productId, type = MovementType.PURCHASE, qtyChange = baseQty, qtyAfter = newQty,
                    unitCost = baseCost, refType = "PURCHASE", refId = purchaseId,
                    note = listOf(label, supplier.trim(), note.trim()).filter { it.isNotEmpty() }.joinToString(" · "),
                    createdAt = now,
                ),
            )
            RepoResult.Ok(productId, "Stok bertambah ${Qty.format(baseQty)} ${p.unit}.")
        }
    }

    /** Koreksi stok setelah hitung fisik (barang rusak, hilang, salah catat). */
    suspend fun adjustStock(productId: Long, newQty: Double, reason: String): RepoResult {
        val target = Qty.round(newQty)
        if (target < 0) return RepoResult.Error("Stok tidak boleh minus.")
        if (reason.isBlank()) return RepoResult.Error("Tuliskan alasan koreksi, misalnya \"barang rusak\".")
        val now = Waktu.now()
        return db.withTransaction<RepoResult> {
            val p = productDao.getById(productId) ?: return@withTransaction RepoResult.Error("Barang tidak ditemukan.")
            val change = Qty.round(target - p.stockQty)
            if (change == 0.0) return@withTransaction RepoResult.Error("Stok tidak berubah.")
            productDao.update(p.copy(stockQty = target, updatedAt = now))
            stockDao.insertMovement(
                StockMovement(
                    productId = productId, type = MovementType.ADJUST, qtyChange = change, qtyAfter = target,
                    unitCost = p.costPrice, note = reason.trim(), createdAt = now,
                ),
            )
            RepoResult.Ok(productId, "Stok dikoreksi menjadi ${Qty.format(target)} ${p.unit}.")
        }
    }

    /**
     * Barang yang belum pernah dijual atau dibeli dihapus permanen.
     * Barang yang punya riwayat diarsipkan (disembunyikan) agar laporan lama tetap utuh.
     */
    suspend fun deleteOrArchive(productId: Long): RepoResult = db.withTransaction<RepoResult> {
        val p = productDao.getById(productId) ?: return@withTransaction RepoResult.Error("Barang tidak ditemukan.")
        val hasHistory = productDao.countSaleItems(productId) > 0 || productDao.countPurchaseItems(productId) > 0
        if (hasHistory) {
            productDao.update(p.copy(isArchived = true, updatedAt = Waktu.now()))
            RepoResult.Ok(productId, "\"${p.name}\" punya riwayat transaksi, jadi diarsipkan (disembunyikan), bukan dihapus.")
        } else {
            stockDao.deleteMovementsForProduct(productId)
            productDao.deleteUnits(productId)
            productDao.delete(productId)
            RepoResult.Ok(productId, "\"${p.name}\" dihapus.")
        }
    }

    private suspend fun categoryIdFor(rawName: String): Long? {
        val name = rawName.trim()
        if (name.isEmpty()) return null
        return productDao.findCategory(name)?.id ?: productDao.insertCategory(Category(name = name))
    }
}
