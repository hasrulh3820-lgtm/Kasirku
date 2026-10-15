package id.kasirku.umkm.data.repository

import id.kasirku.umkm.data.db.AppDatabase
import id.kasirku.umkm.domain.ProfitSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class DashboardRepository(db: AppDatabase) {

    private val dao = db.dashboardDao()

    fun observeSummary(range: Pair<Long, Long>): Flow<ProfitSummary> =
        combine(dao.observeSales(range.first, range.second), dao.observeExpenses(range.first, range.second)) { s, e ->
            ProfitSummary(omzet = s.omzet, hpp = s.hpp, expenses = e, trxCount = s.trxCount)
        }
}
