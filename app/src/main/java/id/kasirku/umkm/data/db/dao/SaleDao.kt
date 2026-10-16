package id.kasirku.umkm.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import id.kasirku.umkm.data.db.entity.Customer
import id.kasirku.umkm.data.db.entity.DebtPayment
import id.kasirku.umkm.data.db.entity.Sale
import id.kasirku.umkm.data.db.entity.SaleItem
import id.kasirku.umkm.data.db.entity.StoreProfile
import kotlinx.coroutines.flow.Flow

data class SaleRow(
    @Embedded val sale: Sale,
    val customerName: String?,
)

@Dao
interface SaleDao {

    @Insert
    suspend fun insertSale(sale: Sale): Long

    @Insert
    suspend fun insertItems(items: List<SaleItem>)

    @Update
    suspend fun updateSale(sale: Sale)

    @Insert
    suspend fun insertDebtPayment(payment: DebtPayment): Long

    @Query("SELECT id FROM sales WHERE clientRequestId = :requestId LIMIT 1")
    suspend fun findByRequestId(requestId: String): Long?

    @Query("SELECT COUNT(*) FROM sales WHERE createdAt >= :from AND createdAt < :to")
    suspend fun countInRange(from: Long, to: Long): Int

    @Query("SELECT COUNT(*) FROM sales WHERE invoiceNo = :invoiceNo")
    suspend fun countInvoice(invoiceNo: String): Int

    @Query("SELECT * FROM sales WHERE id = :id")
    fun observeSale(id: Long): Flow<Sale?>

    @Query("SELECT * FROM sales WHERE id = :id")
    suspend fun getSale(id: Long): Sale?

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId ORDER BY id")
    fun observeItems(saleId: Long): Flow<List<SaleItem>>

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId ORDER BY id")
    suspend fun getItems(saleId: Long): List<SaleItem>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM debt_payments WHERE saleId = :saleId")
    fun observePaidOnDebt(saleId: Long): Flow<Long>

    @Query(
        """
        SELECT s.*, c.name AS customerName FROM sales s
        LEFT JOIN customers c ON c.id = s.customerId
        WHERE s.createdAt >= :from AND s.createdAt < :to
        ORDER BY s.createdAt DESC, s.id DESC
        """,
    )
    fun observeSales(from: Long, to: Long): Flow<List<SaleRow>>

    @Query("SELECT * FROM customers WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findCustomer(name: String): Customer?

    @Query("SELECT * FROM customers WHERE id = :id")
    fun observeCustomer(id: Long): Flow<Customer?>

    @Insert
    suspend fun insertCustomer(customer: Customer): Long

    @Update
    suspend fun updateCustomer(customer: Customer)

    @Query("SELECT * FROM customers ORDER BY name COLLATE NOCASE")
    fun observeCustomers(): Flow<List<Customer>>
}

@Dao
interface StoreDao {
    @Query("SELECT * FROM store_profile WHERE id = 1")
    fun observe(): Flow<StoreProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(profile: StoreProfile)
}
