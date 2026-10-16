package id.kasirku.umkm.data.repository

import androidx.room.withTransaction
import id.kasirku.umkm.data.db.AppDatabase
import id.kasirku.umkm.data.db.dao.CustomerDebt
import id.kasirku.umkm.data.db.dao.DebtSale
import id.kasirku.umkm.data.db.dao.PaymentRow
import id.kasirku.umkm.data.db.entity.DebtPayment
import id.kasirku.umkm.data.db.entity.Expense
import id.kasirku.umkm.domain.DebtCalc
import id.kasirku.umkm.domain.Money
import id.kasirku.umkm.domain.OpenDebt
import id.kasirku.umkm.util.Waktu
import kotlinx.coroutines.flow.Flow

class DebtRepository(private val db: AppDatabase) {
    private val dao = db.debtDao()

    fun observeOpen(query: String): Flow<List<CustomerDebt>> = dao.observeOpenByCustomer(query.trim())
    fun observeTotalReceivable(): Flow<Long> = dao.observeTotalReceivable()
    fun observeSales(customerId: Long): Flow<List<DebtSale>> = dao.observeCustomerDebtSales(customerId)
    fun observePayments(customerId: Long): Flow<List<PaymentRow>> = dao.observePayments(customerId)

    /** Terima cicilan dari pelanggan; dibagi ke nota paling lama dulu. Semua tersimpan atau tidak sama sekali. */
    suspend fun receivePayment(customerId: Long, amount: Long, method: String, note: String): RepoResult {
        if (amount <= 0) return RepoResult.Error("Isi jumlah uang yang dibayar.")
        return db.withTransaction<RepoResult> {
            val open = dao.getOpenSalesOldestFirst(customerId).map { OpenDebt(it.sale.id, it.sale.total, it.paidSoFar) }
            if (open.isEmpty()) return@withTransaction RepoResult.Error("Pelanggan ini tidak punya utang.")
            val allocations = try {
                DebtCalc.allocate(open, amount)
            } catch (e: IllegalArgumentException) {
                return@withTransaction RepoResult.Error(e.message ?: "Jumlah bayar tidak valid.")
            }
            val now = Waktu.now()
            for (a in allocations) {
                dao.insertPayment(
                    DebtPayment(saleId = a.saleId, customerId = customerId, amount = a.amount, method = method, note = note.trim(), createdAt = now),
                )
                dao.updateSalePaid(a.saleId, a.newPaid, a.newStatus)
            }
            val left = open.sumOf { it.remaining } - amount
            RepoResult.Ok(
                customerId,
                if (left == 0L) "Pembayaran ${Money.format(amount)} diterima. Utang LUNAS." else "Pembayaran ${Money.format(amount)} diterima. Sisa utang ${Money.format(left)}.",
            )
        }
    }
}

class ExpenseRepository(db: AppDatabase) {
    private val dao = db.expenseDao()

    fun observe(range: Pair<Long, Long>): Flow<List<Expense>> = dao.observe(range.first, range.second)

    suspend fun add(category: String, amount: Long, note: String): RepoResult {
        if (amount <= 0) return RepoResult.Error("Isi jumlah pengeluaran.")
        if (category.isBlank()) return RepoResult.Error("Pilih jenis pengeluaran.")
        val id = dao.insert(Expense(category = category, amount = amount, note = note.trim(), createdAt = Waktu.now()))
        return RepoResult.Ok(id, "Pengeluaran ${Money.format(amount)} dicatat.")
    }

    suspend fun delete(id: Long) = dao.delete(id)
}
