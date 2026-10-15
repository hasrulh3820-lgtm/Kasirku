package id.kasirku.umkm.ui.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import id.kasirku.umkm.data.db.entity.MovementType
import id.kasirku.umkm.data.db.entity.Product
import id.kasirku.umkm.data.db.entity.StockMovement
import id.kasirku.umkm.data.repository.ProductRepository
import id.kasirku.umkm.data.repository.RepoResult
import id.kasirku.umkm.domain.Money
import id.kasirku.umkm.domain.Qty
import id.kasirku.umkm.ui.components.ConfirmDialog
import id.kasirku.umkm.ui.components.KasirTopBar
import id.kasirku.umkm.ui.components.QtyField
import id.kasirku.umkm.ui.components.SectionCard
import id.kasirku.umkm.ui.components.StatTile
import id.kasirku.umkm.ui.kasirApp
import id.kasirku.umkm.ui.theme.Loss
import id.kasirku.umkm.ui.theme.Profit
import id.kasirku.umkm.util.Waktu
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DetailEvent {
    data class Message(val text: String) : DetailEvent
    data class Removed(val text: String) : DetailEvent
}

class ProductDetailViewModel(private val repo: ProductRepository, handle: SavedStateHandle) : ViewModel() {
    val productId: Long = checkNotNull(handle.get<Long>("id"))

    val product: StateFlow<Product?> = repo.observeProduct(productId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val movements: StateFlow<List<StockMovement>> = repo.observeMovements(productId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = Channel<DetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private var busy = false

    fun adjust(newQtyText: String, reason: String, onDone: () -> Unit) {
        if (busy) return
        val q = Qty.parse(newQtyText)
        if (q == null) {
            viewModelScope.launch { _events.send(DetailEvent.Message("Jumlah stok tidak valid.")) }
            return
        }
        busy = true
        viewModelScope.launch {
            when (val r = repo.adjustStock(productId, q, reason)) {
                is RepoResult.Ok -> { onDone(); _events.send(DetailEvent.Message(r.message)) }
                is RepoResult.Error -> _events.send(DetailEvent.Message(r.message))
            }
            busy = false
        }
    }

    fun delete() {
        if (busy) return
        busy = true
        viewModelScope.launch {
            when (val r = repo.deleteOrArchive(productId)) {
                is RepoResult.Ok -> _events.send(DetailEvent.Removed(r.message))
                is RepoResult.Error -> _events.send(DetailEvent.Message(r.message))
            }
            busy = false
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ProductDetailViewModel(kasirApp().container.productRepo, createSavedStateHandle()) }
        }
    }
}

@Composable
fun ProductDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onStockIn: (Long) -> Unit,
    onRemoved: (String) -> Unit,
    vm: ProductDetailViewModel = viewModel(factory = ProductDetailViewModel.Factory),
) {
    val product by vm.product.collectAsStateWithLifecycle()
    val movements by vm.movements.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showAdjust by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is DetailEvent.Message -> snackbar.showSnackbar(e.text)
                is DetailEvent.Removed -> onRemoved(e.text)
            }
        }
    }

    Scaffold(
        topBar = { KasirTopBar(product?.name ?: "Barang", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        val p = product ?: return@Scaffold
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard {
                    Column {
                        Row(Modifier.fillMaxWidth()) {
                            StatTile("Stok", "${Qty.format(p.stockQty)} ${p.unit}", Modifier.weight(1f), strong = true,
                                valueColor = if (p.stockQty <= p.minStock) Loss else MaterialTheme.colorScheme.onSurface)
                            StatTile("Batas menipis", "${Qty.format(p.minStock)} ${p.unit}", Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth()) {
                            StatTile("Harga jual", Money.format(p.sellPrice), Modifier.weight(1f))
                            StatTile("Modal rata-rata", Money.format(p.costPrice), Modifier.weight(1f))
                        }
                        val margin = p.sellPrice - p.costPrice
                        StatTile("Untung per ${p.unit}", Money.format(margin), valueColor = if (margin < 0) Loss else Profit)
                        val ids = listOfNotNull(p.sku?.let { "SKU $it" }, p.barcode?.let { "Barcode $it" }).joinToString(" · ")
                        if (ids.isNotEmpty()) Text(ids, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Button(onClick = { onStockIn(p.id) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(Icons.Filled.MoveToInbox, contentDescription = null)
                    Text("  Barang Masuk", style = MaterialTheme.typography.titleMedium)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showAdjust = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Tune, contentDescription = null); Text(" Koreksi")
                    }
                    OutlinedButton(onClick = { onEdit(p.id) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Edit, contentDescription = null); Text(" Edit")
                    }
                    OutlinedButton(onClick = { showDelete = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(" Hapus", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            item {
                Text("Riwayat stok", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (movements.isEmpty()) {
                item { Text("Belum ada perubahan stok.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                item {
                    SectionCard {
                        Column {
                            movements.forEachIndexed { i, m ->
                                if (i > 0) HorizontalDivider()
                                MovementRow(m, p.unit)
                            }
                        }
                    }
                }
            }
        }

        if (showAdjust) {
            AdjustDialog(
                currentText = Qty.format(p.stockQty),
                unit = p.unit,
                onDismiss = { showAdjust = false },
                onSave = { qty, reason -> vm.adjust(qty, reason) { showAdjust = false } },
            )
        }
        if (showDelete) {
            ConfirmDialog(
                title = "Hapus barang?",
                message = "\"${p.name}\" akan dihapus. Jika barang ini sudah pernah dibeli atau dijual, barang akan diarsipkan (disembunyikan) agar laporan lama tetap benar.",
                confirmText = "Hapus",
                destructive = true,
                onConfirm = { showDelete = false; vm.delete() },
                onDismiss = { showDelete = false },
            )
        }
    }
}

private fun movementLabel(type: String): String = when (type) {
    MovementType.INITIAL -> "Stok awal"
    MovementType.PURCHASE -> "Barang masuk"
    MovementType.SALE -> "Terjual"
    MovementType.VOID -> "Transaksi batal"
    MovementType.ADJUST -> "Koreksi stok"
    else -> type
}

@Composable
private fun MovementRow(m: StockMovement, unit: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(movementLabel(m.type), fontWeight = FontWeight.SemiBold)
            Text(
                listOf(Waktu.formatDateTime(m.createdAt), m.note).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            val sign = if (m.qtyChange > 0) "+" else ""
            Text("$sign${Qty.format(m.qtyChange)}", fontWeight = FontWeight.Bold, color = if (m.qtyChange < 0) Loss else Profit)
            Text("sisa ${Qty.format(m.qtyAfter)} $unit", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AdjustDialog(currentText: String, unit: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var qty by rememberSaveable { mutableStateOf(currentText) }
    var reason by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Koreksi stok") },
        text = {
            Column {
                Text("Isi jumlah stok sebenarnya setelah dihitung ulang.", style = MaterialTheme.typography.bodyMedium)
                QtyField("Stok sebenarnya", qty, { qty = it }, suffix = unit)
                OutlinedTextField(
                    value = reason, onValueChange = { reason = it },
                    label = { Text("Alasan") }, placeholder = { Text("mis. barang rusak, hitung ulang") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(qty, reason) }) { Text("Simpan", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
