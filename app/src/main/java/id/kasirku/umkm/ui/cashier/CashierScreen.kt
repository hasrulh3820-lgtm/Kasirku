package id.kasirku.umkm.ui.cashier

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import id.kasirku.umkm.data.db.dao.ProductWithCategory
import id.kasirku.umkm.domain.CartItem
import id.kasirku.umkm.domain.CheckoutCalc
import id.kasirku.umkm.domain.Money
import id.kasirku.umkm.domain.PayMethod
import id.kasirku.umkm.domain.PaymentCheck
import id.kasirku.umkm.domain.PaymentInput
import id.kasirku.umkm.domain.Qty
import id.kasirku.umkm.domain.SaleCalc
import id.kasirku.umkm.domain.Shortage
import id.kasirku.umkm.domain.UnitCalc
import id.kasirku.umkm.domain.UnitOption
import id.kasirku.umkm.ui.components.ConfirmDialog
import id.kasirku.umkm.ui.components.KasirTopBar
import id.kasirku.umkm.ui.components.MoneyField
import id.kasirku.umkm.ui.components.QtyField
import id.kasirku.umkm.ui.components.SectionCard
import id.kasirku.umkm.ui.theme.Loss
import id.kasirku.umkm.ui.theme.Profit
import id.kasirku.umkm.ui.theme.Warn

@Composable
fun CashierScreen(
    onBack: () -> Unit,
    onDone: (Long) -> Unit,
    onHistory: () -> Unit,
    vm: CashierViewModel = viewModel(factory = CashierViewModel.Factory),
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val products by vm.productList.collectAsStateWithLifecycle()
    val units by vm.units.collectAsStateWithLifecycle()
    val cart by vm.cart.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val stockWarning by vm.stockWarning.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var showPay by rememberSaveable { mutableStateOf(false) }
    var showCart by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var editKey by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is CashierEvent.Done -> {
                    showPay = false
                    showCart = false
                    onDone(e.saleId)
                }
                is CashierEvent.Message -> snackbar.showSnackbar(e.text)
            }
        }
    }

    val leave: () -> Unit = {
        when {
            showPay -> showPay = false
            showCart -> showCart = false
            cart.isNotEmpty() -> confirmLeave = true
            else -> onBack()
        }
    }
    BackHandler(onBack = leave)

    val total = SaleCalc.totals(cart.map { it.toLine() }).total
    val count = cart.size

    Scaffold(
        topBar = {
            KasirTopBar(
                title = if (showPay) "Pembayaran" else "Kasir",
                onBack = leave,
                subtitle = if (showPay) null else "Ketuk satuan untuk menambah ke keranjang",
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        BoxWithConstraints(Modifier.padding(pad).fillMaxSize().imePadding()) {
            val wide = maxWidth >= 720.dp
            when {
                showPay -> PaymentPane(
                    cart = cart,
                    saving = saving,
                    onSubmit = { vm.checkout(it) },
                )
                wide -> Row(Modifier.fillMaxSize()) {
                    ProductPane(
                        query = query, onQuery = { vm.query.value = it }, products = products, units = units, cart = cart,
                        onAdd = vm::add, onHistory = onHistory, modifier = Modifier.weight(1.5f),
                    )
                    VerticalDivider()
                    CartPane(
                        cart = cart, total = total, onChange = vm::change, onEdit = { editKey = it },
                        onClear = vm::clear, onPay = { showPay = true }, modifier = Modifier.weight(1f),
                    )
                }
                showCart -> CartPane(
                    cart = cart, total = total, onChange = vm::change, onEdit = { editKey = it },
                    onClear = vm::clear, onPay = { showPay = true }, modifier = Modifier.fillMaxSize(),
                )
                else -> Column(Modifier.fillMaxSize()) {
                    ProductPane(
                        query = query, onQuery = { vm.query.value = it }, products = products, units = units, cart = cart,
                        onAdd = vm::add, onHistory = onHistory, modifier = Modifier.weight(1f),
                    )
                    if (count > 0) {
                        Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
                            Row(
                                Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedButton(onClick = { showCart = true }, modifier = Modifier.weight(1f).height(52.dp)) {
                                    Icon(Icons.Filled.ShoppingCart, contentDescription = null)
                                    Text(" $count barang", maxLines = 1)
                                }
                                Button(onClick = { showPay = true }, modifier = Modifier.weight(1.3f).height(52.dp)) {
                                    Text("Bayar ${Money.format(total)}", fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val editing = editKey?.let { k -> cart.firstOrNull { it.key == k } }
    if (editing != null) {
        EditLineDialog(
            item = editing,
            onDismiss = { editKey = null },
            onSave = { q, d -> vm.edit(editing.key, q, d); editKey = null },
            onRemove = { vm.remove(editing.key); editKey = null },
        )
    }

    if (stockWarning.isNotEmpty()) {
        StockWarningDialog(stockWarning, onConfirm = vm::confirmNegativeStock, onDismiss = vm::dismissStockWarning)
    }

    if (confirmLeave) {
        ConfirmDialog(
            title = "Tinggalkan kasir?",
            message = "Keranjang berisi $count barang dan akan dikosongkan.",
            confirmText = "Keluar",
            destructive = true,
            onConfirm = { confirmLeave = false; vm.clear(); onBack() },
            onDismiss = { confirmLeave = false },
        )
    }
}

// ---------------- Daftar barang ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProductPane(
    query: String,
    onQuery: (String) -> Unit,
    products: List<ProductWithCategory>?,
    units: Map<Long, List<UnitOption>>,
    cart: List<CartItem>,
    onAdd: (ProductWithCategory, UnitOption) -> Unit,
    onHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                placeholder = { Text("Cari nama / kode / barcode") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, contentDescription = "Hapus pencarian") }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    // Ketik/scan barcode lalu Enter: jika hasilnya satu barang, langsung masuk keranjang.
                    val list = products.orEmpty()
                    if (list.size == 1) {
                        val p = list.first()
                        onAdd(p, UnitOption(p.unit, 1.0, p.sellPrice))
                        onQuery("")
                    }
                }),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onHistory) { Icon(Icons.Filled.History, contentDescription = "Riwayat penjualan") }
        }

        val list = products
        when {
            list == null -> Unit
            list.isEmpty() -> Text(
                if (query.isEmpty()) "Belum ada barang. Tambahkan barang dulu di menu Produk dan Stok." else "Barang tidak ditemukan.",
                modifier = Modifier.padding(24.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 168.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(list, key = { it.id }) { p ->
                    val big = units[p.id].orEmpty()
                    val options = listOf(UnitOption(p.unit, 1.0, p.sellPrice)) + big
                    val inCart = cart.filter { it.productId == p.id }.sumOf { it.baseQty }
                    Card(
                        onClick = { onAdd(p, options.first()) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(p.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                "Stok " + UnitCalc.breakdown(p.stockQty, p.unit, big),
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    p.stockQty - inCart <= 0 -> Loss
                                    p.stockQty <= p.minStock -> Warn
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 2,
                            )
                            if (inCart > 0) {
                                Text(
                                    "Di keranjang: ${Qty.format(inCart)} ${p.unit}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                options.forEach { u ->
                                    FilledTonalButton(
                                        onClick = { onAdd(p, u) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("+ ${u.name}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                            Text(Money.format(u.sellPrice), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Keranjang ----------------

@Composable
private fun CartPane(
    cart: List<CartItem>,
    total: Long,
    onChange: (String, Double) -> Unit,
    onEdit: (String) -> Unit,
    onClear: () -> Unit,
    onPay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxHeight()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Keranjang (${cart.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (cart.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Kosongkan", color = MaterialTheme.colorScheme.error) }
        }
        if (cart.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp)) {
                Text("Keranjang kosong. Ketuk barang di daftar untuk menambahkan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp)) {
                items(cart, key = { it.key }) { item ->
                    CartRow(item, onChange = { d -> onChange(item.key, d) }, onEdit = { onEdit(item.key) })
                    HorizontalDivider()
                }
            }
        }
        Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Total", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(Money.format(total), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onPay,
                    enabled = cart.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(56.dp),
                ) { Text("Bayar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            }
        }
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "Kosongkan keranjang?",
            message = "Semua ${cart.size} barang di keranjang akan dihapus.",
            confirmText = "Kosongkan",
            destructive = true,
            onConfirm = { confirmClear = false; onClear() },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun CartRow(item: CartItem, onChange: (Double) -> Unit, onEdit: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${Money.format(item.sellPrice)} / ${item.unitName}", style = MaterialTheme.typography.bodySmall)
            if (item.discount > 0) Text("Diskon ${Money.format(item.discount)}", style = MaterialTheme.typography.bodySmall, color = Warn)
            Text(Money.format(item.lineTotal), fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = { onChange(-1.0) }) { Icon(Icons.Filled.Remove, contentDescription = "Kurangi") }
        Text(
            "${Qty.format(item.qty)}\n${item.unitName}",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(52.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(onClick = { onChange(1.0) }) { Icon(Icons.Filled.Add, contentDescription = "Tambah") }
    }
}

@Composable
private fun EditLineDialog(item: CartItem, onDismiss: () -> Unit, onSave: (Double, Long) -> Unit, onRemove: () -> Unit) {
    var qty by rememberSaveable { mutableStateOf(Qty.format(item.qty)) }
    var discount by rememberSaveable { mutableStateOf(if (item.discount > 0) item.discount.toString() else "") }
    val q = Qty.parse(qty)
    val d = Money.parse(discount) ?: 0L
    val gross = if (q != null) Math.round(item.sellPrice * q) else 0L
    val error = when {
        q == null || q <= 0 -> "Jumlah harus lebih dari 0"
        d > gross -> "Diskon melebihi harga"
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name) },
        text = {
            Column {
                Text("${Money.format(item.sellPrice)} / ${item.unitName}", style = MaterialTheme.typography.bodyMedium)
                QtyField("Jumlah", qty, { qty = it }, suffix = item.unitName)
                MoneyField("Diskon untuk barang ini (Rp)", discount, { discount = it })
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
                else Text("Subtotal: ${Money.format(gross - d)}", fontWeight = FontWeight.Bold)
                TextButton(onClick = onRemove) { Text("Hapus dari keranjang", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(q!!, d) }, enabled = error == null) { Text("Simpan", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
private fun StockWarningDialog(shortages: List<Shortage>, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stok di aplikasi kurang") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                shortages.forEach {
                    Text("• ${it.name}: dijual ${Qty.format(it.need)} ${it.baseUnit}, stok ${Qty.format(it.stock)} ${it.baseUnit}")
                }
                Text(
                    "Jika barangnya memang ada di rak, Anda bisa tetap menjual. Stok akan menjadi minus; cocokkan nanti lewat Hitung Stok atau Barang Masuk.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Tetap jual", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

// ---------------- Pembayaran ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaymentPane(cart: List<CartItem>, saving: Boolean, onSubmit: (PendingCheckout) -> Unit) {
    var method by rememberSaveable { mutableStateOf(PayMethod.TUNAI) }
    var transDiscountText by rememberSaveable { mutableStateOf("") }
    var cashText by rememberSaveable { mutableStateOf("") }
    var dpText by rememberSaveable { mutableStateOf("") }
    var restAsDebt by rememberSaveable { mutableStateOf(false) }
    var customer by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }

    val totals = SaleCalc.totals(cart.map { it.toLine() }, Money.parse(transDiscountText) ?: 0L)
    val total = totals.total
    val cash = Money.parse(cashText) ?: 0L
    val input = when (method) {
        PayMethod.TUNAI -> PaymentInput(PayMethod.TUNAI, cashReceived = cash, asDebt = restAsDebt && cash < total)
        PayMethod.UTANG -> PaymentInput(PayMethod.UTANG, downPayment = Money.parse(dpText) ?: 0L)
        else -> PaymentInput(method)
    }
    val check = CheckoutCalc.resolvePayment(total, input)
    val result = (check as? PaymentCheck.Ok)?.result
    val needName = result?.isDebt == true
    val error = when {
        cart.isEmpty() -> "Keranjang kosong."
        method == PayMethod.TUNAI && cashText.isBlank() -> "Isi uang yang diterima."
        check is PaymentCheck.Error -> check.message
        needName && customer.isBlank() -> "Isi nama pelanggan untuk transaksi utang."
        else -> null
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionCard {
            Column {
                Text("${cart.size} baris barang", color = MaterialTheme.colorScheme.onSurfaceVariant)
                SummaryLine("Subtotal", Money.format(totals.subtotal))
                if (totals.itemDiscountTotal > 0) SummaryLine("Diskon barang", "-" + Money.format(totals.itemDiscountTotal))
                MoneyField("Diskon transaksi (boleh kosong)", transDiscountText, { transDiscountText = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("TOTAL", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(Money.format(total), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                val profit = total - totals.costTotal
                Text(
                    "Untung transaksi ini: ${Money.format(profit)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (profit < 0) Loss else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text("Cara bayar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(PayMethod.TUNAI, PayMethod.QRIS, PayMethod.TRANSFER, PayMethod.UTANG).forEach { m ->
                FilterChip(selected = method == m, onClick = { method = m }, label = { Text(PayMethod.label(m)) })
            }
        }

        when (method) {
            PayMethod.TUNAI -> {
                MoneyField("Uang diterima", cashText, { cashText = it })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CheckoutCalc.cashSuggestions(total).forEachIndexed { i, v ->
                        OutlinedButton(onClick = { cashText = v.toString() }) { Text(if (i == 0) "Uang pas" else Money.format(v)) }
                    }
                }
                if (cashText.isNotBlank()) {
                    if (cash >= total) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Kembalian", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            Text(Money.format(cash - total), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Profit)
                        }
                    } else {
                        Text("Kurang ${Money.format(total - cash)}", color = Loss, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { restAsDebt = !restAsDebt }) {
                            Checkbox(checked = restAsDebt, onCheckedChange = { restAsDebt = it })
                            Text("Sisanya dicatat sebagai utang pelanggan")
                        }
                    }
                }
            }
            PayMethod.UTANG -> {
                MoneyField("Uang muka / DP (boleh kosong)", dpText, { dpText = it })
                val dp = Money.parse(dpText) ?: 0L
                if (dp < total) Text("Sisa utang: ${Money.format(total - dp)}", color = Warn, fontWeight = FontWeight.Bold)
            }
            else -> Text(
                "Pastikan uang ${Money.format(total)} sudah masuk lewat ${PayMethod.label(method)} sebelum menyimpan.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Text(
            if (needName) "Data pelanggan (wajib untuk utang)" else "Data pelanggan (boleh kosong)",
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
        )
        OutlinedTextField(
            value = customer, onValueChange = { customer = it.take(40) }, label = { Text("Nama pelanggan") },
            singleLine = true, isError = needName && customer.isBlank(), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = phone, onValueChange = { phone = it.filter { c -> c.isDigit() || c == '+' }.take(15) },
            label = { Text("No. HP / WhatsApp") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )

        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
        Button(
            onClick = {
                onSubmit(PendingCheckout(Money.parse(transDiscountText) ?: 0L, input, customer, phone))
            },
            enabled = error == null && !saving,
            modifier = Modifier.fillMaxWidth().height(60.dp),
        ) {
            Text(if (saving) "Menyimpan…" else "Simpan Transaksi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, modifier = Modifier.weight(1f))
        Text(value)
    }
}
