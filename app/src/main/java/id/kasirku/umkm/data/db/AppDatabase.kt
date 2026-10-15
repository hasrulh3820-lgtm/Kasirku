package id.kasirku.umkm.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import id.kasirku.umkm.data.db.dao.DashboardDao
import id.kasirku.umkm.data.db.dao.ProductDao
import id.kasirku.umkm.data.db.dao.StockDao
import id.kasirku.umkm.data.db.entity.Category
import id.kasirku.umkm.data.db.entity.Customer
import id.kasirku.umkm.data.db.entity.DebtPayment
import id.kasirku.umkm.data.db.entity.Expense
import id.kasirku.umkm.data.db.entity.Product
import id.kasirku.umkm.data.db.entity.ProductUnit
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
        ProductUnit::class,
        StockMovement::class,
        Customer::class,
        Sale::class,
        SaleItem::class,
        DebtPayment::class,
        Purchase::class,
        PurchaseItem::class,
        Expense::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun stockDao(): StockDao
    abstract fun dashboardDao(): DashboardDao

    companion object {
        /** v1 -> v2: multi-satuan. Data lama tidak dihapus. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `product_units` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`productId` INTEGER NOT NULL, `name` TEXT NOT NULL, `factor` REAL NOT NULL, " +
                        "`sellPrice` INTEGER NOT NULL, `barcode` TEXT, `sortOrder` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_units_productId` ON `product_units` (`productId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_product_units_barcode` ON `product_units` (`barcode`)")
                db.execSQL("ALTER TABLE `sale_items` ADD COLUMN `unitFactor` REAL NOT NULL DEFAULT 1")
            }
        }

        const val FILE_NAME = "kasirku.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, FILE_NAME)
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
