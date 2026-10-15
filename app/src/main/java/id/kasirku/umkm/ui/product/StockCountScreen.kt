package id.kasirku.umkm.ui.product

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import id.kasirku.umkm.data.db.dao.ProductWithCategory
import id.kasirku.umkm.data.repository.ProductRepository
import id.kasirku.umkm.data.repository.RepoResult
import id.kasirku.umkm.domain.Qty
import id.kasirku.umkm.domain.UnitCalc
import id.kasirku.umkm.domain.UnitOption
import id.kasirku.umkm.ui.components.ConfirmDialog
import id.kasirku.umkm.ui.components.KasirTopBar
import id.kasirku.umkm.ui.components.MultiUnitInput
import id.kasirku.umkm.ui.components.SectionCard
import id.kasirku.umkm.ui.components.multiUnitTotal
import id.kasirku.umkm.ui.kasirApp
import id.kasirku.umkm.ui.theme.Loss
import id.kasirku.umkm.ui.theme.Profit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CountFilter(val label: String) { ALL("Semua"), UNCOUNTED("Belum dihitung"), DIFF("Ada selisih") }

/** Satu baris hasil hitung yang siap disimpan. */
data class CountLine(val product: ProductWithCategory, val units: List<UnitOption>, val counted: Double) {
    val diff: Double get() = Qty.round(counted - product.stockQty)
}

class StockCountViewModel(private val repo: ProductRepository) : ViewModel() {
    val query = MutableStateFlow("")
    val filter = MutableStateFlow(CountFilter.ALL)

    /** productId -> (nama satuan -> teks hasil hitung). Disimpan di ViewModel agar tidak hilang saat layar diputar. */
    val inputs = MutableStateFlow<Map<Long, Map<String, String>>>(emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val products: StateFlow<List<ProductWithCategory>?> = query
        .flatMapLatest { q -> repo.observeList(q, false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val units: StateFlow<Map<Long, List<UnitOption>>> = repo.observeAllUnits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val saving = MutableStateFlow(false)
    private val _messages = Channel<Pair<String, Boolean>>(Channel.BUFFERED)
    /** (pesan, selesai?) */
    val messages = _messages.receiveAsFlow()

    fun setInput(productId: Long, values: Map<String, String>) {
        inputs.update { it + (productId to values) }
    }

    /** Isi hasil hitung sama persis dengan stok di aplikasi (tombol "Cocok"). */
    fun markMatch(p: ProductWithCategory, bigUnits: List<UnitOption>) {
        var rest = Qty.round(p.stockQty.coerceAtLeast(0.0))
        val values = mutableMapOf<String, String>()
        for (u in bigUnits.sortedByDescending { it.factor }) {
            val n = kotlin.math.floor(Qty.round(rest / u.factor) + 1e-9)
            values[u.name] = if (n >= 1) n.toLong().toString() else ""
            rest = Qty.round(rest - n * u.factor)
        }
        values[p.unit] = if (rest > 0 || bigUnits.isEmpty()) Qty.format(rest) else ""
        setInput(p.id, values)
    }

    fun clear(productId: Long) = inputs.update { it - productId }

    fun lines(products: List<ProductWithCategory>, units: Map<Long, List<UnitOption>>, inputs: Map<Long, Map<String, String>>): List<CountLine> =
        products.mapNotNull { p ->
            val u = units[p.id].orEmpty()
            val total = inputs[p.id]?.let { multiUnitTotal(p.unit, u, it) } ?: return@mapNotNull null
            CountLine(p, u, total)
        }

    fun save(lines: List<CountLine>) {
        if (saving.value || lines.isEmpty()) return
        saving.value = true
        viewModelScope.launch {
            when (val r = repo.saveStockCount(lines.associate { it.product.id to it.counted })) {
                is RepoResult.Ok -> { inputs.value = emptyMap(); _messages.send(r.message to true) }
                is RepoResult.Error -> _messages.send(r.message to false)
            }
            saving.value = false
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { StockCountViewModel(kasirApp().container.productRepo) }
        }
    }
}

@Composable
fun StockCountScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    vm: StockCountViewModel = viewModel(factory = StockCountViewModel.Factory),
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val units by vm.units.collectAsStateWithLifecycle()
    val inputs by vm.inputs.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var confirmSave by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.messages.collect { (msg, done) -> if (done) onSaved(msg) else snackbar.showSnackbar(msg) }
    }

    val all = products.orEmpty()
    val lines = vm.lines(all, units, inputs)
    val diffs = lines.filter { it.diff != 0.0 }
    val tryLeave: () -> Unit = {
        if (lines.isNotEmpty()) {
            confirmLeave = true
        } else {
            onBack()
        }
    }
    BackHandler(onBack = tryLeave)

    Scaffold(
        topBar = { KasirTopBar("Hitung Stok", onBack = tryLeave, subtitle = "Cocokkan barang di rak dengan aplikasi") },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${lines.size} dari ${all.size} barang dihitung", fontWeight = FontWeight.Bold)
                        Text(
                            if (diffs.isEmpty()) "Belum ada selisih" else "${diffs.size} barang selisih",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (diffs.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else Loss,
                        )
                    }
                    Button(
                        onClick = { confirmSave = true },
                        enabled = lines.isNotEmpty() && !saving,
                        modifier = Modifier.height(52.dp),
                    ) { Text(if (saving) "Menyimpan…" else "Simpan") }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                placeholder = { Text("Cari barang") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Filled.Close, contentDescription = "Hapus pencarian") }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CountFilter.entries.forEach { f ->
                    FilterChip(selected = filter == f, onClick = { vm.filter.value = f }, label = { Text(f.label) })
                }
            }

            val counted = lines.associateBy { it.product.id }
            val visible = all.filter { p ->
                when (filter) {
                    CountFilter.ALL -> true
                    CountFilter.UNCOUNTED -> p.id !in counted
                    CountFilter.DIFF -> counted[p.id]?.let { it.diff != 0.0 } == true
                }
            }

            if (products != null && visible.isEmpty()) {
                Text(
                    when (filter) {
                        CountFilter.UNCOUNTED -> "Semua barang sudah dihitung."
                        CountFilter.DIFF -> "Tidak ada selisih."
                        CountFilter.ALL -> "Belum ada barang."
                    },
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visible, key = { it.id }) { p ->
                    val bigUnits = units[p.id].orEmpty()
                    CountRow(
                        p = p,
                        bigUnits = bigUnits,
                        values = inputs[p.id].orEmpty(),
                        line = counted[p.id],
                        onChange = { vm.setInput(p.id, it) },
                        onMatch = { vm.markMatch(p, bigUnits) },
                        onClear = { vm.clear(p.id) },
                    )
                }
            }
        }
    }

    if (confirmLeave) {
        ConfirmDialog(
            title = "Keluar tanpa menyimpan?",
            message = "Hasil hitung ${lines.size} barang belum disimpan dan akan hilang.",
            confirmText = "Keluar",
            destructive = true,
            onConfirm = { confirmLeave = false; onBack() },
            onDismiss = { confirmLeave = false },
        )
    }
    if (confirmSave) {
        AlertDialog(
            onDismissRequest = { confirmSave = false },
            title = { Text("Simpan hasil hitung?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("${lines.size} barang dihitung. ${lines.size - diffs.size} cocok, ${diffs.size} akan dikoreksi:")
                    diffs.take(30).forEach { l ->
                        val sign = if (l.diff > 0) "+" else ""
                        Text(
                            "• ${l.product.name}: $sign${Qty.format(l.diff)} ${l.product.unit}",
                            color = if (l.diff < 0) Loss else Profit,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (diffs.size > 30) Text("…dan ${diffs.size - 30} lainnya")
                    Text(
                        "Stok di aplikasi akan disamakan dengan hasil hitung dan tercatat di riwayat stok.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { confirmSave = false; vm.save(lines) }) { Text("Simpan", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { confirmSave = false }) { Text("Batal") } },
        )
    }
}

@Composable
private fun CountRow(
    p: ProductWithCategory,
    bigUnits: List<UnitOption>,
    values: Map<String, String>,
    line: CountLine?,
    onChange: (Map<String, String>) -> Unit,
    onMatch: () -> Unit,
    onClear: () -> Unit,
) {
    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val inApp = UnitCalc.breakdown(p.stockQty, p.unit, bigUnits)
                    Text(
                        "Di aplikasi: $inApp" + if (bigUnits.isNotEmpty()) " (${Qty.format(p.stockQty)} ${p.unit})" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (line == null) {
                    TextButton(onClick = onMatch) { Text("Cocok ✓") }
                } else {
                    TextButton(onClick = onClear) { Text("Ulangi") }
                }
            }
            MultiUnitInput(baseUnit = p.unit, bigUnits = bigUnits, values = values, onChange = onChange)
            if (line != null) {
                val d = line.diff
                val text = when {
                    d == 0.0 -> "Cocok dengan aplikasi"
                    d < 0 -> "Kurang ${Qty.format(-d)} ${p.unit} (hitung: ${Qty.format(line.counted)} ${p.unit})"
                    else -> "Lebih ${Qty.format(d)} ${p.unit} (hitung: ${Qty.format(line.counted)} ${p.unit})"
                }
                Text(
                    text,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        d == 0.0 -> Profit
                        d < 0 -> Loss
                        else -> MaterialTheme.colorScheme.tertiary
                    },
                )
            }
        }
    }
}
