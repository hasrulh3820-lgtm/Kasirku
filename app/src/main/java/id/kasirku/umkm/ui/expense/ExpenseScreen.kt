package id.kasirku.umkm.ui.expense

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import id.kasirku.umkm.data.db.entity.Expense
import id.kasirku.umkm.data.repository.ExpenseRepository
import id.kasirku.umkm.data.repository.RepoResult
import id.kasirku.umkm.domain.Money
import id.kasirku.umkm.ui.components.ConfirmDialog
import id.kasirku.umkm.ui.components.KasirTopBar
import id.kasirku.umkm.ui.components.MoneyField
import id.kasirku.umkm.ui.components.SectionCard
import id.kasirku.umkm.ui.components.StatTile
import id.kasirku.umkm.ui.kasirApp
import id.kasirku.umkm.ui.theme.Loss
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

val EXPENSE_CATEGORIES = listOf("Operasional", "Gaji", "Listrik & Air", "Sewa", "Transport", "Plastik & Kemasan", "Lain-lain")

enum class ExpenseRange(val label: String) { TODAY("Hari ini"), MONTH("Bulan ini"), DAYS30("30 hari") }

class ExpenseViewModel(private val repo: ExpenseRepository) : ViewModel() {
    val range = MutableStateFlow(ExpenseRange.MONTH)

    @OptIn(ExperimentalCoroutinesApi::class)
    val list: StateFlow<List<Expense>?> = range.flatMapLatest { r ->
        repo.observe(
            when (r) {
                ExpenseRange.TODAY -> Waktu.todayRange()
                ExpenseRange.MONTH -> Waktu.monthRange()
                ExpenseRange.DAYS30 -> Waktu.lastDaysRange(30)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _messages = Channel<Pair<String, Boolean>>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()
    private var busy = false

    fun add(category: String, amount: Long, note: String) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            when (val r = repo.add(category, amount, note)) {
                is RepoResult.Ok -> _messages.send(r.message to true)
                is RepoResult.Error -> _messages.send(r.message to false)
            }
            busy = false
        }
    }

    fun delete(e: Expense) {
        viewModelScope.launch {
            repo.delete(e.id)
            _messages.send("Pengeluaran dihapus." to false)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ExpenseViewModel(kasirApp().container.expenseRepo) }
        }
    }
}

@Composable
fun ExpenseScreen(onBack: () -> Unit, vm: ExpenseViewModel = viewModel(factory = ExpenseViewModel.Factory)) {
    val range by vm.range.collectAsStateWithLifecycle()
    val list by vm.list.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Expense?>(null) }

    LaunchedEffect(Unit) {
        vm.messages.collect { (msg, added) ->
            if (added) showAdd = false
            snackbar.showSnackbar(msg)
        }
    }

    Scaffold(
        topBar = { KasirTopBar("Pengeluaran", onBack = onBack, subtitle = "Biaya toko di luar belanja barang dagangan") },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Catat Pengeluaran") },
            )
        },
    ) { pad ->
        val rows = list.orEmpty()
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpenseRange.entries.forEach { r ->
                        FilterChip(selected = range == r, onClick = { vm.range.value = r }, label = { Text(r.label) })
                    }
                }
            }
            item {
                SectionCard {
                    Column {
                        StatTile("Total pengeluaran", Money.format(rows.sumOf { it.amount }), valueColor = Loss, strong = true)
                        rows.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
                            .entries.sortedByDescending { it.value }
                            .forEach { (cat, sum) ->
                                Row(Modifier.fillMaxWidth()) {
                                    Text(cat, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Text(Money.format(sum), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        Text(
                            "Belanja barang dagangan dicatat lewat \"Barang Masuk\", bukan di sini, supaya biaya tidak terhitung dua kali.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            if (list != null && rows.isEmpty()) {
                item { Text("Belum ada pengeluaran pada periode ini.", modifier = Modifier.padding(16.dp)) }
            }
            items(rows, key = { it.id }) { e ->
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.category, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOf(Waktu.formatDateTime(e.createdAt), e.note).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(Money.format(e.amount), fontWeight = FontWeight.Bold, color = Loss)
                        IconButton(onClick = { toDelete = e }) { Icon(Icons.Filled.Delete, contentDescription = "Hapus") }
                    }
                }
            }
        }
    }

    if (showAdd) AddExpenseDialog(onDismiss = { showAdd = false }, onSave = vm::add)

    toDelete?.let { e ->
        ConfirmDialog(
            title = "Hapus pengeluaran?",
            message = "${e.category} ${Money.format(e.amount)} (${Waktu.formatDateTime(e.createdAt)}) akan dihapus.",
            confirmText = "Hapus",
            destructive = true,
            onConfirm = { vm.delete(e); toDelete = null },
            onDismiss = { toDelete = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddExpenseDialog(onDismiss: () -> Unit, onSave: (String, Long, String) -> Unit) {
    var category by rememberSaveable { mutableStateOf(EXPENSE_CATEGORIES.first()) }
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    val value = Money.parse(amount) ?: 0L
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Catat pengeluaran") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EXPENSE_CATEGORIES.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
                    }
                }
                MoneyField("Jumlah", amount, { amount = it })
                OutlinedTextField(note, { note = it.take(60) }, label = { Text("Keterangan (boleh kosong)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(category, value, note) }, enabled = value > 0) { Text("Simpan", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
