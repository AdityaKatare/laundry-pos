@file:OptIn(ExperimentalMaterial3Api::class)

package com.laundrypos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.laundrypos.billing.Money
import com.laundrypos.data.PriceItem
import com.laundrypos.data.PricingUnit
import kotlinx.coroutines.launch

@Composable
fun PricesScreen() {
    val app = LocalApp.current
    val items by app.repo.prices.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<PriceItem?>(null) }
    var deleting by remember { mutableStateOf<PriceItem?>(null) }
    val services = items.map { it.service }.distinct()

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Price list", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(onClick = {
                editing = PriceItem(name = "", service = services.firstOrNull() ?: "", unit = PricingUnit.PIECE, ratePaise = 0)
            }) { Text("Add item") }
        }
        LazyColumn {
            items(items, key = { it.id }) { item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.service, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(item.name, Modifier.weight(1.2f), fontWeight = FontWeight.SemiBold)
                    Text(Money.rupees(item.ratePaise) + if (item.unit == PricingUnit.KG) " / kg" else " / pc", Modifier.weight(0.8f))
                    Switch(checked = item.active, onCheckedChange = { on -> scope.launch { app.repo.prices.update(item.copy(active = on)) } })
                    IconButton(onClick = { editing = item }) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
                    IconButton(onClick = { deleting = item }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                }
                HorizontalDivider()
            }
        }
    }

    editing?.let { item ->
        PriceItemDialog(
            initial = item,
            services = services,
            onDismiss = { editing = null },
            onSave = { updated ->
                editing = null
                scope.launch { if (updated.id == 0L) app.repo.prices.insert(updated) else app.repo.prices.update(updated) }
            },
        )
    }

    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${item.name} (${item.service})?") },
            text = { Text("Past orders keep their prices. To hide it temporarily, switch it off instead.") },
            confirmButton = {
                Button(onClick = {
                    deleting = null
                    scope.launch { app.repo.prices.delete(item) }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } },
        )
    }
}

@Composable
private fun PriceItemDialog(initial: PriceItem, services: List<String>, onDismiss: () -> Unit, onSave: (PriceItem) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var service by remember { mutableStateOf(initial.service) }
    var unit by remember { mutableStateOf(initial.unit) }
    var rate by remember { mutableStateOf(if (initial.ratePaise > 0) Money.format(initial.ratePaise) else "") }
    val ratePaise = Money.parse(rate)
    val valid = name.isNotBlank() && service.isNotBlank() && ratePaise != null && ratePaise > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Add item" else "Edit item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Item (e.g. Shirt)") }, singleLine = true)
                OutlinedTextField(service, { service = it }, label = { Text("Service (e.g. Dry Clean)") }, singleLine = true)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(services) { s -> AssistChip(onClick = { service = s }, label = { Text(s) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = unit == PricingUnit.PIECE, onClick = { unit = PricingUnit.PIECE }, label = { Text("Per piece") })
                    FilterChip(selected = unit == PricingUnit.KG, onClick = { unit = PricingUnit.KG }, label = { Text("Per kg") })
                }
                OutlinedTextField(
                    rate,
                    { rate = it },
                    label = { Text("Rate ₹") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            Button(enabled = valid, onClick = {
                onSave(initial.copy(name = name.trim(), service = service.trim(), unit = unit, ratePaise = ratePaise ?: 0))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
