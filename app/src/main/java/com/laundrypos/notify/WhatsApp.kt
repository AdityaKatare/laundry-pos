package com.laundrypos.notify

import android.content.Context
import com.laundrypos.billing.Dates
import com.laundrypos.billing.Money
import com.laundrypos.billing.Phones
import com.laundrypos.data.OrderSummary
import java.net.URLEncoder

object WhatsApp {
    private val packages = listOf("com.whatsapp.w4b", "com.whatsapp")

    fun webLink(phone: String, text: String): String =
        "https://wa.me/${Phones.international(phone)}?text=${encode(text)}"

    fun appLink(phone: String, text: String): String =
        "whatsapp://send?phone=${Phones.international(phone)}&text=${encode(text)}"

    fun installedPackage(context: Context): String? = packages.firstOrNull { pkg ->
        runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
    }

    private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
}

enum class MessageKind(val label: String) {
    RECEIVED("Order received"),
    READY("Ready for pickup"),
    DELIVERED("Delivered"),
    DUES("Payment reminder"),
}

object Messages {
    fun forOrder(kind: MessageKind, summary: OrderSummary, shopName: String): String {
        val o = summary.order
        val name = summary.customerName
        val items = "${summary.lineCount} item" + if (summary.lineCount == 1) "" else "s"
        return when (kind) {
            MessageKind.RECEIVED ->
                "Hi $name, we have received your order ${o.invoiceNo} ($items). " +
                    "Total: ${Money.rupees(o.totalPaise)}. Expected ready by ${Dates.day(o.dueAt)}.\n– $shopName"
            MessageKind.READY ->
                "Hi $name, your order ${o.invoiceNo} is ready for pickup." +
                    (if (summary.balancePaise > 0) " Amount due: ${Money.rupees(summary.balancePaise)}." else "") +
                    "\n– $shopName"
            MessageKind.DELIVERED ->
                "Hi $name, your order ${o.invoiceNo} has been delivered. Thank you for choosing $shopName!"
            MessageKind.DUES -> dues(name, summary.balancePaise, shopName)
        }
    }

    fun dues(name: String, duePaise: Long, shopName: String): String =
        "Hi $name, a balance of ${Money.rupees(duePaise)} is pending on your orders with $shopName. " +
            "Please clear it at your next visit. Thank you!"
}
