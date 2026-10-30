package com.laundrypos

import android.app.Application
import android.content.Context
import com.laundrypos.data.AppDatabase
import com.laundrypos.data.Repository
import com.laundrypos.data.SettingsStore
import com.laundrypos.print.ReceiptFormatter
import com.laundrypos.print.SunmiPrinter
import com.laundrypos.print.paperDots
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LaundryApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.scope.launch { container.repo.seedIfEmpty() }
    }
}

class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val settings = SettingsStore(context)
    val repo = Repository(AppDatabase.build(context), settings)
    val printer = SunmiPrinter(context).also { it.connect() }

    suspend fun printOrder(orderId: Long, copies: Int? = null): Boolean = withContext(Dispatchers.IO) {
        val receipt = repo.receipt(orderId) ?: return@withContext false
        val shop = settings.current
        val formatter = ReceiptFormatter(shop.paperDots)
        val count = copies ?: shop.copies
        (1..count).all { i ->
            val label = when {
                count == 1 -> null
                i == 1 -> "CUSTOMER COPY"
                else -> "SHOP COPY"
            }
            printer.print(formatter.invoice(receipt, shop, label))
        }
    }
}
