package id.kasirku.umkm

import android.app.Application
import android.content.Context
import id.kasirku.umkm.data.db.AppDatabase
import id.kasirku.umkm.data.repository.DashboardRepository
import id.kasirku.umkm.data.repository.DebtRepository
import id.kasirku.umkm.data.repository.ExpenseRepository
import id.kasirku.umkm.data.repository.ProductRepository
import id.kasirku.umkm.data.repository.ReportRepository
import id.kasirku.umkm.data.backup.BackupManager
import id.kasirku.umkm.data.repository.SaleRepository

class AppContainer(context: Context) {
    val db: AppDatabase = AppDatabase.get(context)
    val productRepo = ProductRepository(db)
    val dashboardRepo = DashboardRepository(db)
    val saleRepo = SaleRepository(db)
    val debtRepo = DebtRepository(db)
    val expenseRepo = ExpenseRepository(db)
    val reportRepo = ReportRepository(db)
    val backup = BackupManager(context.applicationContext)
}

class KasirKuApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
