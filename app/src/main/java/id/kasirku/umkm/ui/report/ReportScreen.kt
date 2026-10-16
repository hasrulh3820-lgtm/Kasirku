package id.kasirku.umkm.ui.report

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import id.kasirku.umkm.data.repository.Report
import id.kasirku.umkm.data.repository.ReportRepository
import id.kasirku.umkm.domain.Money
import id.kasirku.umkm.domain.PayMethod
import id.kasirku.umkm.domain.Qty
import id.kasirku.umkm.ui.components.KasirTopBar
import id.kasirku.umkm.ui.components.SectionCard
import id.kasirku.umkm.ui.components.StatTile
import id.kasirku.umkm.ui.kasirApp
import id.kasirku.umkm.ui.theme.Loss
import id.kasirku.umkm.ui.theme.Profit
import id.kasirku.umkm.ui.theme.Warn
import id.kasirku.umkm.util.Waktu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ReportRange(val label: String) {
    TODAY("Hari ini"), YESTERDAY("Kemarin"), WEEK("7 hari"), MONTH("Bulan ini"), LAST_MONTH("Bulan lalu");

    fun range(): Pair<Long, Long> = when (this) {
        TODAY -> Waktu.todayRange()
        YESTERDAY -> Waktu.dayRange(1)
        WEEK -> Waktu.lastDaysRange(7)
        MONTH -> Waktu.monthRange()
        LAST_MONTH -> Waktu.lastMonthRange()
    }
}

enum class CsvKind(val fileName: String) {
    SALES("transaksi"), PRODUCTS("barang-terjual"), STOCK("stok"), EXPENSES("pengeluaran"), DEBTS("utang")
}

class ReportViewModel(private val repo: ReportRepository) : ViewModel() {
    val range = MutableStateFlow(ReportRange.TODAY)

    @OptIn(ExperimentalCoroutinesApi::class)
    val report: StateFlow<Report?> = range.flatMapLatest { repo.observe(it.range()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun export(context: android.content.Context, uri: Uri, kind: CsvKind) {
        val r = report.value ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val text = when (kind) {
                        CsvKind.SALES -> repo.salesCsv(r)
                        CsvKind.PRODUCTS -> repo.productSalesCsv(r)
                        CsvKind.STOCK -> repo.stockCsv()
                        CsvKind.EXPENSES -> repo.expensesCsv(r)
                        CsvKind.DEBTS -> repo.debtsCsv()
                    }
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        ?: error("tidak bisa menulis file")
                }.isSuccess
            }
            _messages.send(if (ok) "File CSV disimpan." else "Gagal menyimpan file. Coba pilih folder lain.")
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ReportViewModel(kasirApp().container.reportRepo) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportScreen(
    onBack: () -> Unit,
    onDebts: () -> Unit,
    onProducts: () -> Unit,
    vm: ReportViewModel = viewModel(factory = ReportViewModel.Factory),
) {
    val range by vm.range.collectAsStateWithLifecycle()
    val report by vm.report.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var pendingKind by rememberSaveable { mutableStateOf<CsvKind?>(null) }
    var productSortQty by rememberSaveable { mutableStateOf(false) }
    var showAllProducts by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val kind = pendingKind
        if (uri != null && kind != null) vm.export(context, uri, kind)
        pendingKind = null
    }
    fun export(kind: CsvKind) {
        pendingKind = kind
        saveCsv.launch("KasirKu-${kind.fileName}-${range.name.lowercase()}-${Waktu.fileStamp()}.csv")
    }

    Scaffold(
        topBar = { KasirTopBar("Laporan", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        val r = report
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReportRange.entries.forEach { x ->
                        FilterChip(selected = range == x, onClick = { vm.range.value = x }, label = { Text(x.label) })
                    }
                }
            }
            if (r == null) return@LazyColumn

            // 1. Laba
            item {
                SectionCard {
                    Column {
                        Title("Penjualan & Laba")
                        Row(Modifier.fillMaxWidth()) {
                            StatTile("Omzet", Money.format(r.summary.omzet), Modifier.weight(1f), strong = true)
                            StatTile("Transaksi", r.summary.trxCount.toString(), Modifier.weight(1f))
                        }
                        Line("Modal barang terjual (HPP)", "-" + Money.format(r.summary.hpp))
                        Line("Laba kotor", Money.format(r.summary.grossProfit), bold = true)
                        Line("Pengeluaran", "-" + Money.format(r.expenseTotal))
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Line("LABA BERSIH", Money.format(r.netProfit), bold = true, color = if (r.netProfit < 0) Loss else Profit)
                        Spacer6()
                        Note(
                            "Rata-rata per transaksi ${Money.format(r.summary.average)} · diskon diberikan ${Money.format(r.summary.discountTotal)}" +
                                if (r.summary.voidCount > 0) " · ${r.summary.voidCount} transaksi batal (tidak dihitung)" else "",
                        )
                        Note("Laba bersih = omzet setelah diskon − modal barang terjual − pengeluaran.")
                    }
                }
            }

            // 2. Kas masuk
            item {
                SectionCard {
                    Column {
                        Title("Uang masuk")
                        if (r.cashIn.isEmpty()) Note("Belum ada uang masuk.")
                        r.cashIn.forEach { (m, v) -> Line(PayMethod.label(m), Money.format(v)) }
                        if (r.cashIn.isNotEmpty()) {
                            HorizontalDivider(Modifier.padding(vertical = 6.dp))
                            Line("Total", Money.format(r.cashIn.sumOf { it.second }), bold = true)
                        }
                        if (r.debtPaid > 0) Note("Termasuk DP & cicilan utang ${Money.format(r.debtPaid)}.")
                        Note("Penjualan utang yang belum dibayar tidak termasuk di sini.")
                    }
                }
            }

            // 3. Per hari
            if (r.days.size > 1) {
                item {
                    SectionCard {
                        Column {
                            Title("Per hari")
                            Row(Modifier.fillMaxWidth()) {
                                Text("Tanggal", Modifier.weight(1.3f), style = MaterialTheme.typography.labelMedium)
                                Text("Trx", Modifier.weight(0.5f), style = MaterialTheme.typography.labelMedium)
                                Text("Omzet", Modifier.weight(1.2f), style = MaterialTheme.typography.labelMedium)
                                Text("Laba kotor", Modifier.weight(1.2f), style = MaterialTheme.typography.labelMedium)
                            }
                            r.days.forEach { d ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                    Text(Waktu.dayLabel(d.dayKey), Modifier.weight(1.3f))
                                    Text(d.trxCount.toString(), Modifier.weight(0.5f))
                                    Text(Money.format(d.omzet), Modifier.weight(1.2f))
                                    Text(Money.format(d.profit), Modifier.weight(1.2f), color = if (d.profit < 0) Loss else Profit)
                                }
                            }
                        }
                    }
                }
            }

            // 4. Barang terlaris
            item {
                SectionCard {
                    Column {
                        Title("Barang terlaris")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = !productSortQty, onClick = { productSortQty = false }, label = { Text("Omzet") })
                            FilterChip(selected = productSortQty, onClick = { productSortQty = true }, label = { Text("Jumlah") })
                        }
                        val sorted = if (productSortQty) r.products.sortedByDescending { it.baseQty } else r.products
                        if (sorted.isEmpty()) Note("Belum ada barang terjual.")
                        val shown = if (showAllProducts) sorted else sorted.take(10)
                        shown.forEachIndexed { i, p ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text("${i + 1}. ${p.name}", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${Qty.format(p.baseQty)} ${p.baseUnit} · laba ${Money.format(p.profit)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (p.profit < 0) Loss else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(Money.format(p.omzet), fontWeight = FontWeight.Bold)
                            }
                        }
                        if (sorted.size > 10) {
                            TextButton(onClick = { showAllProducts = !showAllProducts }) {
                                Text(if (showAllProducts) "Tampilkan 10 teratas" else "Tampilkan semua (${sorted.size})")
                            }
                        }
                        Note("Angka per barang sebelum diskon transaksi.")
                    }
                }
            }

            // 5. Stok, utang, pengeluaran
            item {
                SectionCard {
                    Column {
                        Title("Stok saat ini")
                        Line("Jumlah jenis barang", r.stock.itemCount.toString())
                        Line("Nilai stok (harga modal)", Money.format(r.stock.stockValue), bold = true)
                        Line("Stok menipis", r.stock.lowCount.toString(), color = if (r.stock.lowCount > 0) Warn else null)
                        Line("Stok habis", r.stock.outCount.toString(), color = if (r.stock.outCount > 0) Loss else null)
                        TextButton(onClick = onProducts) { Text("Lihat Produk dan Stok") }
                    }
                }
            }
            item {
                SectionCard {
                    Column {
                        Title("Utang pelanggan")
                        Line("Total piutang (semua waktu)", Money.format(r.receivable), bold = true, color = if (r.receivable > 0) Warn else null)
                        TextButton(onClick = onDebts) { Text("Lihat Utang Pelanggan") }
                    }
                }
            }
            item {
                SectionCard {
                    Column {
                        Title("Pengeluaran")
                        if (r.expenses.isEmpty()) Note("Tidak ada pengeluaran pada periode ini.")
                        r.expenses.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
                            .entries.sortedByDescending { it.value }
                            .forEach { (c, v) -> Line(c, Money.format(v)) }
                        if (r.expenses.isNotEmpty()) {
                            HorizontalDivider(Modifier.padding(vertical = 6.dp))
                            Line("Total", Money.format(r.expenseTotal), bold = true)
                        }
                    }
                }
            }

            // 6. Ekspor
            item {
                SectionCard {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Title("Ekspor ke Excel (CSV)")
                        Note("File bisa dibuka di Excel, WPS, atau Google Sheets. Pilih folder Download atau Google Drive saat menyimpan.")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { export(CsvKind.SALES) }) { Text("Transaksi") }
                            OutlinedButton(onClick = { export(CsvKind.PRODUCTS) }) { Text("Barang terjual") }
                            OutlinedButton(onClick = { export(CsvKind.STOCK) }) { Text("Stok") }
                            OutlinedButton(onClick = { export(CsvKind.EXPENSES) }) { Text("Pengeluaran") }
                            OutlinedButton(onClick = { export(CsvKind.DEBTS) }) { Text("Utang") }
                        }
                        Note("Ekspor PDF belum tersedia di versi ini.")
                    }
                }
            }
        }
    }
}

@Composable
private fun Title(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))

@Composable
private fun Note(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))

@Composable
private fun Spacer6() = androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 6.dp))

@Composable
private fun Line(label: String, value: String, bold: Boolean = false, color: androidx.compose.ui.graphics.Color? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(1f), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(
            value,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = color ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}
