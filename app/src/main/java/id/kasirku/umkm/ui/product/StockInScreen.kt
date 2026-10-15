package id.kasirku.umkm.ui.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import id.kasirku.umkm.data.db.entity.Product
import id.kasirku.umkm.data.repository.ProductRepository
import id.kasirku.umkm.data.repository.RepoResult
import id.kasirku.umkm.domain.Money
import id.kasirku.umkm.domain.Qty
import id.kasirku.umkm.ui.components.KasirTopBar
import id.kasirku.umkm.ui.components.MoneyField
import id.kasirku.umkm.ui.components.QtyField
import id.kasirku.umkm.ui.components.SectionCard
import id.kasirku.umkm.ui.kasirApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

data class StockInUi(val saving: Boolean = false, val error: String? = null, val done: String? = null)

class StockInViewModel(private val repo: ProductRepository, handle: SavedStateHandle) : ViewModel() {
    val productId: Long = checkNotNull(handle.get<Long>("id"))
    val product: StateFlow<Product?> = repo.observeProduct(productId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _ui = MutableStateFlow(StockInUi())
    val ui: StateFlow<StockInUi> = _ui.asStateFlow()

    fun save(qtyText: String, costText: String, supplier: String, note: String) {
        if (_ui.value.saving) return
        val qty = Qty.parse(qtyText)
        val cost = Money.parse(costText)
        when {
            qty == null || qty <= 0 -> { _ui.value = StockInUi(error = "Isi jumlah barang masuk."); return }
            cost == null -> { _ui.value = StockInUi(error = "Isi harga beli per satuan."); return }
        }
        _ui.value = StockInUi(saving = true)
        viewModelScope.launch {
            _ui.value = when (val r = repo.stockIn(productId, qty!!, cost!!, supplier, note)) {
                is RepoResult.Ok -> StockInUi(done = r.message)
                is RepoResult.Error -> StockInUi(error = r.message)
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { StockInViewModel(kasirApp().container.productRepo, createSavedStateHandle()) }
        }
    }
}

@Composable
fun StockInScreen(
    onBack: () -> Unit,
    onDone: (String) -> Unit,
    vm: StockInViewModel = viewModel(factory = StockInViewModel.Factory),
) {
    val product by vm.product.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(ui.done) { ui.done?.let(onDone) }

    Scaffold(topBar = { KasirTopBar("Barang Masuk", onBack = onBack, subtitle = product?.name) }) { pad ->
        val p = product ?: return@Scaffold
        var qty by rememberSaveable { mutableStateOf("") }
        var cost by rememberSaveable { mutableStateOf(p.costPrice.takeIf { it > 0 }?.toString() ?: "") }
        var supplier by rememberSaveable { mutableStateOf("") }
        var note by rememberSaveable { mutableStateOf("") }

        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Stok sekarang: ${Qty.format(p.stockQty)} ${p.unit} · modal ${Money.format(p.costPrice)}/${p.unit}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            QtyField("Jumlah masuk", qty, { qty = it }, suffix = p.unit)
            MoneyField("Harga beli per ${p.unit}", cost, { cost = it })
            OutlinedTextField(
                value = supplier, onValueChange = { supplier = it }, label = { Text("Pemasok (boleh kosong)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = note, onValueChange = { note = it }, label = { Text("Catatan (boleh kosong)") },
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )

            val q = Qty.parse(qty)
            val c = Money.parse(cost)
            if (q != null && q > 0 && c != null) {
                val newCost = Money.weightedAverageCost(p.stockQty, p.costPrice, q, c)
                SectionCard {
                    Column {
                        Text("Ringkasan", fontWeight = FontWeight.Bold)
                        Text("Total belanja: ${Money.format((q * c).roundToLong())}")
                        Text("Stok menjadi: ${Qty.format(p.stockQty + q)} ${p.unit}")
                        Text("Modal rata-rata menjadi: ${Money.format(newCost)}/${p.unit}")
                        Text(
                            "Belanja barang tidak dihitung sebagai pengeluaran; modalnya baru dihitung saat barang terjual.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }

            Button(
                onClick = { vm.save(qty, cost, supplier, note) },
                enabled = !ui.saving && ui.done == null,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(56.dp),
            ) {
                Text(if (ui.saving) "Menyimpan…" else "Simpan Barang Masuk", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
