package id.kasirku.umkm.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import id.kasirku.umkm.data.db.entity.Category
import id.kasirku.umkm.data.db.entity.Product
import id.kasirku.umkm.data.db.entity.ProductUnit
import kotlinx.coroutines.flow.Flow

/** Produk + nama kategorinya, untuk daftar produk. */
data class ProductWithCategory(
    val id: Long,
    val name: String,
    val sku: String?,
    val barcode: String?,
    val unit: String,
    val costPrice: Long,
    val sellPrice: Long,
    val stockQty: Double,
    val minStock: Double,
    val categoryName: String?,
)

@Dao
interface ProductDao {

    @Query(
        """
        SELECT p.id, p.name, p.sku, p.barcode, p.unit, p.costPrice, p.sellPrice, p.stockQty, p.minStock,
               c.name AS categoryName
        FROM products p LEFT JOIN categories c ON c.id = p.categoryId
        WHERE p.isArchived = 0
          AND (:q = '' OR p.name LIKE '%' || :q || '%' OR p.sku LIKE '%' || :q || '%' OR p.barcode = :q)
          AND (:lowOnly = 0 OR p.stockQty <= p.minStock)
        ORDER BY p.name COLLATE NOCASE
        """,
    )
    fun observeList(q: String, lowOnly: Boolean): Flow<List<ProductWithCategory>>

    @Query(
        """
        SELECT p.id, p.name, p.sku, p.barcode, p.unit, p.costPrice, p.sellPrice, p.stockQty, p.minStock,
               c.name AS categoryName
        FROM products p LEFT JOIN categories c ON c.id = p.categoryId
        WHERE p.isArchived = 0 AND p.stockQty <= p.minStock
        ORDER BY p.stockQty ASC LIMIT :limit
        """,
    )
    fun observeLowStock(limit: Int): Flow<List<ProductWithCategory>>

    @Query("SELECT * FROM products WHERE id = :id")
    fun observeById(id: Long): Flow<Product?>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: Long): Product?

    @Insert
    suspend fun insert(product: Product): Long

    @Update
    suspend fun update(product: Product)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM sale_items WHERE productId = :id")
    suspend fun countSaleItems(id: Long): Int

    @Query("SELECT COUNT(*) FROM purchase_items WHERE productId = :id")
    suspend fun countPurchaseItems(id: Long): Int

    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE")
    fun observeCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findCategory(name: String): Category?

    @Insert
    suspend fun insertCategory(category: Category): Long

    @Query("SELECT name FROM categories WHERE id = :id")
    suspend fun categoryName(id: Long): String?

    // ---- Satuan besar ----

    @Query("SELECT * FROM product_units WHERE productId = :productId ORDER BY factor ASC")
    fun observeUnits(productId: Long): Flow<List<ProductUnit>>

    @Query("SELECT * FROM product_units WHERE productId = :productId ORDER BY factor ASC")
    suspend fun getUnits(productId: Long): List<ProductUnit>

    @Query("SELECT * FROM product_units ORDER BY productId, factor ASC")
    fun observeAllUnits(): Flow<List<ProductUnit>>

    @Query("DELETE FROM product_units WHERE productId = :productId")
    suspend fun deleteUnits(productId: Long)

    @Insert
    suspend fun insertUnits(units: List<ProductUnit>)
}
