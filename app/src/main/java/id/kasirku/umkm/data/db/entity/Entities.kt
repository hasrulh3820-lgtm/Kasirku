package id.kasirku.umkm.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Aturan umum:
 * - Semua uang disimpan sebagai Long dalam Rupiah utuh (tanpa desimal).
 * - Jumlah barang (qty/stok) disimpan sebagai Double, dibulatkan 3 angka di belakang koma (untuk kg/liter).
 * - Waktu disimpan sebagai epoch milidetik.
 * - isDemo = true menandai data contoh yang bisa dihapus tanpa menyentuh data asli.
 */

@Entity(tableName = "store_profile")
data class StoreProfile(
    @PrimaryKey val id: Long = 1,
    val name: String = "Toko Saya",
    val address: String = "",
    val phone: String = "",
    val receiptFooter: String = "Terima kasih atas kunjungan Anda",
)

@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "products",
    indices = [
        Index(value = ["sku"], unique = true),
        Index(value = ["barcode"], unique = true),
        Index(value = ["categoryId"]),
        Index(value = ["name"]),
    ],
)
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sku: String? = null,
    val barcode: String? = null,
    val categoryId: Long? = null,
    val unit: String = "pcs",
    val costPrice: Long = 0,
    val sellPrice: Long = 0,
    val stockQty: Double = 0.0,
    val minStock: Double = 0.0,
    val isArchived: Boolean = false,
    val isDemo: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

object MovementType {
    const val INITIAL = "INITIAL"
    const val PURCHASE = "PURCHASE"
    const val SALE = "SALE"
    const val VOID = "VOID"
    const val ADJUST = "ADJUST"
}

@Entity(tableName = "stock_movements", indices = [Index(value = ["productId"]), Index(value = ["createdAt"])])
data class StockMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val type: String,
    val qtyChange: Double,
    val qtyAfter: Double,
    val unitCost: Long,
    val refType: String? = null,
    val refId: Long? = null,
    val note: String = "",
    val isDemo: Boolean = false,
    val createdAt: Long,
)

@Entity(tableName = "customers", indices = [Index(value = ["name"])])
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val note: String = "",
    val isDemo: Boolean = false,
    val createdAt: Long,
)

object SaleStatus {
    const val PAID = "PAID"
    const val PARTIAL = "PARTIAL"
    const val UNPAID = "UNPAID"
    const val VOID = "VOID"
}

@Entity(
    tableName = "sales",
    indices = [
        Index(value = ["invoiceNo"], unique = true),
        Index(value = ["clientRequestId"], unique = true),
        Index(value = ["createdAt"]),
        Index(value = ["customerId"]),
    ],
)
data class Sale(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceNo: String,
    /** Kunci unik dari layar kasir; mencegah transaksi ganda saat tombol Simpan ditekan berulang. */
    val clientRequestId: String,
    val customerId: Long? = null,
    val createdAt: Long,
    val subtotal: Long,
    val itemDiscountTotal: Long,
    val transDiscount: Long,
    val total: Long,
    val costTotal: Long,
    val paidAmount: Long,
    val changeAmount: Long,
    val paymentMethod: String,
    val status: String,
    val voidReason: String? = null,
    val isDemo: Boolean = false,
)

@Entity(tableName = "sale_items", indices = [Index(value = ["saleId"]), Index(value = ["productId"])])
data class SaleItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    val productId: Long,
    val productName: String,
    val unit: String,
    val qty: Double,
    val sellPrice: Long,
    val costPrice: Long,
    val discount: Long,
    val lineTotal: Long,
)

@Entity(tableName = "debt_payments", indices = [Index(value = ["saleId"]), Index(value = ["customerId"])])
data class DebtPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    val customerId: Long?,
    val amount: Long,
    val method: String,
    val note: String = "",
    val isDemo: Boolean = false,
    val createdAt: Long,
)

@Entity(tableName = "purchases", indices = [Index(value = ["createdAt"])])
data class Purchase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supplierName: String = "",
    val total: Long,
    val note: String = "",
    val isDemo: Boolean = false,
    val createdAt: Long,
)

@Entity(tableName = "purchase_items", indices = [Index(value = ["purchaseId"]), Index(value = ["productId"])])
data class PurchaseItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseId: Long,
    val productId: Long,
    val qty: Double,
    val unitCost: Long,
)

@Entity(tableName = "expenses", indices = [Index(value = ["createdAt"])])
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val amount: Long,
    val note: String = "",
    val isDemo: Boolean = false,
    val createdAt: Long,
)
