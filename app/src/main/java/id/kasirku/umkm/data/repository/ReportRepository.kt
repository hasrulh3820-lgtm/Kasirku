package id.kasirku.umkm.data.repository

import id.kasirku.umkm.data.db.AppDatabase
import id.kasirku.umkm.data.db.dao.MethodSum
import id.kasirku.umkm.data.db.dao.ProductSales
import id.kasirku.umkm.data.db.dao.SaleRow
import id.kasirku.umkm.data.db.dao.StockSummary
import id.kasirku.umkm.data.db.entity.Expense
import id.kasirku.umkm.domain.Csv
import id.kasirku.umkm.domain.PayMethod
import id.kasirku.umkm.domain.Qty
import id.kasirku.umkm.domain.ReportCalc
import id.kasirku.umkm.domain.SalesSummary
import id.kasirku.umkm.domain.DayRow
import id.kasirku.umkm.domain.SaleState
import id.kasirku.umkm.domain.UnitCalc
import id.kasirku.umkm.util.Waktu
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

data class Report(
    val range: Pair<Long, Long>,
    val sales: List<SaleRow>,
    val summary: SalesSummary,
    val expenses: List<Expense>,
    val products: List<ProductSales>,
    val cashIn: List<Pair<String, Long>>,
    val debtPaid: Long,
    val days: List<DayRow>,
    val stock: StockSummary,
    val receivable: Long,
) {
    val expenseTotal: Long get() = expenses.sumOf { it.amount }
    val netProfit: Long get() = summary.grossProfit - expenseTotal
}

class ReportRepository(private val db: AppDatabase) {
    private val dao = db.reportDao()
    private val saleDao = db.saleDao()
    private val expenseDao = db.expenseDao()
    private val debtDao = db.debtDao()

    fun observe(range: Pair<Long, Long>): Flow<Report> {
        val (from, to) = range
        val salesAndExpenses = combine(saleDao.observeSales(from, to), expenseDao.observe(from, to)) { s, e -> s to e }
        val money = combine(dao.observeCashSales(from, to), dao.observeDebtPayments(from, to)) { c, d -> c to d }
        val stockAndDebt = combine(dao.observeStockSummary(), debtDao.observeTotalReceivable()) { st, r -> st to r }
        return combine(salesAndExpenses, dao.observeProductSales(from, to), money, stockAndDebt) { (sales, expenses), products, (cash, debt), (stock, receivable) ->
            val saleList = sales.map { it.sale }
            Report(
                range = range,
                sales = sales,
                summary = ReportCalc.summarize(saleList),
                expenses = expenses,
                products = products,
                cashIn = ReportCalc.mergeByMethod(cash.toPairs(), debt.toPairs()),
                debtPaid = debt.sumOf { it.amount },
                days = ReportCalc.byDay(saleList) { Waktu.dayKey(it) },
                stock = stock,
                receivable = receivable,
            )
        }
    }

    private fun List<MethodSum>.toPairs() = map { it.method to it.amount }

    // ---------------- Ekspor CSV ----------------

    fun salesCsv(r: Report): String = Csv.build(
        listOf("No Nota", "Tanggal", "Pelanggan", "Cara Bayar", "Status", "Subtotal", "Diskon", "Total", "Modal", "Laba Kotor"),
        r.sales.map { row ->
            val s = row.sale
            listOf(
                s.invoiceNo, Waktu.formatDateTime(s.createdAt), row.customerName.orEmpty(), PayMethod.label(s.paymentMethod),
                SaleState.label(s.status), s.subtotal, s.itemDiscountTotal + s.transDiscount, s.total, s.costTotal,
                if (s.status == SaleState.VOID) 0 else s.total - s.costTotal,
            )
        },
    )

    fun productSalesCsv(r: Report): String = Csv.build(
        listOf("Barang", "Terjual", "Satuan", "Omzet", "Modal", "Laba Kotor"),
        r.products.map { listOf(it.name, Csv.qty(it.baseQty), it.baseUnit, it.omzet, it.cost, it.profit) },
    )

    suspend fun stockCsv(): String {
        val products = db.productDao().observeList("", false).first()
        val units = db.productDao().observeAllUnits().first().groupBy { it.productId }
        return Csv.build(
            listOf("Barang", "Kategori", "SKU", "Stok", "Satuan", "Rincian", "Batas Menipis", "Modal per Satuan", "Harga Jual", "Nilai Stok (Modal)"),
            products.map { p ->
                val big = units[p.id].orEmpty().map { id.kasirku.umkm.domain.UnitOption(it.name, it.factor, it.sellPrice) }
                listOf(
                    p.name, p.categoryName.orEmpty(), p.sku.orEmpty(), Csv.qty(p.stockQty), p.unit,
                    UnitCalc.breakdown(p.stockQty, p.unit, big), Csv.qty(p.minStock), p.costPrice, p.sellPrice,
                    if (p.stockQty > 0) Math.round(p.stockQty * p.costPrice) else 0L,
                )
            },
        )
    }

    fun expensesCsv(r: Report): String = Csv.build(
        listOf("Tanggal", "Jenis", "Jumlah", "Keterangan"),
        r.expenses.map { listOf(Waktu.formatDateTime(it.createdAt), it.category, it.amount, it.note) },
    )

    suspend fun debtsCsv(): String {
        val list = debtDao.observeOpenByCustomer("").first()
        return Csv.build(
            listOf("Pelanggan", "No HP", "Jumlah Nota", "Total Utang", "Sudah Dibayar", "Sisa Utang", "Nota Terlama"),
            list.map { listOf(it.name, it.phone, it.openCount, it.total, it.paid, it.remaining, Waktu.formatDateTime(it.oldest)) },
        )
    }
}
