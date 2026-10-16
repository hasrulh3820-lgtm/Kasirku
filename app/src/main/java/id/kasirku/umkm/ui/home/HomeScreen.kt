package id.kasirku.umkm.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import id.kasirku.umkm.data.db.dao.ProductWithCategory
import id.kasirku.umkm.data.repository.DashboardRepository
import id.kasirku.umkm.data.backup.BackupManager
import id.kasirku.umkm.data.repository.ProductRepository
import id.kasirku.umkm.domain.Money
import id.kasirku.umkm.domain.ProfitSummary
import id.kasirku.umkm.domain.Qty
import id.kasirku.umkm.ui.components.BigMenuButton
import id.kasirku.umkm.ui.components.KasirTopBar
import id.kasirku.umkm.ui.components.SectionCard
import id.kasirku.umkm.ui.components.StatTile
import id.kasirku.umkm.ui.kasirApp
import id.kasirku.umkm.ui.theme.Loss
import id.kasirku.umkm.ui.theme.Profit
import id.kasirku.umkm.ui.theme.Warn
import id.kasirku.umkm.util.Waktu
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeState(
    val today: ProfitSummary = ProfitSummary.EMPTY,
    val month: ProfitSummary = ProfitSummary.EMPTY,
    val lowStock: List<ProductWithCategory> = emptyList(),
)

class HomeViewModel(dashboard: DashboardRepository, products: ProductRepository, private val backup: BackupManager) : ViewModel() {
    val lastBackupAt: Long get() = backup.lastBackupAt

    val state: StateFlow<HomeState> = combine(
        dashboard.observeSummary(Waktu.todayRange()),
        dashboard.observeSummary(Waktu.monthRange()),
        products.observeLowStock(),
    ) { today, month, low -> HomeState(today, month, low) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = kasirApp().container
                HomeViewModel(c.dashboardRepo, c.productRepo, c.backup)
            }
        }
    }
}

@Composable
fun HomeScreen(
    onProducts: () -> Unit,
    onAddProduct: () -> Unit,
    onProductClick: (Long) -> Unit,
    onCashier: () -> Unit,
    onSales: () -> Unit,
    onStore: () -> Unit,
    onDebts: () -> Unit,
    onExpenses: () -> Unit,
    onReport: () -> Unit,
    onBackup: () -> Unit,
    vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { KasirTopBar(title = "KasirKu UMKM", subtitle = Waktu.todayLabel()) }) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SummaryCard(title = "Hari ini", s = state.today) }

            val last = vm.lastBackupAt
            val days = if (last == 0L) -1L else java.util.concurrent.TimeUnit.MILLISECONDS.toDays(Waktu.now() - last)
            if (last == 0L || days >= 7) {
                item {
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = Warn)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(
                                    if (last == 0L) "Data belum pernah di-backup" else "Backup terakhir $days hari lalu",
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "Jika HP rusak atau aplikasi terhapus, data hilang tanpa backup.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            TextButton(onClick = onBackup) { Text("Backup") }
                        }
                    }
                }
            }

            item {
                BigMenuButton(
                    icon = Icons.Filled.PointOfSale, title = "Mulai Kasir",
                    subtitle = "Jual per pcs, renteng, atau dus", onClick = onCashier, highlight = true,
                )
            }
            item {
                BigMenuButton(
                    icon = Icons.Filled.Inventory2, title = "Produk dan Stok",
                    subtitle = "Lihat stok, barang masuk, hitung stok", onClick = onProducts,
                )
            }
            item {
                BigMenuButton(
                    icon = Icons.Filled.AddBox, title = "Tambah Barang",
                    subtitle = "Daftarkan barang baru", onClick = onAddProduct,
                )
            }
            item {
                BigMenuButton(
                    icon = Icons.Filled.BarChart, title = "Laporan",
                    subtitle = "Laba, terlaris, stok, ekspor Excel", onClick = onReport,
                )
            }
            item {
                BigMenuButton(
                    icon = Icons.Filled.AccountBalanceWallet, title = "Utang Pelanggan",
                    subtitle = "Catat & terima cicilan utang", onClick = onDebts,
                )
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onSales, modifier = Modifier.weight(1f)) {
                        Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null); Text("  Riwayat Penjualan")
                    }
                    OutlinedButton(onClick = onExpenses, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Payments, contentDescription = null); Text("  Pengeluaran")
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onBackup, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Backup, contentDescription = null); Text("  Backup Data")
                    }
                    OutlinedButton(onClick = onStore, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Storefront, contentDescription = null); Text("  Profil Toko")
                    }
                }
            }

            item { SummaryCard(title = "Bulan ${Waktu.monthLabel()}", s = state.month) }

            if (state.lowStock.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = Warn)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Stok menipis (${state.lowStock.size})",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        )
                    }
                }
                item {
                    SectionCard {
                        Column {
                            state.lowStock.forEachIndexed { i, p ->
                                if (i > 0) HorizontalDivider()
                                LowStockRow(p) { onProductClick(p.id) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(title: String, s: ProfitSummary) {
    SectionCard {
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth()) {
                StatTile("Omzet (penjualan)", Money.format(s.omzet), Modifier.weight(1f))
                StatTile("Jumlah transaksi", s.trxCount.toString(), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                StatTile("Modal barang terjual", Money.format(s.hpp), Modifier.weight(1f))
                StatTile("Laba kotor", Money.format(s.grossProfit), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                StatTile("Pengeluaran lain", Money.format(s.expenses), Modifier.weight(1f))
                StatTile(
                    "Laba bersih", Money.format(s.netProfit), Modifier.weight(1f),
                    valueColor = if (s.netProfit < 0) Loss else Profit, strong = true,
                )
            }
        }
    }
}

@Composable
private fun LowStockRow(p: ProductWithCategory, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(p.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(
            "sisa ${Qty.format(p.stockQty)} ${p.unit}",
            color = if (p.stockQty <= 0) Loss else Warn, fontWeight = FontWeight.Bold,
        )
    }
}
