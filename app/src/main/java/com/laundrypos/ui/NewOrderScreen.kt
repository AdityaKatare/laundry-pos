@file:OptIn(ExperimentalMaterial3Api::class)

package com.laundrypos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.laundrypos.billing.Billing
import com.laundrypos.billing.Dates
import com.laundrypos.billing.Money
import com.laundrypos.billing.Phones
import com.laundrypos.data.Customer
import com.laundrypos.data.DraftLine
import com.laundrypos.data.OrderDraft
import com.laundrypos.data.OrderSummary
import com.laundrypos.data.PriceItem
import com.laundrypos.data.PricingUnit
import com.laundrypos.data.Repository
import com.laundrypos.notify.MessageKind
import com.laundrypos.notify.Messages
import kotlinx.coroutines.launch

class NewOrderViewModel(private val repo: Repository, private val defaultDueDays: Int) : ViewModel() {
    var phone by mutableStateOf("")
    var name by mutableStateOf("")
    var address by mutableStateOf("")
    var known by mutableStateOf<Customer?>(null)
        private set
    val cart = mutableStateListOf<DraftLine>()
    var discountText by mutableStateOf("")
    var dueDays by mutableIntStateOf(defaultDueDays)
    var notes by mutableStateOf("")

    val discountPaise: Long get() = Money.parse(discountText) ?: 0L
    val canSave: Boolean get() = Phones.isValid(phone) && name.isNotBlank() && cart.isNotEmpty()

    suspend fun lookup() {
        if (!Phones.isValid(phone)) {
            known = null
            return
        }
        val customer = repo.findCustomer(phone)
        if (customer != null && customer.id != known?.id) {
            name = customer.name
            address = customer.address
        }
        known = customer
    }

    fun add(item: PriceItem, qtyMilli: Long) {
        val line = DraftLine(item.name, item.service, item.unit, item.ratePaise, qtyMilli)
        val index = cart.indexOfFirst { it.sameItem(line) }
        if (index >= 0) cart[index] = cart[index].copy(qtyMilli = cart[index].qtyMilli + qtyMilli) else cart.add(line)
    }

    fun setQty(index: Int, qtyMilli: Long) {
        if (qtyMilli <= 0) cart.removeAt(index) else cart[index] = cart[index].copy(qtyMilli = qtyMilli)
    }

    suspend fun save(): Long = repo.createOrder(
        OrderDraft(
            phone = phone,
            name = name.trim(),
            address = address.trim(),
            lines = cart.toList(),
            discountPaise = discountPaise,
            dueAt = Dates.dueAt(dueDays),
            notes = notes.trim(),
        )
    )

    fun reset() {
        phone = ""
        name = ""
        address = ""
        known = null
        cart.clear()
        discountText = ""
        dueDays = defaultDueDays
        notes = ""
    }
}

private val DUE_OPTIONS = listOf(0 to "Today", 1 to "+1 day", 2 to "+2 days", 3 to "+3 days", 5 to "+5 days", 7 to "+7 days")

@Composable
fun NewOrderScreen() {
    val app = LocalApp.current
    val settings by app.settings.state.collectAsState()
    val vm: NewOrderViewModel = viewModel { NewOrderViewModel(app.repo, app.settings.current.defaultDueDays) }
    val items by app.repo.prices.observeActive().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val whatsApp = rememberWhatsApp()

    var service by remember { mutableStateOf<String?>(null) }
    var weighing by remember { mutableStateOf<PriceItem?>(null) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var saved by remember { mutableStateOf<OrderSummary?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(vm.phone) { vm.lookup() }

    val services = items.map { it.service }.distinct()
    val selectedService = service?.takeIf { it in services } ?: services.firstOrNull()
    val totals = Billing.totals(
        vm.cart.map { it.amountPaise },
        vm.discountPaise,
        settings.gstEnabled,
        settings.gstRateBp,
        settings.pricesIncludeGst,
    )

    Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1.4f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = vm.phone,
                            onValueChange = { vm.phone = it.filter { c -> c.isDigit() || c == '+' }.take(14) },
                            label = { Text("Phone") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = vm.name,
                            onValueChange = { vm.name = it },
                            label = { Text("Name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        value = vm.address,
                        onValueChange = { vm.address = it },
                        label = { Text("Address (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        when {
                            vm.known != null -> "Returning customer"
                            Phones.isValid(vm.phone) -> "New customer"
                            else -> "Enter a 10-digit mobile number"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(services) { s ->
                    FilterChip(selected = s == selectedService, onClick = { service = s }, label = { Text(s) })
                }
            }

            if (items.isEmpty()) {
                Text("No prices yet. Add items in Prices.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(items.filter { it.service == selectedService }, key = { it.id }) { item ->
                    ElevatedCard(
                        onClick = {
                            if (item.unit == PricingUnit.KG) weighing = item else vm.add(item, 1000)
                        },
                    ) {
                        Column(Modifier.padding(12.dp).fillMaxWidth()) {
                            Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                Money.rupees(item.ratePaise) + if (item.unit == PricingUnit.KG) " / kg" else "",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }

        Card(Modifier.weight(1f).fillMaxHeight()) {
            Column(Modifier.padding(12.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Order", style = MaterialTheme.typography.titleLarge)
                LazyColumn(Modifier.weight(1f)) {
                    if (vm.cart.isEmpty()) {
                        item { Text("Tap items on the left to add them.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    itemsIndexed(vm.cart) { index, line ->
                        CartRow(
                            line = line,
                            onMinus = { vm.setQty(index, line.qtyMilli - 1000) },
                            onPlus = { vm.setQty(index, line.qtyMilli + 1000) },
                            onEdit = { editing = index },
                            onRemove = { vm.setQty(index, 0) },
                        )
                        HorizontalDivider()
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = vm.discountText,
                        onValueChange = { vm.discountText = it },
                        label = { Text("Discount ₹") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = vm.notes,
                        onValueChange = { vm.notes = it },
                        label = { Text("Notes (stains, damage)") },
                        singleLine = true,
                        modifier = Modifier.weight(2f),
                    )
                }
                Text("Ready by: ${Dates.day(Dates.dueAt(vm.dueDays))}", style = MaterialTheme.typography.bodyMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(DUE_OPTIONS) { (days, label) ->
                        FilterChip(selected = vm.dueDays == days, onClick = { vm.dueDays = days }, label = { Text(label) })
                    }
                }
                HorizontalDivider()
                TotalRow("Subtotal", totals.grossPaise)
                if (totals.discountPaise > 0) TotalRow("Discount", -totals.discountPaise)
                if (settings.gstEnabled) {
                    TotalRow("CGST + SGST", totals.cgstPaise + totals.sgstPaise)
                }
                if (totals.roundOffPaise != 0L) TotalRow("Round off", totals.roundOffPaise)
                TotalRow("Total", totals.totalPaise, emphasize = true)
                Button(
                    enabled = vm.canSave && !saving,
                    onClick = {
                        saving = true
                        scope.launch {
                            try {
                                val id = vm.save()
                                saved = app.repo.orders.summary(id)
                                if (!app.printOrder(id)) snackbar.showSnackbar("Saved, but the printer is not available")
                            } catch (e: Exception) {
                                snackbar.showSnackbar("Could not save order: ${e.message}")
                            } finally {
                                saving = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("Save & print") }
            }
        }
    }

    weighing?.let { item ->
        QuantityDialog(
            title = "${item.name} (${item.service}) — weight in kg",
            initial = "",
            onDismiss = { weighing = null },
            onConfirm = { qty ->
                vm.add(item, qty)
                weighing = null
            },
        )
    }

    editing?.let { index ->
        val line = vm.cart.getOrNull(index)
        if (line == null) {
            editing = null
        } else {
            QuantityDialog(
                title = "${line.name} — " + if (line.unit == PricingUnit.KG) "weight in kg" else "quantity",
                initial = Money.formatQty(line.qtyMilli),
                onDismiss = { editing = null },
                onConfirm = { qty ->
                    vm.setQty(index, qty)
                    editing = null
                },
            )
        }
    }

    saved?.let { summary ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Order ${summary.order.invoiceNo} saved") },
            text = { Text("${summary.customerName} · ${Money.rupees(summary.order.totalPaise)}") },
            confirmButton = {
                Button(onClick = {
                    saved = null
                    vm.reset()
                }) { Text("New order") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        whatsApp.send(summary.customerPhone, Messages.forOrder(MessageKind.RECEIVED, summary, settings.shopName))
                    }) { Text("WhatsApp") }
                    OutlinedButton(onClick = {
                        scope.launch {
                            if (!app.printOrder(summary.order.id, 1)) snackbar.showSnackbar("Printer not available")
                        }
                    }) { Text("Print again") }
                }
            },
        )
    }
}

@Composable
private fun CartRow(line: DraftLine, onMinus: () -> Unit, onPlus: () -> Unit, onEdit: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(line.name, fontWeight = FontWeight.SemiBold)
            Text("${line.service} · ${Money.rupees(line.ratePaise)}", style = MaterialTheme.typography.bodySmall)
        }
        if (line.unit == PricingUnit.PIECE) {
            FilledTonalButton(onClick = onMinus, contentPadding = SMALL_PADDING) { Text("−") }
            TextButton(onClick = onEdit) { Text(Money.formatQty(line.qtyMilli)) }
            FilledTonalButton(onClick = onPlus, contentPadding = SMALL_PADDING) { Text("+") }
        } else {
            TextButton(onClick = onEdit) { Text(Money.formatQty(line.qtyMilli) + " kg") }
        }
        Spacer(Modifier.padding(4.dp))
        Text(Money.rupees(line.amountPaise), fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, contentDescription = "Remove") }
    }
}

private val SMALL_PADDING = PaddingValues(horizontal = 4.dp)

@Composable
fun TotalRow(label: String, paise: Long, emphasize: Boolean = false) {
    val style = if (emphasize) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = style, modifier = Modifier.weight(1f))
        Text(Money.rupees(paise), style = style, fontWeight = if (emphasize) FontWeight.Bold else null)
    }
}

@Composable
fun QuantityDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val qty = Money.parseQty(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = {
            Button(enabled = qty != null && qty > 0, onClick = { qty?.let(onConfirm) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
