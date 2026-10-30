package com.laundrypos.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.laundrypos.LaundryApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as LaundryApp).container
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF00695C), secondary = Color(0xFF00897B))) {
                CompositionLocalProvider(LocalApp provides container) {
                    AppRoot()
                }
            }
        }
    }
}

private enum class Section(val label: String, val icon: ImageVector) {
    NEW_ORDER("New order", Icons.Filled.Add),
    ORDERS("Orders", Icons.AutoMirrored.Filled.List),
    CUSTOMERS("Customers", Icons.Filled.Person),
    REPORT("Report", Icons.Filled.DateRange),
    PRICES("Prices", Icons.Filled.Edit),
    SETTINGS("Settings", Icons.Filled.Settings),
}

@Composable
private fun AppRoot() {
    var section by rememberSaveable { mutableStateOf(Section.NEW_ORDER) }
    var openOrderId by rememberSaveable { mutableStateOf<Long?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val openOrder: (Long) -> Unit = { openOrderId = it }

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            Row(Modifier.padding(padding).fillMaxSize()) {
                NavigationRail {
                    Section.entries.forEach {
                        NavigationRailItem(
                            selected = it == section,
                            onClick = { section = it },
                            icon = { Icon(it.icon, contentDescription = null) },
                            label = { Text(it.label) },
                        )
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when (section) {
                        Section.NEW_ORDER -> NewOrderScreen()
                        Section.ORDERS -> OrdersScreen(openOrder)
                        Section.CUSTOMERS -> CustomersScreen(openOrder)
                        Section.REPORT -> ReportScreen()
                        Section.PRICES -> PricesScreen()
                        Section.SETTINGS -> SettingsScreen()
                    }
                }
            }
        }
        openOrderId?.let { OrderDetailDialog(it, onDismiss = { openOrderId = null }) }
    }
}
