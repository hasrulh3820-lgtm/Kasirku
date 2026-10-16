package id.kasirku.umkm.ui.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.Alignment
import id.kasirku.umkm.ui.components.SectionCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import id.kasirku.umkm.data.repository.ProductInput
import id.kasirku.umkm.data.repository.ProductRepository
import id.kasirku.umkm.data.repository.RepoResult
import id.kasirku.umkm.domain.Money
import id.kasirku.umkm.domain.Qty
import id.kasirku.umkm.domain.UnitCalc
import id.kasirku.umkm.domain.UnitOption
import id.kasirku.umkm.ui.components.KasirTopBar
import id.kasirku.umkm.ui.components.MoneyField
import id.kasirku.umkm.ui.components.QtyField
import id.kasirku.umkm.ui.kasirApp
import id.kasirku.umkm.ui.theme.Warn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

val UNITS = listOf("pcs", "bungkus", "botol", "sachet", "pack", "kg", "gram", "liter", "pasang", "meter", "lembar", "biji")
val BIG_UNITS = listOf("renteng", "pak", "slop", "lusin", "dus", "karton", "bal", "karung", "kodi")

/** Isian satuan besar di form (masih teks, divalidasi saat Simpan). */
data class BigUnitDraft(val name: String = "", val factor: String = "", val price: String = "")

data class ProductForm(
    val name: String = "",
    val sku: String = "",
    val barcode: String = "",
    val category: String = "",
    val unit: String = "pcs",
    val cost: String = "",
    val sell: String = "",
    val initialStock: String = "",
    val minStock: String = "5",
    val bigUnits: List<BigUnitDraft> = emptyList(),
    /** Hanya untuk tampilan saat edit. */
    val currentStock: Double = 0.0,
)

data class FormUi(
    val form: ProductForm = ProductForm(),
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val errors: Map<String, String> = emptyMap(),
    val generalError: String? = null,
    val savedMessage: String? = null,
)

class ProductFormViewModel(private val repo: ProductRepository, handle: SavedStateHandle) : ViewModel() {
    val productId: Long? = handle.get<Long>("id")?.takeIf { it > 0 }
    val isEdit get() = productId != null

    private val _ui = MutableStateFlow(FormUi(loaded = productId == null))
    val ui: StateFlow<FormUi> = _ui.asStateFlow()

    val categories: StateFlow<List<String>> = repo.observeCategories()
        .map { list -> list.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (productId != null) {
            viewModelScope.launch {
                val p = repo.getProduct(productId)
                if (p == null) {
                    _ui.update { it.copy(loaded = true, generalError = "Barang tidak ditemukan.") }
                } else {
                    val units = repo.getUnits(p.id)
                    _ui.update {
                        it.copy(
                            loaded = true,
                            form = ProductForm(
                                name = p.name, sku = p.sku.orEmpty(), barcode = p.barcode.orEmpty(),
                                category = repo.categoryName(p.categoryId), unit = p.unit,
                                cost = p.costPrice.toString(), sell = p.sellPrice.toString(),
                                minStock = Qty.format(p.minStock), currentStock = p.stockQty,
                                bigUnits = units.map { u -> BigUnitDraft(u.name, Qty.format(u.factor), u.sellPrice.toString()) },
                            ),
                        )
                    }
                }
            }
        }
    }

    fun edit(transform: (ProductForm) -> ProductForm) {
        _ui.update { it.copy(form = transform(it.form), generalError = null) }
    }

    fun save() {
        val state = _ui.value
        if (state.saving) return // cegah simpan ganda
        val f = state.form
        val errors = mutableMapOf<String, String>()
        if (f.name.isBlank()) errors["name"] = "Nama barang wajib diisi"
        val sell = Money.parse(f.sell)
        if (sell == null || sell <= 0) errors["sell"] = "Harga jual wajib diisi"
        val cost = if (f.cost.isBlank()) 0L else Money.parse(f.cost)
        if (cost == null) errors["cost"] = "Harga modal tidak valid"
        val initial = if (isEdit || f.initialStock.isBlank()) 0.0 else Qty.parse(f.initialStock)
        if (initial == null || initial < 0) errors["initialStock"] = "Stok awal tidak valid"
        val min = if (f.minStock.isBlank()) 0.0 else Qty.parse(f.minStock)
        if (min == null || min < 0) errors["minStock"] = "Batas stok tidak valid"
        val bigUnits = f.bigUnits.mapIndexed { i, d ->
            val factor = Qty.parse(d.factor)
            val price = Money.parse(d.price)
            if (factor == null) errors["unitFactor$i"] = "Isi wajib diisi"
            if (price == null) errors["unitPrice$i"] = "Harga wajib diisi"
            UnitOption(name = d.name.trim(), factor = factor ?: 0.0, sellPrice = price ?: 0L)
        }
        if (errors.isEmpty()) UnitCalc.validate(f.unit, bigUnits)?.let { errors["units"] = it }
        if (errors.isNotEmpty()) {
            _ui.update { it.copy(errors = errors, generalError = errors["units"] ?: "Periksa kembali isian yang berwarna merah.") }
            return
        }
        _ui.update { it.copy(saving = true, errors = emptyMap(), generalError = null) }
        viewModelScope.launch {
            val result = repo.saveProduct(
                ProductInput(
                    id = productId, name = f.name, sku = f.sku, barcode = f.barcode, categoryName = f.category,
                    unit = f.unit, costPrice = cost!!, sellPrice = sell!!, initialStock = initial!!, minStock = min!!,
                    bigUnits = bigUnits,
                ),
            )
            when (result) {
                is RepoResult.Ok -> _ui.update { it.copy(saving = false, savedMessage = result.message) }
                is RepoResult.Error -> _ui.update { it.copy(saving = false, generalError = result.message) }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ProductFormViewModel(kasirApp().container.productRepo, createSavedStateHandle()) }
        }
    }
}

@Composable
fun ProductFormScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    vm: ProductFormViewModel = viewModel(factory = ProductFormViewModel.Factory),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    var showMore by rememberSaveable { mutableStateOf(vm.isEdit) }

    LaunchedEffect(ui.savedMessage) { if (ui.savedMessage != null) onSaved() }

    Scaffold(topBar = { KasirTopBar(if (vm.isEdit) "Edit Barang" else "Tambah Barang", onBack = onBack) }) { pad ->
        if (!ui.loaded) return@Scaffold
        val f = ui.form
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OutlinedTextField(
                value = f.name, onValueChange = { v -> vm.edit { it.copy(name = v) } },
                label = { Text("Nama barang *") }, singleLine = true,
                isError = ui.errors.containsKey("name"),
                supportingText = { ui.errors["name"]?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            MoneyField("Harga jual per ${f.unit} (eceran) *", f.sell, { v -> vm.edit { it.copy(sell = v) } }, error = ui.errors["sell"])
            MoneyField("Harga modal per ${f.unit}", f.cost, { v -> vm.edit { it.copy(cost = v) } }, error = ui.errors["cost"])

            val sell = Money.parse(f.sell)
            val cost = Money.parse(f.cost)
            if (sell != null && cost != null && cost > sell) {
                Text("Perhatian: harga jual lebih rendah dari modal.", color = Warn, style = MaterialTheme.typography.bodySmall)
            }

            Text("Satuan terkecil / eceran (biasanya pcs)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(UNITS) { u ->
                    FilterChip(selected = f.unit == u, onClick = { vm.edit { it.copy(unit = u) } }, label = { Text(u) })
                }
            }

            BigUnitsSection(
                baseUnit = f.unit,
                baseCost = Money.parse(f.cost) ?: 0L,
                baseSell = Money.parse(f.sell),
                drafts = f.bigUnits,
                errors = ui.errors,
                onChange = { list -> vm.edit { it.copy(bigUnits = list) } },
            )

            if (vm.isEdit) {
                Text(
                    "Stok sekarang: ${Qty.format(f.currentStock)} ${f.unit}. Ubah stok lewat \"Barang Masuk\" atau \"Koreksi Stok\" di halaman barang.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                QtyField("Stok awal (dalam ${f.unit})", f.initialStock, { v -> vm.edit { it.copy(initialStock = v) } }, suffix = f.unit, error = ui.errors["initialStock"])
                val initial = Qty.parse(f.initialStock)
                val previewUnits = f.bigUnits.mapNotNull { d -> Qty.parse(d.factor)?.takeIf { it > 1 }?.let { UnitOption(d.name.ifBlank { "?" }, it, 0) } }
                if (initial != null && initial > 0 && previewUnits.isNotEmpty()) {
                    Text("= ${UnitCalc.breakdown(initial, f.unit, previewUnits)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (!showMore) {
                TextButton(onClick = { showMore = true }) { Text("+ Isian lainnya (kategori, SKU, barcode, batas stok)") }
            } else {
                OutlinedTextField(
                    value = f.category, onValueChange = { v -> vm.edit { it.copy(category = v) } },
                    label = { Text("Kategori") }, singleLine = true,
                    placeholder = { Text("mis. Sembako, Minuman") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (categories.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        items(categories) { c ->
                            FilterChip(selected = f.category.equals(c, ignoreCase = true), onClick = { vm.edit { it.copy(category = c) } }, label = { Text(c) })
                        }
                    }
                }
                QtyField("Batas stok menipis", f.minStock, { v -> vm.edit { it.copy(minStock = v) } }, suffix = f.unit, error = ui.errors["minStock"])
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = f.sku, onValueChange = { v -> vm.edit { it.copy(sku = v) } },
                        label = { Text("SKU / kode") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = f.barcode, onValueChange = { v -> vm.edit { it.copy(barcode = v.filter { c -> c.isLetterOrDigit() }) } },
                        label = { Text("Barcode") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
            }

            ui.generalError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            }

            Button(
                onClick = vm::save,
                enabled = !ui.saving,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(56.dp),
            ) {
                Text(if (ui.saving) "Menyimpan…" else "Simpan", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun BigUnitsSection(
    baseUnit: String,
    baseCost: Long,
    baseSell: Long?,
    drafts: List<BigUnitDraft>,
    errors: Map<String, String>,
    onChange: (List<BigUnitDraft>) -> Unit,
) {
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Satuan besar: renteng / dus (opsional)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            "Isi di sini kalau barang juga dijual per renteng / dus. Contoh: Dus, isi 40 $baseUnit, harga per dus Rp 125.000. Stok tetap dihitung dalam $baseUnit.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        drafts.forEachIndexed { i, d ->
            SectionCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = d.name,
                            onValueChange = { v -> onChange(drafts.toMutableList().also { it[i] = d.copy(name = v.take(20)) }) },
                            label = { Text("Satuan besar ${i + 1} (pilih di bawah)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onChange(drafts.toMutableList().also { it.removeAt(i) }) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Hapus satuan ini")
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        items(BIG_UNITS.filter { it != baseUnit }) { name ->
                            FilterChip(
                                selected = d.name.equals(name, ignoreCase = true),
                                onClick = { onChange(drafts.toMutableList().also { it[i] = d.copy(name = name) }) },
                                label = { Text(name) },
                            )
                        }
                    }
                    QtyField(
                        "1 ${d.name.ifBlank { "satuan besar" }} berisi berapa $baseUnit?", d.factor,
                        { v -> onChange(drafts.toMutableList().also { it[i] = d.copy(factor = v) }) },
                        suffix = baseUnit, error = errors["unitFactor$i"],
                    )
                    MoneyField(
                        "Harga jual per ${d.name.ifBlank { "satuan besar" }}", d.price,
                        { v -> onChange(drafts.toMutableList().also { it[i] = d.copy(price = v) }) },
                        error = errors["unitPrice$i"],
                    )
                    val factor = Qty.parse(d.factor)
                    val price = Money.parse(d.price)
                    if (factor != null && factor > 1 && price != null && price > 0) {
                        val perBase = (price / factor).toLong()
                        val cost = UnitCalc.costPerUnit(baseCost, factor)
                        val info = buildString {
                            append("≈ ${Money.format(perBase)} per $baseUnit")
                            if (baseSell != null && baseSell > 0 && perBase > baseSell) append(" (lebih mahal dari ecer!)")
                            if (baseCost > 0) append(" · modal ${Money.format(cost)} · untung ${Money.format(price - cost)}")
                        }
                        Text(info, style = MaterialTheme.typography.bodySmall, color = if (price < cost) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (drafts.size < 2) {
            OutlinedButton(onClick = { onChange(drafts + BigUnitDraft()) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (drafts.isEmpty()) "+ Tambah satuan besar (renteng, dus, …)" else "+ Tambah satuan besar lagi")
            }
        }
    }
}
