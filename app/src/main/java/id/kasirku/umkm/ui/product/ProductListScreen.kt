package id.kasirku.umkm.ui.product

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import id.kasirku.umkm.data.db.dao.ProductWithCategory
import id.kasirku.umkm.data.repository.ProductRepository
import id.kasirku.umkm.domain.Money
import id.kasirku.umkm.domain.Qty
import id.kasirku.umkm.domain.UnitCalc
import id.kasirku.umkm.domain.UnitOption
import id.kasirku.umkm.ui.components.KasirTopBar
import id.kasirku.umkm.ui.kasirApp
import id.kasirku.umkm.ui.theme.Loss
import id.kasirku.umkm.ui.theme.Warn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

class ProductListViewModel(repo: ProductRepository) : ViewModel() {
    val units: StateFlow<Map<Long, List<UnitOption>>> = repo.observeAllUnits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val query = MutableStateFlow("")
    val lowOnly = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val products: StateFlow<List<ProductWithCategory>?> =
        combine(query, lowOnly) { q, low -> q to low }
            .flatMapLatest { (q, low) -> repo.observeList(q, low) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ProductListViewModel(kasirApp().container.productRepo) }
        }
    }
}

@Composable
fun ProductListScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onCount: () -> Unit,
    vm: ProductListViewModel = viewModel(factory = ProductListViewModel.Factory),
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val lowOnly by vm.lowOnly.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val units by vm.units.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { KasirTopBar("Produk dan Stok", onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Tambah Barang") },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                placeholder = { Text("Cari nama, SKU, atau barcode") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Filled.Close, contentDescription = "Hapus pencarian") }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !lowOnly, onClick = { vm.lowOnly.value = false }, label = { Text("Semua") })
                FilterChip(selected = lowOnly, onClick = { vm.lowOnly.value = true }, label = { Text("Stok menipis") })
            }
            OutlinedButton(
                onClick = onCount,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Filled.Checklist, contentDescription = null)
                Text("  Hitung Stok (cocokkan dengan rak)")
            }

            val list = products
            when {
                list == null -> Unit
                list.isEmpty() -> EmptyState(hasFilter = query.isNotEmpty() || lowOnly)
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(list, key = { it.id }) { p -> ProductRow(p, units[p.id].orEmpty()) { onOpen(p.id) } }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(hasFilter: Boolean) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (hasFilter) "Tidak ada barang yang cocok." else "Belum ada barang.",
            style = MaterialTheme.typography.titleMedium,
        )
        if (!hasFilter) {
            Text(
                "Tekan \"Tambah Barang\" di kanan bawah untuk mendaftarkan barang pertama Anda.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ProductRow(p: ProductWithCategory, bigUnits: List<UnitOption>, onClick: () -> Unit) {
    val low = p.stockQty <= p.minStock
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(p.categoryName, p.sku).joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    (listOf("${Money.format(p.sellPrice)}/${p.unit}") + bigUnits.map { "${Money.format(it.sellPrice)}/${it.name}" }).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                )
                if (bigUnits.isNotEmpty() && p.stockQty > 0) {
                    Text(UnitCalc.breakdown(p.stockQty, p.unit, bigUnits), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${Qty.format(p.stockQty)} ${p.unit}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        p.stockQty <= 0 -> Loss
                        low -> Warn
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    if (p.stockQty <= 0) "habis" else if (low) "menipis" else "stok",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
