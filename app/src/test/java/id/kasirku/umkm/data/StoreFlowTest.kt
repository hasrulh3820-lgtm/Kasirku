package id.kasirku.umkm.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import id.kasirku.umkm.data.backup.BackupManager
import id.kasirku.umkm.data.db.AppDatabase
import id.kasirku.umkm.data.repository.CheckoutRequest
import id.kasirku.umkm.data.repository.CheckoutResult
import id.kasirku.umkm.data.repository.DashboardRepository
import id.kasirku.umkm.data.repository.DebtRepository
import id.kasirku.umkm.data.repository.ExpenseRepository
import id.kasirku.umkm.data.repository.ProductInput
import id.kasirku.umkm.data.repository.ProductRepository
import id.kasirku.umkm.data.repository.RepoResult
import id.kasirku.umkm.data.repository.ReportRepository
import id.kasirku.umkm.data.repository.SaleRepository
import id.kasirku.umkm.domain.CartItem
import id.kasirku.umkm.domain.PayMethod
import id.kasirku.umkm.domain.PaymentInput
import id.kasirku.umkm.domain.SaleState
import id.kasirku.umkm.domain.UnitCalc
import id.kasirku.umkm.domain.UnitOption
import id.kasirku.umkm.util.Waktu
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Uji alur toko dari ujung ke ujung dengan database SQLite sungguhan:
 * barang → kasir → stok → batal → utang → pengeluaran → laporan → backup & pulihkan.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoreFlowTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var products: ProductRepository
    private lateinit var sales: SaleRepository
    private lateinit var debts: DebtRepository
    private lateinit var expenses: ExpenseRepository
    private lateinit var dashboard: DashboardRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        products = ProductRepository(db)
        sales = SaleRepository(db)
        debts = DebtRepository(db)
        expenses = ExpenseRepository(db)
        dashboard = DashboardRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
        AppDatabase.closeAndReset()
    }

    /** Indomie: eceran Rp 3.500 / modal Rp 3.000, dus isi 40 Rp 125.000, stok awal 100 pcs. */
    private suspend fun indomie(repo: ProductRepository = products, stock: Double = 100.0): Long {
        val r = repo.saveProduct(
            ProductInput(
                id = null, name = "Indomie Goreng", sku = "", barcode = "", categoryName = "Mie", unit = "pcs",
                costPrice = 3_000, sellPrice = 3_500, initialStock = stock, minStock = 10.0,
                bigUnits = listOf(UnitOption("dus", 40.0, 125_000)),
            ),
        )
        return (r as RepoResult.Ok).id
    }

    private fun line(id: Long, unit: String, factor: Double, price: Long, qty: Double) = CartItem(
        productId = id, name = "Indomie Goreng", baseUnit = "pcs", unitName = unit, factor = factor,
        sellPrice = price, costPerUnit = UnitCalc.costPerUnit(3_000, factor), qty = qty,
    )

    private fun request(
        id: Long,
        requestId: String = "req-1",
        payment: PaymentInput = PaymentInput(PayMethod.TUNAI, cashReceived = 150_000),
        items: List<CartItem> = listOf(line(id, "pcs", 1.0, 3_500, 3.0), line(id, "dus", 40.0, 125_000, 1.0)),
        customer: String = "",
        allowNegative: Boolean = false,
    ) = CheckoutRequest(requestId, items, 0, payment, customer, "", allowNegative)

    private suspend fun stockOf(id: Long) = products.getProduct(id)!!.stockQty

    @Test
    fun databaseVersionConstantMatchesRoom() {
        assertEquals(AppDatabase.VERSION, db.openHelper.readableDatabase.version)
    }

    @Test
    fun checkoutMixedUnitsReducesStockAndCountsProfit() = runBlocking {
        val id = indomie()
        val r = sales.checkout(request(id)) as CheckoutResult.Success
        val sale = sales.observeSale(r.saleId).first()!!
        assertEquals(135_500L, sale.total)
        assertEquals(129_000L, sale.costTotal)
        assertEquals(14_500L, sale.changeAmount)
        assertEquals(SaleState.PAID, sale.status)
        assertEquals(57.0, stockOf(id), 0.0)

        val today = dashboard.observeSummary(Waktu.todayRange()).first()
        assertEquals(135_500L, today.omzet)
        assertEquals(129_000L, today.hpp)
        assertEquals(6_500L, today.grossProfit)
        assertEquals(1, today.trxCount)
    }

    @Test
    fun pressingSaveTwiceDoesNotDuplicate() = runBlocking {
        val id = indomie()
        val a = sales.checkout(request(id)) as CheckoutResult.Success
        val b = sales.checkout(request(id)) as CheckoutResult.Success
        assertEquals(a.saleId, b.saleId)
        assertTrue(b.alreadySaved)
        assertEquals(57.0, stockOf(id), 0.0)
        assertEquals(1, dashboard.observeSummary(Waktu.todayRange()).first().trxCount)
    }

    @Test
    fun voidRestoresStockAndRemovesFromProfit() = runBlocking {
        val id = indomie()
        val r = sales.checkout(request(id)) as CheckoutResult.Success
        assertTrue(sales.voidSale(r.saleId, "salah input") is RepoResult.Ok)
        assertEquals(100.0, stockOf(id), 0.0)
        assertEquals(0L, dashboard.observeSummary(Waktu.todayRange()).first().omzet)
        assertTrue(sales.voidSale(r.saleId, "lagi") is RepoResult.Error)
        // riwayat stok: awal, jual, batal
        val moves = products.observeMovements(id).first()
        assertEquals(listOf("VOID", "SALE", "INITIAL"), moves.map { it.type })
    }

    @Test
    fun shortageNeedsConfirmationThenGoesNegative() = runBlocking {
        val id = indomie(stock = 10.0)
        val items = listOf(line(id, "pcs", 1.0, 3_500, 12.0))
        val pay = PaymentInput(PayMethod.TUNAI, cashReceived = 50_000)
        val first = sales.checkout(request(id, items = items, payment = pay))
        assertTrue(first is CheckoutResult.NeedStockConfirm)
        assertEquals(10.0, stockOf(id), 0.0) // belum ada yang tersimpan
        val second = sales.checkout(request(id, items = items, payment = pay, allowNegative = true))
        assertTrue(second is CheckoutResult.Success)
        assertEquals(-2.0, stockOf(id), 0.0)
    }

    @Test
    fun debtWithDownPaymentThenInstallmentsUntilPaid() = runBlocking {
        val id = indomie()
        val items = listOf(line(id, "dus", 40.0, 125_000, 1.0))
        val noName = sales.checkout(request(id, items = items, payment = PaymentInput(PayMethod.UTANG, downPayment = 25_000)))
        assertTrue(noName is CheckoutResult.Error) // utang wajib nama pelanggan

        val r = sales.checkout(
            request(id, requestId = "req-2", items = items, payment = PaymentInput(PayMethod.UTANG, downPayment = 25_000), customer = "Budi"),
        ) as CheckoutResult.Success
        assertEquals(SaleState.PARTIAL, sales.observeSale(r.saleId).first()!!.status)
        assertEquals(100_000L, debts.observeTotalReceivable().first())

        val customerId = debts.observeOpen("").first().single().customerId
        assertTrue(debts.receivePayment(customerId, 100_001, PayMethod.TUNAI, "") is RepoResult.Error)
        assertTrue(debts.receivePayment(customerId, 60_000, PayMethod.QRIS, "") is RepoResult.Ok)
        assertEquals(40_000L, debts.observeTotalReceivable().first())
        assertTrue(debts.receivePayment(customerId, 40_000, PayMethod.TUNAI, "lunas") is RepoResult.Ok)
        assertEquals(0L, debts.observeTotalReceivable().first())
        assertEquals(SaleState.PAID, sales.observeSale(r.saleId).first()!!.status)
        assertEquals(3, debts.observePayments(customerId).first().size) // DP + 2 cicilan

        // laporan uang masuk: DP 25.000 tunai + 60.000 QRIS + 40.000 tunai
        val report = ReportRepository(db).observe(Waktu.todayRange()).first()
        assertEquals(mapOf("TUNAI" to 65_000L, "QRIS" to 60_000L), report.cashIn.toMap())
        assertEquals(125_000L, report.summary.omzet)
    }

    @Test
    fun stockInPerBoxUpdatesAverageCost() = runBlocking {
        val id = indomie(stock = 10.0)
        assertTrue(products.stockIn(id, 5.0, 110_000, "Agen", "", unitName = "dus", factor = 40.0) is RepoResult.Ok)
        val p = products.getProduct(id)!!
        assertEquals(210.0, p.stockQty, 0.0)
        // (10 × 3.000 + 200 × 2.750) / 210 = 2.761,9 → 2.762
        assertEquals(2_762L, p.costPrice)
    }

    @Test
    fun expenseReducesNetProfitButPurchaseDoesNot() = runBlocking {
        val id = indomie()
        sales.checkout(request(id))
        products.stockIn(id, 1.0, 110_000, "", "", unitName = "dus", factor = 40.0)
        expenses.add("Listrik & Air", 2_000, "token")
        val s = dashboard.observeSummary(Waktu.todayRange()).first()
        assertEquals(6_500L, s.grossProfit)
        assertEquals(2_000L, s.expenses)
        assertEquals(4_500L, s.netProfit)
    }

    @Test
    fun stockCountSavesOnlyDifferences() = runBlocking {
        val a = indomie()
        val r = products.saveStockCount(mapOf(a to 100.0))
        assertTrue(r is RepoResult.Ok)
        assertEquals(1, products.observeMovements(a).first().size) // cocok → tidak ada koreksi
        products.saveStockCount(mapOf(a to 95.0))
        assertEquals(95.0, stockOf(a), 0.0)
        assertEquals(-5.0, products.observeMovements(a).first().first().qtyChange, 0.0)
    }

    @Test
    fun backupThenRestoreBringsBackExactData() = runBlocking {
        val fileDb = AppDatabase.get(context)
        val repo = ProductRepository(fileDb)
        val id = indomie(repo)
        val backup = BackupManager(context)

        val bytes = ByteArrayOutputStream().also { backup.writeBackup(it) }.toByteArray()
        assertTrue(backup.lastBackupAt > 0)

        // Data berubah setelah backup
        repo.adjustStock(id, 1.0, "tes")
        repo.saveProduct(ProductInput(null, "Gula 1kg", "", "", "", "pcs", 15_000, 17_000, 5.0, 1.0))
        assertEquals(2, repo.observeList("", false).first().size)

        // Pulihkan
        assertNull(backup.restore(ByteArrayInputStream(bytes)))
        val restored = ProductRepository(AppDatabase.get(context))
        val list = restored.observeList("", false).first()
        assertEquals(listOf("Indomie Goreng"), list.map { it.name })
        assertEquals(100.0, list.single().stockQty, 0.0)
        assertEquals(1, restored.observeUnits(id).first().size) // satuan dus ikut kembali
    }

    @Test
    fun restoringWrongFileIsRejectedAndDataKept() = runBlocking {
        val fileDb = AppDatabase.get(context)
        indomie(ProductRepository(fileDb))
        val backup = BackupManager(context)
        val err = backup.restore(ByteArrayInputStream("ini bukan backup".toByteArray()))
        assertNotNull(err)
        assertEquals(1, ProductRepository(AppDatabase.get(context)).observeList("", false).first().size)
    }
}
