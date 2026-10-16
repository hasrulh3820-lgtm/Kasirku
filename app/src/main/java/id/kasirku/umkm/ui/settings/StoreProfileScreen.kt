package id.kasirku.umkm.ui.settings

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import id.kasirku.umkm.data.db.entity.StoreProfile
import id.kasirku.umkm.data.repository.SaleRepository
import id.kasirku.umkm.ui.components.KasirTopBar
import id.kasirku.umkm.ui.kasirApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class StoreProfileViewModel(private val repo: SaleRepository) : ViewModel() {
    suspend fun load(): StoreProfile = repo.observeProfile().first()
    fun save(p: StoreProfile, done: () -> Unit) {
        viewModelScope.launch { repo.saveProfile(p); done() }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { StoreProfileViewModel(kasirApp().container.saleRepo) }
        }
    }
}

@Composable
fun StoreProfileScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    vm: StoreProfileViewModel = viewModel(factory = StoreProfileViewModel.Factory),
) {
    var loaded by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var footer by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (!loaded) {
            val p = vm.load()
            name = p.name; address = p.address; phone = p.phone; footer = p.receiptFooter
            loaded = true
        }
    }

    Scaffold(topBar = { KasirTopBar("Profil Toko", onBack = onBack, subtitle = "Tampil di bagian atas struk") }) { pad ->
        if (!loaded) return@Scaffold
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(name, { name = it.take(40) }, label = { Text("Nama toko *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(address, { address = it.take(64) }, label = { Text("Alamat") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(phone, { phone = it.take(20) }, label = { Text("No. HP / WhatsApp toko") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(footer, { footer = it.take(64) }, label = { Text("Pesan di bawah struk") }, modifier = Modifier.fillMaxWidth())
            if (name.isBlank()) Text("Nama toko wajib diisi.", color = MaterialTheme.colorScheme.error)
            Button(
                onClick = { vm.save(StoreProfile(name = name.trim(), address = address.trim(), phone = phone.trim(), receiptFooter = footer.trim()), onSaved) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(56.dp),
            ) { Text("Simpan", style = MaterialTheme.typography.titleMedium) }
        }
    }
}
