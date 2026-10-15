package id.kasirku.umkm.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import id.kasirku.umkm.data.db.dao.DashboardDao
import id.kasirku.umkm.data.db.dao.ProductDao
import id.kasirku.umkm.data.db.dao.StockDao
import id.kasirku.umkm.data.db.entity.Category
import id.kasirku.umkm.data.db.entity.Customer
import id.kasirku.umkm.data.db.entity.DebtPayment
import id.kasirku.umkm.data.db.entity.Expense
import id.kasirku.umkm.data.db.entity.Product
import id.kasirku.umkm.data.db.entity.Purchase
import id.kasirku.umkm.data.db.entity.PurchaseItem
import id.kasirku.umkm.data.db.entity.Sale
import id.kasirku.umkm.data.db.entity.SaleItem
import id.kasirku.umkm.data.db.entity.StockMovement
import id.kasirku.umkm.data.db.entity.StoreProfile

@Database(
    entities = [
        StoreProfile::class,
        Category::class,
        Product::class,
        StockMovement::class,
        Customer::class,
        Sale::class,
        SaleItem::class,
        DebtPayment::class,
        Purchase::class,
        PurchaseItem::class,
        Expense::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun stockDao(): StockDao
    abstract fun dashboardDao(): DashboardDao

    companion object {
        const val FILE_NAME = "kasirku.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, FILE_NAME)
                    .build()
                    .also { instance = it }
            }
    }
}
