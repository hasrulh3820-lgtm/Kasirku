package id.kasirku.umkm.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class SalesAggregate(
    val omzet: Long,
    val hpp: Long,
    val trxCount: Int,
)

@Dao
interface DashboardDao {

    /** Transaksi batal (VOID) tidak ikut dihitung. Rentang waktu: from <= createdAt < to. */
    @Query(
        """
        SELECT COALESCE(SUM(total), 0) AS omzet,
               COALESCE(SUM(costTotal), 0) AS hpp,
               COUNT(*) AS trxCount
        FROM sales
        WHERE status != 'VOID' AND createdAt >= :from AND createdAt < :to
        """,
    )
    fun observeSales(from: Long, to: Long): Flow<SalesAggregate>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE createdAt >= :from AND createdAt < :to")
    fun observeExpenses(from: Long, to: Long): Flow<Long>
}
