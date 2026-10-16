package id.kasirku.umkm.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class ProductSales(
    val productId: Long,
    val name: String,
    val baseUnit: String,
    /** Jumlah terjual dalam satuan dasar (dus sudah dikonversi ke pcs). */
    val baseQty: Double,
    val omzet: Long,
    val cost: Long,
) {
    val profit: Long get() = omzet - cost
}

data class MethodSum(val method: String, val amount: Long)

data class StockSummary(val itemCount: Int, val stockValue: Long, val lowCount: Int, val outCount: Int)

@Dao
interface ReportDao {

    /** Penjualan per barang. Diskon transaksi tidak dibagi ke barang, jadi angka ini sebelum diskon transaksi. */
    @Query(
        """
        SELECT si.productId AS productId, MAX(si.productName) AS name, COALESCE(MAX(p.unit), '') AS baseUnit,
               SUM(si.qty * si.unitFactor) AS baseQty,
               SUM(si.lineTotal) AS omzet,
               SUM(CAST(ROUND(si.costPrice * si.qty) AS INTEGER)) AS cost
        FROM sale_items si
        JOIN sales s ON s.id = si.saleId
        LEFT JOIN products p ON p.id = si.productId
        WHERE s.status != 'VOID' AND s.createdAt >= :from AND s.createdAt < :to
        GROUP BY si.productId
        ORDER BY omzet DESC
        """,
    )
    fun observeProductSales(from: Long, to: Long): Flow<List<ProductSales>>

    /** Uang masuk dari penjualan lunas yang tidak lewat utang, per cara bayar. */
    @Query(
        """
        SELECT s.paymentMethod AS method, SUM(s.total) AS amount FROM sales s
        WHERE s.status = 'PAID' AND s.createdAt >= :from AND s.createdAt < :to
          AND NOT EXISTS (SELECT 1 FROM debt_payments p WHERE p.saleId = s.id)
        GROUP BY s.paymentMethod
        """,
    )
    fun observeCashSales(from: Long, to: Long): Flow<List<MethodSum>>

    /** Uang masuk dari DP dan cicilan utang pada periode ini, per cara bayar. */
    @Query(
        """
        SELECT p.method AS method, SUM(p.amount) AS amount FROM debt_payments p
        JOIN sales s ON s.id = p.saleId
        WHERE s.status != 'VOID' AND p.createdAt >= :from AND p.createdAt < :to
        GROUP BY p.method
        """,
    )
    fun observeDebtPayments(from: Long, to: Long): Flow<List<MethodSum>>

    @Query(
        """
        SELECT COUNT(*) AS itemCount,
               COALESCE(SUM(CASE WHEN stockQty > 0 THEN CAST(ROUND(stockQty * costPrice) AS INTEGER) ELSE 0 END), 0) AS stockValue,
               COALESCE(SUM(CASE WHEN stockQty <= minStock THEN 1 ELSE 0 END), 0) AS lowCount,
               COALESCE(SUM(CASE WHEN stockQty <= 0 THEN 1 ELSE 0 END), 0) AS outCount
        FROM products WHERE isArchived = 0
        """,
    )
    fun observeStockSummary(): Flow<StockSummary>
}
