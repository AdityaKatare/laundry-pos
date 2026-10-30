@file:OptIn(ExperimentalMaterial3Api::class)

package com.laundrypos.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.laundrypos.billing.Money
import com.laundrypos.print.ReceiptFormatter
import com.laundrypos.print.paperDots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun SettingsScreen() {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val saved by app.settings.state.collectAsState()
    val printerReady by app.printer.ready.collectAsState()
    var draft by remember(saved) { mutableStateOf(saved) }
    var gstRate by remember(saved) { mutableStateOf(Money.formatPercentBp(saved.gstRateBp)) }
    var dueDays by remember(saved) { mutableStateOf(saved.defaultDueDays.toString()) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }

    val gstRateBp = Money.parse(gstRate)?.toInt()
    val dueDaysValue = dueDays.toIntOrNull()
    val valid = draft.shopName.isNotBlank() && gstRateBp != null && gstRateBp in 0..2800 && dueDaysValue != null && dueDaysValue in 0..60 &&
        (!draft.gstEnabled || draft.gstin.length == 15)
    val pending = draft.copy(gstRateBp = gstRateBp ?: draft.gstRateBp, defaultDueDays = dueDaysValue ?: draft.defaultDueDays)

    fun write(uri: Uri, text: String) {
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { write(uri, app.repo.exportBackup()) } }
            snackbar.showSnackbar(if (result.isSuccess) "Backup saved" else "Backup failed: ${result.exceptionOrNull()?.message}")
        }
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { write(uri, app.repo.invoicesCsv()) } }
            snackbar.showSnackbar(if (result.isSuccess) "Invoices exported" else "Export failed: ${result.exceptionOrNull()?.message}")
        }
    }
    val pickRestore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = uri
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Shop", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Shop name", draft.shopName, Modifier.weight(1f)) { draft = draft.copy(shopName = it) }
            Field("Phone", draft.phone, Modifier.weight(1f), KeyboardType.Phone) { draft = draft.copy(phone = it) }
        }
        Field("Address", draft.address, Modifier.fillMaxWidth()) { draft = draft.copy(address = it) }
        OutlinedTextField(
            value = draft.footer,
            onValueChange = { draft = draft.copy(footer = it) },
            label = { Text("Receipt footer / terms (printed small)") },
            minLines = 3,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("GST registered", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Switch(checked = draft.gstEnabled, onCheckedChange = { draft = draft.copy(gstEnabled = it) })
        }
        if (draft.gstEnabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("GSTIN (15 characters)", draft.gstin, Modifier.weight(1.4f)) { draft = draft.copy(gstin = it.uppercase().take(15)) }
                Field("State (place of supply)", draft.stateName, Modifier.weight(1f)) { draft = draft.copy(stateName = it) }
                Field("SAC code", draft.sacCode, Modifier.weight(0.7f), KeyboardType.Number) { draft = draft.copy(sacCode = it) }
                Field("GST rate %", gstRate, Modifier.weight(0.6f), KeyboardType.Decimal) { gstRate = it }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Prices already include GST", modifier = Modifier.weight(1f))
                Switch(checked = draft.pricesIncludeGst, onCheckedChange = { draft = draft.copy(pricesIncludeGst = it) })
            }
        }

        HorizontalDivider()
        Text("Orders & printing", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Field("Invoice prefix", draft.invoicePrefix, Modifier.weight(1f)) {
                draft = draft.copy(invoicePrefix = it.filter { c -> c.isLetterOrDigit() }.uppercase().take(5))
            }
            Field("Default ready in (days)", dueDays, Modifier.weight(1f), KeyboardType.Number) { dueDays = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Paper")
            FilterChip(selected = draft.paperChars == 32, onClick = { draft = draft.copy(paperChars = 32) }, label = { Text("58 mm") })
            FilterChip(selected = draft.paperChars == 48, onClick = { draft = draft.copy(paperChars = 48) }, label = { Text("80 mm") })
            Text("   Copies")
            FilterChip(selected = draft.copies == 1, onClick = { draft = draft.copy(copies = 1) }, label = { Text("1") })
            FilterChip(selected = draft.copies == 2, onClick = { draft = draft.copy(copies = 2) }, label = { Text("2 (customer + shop)") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(enabled = valid && pending != saved, onClick = {
                app.settings.save(pending)
                scope.launch { snackbar.showSnackbar("Settings saved") }
            }) { Text("Save settings") }
            if (draft.gstEnabled && draft.gstin.length != 15) {
                Text("GSTIN must be 15 characters", color = MaterialTheme.colorScheme.error)
            }
            Text(
                "Printer: " + if (printerReady) "connected" else "not found",
                modifier = Modifier.weight(1f),
                color = if (printerReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            OutlinedButton(enabled = printerReady, onClick = {
                scope.launch {
                    val shop = app.settings.current
                    val ok = withContext(Dispatchers.IO) { app.printer.print(ReceiptFormatter(shop.paperDots).testPage(shop)) }
                    if (!ok) snackbar.showSnackbar("Print failed")
                }
            }) { Text("Test print") }
        }

        HorizontalDivider()
        Text("Data", style = MaterialTheme.typography.titleLarge)
        Text(
            "All data stays on this device. Save a backup regularly to a USB drive or cloud folder.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { exportBackup.launch("laundry-backup-${LocalDate.now()}.json") }) { Text("Save backup") }
            OutlinedButton(onClick = { pickRestore.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }) {
                Text("Restore backup")
            }
            OutlinedButton(onClick = { exportCsv.launch("invoices-${LocalDate.now()}.csv") }) { Text("Export invoices (CSV)") }
        }
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore backup?") },
            text = { Text("This replaces ALL current orders, customers, prices and settings with the backup's contents.") },
            confirmButton = {
                Button(onClick = {
                    pendingRestore = null
                    scope.launch {
                        val result = runCatching {
                            val text = withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                            }
                            app.repo.importBackup(text)
                        }
                        snackbar.showSnackbar(if (result.isSuccess) "Backup restored" else "Restore failed: ${result.exceptionOrNull()?.message}")
                    }
                }) { Text("Replace everything") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    modifier: Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = modifier,
    )
}
