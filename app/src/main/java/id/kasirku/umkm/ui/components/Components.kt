package id.kasirku.umkm.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.kasirku.umkm.domain.Money

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KasirTopBar(title: String, onBack: (() -> Unit)? = null, subtitle: String? = null) {
    TopAppBar(
        title = {
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = id.kasirku.umkm.ui.theme.BrandDark,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

/** Tombol menu besar di Beranda. Tombol nonaktif selalu menjelaskan alasannya di [subtitle]. */
@Composable
fun BigMenuButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    highlight: Boolean = false,
) {
    val container = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant
        highlight -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val content = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        highlight -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container,
            disabledContentColor = content,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (enabled) 1.dp else 0.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified, strong: Boolean = false) {
    Column(modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = if (strong) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor,
        )
    }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box(Modifier.padding(16.dp)) { content() }
    }
}

/** Kolom uang: hanya angka; di bawahnya tampil format Rupiah agar pengguna yakin jumlahnya benar. */
@Composable
fun MoneyField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, error: String? = null) {
    val parsed = Money.parse(value)
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { c -> c.isDigit() }.take(12)) },
        label = { Text(label) },
        prefix = { Text("Rp ") },
        singleLine = true,
        isError = error != null,
        supportingText = {
            when {
                error != null -> Text(error)
                parsed != null -> Text(Money.format(parsed))
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun QtyField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, suffix: String? = null, error: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(10)) },
        label = { Text(label) },
        suffix = if (suffix != null) { { Text(suffix) } } else null,
        singleLine = true,
        isError = error != null,
        supportingText = if (error != null) { { Text(error) } } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

/**
 * Isian hitung stok per satuan: [dus] [renteng] [pcs]. Satuan terbesar di kiri.
 * [values] = nama satuan -> teks yang diketik.
 */
@Composable
fun MultiUnitInput(
    baseUnit: String,
    bigUnits: List<id.kasirku.umkm.domain.UnitOption>,
    values: Map<String, String>,
    onChange: (Map<String, String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val names = bigUnits.sortedByDescending { it.factor }.map { it.name } + baseUnit
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        names.forEach { name ->
            OutlinedTextField(
                value = values[name].orEmpty(),
                onValueChange = { v -> onChange(values + (name to v.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(8))) },
                label = { Text(name) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Total hasil hitung dalam satuan dasar; null jika semua isian masih kosong. */
fun multiUnitTotal(baseUnit: String, bigUnits: List<id.kasirku.umkm.domain.UnitOption>, values: Map<String, String>): Double? {
    if (values.values.all { it.isBlank() }) return null
    val factors = bigUnits.associate { it.name to it.factor } + (baseUnit to 1.0)
    val parts = values.mapNotNull { (name, text) ->
        val f = factors[name] ?: return@mapNotNull null
        val q = id.kasirku.umkm.domain.Qty.parse(text) ?: 0.0
        q to f
    }
    return id.kasirku.umkm.domain.UnitCalc.sumParts(parts)
}
