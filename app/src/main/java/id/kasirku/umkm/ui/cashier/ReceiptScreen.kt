package id.kasirku.umkm.ui.cashier

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import id.kasirku.umkm.data.db.entity.Customer
import id.kasirku.umkm.data.db.entity.Sale
import id.kasirku.umkm.data.db.entity.SaleItem
import id.kasirku.umkm.data.db.entity.StoreProfile
import id.kasirku.umkm.data.repository.RepoResult
import id.kasirku.umkm.data.repository.SaleRepository
import id.kasirku.umkm.domain.Money
import id.kasirku.umkm.domain.ReceiptFormatter
import id.kasirku.umkm.domain.SaleState
import id.kasirku.umkm.ui.components.KasirTopBar
import id.kasirku.umkm.ui.components.SectionCard
import id.kasirku.umkm.ui.kasirApp
import id.kasirku.umkm.ui.theme.Loss
import id.kasirku.umkm.util.Waktu
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReceiptData(
    val sale: Sale,
    val items: List<SaleItem>,
    val profile: StoreProfile,
    val customer: Customer?,
    val paidOnDebt: Long,
) {
    val isDebt: Boolean get() = sale.status == SaleState.PARTIAL || sale.status == SaleState.UNPAID
    val remainingDebt: Long get() = if (isDebt) (sale.total - paidOnDebt).coerceAtLeast(0) else 0

    fun text(): String = ReceiptFormatter.build(
        ReceiptFormatter.Header(profile.name, profile.address, profile.phone, profile.receiptFooter),
        ReceiptFormatter.Body(
            invoiceNo = sale.invoiceNo,
            dateTime = Waktu.formatDateTime(sale.createdAt),
            customer = customer?.name,
            lines = items.map { ReceiptFormatter.Line(it.productName, it.qty, it.unit, it.sellPrice, it.discount, it.lineTotal) },
            subtotal = sale.subtotal,
            itemDiscount = sale.itemDiscountTotal,
            transDiscount = sale.transDiscount,
            total = sale.total,
            method = sale.paymentMethod,
            paidNow = sale.paidAmount,
            change = sale.changeAmount,
            status = sale.status,
            remainingDebt = remainingDebt,
        ),
    )
}

class ReceiptViewModel(private val repo: SaleRepository, handle: SavedStateHandle) : ViewModel() {
    val saleId: Long = checkNotNull(handle.get<Long>("id"))

    @OptIn(ExperimentalCoroutinesApi::class)
    private val customer = repo.observeSale(saleId).flatMapLatest { s ->
        val cid = s?.customerId
        if (cid == null) flowOf(null) else repo.observeCustomer(cid)
    }

    val data: StateFlow<ReceiptData?> = combine(
        repo.observeSale(saleId), repo.observeItems(saleId), repo.observeProfile(), customer, repo.observePaidOnDebt(saleId),
    ) { sale, items, profile, cust, paid ->
        sale?.let { ReceiptData(it, items, profile, cust, paid) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()
    private var busy = false

    fun void(reason: String) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            when (val r = repo.voidSale(saleId, reason)) {
                is RepoResult.Ok -> _messages.send(r.message)
                is RepoResult.Error -> _messages.send(r.message)
            }
            busy = false
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ReceiptViewModel(kasirApp().container.saleRepo, createSavedStateHandle()) }
        }
    }
}

@Composable
fun ReceiptScreen(
    onBack: () -> Unit,
    vm: ReceiptViewModel = viewModel(factory = ReceiptViewModel.Factory),
) {
    val data by vm.data.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showVoid by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = { KasirTopBar("Struk", onBack = onBack, subtitle = data?.sale?.invoiceNo) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        val d = data ?: return@Scaffold
        val text = d.text()
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (d.sale.status == SaleState.VOID) {
                Text(
                    "Transaksi ini DIBATALKAN" + (d.sale.voidReason?.let { " — $it" } ?: ""),
                    color = Loss, fontWeight = FontWeight.Bold,
                )
            } else if (d.isDebt) {
                Text(
                    "${SaleState.label(d.sale.status)} · sisa utang ${Money.format(d.remainingDebt)}",
                    color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold,
                )
            }
            SectionCard {
                Text(
                    text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                )
            }
            Button(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(send, "Bagikan struk"))
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Filled.Share, contentDescription = null)
                Text("  Bagikan Struk (WhatsApp, dll)", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Selesai", style = MaterialTheme.typography.titleMedium)
            }
            if (d.sale.status != SaleState.VOID) {
                Row {
                    TextButton(onClick = { showVoid = true }) { Text("Batalkan transaksi ini", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }

    if (showVoid) {
        var reason by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showVoid = false },
            title = { Text("Batalkan transaksi?") },
            text = {
                Column {
                    Text("Stok barang akan dikembalikan dan transaksi ini tidak dihitung di omzet maupun laba. Tindakan ini tidak bisa diurungkan.")
                    OutlinedTextField(
                        value = reason, onValueChange = { reason = it.take(80) },
                        label = { Text("Alasan") }, placeholder = { Text("mis. salah input, pembeli batal") },
                        singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showVoid = false; vm.void(reason) }, enabled = reason.isNotBlank()) {
                    Text("Batalkan Transaksi", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showVoid = false }) { Text("Kembali") } },
        )
    }
}
