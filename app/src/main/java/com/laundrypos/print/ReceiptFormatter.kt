package com.laundrypos.print

import com.laundrypos.billing.Dates
import com.laundrypos.billing.Money
import com.laundrypos.data.DayReport
import com.laundrypos.data.OrderLine
import com.laundrypos.data.PricingUnit
import com.laundrypos.data.Receipt
import com.laundrypos.data.ShopSettings

enum class Align { LEFT, CENTER, RIGHT }

enum class TextSize(val points: Float) { SMALL(18f), NORMAL(22f), LARGE(28f) }

sealed interface PrintLine {
    data class Text(
        val text: String,
        val align: Align = Align.LEFT,
        val size: TextSize = TextSize.NORMAL,
        val bold: Boolean = false,
    ) : PrintLine

    data object Feed : PrintLine
}

val ShopSettings.paperDots: Int get() = if (paperChars >= 48) 576 else 384

class ReceiptFormatter(private val paperDots: Int) {
    private val width = chars(TextSize.NORMAL)

    fun chars(size: TextSize): Int = (paperDots / (size.points / 2)).toInt() - 2

    fun invoice(receipt: Receipt, shop: ShopSettings, copyLabel: String? = null): List<PrintLine> {
        val order = receipt.order
        val out = mutableListOf<PrintLine>()
        out += header(shop)
        out += PrintLine.Text(if (order.gstEnabled) "TAX INVOICE" else "RECEIPT", Align.CENTER, bold = true)
        copyLabel?.let { out += PrintLine.Text(it, Align.CENTER) }
        out += text(rule())
        out += text(row("Invoice", order.invoiceNo))
        out += text(row("Date", Dates.dateTime(order.createdAt)))
        out += text(row("Due", Dates.day(order.dueAt)))
        out += text(row("Customer", receipt.customer.name))
        out += text(row("Phone", receipt.customer.phone))
        if (receipt.customer.gstin.isNotBlank()) out += text(row("GSTIN", receipt.customer.gstin))
        out += text(rule())
        receipt.lines.forEach { out += lineItem(it) }
        out += text(rule())
        out += text(row("Subtotal", Money.format(order.grossPaise)))
        if (order.discountPaise > 0) out += text(row("Discount", "-" + Money.format(order.discountPaise)))
        if (order.gstEnabled) {
            val half = Money.formatPercentBp(order.gstRateBp / 2)
            out += text(row("Taxable value", Money.format(order.taxablePaise)))
            out += text(row("CGST @$half%", Money.format(order.cgstPaise)))
            out += text(row("SGST @$half%", Money.format(order.sgstPaise)))
        }
        if (order.roundOffPaise != 0L) out += text(row("Round off", Money.format(order.roundOffPaise)))
        out += PrintLine.Text(row("TOTAL Rs.", Money.format(order.totalPaise)), bold = true)
        out += text(row("Paid", Money.format(receipt.paidPaise)))
        out += text(row("Balance", Money.format(order.totalPaise - receipt.paidPaise)))
        if (order.gstEnabled) {
            out += text("SAC: ${shop.sacCode}")
            if (shop.stateName.isNotBlank()) wrap("Place of supply: ${shop.stateName}").forEach { out += text(it) }
        }
        if (order.notes.isNotBlank()) {
            out += text(rule())
            wrap("Notes: ${order.notes}").forEach { out += text(it) }
        }
        if (shop.footer.isNotBlank()) {
            out += text(rule())
            wrap(shop.footer, chars(TextSize.SMALL)).forEach { out += PrintLine.Text(it, Align.CENTER, TextSize.SMALL) }
        }
        return out
    }

    fun dayReport(report: DayReport, shop: ShopSettings): List<PrintLine> {
        val out = mutableListOf<PrintLine>()
        out += PrintLine.Text(shop.shopName, Align.CENTER, bold = true)
        out += PrintLine.Text("DAILY REPORT", Align.CENTER, bold = true)
        out += PrintLine.Text(Dates.day(report.date), Align.CENTER)
        out += text(rule())
        out += text(row("Orders", report.billed.count.toString()))
        out += text(row("Billed", Money.format(report.billed.amount)))
        out += text(row("Payments", report.collected.count.toString()))
        out += text(row("Collected", Money.format(report.collected.amount)))
        report.collectedByMode.forEach { out += text(row("  ${it.mode}", Money.format(it.amount))) }
        out += text(row("Delivered", report.deliveredCount.toString()))
        out += text(rule())
        out += text(row("Ready, not collected", report.readyCount.toString()))
        out += text(row("Total dues", Money.format(report.outstandingPaise)))
        return out
    }

    fun testPage(shop: ShopSettings): List<PrintLine> =
        header(shop) + listOf(text(rule()), text(row("Printer test", "OK")), text(rule()))

    internal fun row(left: String, right: String, width: Int = this.width): String {
        val room = width - right.length - 1
        if (room <= 0) return right.takeLast(width)
        val l = if (left.length > room) left.take(room) else left
        return l + " ".repeat(width - l.length - right.length) + right
    }

    internal fun wrap(value: String, width: Int = this.width): List<String> {
        val lines = mutableListOf<String>()
        value.split('\n').forEach { paragraph ->
            var current = StringBuilder()
            paragraph.split(' ').filter { it.isNotEmpty() }.forEach { word ->
                var w = word
                while (w.length > width) {
                    if (current.isNotEmpty()) {
                        lines += current.toString()
                        current = StringBuilder()
                    }
                    lines += w.take(width)
                    w = w.drop(width)
                }
                if (current.isEmpty()) current.append(w)
                else if (current.length + 1 + w.length <= width) current.append(' ').append(w)
                else {
                    lines += current.toString()
                    current = StringBuilder(w)
                }
            }
            if (current.isNotEmpty()) lines += current.toString()
        }
        return lines
    }

    private fun header(shop: ShopSettings): List<PrintLine> {
        val out = mutableListOf<PrintLine>()
        wrap(shop.shopName, chars(TextSize.LARGE)).forEach { out += PrintLine.Text(it, Align.CENTER, TextSize.LARGE, bold = true) }
        wrap(shop.address).forEach { out += PrintLine.Text(it, Align.CENTER) }
        if (shop.phone.isNotBlank()) out += PrintLine.Text("Ph: ${shop.phone}", Align.CENTER)
        if (shop.gstEnabled && shop.gstin.isNotBlank()) out += PrintLine.Text("GSTIN: ${shop.gstin}", Align.CENTER)
        return out
    }

    private fun lineItem(line: OrderLine): List<PrintLine> {
        val qty = when (line.unit) {
            PricingUnit.PIECE -> Money.formatQty(line.qtyMilli)
            PricingUnit.KG -> Money.formatQty(line.qtyMilli) + "kg"
        }
        return wrap("${line.name} (${line.service})").map { text(it) } +
            text(row("  $qty x ${Money.format(line.ratePaise)}", Money.format(line.amountPaise)))
    }

    private fun rule(): String = "-".repeat(width)

    private fun text(value: String): PrintLine = PrintLine.Text(value)
}
