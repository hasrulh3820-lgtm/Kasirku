package id.kasirku.umkm.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import id.kasirku.umkm.data.db.entity.Purchase
import id.kasirku.umkm.data.db.entity.PurchaseItem
import id.kasirku.umkm.data.db.entity.StockMovement
import kotlinx.coroutines.flow.Flow

@Dao
interface StockDao {

    @Insert
    suspend fun insertMovement(movement: StockMovement): Long

    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY createdAt DESC, id DESC LIMIT 200")
    fun observeMovements(productId: Long): Flow<List<StockMovement>>

    @Query("DELETE FROM stock_movements WHERE productId = :productId")
    suspend fun deleteMovementsForProduct(productId: Long)

    @Insert
    suspend fun insertPurchase(purchase: Purchase): Long

    @Insert
    suspend fun insertPurchaseItem(item: PurchaseItem): Long
}
