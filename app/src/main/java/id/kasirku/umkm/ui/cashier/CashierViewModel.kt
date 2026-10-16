package id.kasirku.umkm.ui.cashier

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import id.kasirku.umkm.data.db.dao.ProductWithCategory
import id.kasirku.umkm.data.repository.CheckoutRequest
import id.kasirku.umkm.data.repository.CheckoutResult
import id.kasirku.umkm.data.repository.ProductRepository
import id.kasirku.umkm.data.repository.SaleRepository
import id.kasirku.umkm.domain.CartItem
import id.kasirku.umkm.domain.PaymentInput
import id.kasirku.umkm.domain.Qty
import id.kasirku.umkm.domain.Shortage
import id.kasirku.umkm.domain.UnitCalc
import id.kasirku.umkm.domain.UnitOption
import id.kasirku.umkm.ui.kasirApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface CashierEvent {
    data class Done(val saleId: Long) : CashierEvent
    data class Message(val text: String) : CashierEvent
}

/** Data pembayaran terakhir, disimpan agar bisa diulang setelah pemilik setuju stok minus. */
data class PendingCheckout(
    val transDiscount: Long,
    val payment: PaymentInput,
    val customerName: String,
    val customerPhone: String,
)

class CashierViewModel(private val products: ProductRepository, private val sales: SaleRepository) : ViewModel() {

    val query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val productList: StateFlow<List<ProductWithCategory>?> = query
        .flatMapLatest { products.observeList(it, false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val units: StateFlow<Map<Long, List<UnitOption>>> = products.observeAllUnits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    /** Kunci unik keranjang ini; diganti setelah transaksi tersimpan. Mencegah transaksi ganda. */
    private var requestId = UUID.randomUUID().toString()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _stockWarning = MutableStateFlow<List<Shortage>>(emptyList())
    val stockWarning: StateFlow<List<Shortage>> = _stockWarning.asStateFlow()
    private var pending: PendingCheckout? = null

    private val _events = Channel<CashierEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun add(p: ProductWithCategory, unit: UnitOption) {
        val item = CartItem(
            productId = p.id, name = p.name, baseUnit = p.unit, unitName = unit.name, factor = unit.factor,
            sellPrice = unit.sellPrice, costPerUnit = UnitCalc.costPerUnit(p.costPrice, unit.factor), qty = 1.0,
        )
        _cart.update { list ->
            val i = list.indexOfFirst { it.key == item.key }
            if (i >= 0) list.toMutableList().also { it[i] = it[i].copy(qty = Qty.round(it[i].qty + 1)) } else list + item
        }
    }

    fun change(key: String, delta: Double) {
        _cart.update { list ->
            list.mapNotNull { if (it.key == key) it.copy(qty = Qty.round(it.qty + delta)).takeIf { c -> c.qty > 0 } else it }
        }
    }

    fun edit(key: String, qty: Double, discount: Long) {
        _cart.update { list ->
            list.mapNotNull { if (it.key == key) it.copy(qty = Qty.round(qty), discount = discount.coerceAtLeast(0)).takeIf { c -> c.qty > 0 } else it }
        }
    }

    fun remove(key: String) = _cart.update { list -> list.filterNot { it.key == key } }

    fun clear() = _cart.update { emptyList() }

    fun checkout(p: PendingCheckout, allowNegative: Boolean = false) {
        if (_saving.value) return // cegah tekan dua kali
        _saving.value = true
        pending = p
        viewModelScope.launch {
            val result = sales.checkout(
                CheckoutRequest(
                    requestId = requestId, items = _cart.value, transDiscount = p.transDiscount, payment = p.payment,
                    customerName = p.customerName, customerPhone = p.customerPhone, allowNegativeStock = allowNegative,
                ),
            )
            when (result) {
                is CheckoutResult.Success -> {
                    _cart.value = emptyList()
                    requestId = UUID.randomUUID().toString()
                    pending = null
                    _events.send(CashierEvent.Done(result.saleId))
                }
                is CheckoutResult.NeedStockConfirm -> _stockWarning.value = result.shortages
                is CheckoutResult.Error -> _events.send(CashierEvent.Message(result.message))
            }
            _saving.value = false
        }
    }

    fun confirmNegativeStock() {
        _stockWarning.value = emptyList()
        pending?.let { checkout(it, allowNegative = true) }
    }

    fun dismissStockWarning() {
        _stockWarning.value = emptyList()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = kasirApp().container
                CashierViewModel(c.productRepo, c.saleRepo)
            }
        }
    }
}
