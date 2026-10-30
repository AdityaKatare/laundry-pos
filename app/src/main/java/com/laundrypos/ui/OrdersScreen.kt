@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.laundrypos.billing.Dates
import com.laundrypos.billing.Money
import com.laundrypos.data.OrderStatus
import com.laundrypos.data.OrderSummary
import com.laundrypos.data.PricingUnit
import com.laundrypos.notify.MessageKind
import com.laundrypos.notify.Messages
import kotlinx.coroutines.launch

@Composable
fun OrdersScreen(onOpenOrder: (Long) -> Unit) {
    val app = LocalApp.current
    var query by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf<OrderStatus?>(OrderStatus.RECEIVED) }
    val orders by remember(query, status) { app.repo.orders.search(status?.name ?: "", query.trim()) }
        .collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search phone, name or invoice") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            FilterChip(selected = status == null, onClick = { status = null }, label = { Text("All") })
            listOf(OrderStatus.RECEIVED, OrderStatus.READY, OrderStatus.DELIVERED).forEach { s ->
                FilterChip(
                    selected = status == s,
                    onClick = { status = s },
                    label = { Text(s.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }
        if (orders.isEmpty()) Text("No orders.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn {
            items(orders, key = { it.order.id }) { summary ->
                OrderRow(summary, onClick = { onOpenOrder(summary.order.id) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun OrderRow(summary: OrderSummary, onClick: () -> Unit) {
    val o = summary.order
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1.2f)) {
            Text(o.invoiceNo, fontWeight = FontWeight.SemiBold)
            Text(Dates.dateTime(o.createdAt), style = MaterialTheme.typography.bodySmall)
        }
        Column(Modifier.weight(1.5f)) {
            Text(summary.customerName)
            Text(summary.customerPhone, style = MaterialTheme.typography.bodySmall)
        }
        Text("Due ${Dates.day(o.dueAt)}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(Money.rupees(o.totalPaise), fontWeight = FontWeight.SemiBold)
            if (summary.balancePaise > 0) {
                Text("Bal ${Money.rupees(summary.balancePaise)}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        Box(Modifier.weight(0.8f), contentAlignment = Alignment.CenterEnd) { StatusBadge(o.status) }
    }
}

@Composable
fun OrderDetailDialog(orderId: Long, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val settings by app.settings.state.collectAsState()
    val summary by remember(orderId) { app.repo.orders.observeSummary(orderId) }.collectAsState(initial = null)
    val lines by remember(orderId) { app.repo.orders.observeLines(orderId) }.collectAsState(initial = emptyList())
    val payments by remember(orderId) { app.repo.payments.observeForOrder(orderId) }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val whatsApp = rememberWhatsApp()
    var paying by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(0.92f), shape = MaterialTheme.shapes.large) {
            val s = summary
            if (s == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading…") }
                return@Surface
            }
            val o = s.order
            fun notify(kind: MessageKind) = whatsApp.send(s.customerPhone, Messages.forOrder(kind, s, settings.shopName))

            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(o.invoiceNo, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    StatusBadge(o.status)
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                }
                Text("${s.customerName} · ${s.customerPhone}", style = MaterialTheme.typography.titleMedium)
                Text("Received ${Dates.dateTime(o.createdAt)} · Ready by ${Dates.day(o.dueAt)}", style = MaterialTheme.typography.bodyMedium)
                if (o.notes.isNotBlank()) Text("Notes: ${o.notes}", style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider()

                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    LazyColumn(Modifier.weight(1.3f)) {
                        items(lines, key = { it.id }) { line ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text("${line.name} (${line.service})", Modifier.weight(1f))
                                Text(
                                    Money.formatQty(line.qtyMilli) + (if (line.unit == PricingUnit.KG) " kg" else "") +
                                        " × " + Money.format(line.ratePaise),
                                    Modifier.padding(horizontal = 12.dp),
                                )
                                Text(Money.rupees(line.amountPaise))
                            }
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TotalRow("Subtotal", o.grossPaise)
                        if (o.discountPaise > 0) TotalRow("Discount", -o.discountPaise)
                        if (o.gstEnabled) {
                            TotalRow("Taxable value", o.taxablePaise)
                            TotalRow("CGST", o.cgstPaise)
                            TotalRow("SGST", o.sgstPaise)
                        }
                        if (o.roundOffPaise != 0L) TotalRow("Round off", o.roundOffPaise)
                        TotalRow("Total", o.totalPaise, emphasize = true)
                        payments.forEach { p ->
                            Text(
                                "Paid ${Money.rupees(p.amountPaise)} · ${p.mode} · ${Dates.dateTime(p.paidAt)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TotalRow("Balance", s.balancePaise, emphasize = s.balancePaise > 0)
                    }
                }

                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    when (o.status) {
                        OrderStatus.RECEIVED -> Button(onClick = {
                            scope.launch {
                                app.repo.setStatus(o.id, OrderStatus.READY)
                                app.repo.orders.summary(o.id)?.let {
                                    whatsApp.send(it.customerPhone, Messages.forOrder(MessageKind.READY, it, settings.shopName))
                                }
                            }
                        }) { Text("Mark ready & notify") }
                        OrderStatus.READY -> Button(onClick = {
                            scope.launch { app.repo.setStatus(o.id, OrderStatus.DELIVERED) }
                        }) { Text("Mark delivered") }
                        else -> {}
                    }
                    if (s.balancePaise > 0) OutlinedButton(onClick = { paying = true }) { Text("Record payment") }
                    OutlinedButton(onClick = {
                        scope.launch { if (!app.printOrder(o.id, 1)) snackbar.showSnackbar("Printer not available") }
                    }) { Text("Print") }
                    Box {
                        OutlinedButton(onClick = { menuOpen = true }) { Text("WhatsApp…") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            MessageKind.entries.forEach { kind ->
                                DropdownMenuItem(text = { Text(kind.label) }, onClick = {
                                    menuOpen = false
                                    notify(kind)
                                })
                            }
                        }
                    }
                    Box(Modifier.weight(1f))
                    if (o.status == OrderStatus.RECEIVED || o.status == OrderStatus.READY) {
                        TextButton(onClick = { confirmCancel = true }) {
                            Text("Cancel order", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            if (paying) {
                PaymentDialog(
                    balancePaise = s.balancePaise,
                    onDismiss = { paying = false },
                    onConfirm = { amount, mode ->
                        paying = false
                        scope.launch { app.repo.addPayment(o.id, amount, mode) }
                    },
                )
            }
            if (confirmCancel) {
                AlertDialog(
                    onDismissRequest = { confirmCancel = false },
                    title = { Text("Cancel ${o.invoiceNo}?") },
                    text = { Text("The invoice number stays in the records, marked cancelled. This can't be undone.") },
                    confirmButton = {
                        Button(onClick = {
                            confirmCancel = false
                            scope.launch { app.repo.setStatus(o.id, OrderStatus.CANCELLED) }
                        }) { Text("Cancel order") }
                    },
                    dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Keep") } },
                )
            }
        }
    }
}

private val PAYMENT_MODES = listOf("UPI", "Cash", "Card")

@Composable
private fun PaymentDialog(balancePaise: Long, onDismiss: () -> Unit, onConfirm: (Long, String) -> Unit) {
    var text by remember { mutableStateOf(Money.format(balancePaise)) }
    var mode by remember { mutableStateOf(PAYMENT_MODES.first()) }
    val amount = Money.parse(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Amount ₹") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PAYMENT_MODES.forEach { m -> FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m) }) }
                }
            }
        },
        confirmButton = {
            Button(enabled = amount != null && amount > 0, onClick = { amount?.let { onConfirm(it, mode) } }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
