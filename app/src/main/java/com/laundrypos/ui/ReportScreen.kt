package com.laundrypos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.laundrypos.billing.Dates
import com.laundrypos.billing.Money
import com.laundrypos.data.DayReport
import com.laundrypos.print.ReceiptFormatter
import com.laundrypos.print.paperDots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun ReportScreen() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    var epochDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    val date = LocalDate.ofEpochDay(epochDay)
    val report by produceState<DayReport?>(null, epochDay) { value = app.repo.dayReport(date) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { epochDay -= 1 }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day") }
            Text(Dates.day(date), style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(200.dp))
            IconButton(
                onClick = { epochDay += 1 },
                enabled = epochDay < LocalDate.now().toEpochDay(),
            ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day") }
            Column(Modifier.weight(1f)) {}
            Button(enabled = report != null, onClick = {
                val r = report ?: return@Button
                scope.launch {
                    val shop = app.settings.current
                    val ok = withContext(Dispatchers.IO) { app.printer.print(ReceiptFormatter(shop.paperDots).dayReport(r, shop)) }
                    if (!ok) snackbar.showSnackbar("Printer not available")
                }
            }) { Text("Print report") }
        }

        val r = report ?: return@Column
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard("Orders", r.billed.count.toString(), Modifier.weight(1f))
            StatCard("Billed", Money.rupees(r.billed.amount), Modifier.weight(1f))
            StatCard("Collected", Money.rupees(r.collected.amount), Modifier.weight(1f))
            StatCard("Delivered", r.deliveredCount.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard("Ready, not collected", r.readyCount.toString(), Modifier.weight(1f))
            StatCard("Total dues (all orders)", Money.rupees(r.outstandingPaise), Modifier.weight(1f))
            Column(Modifier.weight(2f)) {
                if (r.collectedByMode.isNotEmpty()) {
                    Text("Collected by mode", style = MaterialTheme.typography.titleMedium)
                    r.collectedByMode.forEach { TotalRow(it.mode, it.amount) }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}
