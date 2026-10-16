package id.kasirku.umkm.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import id.kasirku.umkm.data.backup.BackupManager
import id.kasirku.umkm.ui.components.KasirTopBar
import id.kasirku.umkm.ui.components.SectionCard
import id.kasirku.umkm.ui.kasirApp
import id.kasirku.umkm.ui.theme.Loss
import id.kasirku.umkm.ui.theme.Warn
import id.kasirku.umkm.util.Waktu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.system.exitProcess

sealed interface BackupEvent {
    data class Message(val text: String) : BackupEvent
    data object Restored : BackupEvent
}

class BackupViewModel(private val backup: BackupManager) : ViewModel() {
    val busy = MutableStateFlow(false)
    private val _events = Channel<BackupEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val lastBackupAt: Long get() = backup.lastBackupAt

    fun save(context: Context, uri: Uri) = launchOp {
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { backup.writeBackup(it) } ?: error("no stream")
        }
        _events.send(BackupEvent.Message("Backup berhasil disimpan."))
    }

    fun restore(context: Context, uri: Uri) = launchOp {
        val err = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.let { backup.restore(it) } ?: "File tidak bisa dibuka."
        }
        _events.send(if (err == null) BackupEvent.Restored else BackupEvent.Message(err))
    }

    private fun launchOp(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _events.send(BackupEvent.Message("Gagal: pilih folder lain lalu coba lagi."))
            } finally {
                busy.value = false
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { BackupViewModel(kasirApp().container.backup) }
        }
    }
}

/** Mulai ulang aplikasi setelah database diganti, supaya semua layar membaca data hasil pemulihan. */
fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    if (intent != null) context.startActivity(intent)
    exitProcess(0)
}

@Composable
fun BackupScreen(onBack: () -> Unit, vm: BackupViewModel = viewModel(factory = BackupViewModel.Factory)) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val busy by vm.busy.collectAsStateWithLifecycle()
    var lastBackup by remember { mutableLongStateOf(vm.lastBackupAt) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    var restored by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is BackupEvent.Message -> {
                    lastBackup = vm.lastBackupAt
                    snackbar.showSnackbar(e.text)
                }
                BackupEvent.Restored -> restored = true
            }
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) vm.save(context, uri)
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = uri
    }

    Scaffold(
        topBar = { KasirTopBar("Backup & Pulihkan", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Penting", fontWeight = FontWeight.Bold, color = Warn)
                    Text(
                        "Semua data toko hanya tersimpan di HP/tablet ini. Jika aplikasi dihapus, HP rusak, atau hilang, " +
                            "data ikut hilang — kecuali Anda punya file backup.",
                    )
                    Text(
                        "Buat backup minimal seminggu sekali. Simpan di Google Drive atau kirim ke HP lain supaya aman.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Text(
                if (lastBackup == 0L) "Belum pernah backup." else "Backup terakhir: ${Waktu.formatDateTime(lastBackup)}",
                fontWeight = FontWeight.SemiBold,
                color = if (lastBackup == 0L) Loss else MaterialTheme.colorScheme.onSurface,
            )
            Button(
                onClick = { saveLauncher.launch("AuroraShop-backup-${Waktu.fileStamp()}.kasirku") },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Filled.Backup, contentDescription = null)
                Text("  Buat Backup Sekarang", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Pilih tempat menyimpan: folder Download, atau Google Drive jika aplikasinya terpasang.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { openLauncher.launch(arrayOf("*/*")) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 12.dp),
            ) {
                Icon(Icons.Filled.Restore, contentDescription = null)
                Text("  Pulihkan dari File Backup")
            }
            Text(
                "Memulihkan akan MENGGANTI semua data di HP ini dengan isi file backup.",
                style = MaterialTheme.typography.bodySmall,
                color = Loss,
            )
        }
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Ganti semua data?") },
            text = {
                Text(
                    "Semua data sekarang (barang, transaksi, utang, pengeluaran) akan DIGANTI dengan isi file backup. " +
                        "Data yang dibuat setelah backup itu akan hilang.\n\nSaran: buat backup data sekarang dulu sebelum memulihkan.",
                )
            },
            confirmButton = {
                TextButton(onClick = { pendingRestore = null; vm.restore(context, uri) }) {
                    Text("Ganti & Pulihkan", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Batal") } },
        )
    }

    if (restored) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Data berhasil dipulihkan") },
            text = { Text("Aplikasi akan dimulai ulang untuk memuat data hasil pemulihan.") },
            confirmButton = { TextButton(onClick = { restartApp(context) }) { Text("Mulai Ulang", fontWeight = FontWeight.Bold) } },
        )
    }
}
