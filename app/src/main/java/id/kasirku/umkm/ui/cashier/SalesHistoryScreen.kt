package id.kasirku.umkm.ui.cashier

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import id.kasirku.umkm.data.db.dao.SaleRow
import id.kasirku.umkm.data.repository.SaleRepository
import id.kasirku.umkm.domain.Money
import id.kasirku.umkm.domain.PayMethod
import id.kasirku.umkm.domain.SaleState
import id.kasirku.umkm.ui.components.KasirTopBar
import id.kasirku.umkm.ui.components.SectionCard
import id.kasirku.umkm.ui.components.StatTile
import id.kasirku.umkm.ui.kasirApp
import id.kasirku.umkm.ui.theme.Loss
import id.kasirku.umkm.ui.theme.Profit
import id.kasirku.umkm.ui.theme.Warn
import id.kasirku.umkm.util.Waktu
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

enum class HistoryRange(val label: String) { TODAY("Hari ini"), YESTERDAY("Kemarin"), WEEK("7 hari") }

class SalesHistoryViewModel(repo: SaleRepository) : ViewModel() {
    val range = MutableStateFlow(HistoryRange.TODAY)

    @OptIn(ExperimentalCoroutinesApi::class)
    val sales: StateFlow<List<SaleRow>?> = range.flatMapLatest { r ->
        repo.observeSales(
            when (r) {
                HistoryRange.TODAY -> Waktu.todayRange()
                HistoryRange.YESTERDAY -> Waktu.dayRange(1)
                HistoryRange.WEEK -> Waktu.lastDaysRange(7)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { SalesHistoryViewModel(kasirApp().container.saleRepo) }
        }
    }
}

@Composable
fun SalesHistoryScreen(
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    vm: SalesHistoryViewModel = viewModel(factory = SalesHistoryViewModel.Factory),
) {
    val range by vm.range.collectAsStateWithLifecycle()
    val sales by vm.sales.collectAsStateWithLifecycle()

    Scaffold(topBar = { KasirTopBar("Riwayat Penjualan", onBack = onBack) }) { pad ->
        val list = sales.orEmpty()
        val valid = list.filter { it.sale.status != SaleState.VOID }
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HistoryRange.entries.forEach { r ->
                        FilterChip(selected = range == r, onClick = { vm.range.value = r }, label = { Text(r.label) })
                    }
                }
            }
            item {
                SectionCard {
                    Row(Modifier.fillMaxWidth()) {
                        StatTile("Transaksi", valid.size.toString(), Modifier.weight(1f))
                        StatTile("Omzet", Money.format(valid.sumOf { it.sale.total }), Modifier.weight(1f))
                        StatTile(
                            "Laba kotor", Money.format(valid.sumOf { it.sale.total - it.sale.costTotal }), Modifier.weight(1f),
                            valueColor = Profit,
                        )
                    }
                }
            }
            if (sales != null && list.isEmpty()) {
                item { Text("Belum ada transaksi.", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium) }
            }
            items(list, key = { it.sale.id }) { row -> SaleRowCard(row, range != HistoryRange.TODAY) { onOpen(row.sale.id) } }
        }
    }
}

@Composable
private fun SaleRowCard(row: SaleRow, showDate: Boolean, onClick: () -> Unit) {
    val s = row.sale
    val void = s.status == SaleState.VOID
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.invoiceNo, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(
                        if (showDate) Waktu.formatDateTime(s.createdAt) else Waktu.formatTime(s.createdAt),
                        PayMethod.label(s.paymentMethod),
                        row.customerName,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Money.format(s.total),
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (void) TextDecoration.LineThrough else null,
                )
                Text(
                    SaleState.label(s.status),
                    style = MaterialTheme.typography.bodySmall,
                    color = when (s.status) {
                        SaleState.VOID -> Loss
                        SaleState.PAID -> Profit
                        else -> Warn
                    },
                )
            }
        }
    }
}
