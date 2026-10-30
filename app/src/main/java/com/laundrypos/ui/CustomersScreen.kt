package com.laundrypos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.laundrypos.billing.Money
import com.laundrypos.notify.Messages
import kotlinx.coroutines.launch

@Composable
fun CustomersScreen(onOpenOrder: (Long) -> Unit) {
    val app = LocalApp.current
    var query by rememberSaveable { mutableStateOf("") }
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    val customers by remember(query) { app.repo.customers.search(query.trim()) }.collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search name or phone") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (customers.isEmpty()) Text("No customers yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn {
            items(customers, key = { it.customer.id }) { c ->
                Row(
                    Modifier.fillMaxWidth().clickable { openId = c.customer.id }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(c.customer.name, fontWeight = FontWeight.SemiBold)
                        Text(c.customer.phone, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${c.orderCount} orders", Modifier.weight(0.5f))
                    Text(
                        if (c.duePaise > 0) "Due ${Money.rupees(c.duePaise)}" else "No dues",
                        Modifier.weight(0.6f),
                        color = if (c.duePaise > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
        }
    }

    openId?.let { CustomerDialog(it, onOpenOrder, onDismiss = { openId = null }) }
}

@Composable
private fun CustomerDialog(customerId: Long, onOpenOrder: (Long) -> Unit, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val settings by app.settings.state.collectAsState()
    val summary by remember(customerId) { app.repo.customers.observeSummary(customerId) }.collectAsState(initial = null)
    val orders by remember(customerId) { app.repo.orders.observeForCustomer(customerId) }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val whatsApp = rememberWhatsApp()
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var gstin by remember { mutableStateOf("") }

    LaunchedEffect(summary?.customer?.id) {
        summary?.customer?.let {
            name = it.name
            address = it.address
            gstin = it.gstin
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(0.92f), shape = MaterialTheme.shapes.large) {
            val s = summary
            if (s == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading…") }
                return@Surface
            }
            val c = s.customer
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.phone, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(address, { address = it }, label = { Text("Address") }, singleLine = true, modifier = Modifier.weight(1.5f))
                    OutlinedTextField(gstin, { gstin = it.uppercase().take(15) }, label = { Text("GSTIN (business)") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        enabled = name.isNotBlank() && (name != c.name || address != c.address || gstin != c.gstin),
                        onClick = {
                            scope.launch {
                                app.repo.customers.update(c.copy(name = name.trim(), address = address.trim(), gstin = gstin.trim()))
                                snackbar.showSnackbar("Customer updated")
                            }
                        },
                    ) { Text("Save") }
                    if (s.duePaise > 0) {
                        OutlinedButton(onClick = {
                            whatsApp.send(c.phone, Messages.dues(c.name, s.duePaise, settings.shopName))
                        }) { Text("WhatsApp dues reminder") }
                    }
                    Box(Modifier.weight(1f))
                    Text(
                        if (s.duePaise > 0) "Total due ${Money.rupees(s.duePaise)}" else "No dues",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (s.duePaise > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
                LazyColumn {
                    items(orders, key = { it.order.id }) { o ->
                        OrderRow(o, onClick = { onOpenOrder(o.order.id) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
