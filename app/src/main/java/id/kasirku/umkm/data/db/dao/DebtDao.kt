package id.kasirku.umkm.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import id.kasirku.umkm.data.db.entity.DebtPayment
import id.kasirku.umkm.data.db.entity.Expense
import id.kasirku.umkm.data.db.entity.Sale
import kotlinx.coroutines.flow.Flow

data class CustomerDebt(
    val customerId: Long,
    val name: String,
    val phone: String,
    val total: Long,
    val paid: Long,
    val openCount: Int,
    val oldest: Long,
) {
    val remaining: Long get() = (total - paid).coerceAtLeast(0)
}

data class DebtSale(
    @Embedded val sale: Sale,
    val paidSoFar: Long,
)

data class PaymentRow(
    @Embedded val payment: DebtPayment,
    val invoiceNo: String,
)

@Dao
interface DebtDao {

    /** Ringkasan per pelanggan yang masih punya utang (transaksi batal tidak dihitung). */
    @Query(
        """
        SELECT c.id AS customerId, c.name AS name, c.phone AS phone,
               SUM(x.total) AS total, SUM(x.paid) AS paid,
               COUNT(*) AS openCount, MIN(x.createdAt) AS oldest
        FROM (
            SELECT s.customerId AS customerId, s.total AS total, s.createdAt AS createdAt,
                   (SELECT COALESCE(SUM(p.amount), 0) FROM debt_payments p WHERE p.saleId = s.id) AS paid
            FROM sales s WHERE s.status IN ('PARTIAL', 'UNPAID')
        ) x
        JOIN customers c ON c.id = x.customerId
        WHERE (:q = '' OR c.name LIKE '%' || :q || '%' OR c.phone LIKE '%' || :q || '%')
        GROUP BY c.id
        ORDER BY SUM(x.total) - SUM(x.paid) DESC
        """,
    )
    fun observeOpenByCustomer(q: String): Flow<List<CustomerDebt>>

    /** Semua nota utang pelanggan ini (yang belum lunas maupun yang sudah dilunasi lewat cicilan). */
    @Query(
        """
        SELECT s.*, (SELECT COALESCE(SUM(p.amount), 0) FROM debt_payments p WHERE p.saleId = s.id) AS paidSoFar
        FROM sales s
        WHERE s.customerId = :customerId AND s.status != 'VOID'
          AND (s.status IN ('PARTIAL', 'UNPAID') OR s.paymentMethod = 'UTANG'
               OR EXISTS (SELECT 1 FROM debt_payments p WHERE p.saleId = s.id))
        ORDER BY s.createdAt DESC
        """,
    )
    fun observeCustomerDebtSales(customerId: Long): Flow<List<DebtSale>>

    /** Nota yang belum lunas, paling lama dulu (untuk pembagian cicilan). */
    @Query(
        """
        SELECT s.*, (SELECT COALESCE(SUM(p.amount), 0) FROM debt_payments p WHERE p.saleId = s.id) AS paidSoFar
        FROM sales s
        WHERE s.customerId = :customerId AND s.status IN ('PARTIAL', 'UNPAID')
        ORDER BY s.createdAt ASC, s.id ASC
        """,
    )
    suspend fun getOpenSalesOldestFirst(customerId: Long): List<DebtSale>

    @Query(
        """
        SELECT p.*, s.invoiceNo AS invoiceNo FROM debt_payments p JOIN sales s ON s.id = p.saleId
        WHERE s.customerId = :customerId
        ORDER BY p.createdAt DESC, p.id DESC
        """,
    )
    fun observePayments(customerId: Long): Flow<List<PaymentRow>>

    @Query("UPDATE sales SET paidAmount = :paid, status = :status WHERE id = :saleId")
    suspend fun updateSalePaid(saleId: Long, paid: Long, status: String)

    @Insert
    suspend fun insertPayment(payment: DebtPayment): Long

    @Query(
        """
        SELECT COALESCE(SUM(x.total - x.paid), 0) FROM (
            SELECT s.total AS total,
                   (SELECT COALESCE(SUM(p.amount), 0) FROM debt_payments p WHERE p.saleId = s.id) AS paid
            FROM sales s WHERE s.status IN ('PARTIAL', 'UNPAID')
        ) x
        """,
    )
    fun observeTotalReceivable(): Flow<Long>
}

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insert(expense: Expense): Long

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM expenses WHERE createdAt >= :from AND createdAt < :to ORDER BY createdAt DESC, id DESC")
    fun observe(from: Long, to: Long): Flow<List<Expense>>
}
