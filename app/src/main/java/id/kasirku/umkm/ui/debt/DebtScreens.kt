package id.kasirku.umkm.ui.debt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import id.kasirku.umkm.data.db.dao.CustomerDebt
import id.kasirku.umkm.data.db.dao.DebtSale
import id.kasirku.umkm.data.db.dao.PaymentRow
import id.kasirku.umkm.data.db.entity.Customer
import id.kasirku.umkm.data.repository.DebtRepository
import id.kasirku.umkm.data.repository.RepoResult
import id.kasirku.umkm.data.repository.SaleRepository
import id.kasirku.umkm.domain.Money
import id.kasirku.umkm.domain.PayMethod
import id.kasirku.umkm.domain.SaleState
import id.kasirku.umkm.ui.components.KasirTopBar
import id.kasirku.umkm.ui.components.MoneyField
import id.kasirku.umkm.ui.components.SectionCard
import id.kasirku.umkm.ui.components.StatTile
import id.kasirku.umkm.ui.kasirApp
import id.kasirku.umkm.ui.theme.Loss
import id.kasirku.umkm.ui.theme.Profit
import id.kasirku.umkm.ui.theme.Warn
import id.kasirku.umkm.util.Waktu
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

// ======================= Daftar pelanggan berutang =======================

class DebtListViewModel(repo: DebtRepository) : ViewModel() {
    val query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val list: StateFlow<List<CustomerDebt>?> = query.flatMapLatest { repo.observeOpen(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val totalReceivable: StateFlow<Long> = repo.observeTotalReceivable()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { DebtListViewModel(kasirApp().container.debtRepo) }
        }
    }
}

private fun daysAgo(epoch: Long): Long = TimeUnit.MILLISECONDS.toDays(Waktu.now() - epoch)

@Composable
fun DebtListScreen(
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    vm: DebtListViewModel = viewModel(factory = DebtListViewModel.Factory),
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val list by vm.list.collectAsStateWithLifecycle()
    val total by vm.totalReceivable.collectAsStateWithLifecycle()

    Scaffold(topBar = { KasirTopBar("Utang Pelanggan", onBack = onBack) }) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SectionCard {
                    Row(Modifier.fillMaxWidth()) {
                        StatTile("Total piutang (belum dibayar)", Money.format(total), Modifier.weight(1f), valueColor = Warn, strong = true)
                        StatTile("Pelanggan", (list?.size ?: 0).toString(), Modifier.weight(0.6f))
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { vm.query.value = it },
                    placeholder = { Text("Cari nama / no. HP") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Filled.Close, contentDescription = "Hapus pencarian") }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val l = list
            if (l != null && l.isEmpty()) {
                item {
                    Text(
                        if (query.isEmpty()) "Tidak ada utang. Utang tercatat otomatis saat kasir memilih cara bayar \"Utang\"." else "Pelanggan tidak ditemukan.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            items(l.orEmpty(), key = { it.customerId }) { c ->
                Card(
                    onClick = { onOpen(c.customerId) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOfNotNull(
                                    c.phone.takeIf { it.isNotBlank() },
                                    "${c.openCount} nota",
                                    "terlama ${daysAgo(c.oldest)} hari",
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Money.format(c.remaining), fontWeight = FontWeight.Bold, color = Warn, style = MaterialTheme.typography.titleMedium)
                            Text("sisa utang", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

// ======================= Detail utang satu pelanggan =======================

class CustomerDebtViewModel(
    private val debts: DebtRepository,
    sales: SaleRepository,
    handle: SavedStateHandle,
) : ViewModel() {
    val customerId: Long = checkNotNull(handle.get<Long>("id"))

    val customer: StateFlow<Customer?> = sales.observeCustomer(customerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val notes: StateFlow<List<DebtSale>?> = debts.observeSales(customerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val payments: StateFlow<List<PaymentRow>> = debts.observePayments(customerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val saving = MutableStateFlow(false)
    private val _messages = Channel<Pair<String, Boolean>>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun pay(amount: Long, method: String, note: String) {
        if (saving.value) return
        saving.value = true
        viewModelScope.launch {
            when (val r = debts.receivePayment(customerId, amount, method, note)) {
                is RepoResult.Ok -> _messages.send(r.message to true)
                is RepoResult.Error -> _messages.send(r.message to false)
            }
            saving.value = false
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = kasirApp().container
                CustomerDebtViewModel(c.debtRepo, c.saleRepo, createSavedStateHandle())
            }
        }
    }
}

@Composable
fun CustomerDebtScreen(
    onBack: () -> Unit,
    onOpenSale: (Long) -> Unit,
    vm: CustomerDebtViewModel = viewModel(factory = CustomerDebtViewModel.Factory),
) {
    val customer by vm.customer.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val payments by vm.payments.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showPay by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.messages.collect { (msg, ok) ->
            if (ok) showPay = false
            snackbar.showSnackbar(msg)
        }
    }

    val list = notes.orEmpty()
    val open = list.filter { it.sale.status == SaleState.PARTIAL || it.sale.status == SaleState.UNPAID }
    val remaining = open.sumOf { (it.sale.total - it.paidSoFar).coerceAtLeast(0) }

    Scaffold(
        topBar = { KasirTopBar(customer?.name ?: "Pelanggan", onBack = onBack, subtitle = customer?.phone?.takeIf { it.isNotBlank() }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SectionCard {
                    Column {
                        StatTile(
                            "Sisa utang", Money.format(remaining),
                            valueColor = if (remaining > 0) Warn else Profit, strong = true,
                        )
                        Text(
                            if (remaining > 0) "${open.size} nota belum lunas" else "Semua utang sudah LUNAS",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            if (remaining > 0) {
                item {
                    Button(onClick = { showPay = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text("Terima Pembayaran", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item { Text("Nota utang", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(list, key = { it.sale.id }) { d ->
                val s = d.sale
                val rest = (s.total - d.paidSoFar).coerceAtLeast(0)
                Card(
                    onClick = { onOpenSale(s.id) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.invoiceNo, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${Waktu.formatDateTime(s.createdAt)} · total ${Money.format(s.total)} · dibayar ${Money.format(d.paidSoFar)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                if (rest > 0) Money.format(rest) else "Lunas",
                                fontWeight = FontWeight.Bold,
                                color = if (rest > 0) Warn else Profit,
                            )
                            if (rest > 0) Text("sisa", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item { Text("Riwayat pembayaran", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            if (payments.isEmpty()) {
                item { Text("Belum ada pembayaran.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                item {
                    SectionCard {
                        Column {
                            payments.forEachIndexed { i, p ->
                                if (i > 0) HorizontalDivider()
                                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(Waktu.formatDateTime(p.payment.createdAt), fontWeight = FontWeight.SemiBold)
                                        Text(
                                            listOf(p.invoiceNo, PayMethod.label(p.payment.method), p.payment.note).filter { it.isNotBlank() }.joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    Text("+" + Money.format(p.payment.amount), color = Profit, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPay) {
        PayDialog(
            remaining = remaining,
            saving = saving,
            onDismiss = { showPay = false },
            onPay = { amount, method, note -> vm.pay(amount, method, note) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PayDialog(remaining: Long, saving: Boolean, onDismiss: () -> Unit, onPay: (Long, String, String) -> Unit) {
    var amount by rememberSaveable { mutableStateOf("") }
    var method by rememberSaveable { mutableStateOf(PayMethod.TUNAI) }
    var note by rememberSaveable { mutableStateOf("") }
    val value = Money.parse(amount) ?: 0L
    val error = when {
        amount.isBlank() -> null
        value <= 0 -> "Isi jumlah yang dibayar"
        value > remaining -> "Melebihi sisa utang ${Money.format(remaining)}"
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Terima pembayaran") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Sisa utang: ${Money.format(remaining)}", fontWeight = FontWeight.Bold)
                MoneyField("Jumlah dibayar", amount, { amount = it }, error = error)
                OutlinedButton(onClick = { amount = remaining.toString() }) { Text("Bayar lunas (${Money.format(remaining)})") }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(PayMethod.TUNAI, PayMethod.QRIS, PayMethod.TRANSFER).forEach { m ->
                        FilterChip(selected = method == m, onClick = { method = m }, label = { Text(PayMethod.label(m)) })
                    }
                }
                OutlinedTextField(note, { note = it.take(60) }, label = { Text("Catatan (boleh kosong)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (value in 1..remaining) {
                    Text(
                        if (value == remaining) "Utang akan LUNAS." else "Sisa setelah bayar: ${Money.format(remaining - value)}",
                        color = if (value == remaining) Profit else Loss,
                    )
                    Text("Dibagi ke nota paling lama dulu.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPay(value, method, note) }, enabled = !saving && value in 1..remaining) {
                Text(if (saving) "Menyimpan…" else "Simpan", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
