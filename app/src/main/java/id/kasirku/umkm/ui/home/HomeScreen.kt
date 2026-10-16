package id.kasirku.umkm.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import id.kasirku.umkm.data.backup.BackupManager
import id.kasirku.umkm.data.db.dao.ProductWithCategory
import id.kasirku.umkm.data.repository.DashboardRepository
import id.kasirku.umkm.data.repository.DebtRepository
import id.kasirku.umkm.data.repository.ProductRepository
import id.kasirku.umkm.data.repository.SaleRepository
import id.kasirku.umkm.domain.Money
import id.kasirku.umkm.domain.ProfitSummary
import id.kasirku.umkm.domain.Qty
import id.kasirku.umkm.ui.components.SectionCard
import id.kasirku.umkm.ui.components.StatTile
import id.kasirku.umkm.ui.kasirApp
import id.kasirku.umkm.ui.theme.Brand
import id.kasirku.umkm.ui.theme.BrandDark
import id.kasirku.umkm.ui.theme.BrandSoft
import id.kasirku.umkm.ui.theme.Loss
import id.kasirku.umkm.ui.theme.Profit
import id.kasirku.umkm.ui.theme.Warn
import id.kasirku.umkm.ui.theme.WarnSoft
import id.kasirku.umkm.util.Waktu
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.math.abs
import kotlin.math.roundToLong

data class HomeState(
    val storeName: String = "",
    val today: ProfitSummary = ProfitSummary.EMPTY,
    val yesterday: ProfitSummary = ProfitSummary.EMPTY,
    val month: ProfitSummary = ProfitSummary.EMPTY,
    val receivable: Long = 0,
    val lowStock: List<ProductWithCategory> = emptyList(),
)

class HomeViewModel(
    dashboard: DashboardRepository,
    products: ProductRepository,
    sales: SaleRepository,
    debts: DebtRepository,
    private val backup: BackupManager,
) : ViewModel() {
    val lastBackupAt: Long get() = backup.lastBackupAt

    private val summaries = combine(
        dashboard.observeSummary(Waktu.todayRange()),
        dashboard.observeSummary(Waktu.dayRange(1)),
        dashboard.observeSummary(Waktu.monthRange()),
    ) { t, y, m -> Triple(t, y, m) }

    val state: StateFlow<HomeState> = combine(
        summaries, sales.observeProfile(), debts.observeTotalReceivable(), products.observeLowStock(),
    ) { (t, y, m), profile, receivable, low ->
        HomeState(profile.name, t, y, m, receivable, low)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = kasirApp().container
                HomeViewModel(c.dashboardRepo, c.productRepo, c.saleRepo, c.debtRepo, c.backup)
            }
        }
    }
}

/** "naik 12% dari kemarin" / "turun 5% dari kemarin"; null jika kemarin belum ada penjualan. */
private fun trendText(today: Long, yesterday: Long): Pair<String, Boolean>? {
    if (yesterday <= 0) return null
    val pct = ((today - yesterday) * 100.0 / yesterday).roundToLong()
    return when {
        pct > 0 -> "naik $pct% dari kemarin" to true
        pct < 0 -> "turun ${abs(pct)}% dari kemarin" to false
        else -> "sama dengan kemarin" to true
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
    val s by vm.state.collectAsStateWithLifecycle()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        LazyColumn(
            modifier = Modifier.padding(bottom = pad.calculateBottomPadding()).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            // ---------- Header biru + 4 kartu angka yang menumpang ----------
            item {
                Box {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(BrandDark)
                            .statusBarsPadding()
                            .height(150.dp)
                            .padding(start = 20.dp, end = 8.dp, top = 12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                s.storeName.ifBlank { "Aurora Shop" },
                                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = onStore) {
                                Icon(Icons.Filled.Settings, contentDescription = "Profil toko", tint = Color.White)
                            }
                        }
                        Text(Waktu.todayLabel(), color = Color(0xFFC7D4F5), fontSize = 15.sp)
                    }
                    Column(
                        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 90.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val trend = trendText(s.today.omzet, s.yesterday.omzet)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            KpiCard("Omzet hari ini", Money.format(s.today.omzet), Modifier.weight(1f),
                                note = trend?.first, noteColor = if (trend?.second == false) Loss else Profit)
                            KpiCard("Laba bersih hari ini", Money.format(s.today.netProfit), Modifier.weight(1f),
                                valueColor = if (s.today.netProfit < 0) Loss else Profit, note = "setelah modal & biaya")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            KpiCard("Transaksi", s.today.trxCount.toString(), Modifier.weight(1f))
                            KpiCard("Piutang pelanggan", Money.format(s.receivable), Modifier.weight(1f),
                                valueColor = if (s.receivable > 0) Warn else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }

            // ---------- Menu kotak ----------
            item {
                Text("Menu", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 12.dp))
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Card(
                            onClick = onCashier,
                            modifier = Modifier.weight(2.08f).height(150.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Brand, contentColor = Color.White),
                        ) {
                            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Icon(Icons.Filled.PointOfSale, contentDescription = null, modifier = Modifier.size(44.dp))
                                Text("Mulai Kasir", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                        MenuTile(Icons.Filled.Add, "Tambah Barang", onAddProduct, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MenuTile(Icons.Filled.Inventory2, "Produk & Stok", onProducts, Modifier.weight(1f))
                        MenuTile(Icons.Filled.BarChart, "Laporan", onReport, Modifier.weight(1f))
                        MenuTile(Icons.Filled.PersonAdd, "Utang Pelanggan", onDebts, Modifier.weight(1f), warm = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MenuTile(Icons.Filled.Payments, "Pengeluaran", onExpenses, Modifier.weight(1f))
                        MenuTile(Icons.AutoMirrored.Filled.ReceiptLong, "Riwayat Penjualan", onSales, Modifier.weight(1f))
                        MenuTile(Icons.Filled.Backup, "Backup Data", onBackup, Modifier.weight(1f))
                    }
                }
            }

            // ---------- Peringatan ----------
            val last = vm.lastBackupAt
            val days = if (last == 0L) -1L else java.util.concurrent.TimeUnit.MILLISECONDS.toDays(Waktu.now() - last)
            if (last == 0L || days >= 7) {
                item {
                    Banner(
                        text = if (last == 0L) "Data belum pernah di-backup. Jika HP rusak atau aplikasi terhapus, data hilang." else "Backup terakhir $days hari lalu. Sebaiknya backup lagi.",
                        action = "Backup",
                        onAction = onBackup,
                    )
                }
            }
            if (s.lowStock.isNotEmpty()) {
                item {
                    Banner(
                        text = "${s.lowStock.size} barang stoknya menipis: " + s.lowStock.take(3).joinToString(", ") { it.name } +
                            if (s.lowStock.size > 3) ", …" else "",
                        action = "Cek",
                        onAction = onProducts,
                    )
                }
                item {
                    SectionCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Column {
                            s.lowStock.take(5).forEachIndexed { i, p ->
                                if (i > 0) HorizontalDivider()
                                LowStockRow(p) { onProductClick(p.id) }
                            }
                        }
                    }
                }
            }

            // ---------- Ringkasan bulan ----------
            item {
                SectionCard(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Column {
                        Text("Bulan ${Waktu.monthLabel()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth()) {
                            StatTile("Omzet", Money.format(s.month.omzet), Modifier.weight(1f))
                            StatTile("Transaksi", s.month.trxCount.toString(), Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth()) {
                            StatTile("Modal terjual", Money.format(s.month.hpp), Modifier.weight(1f))
                            StatTile("Pengeluaran", Money.format(s.month.expenses), Modifier.weight(1f))
                        }
                        StatTile(
                            "Laba bersih", Money.format(s.month.netProfit),
                            valueColor = if (s.month.netProfit < 0) Loss else Profit, strong = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    note: String? = null,
    noteColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Card(
        modifier = modifier.height(110.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, fontSize = 13.sp, color = Color(0xFF475569), maxLines = 1)
            Text(
                value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = valueColor,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
            )
            if (note != null) Text(note, fontSize = 12.sp, color = noteColor, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun MenuTile(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, warm: Boolean = false) {
    Card(
        onClick = onClick,
        modifier = modifier.height(150.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White, contentColor = MaterialTheme.colorScheme.onSurface),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
    ) {
        Column(
            Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(60.dp).background(if (warm) WarnSoft else BrandSoft, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = if (warm) Warn else Brand, modifier = Modifier.size(30.dp))
            }
            Text(
                label, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                maxLines = 2, modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun Banner(text: String, action: String, onAction: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .background(Color(0xFFFFF7ED), RoundedCornerShape(16.dp))
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = Warn)
        Text(text, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
        TextButton(onClick = onAction) { Text(action, fontWeight = FontWeight.Bold) }
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
            if (p.stockQty <= 0) "habis" else "sisa ${Qty.format(p.stockQty)} ${p.unit}",
            color = if (p.stockQty <= 0) Loss else Warn, fontWeight = FontWeight.Bold,
        )
    }
}
