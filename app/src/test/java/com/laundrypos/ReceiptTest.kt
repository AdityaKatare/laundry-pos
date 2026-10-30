package com.laundrypos

import com.laundrypos.data.Customer
import com.laundrypos.data.Order
import com.laundrypos.data.OrderLine
import com.laundrypos.data.OrderStatus
import com.laundrypos.data.OrderSummary
import com.laundrypos.data.PricingUnit
import com.laundrypos.data.Receipt
import com.laundrypos.data.ShopSettings
import com.laundrypos.notify.MessageKind
import com.laundrypos.notify.Messages
import com.laundrypos.notify.WhatsApp
import com.laundrypos.print.PrintLine
import com.laundrypos.print.ReceiptFormatter
import com.laundrypos.print.TextSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptTest {
    private val order = Order(
        id = 1, invoiceNo = "INV/26-27/0001", fy = "26-27", seq = 1, customerId = 1,
        createdAt = 1_793_000_000_000, dueAt = 1_793_200_000_000, status = OrderStatus.RECEIVED,
        statusAt = 1_793_000_000_000, notes = "Coffee stain on collar",
        grossPaise = 210_00, discountPaise = 10_00, taxablePaise = 200_00, cgstPaise = 18_00, sgstPaise = 18_00,
        roundOffPaise = 0, totalPaise = 236_00, gstEnabled = true, gstRateBp = 1800,
    )
    private val lines = listOf(
        OrderLine(1, 1, "Shirt", "Wash & Iron", PricingUnit.PIECE, 3_000, 20_00, 60_00),
        OrderLine(2, 1, "Mixed clothes", "Wash & Fold", PricingUnit.KG, 2_500, 60_00, 150_00),
    )
    private val customer = Customer(1, "Asha", "9876543210", createdAt = 0)
    private val shop = ShopSettings(shopName = "Sparkle Laundry", address = "12 MG Road, Bengaluru", gstEnabled = true, gstin = "29ABCDE1234F1Z5", stateName = "Karnataka")

    private fun texts(lines: List<PrintLine>) = lines.filterIsInstance<PrintLine.Text>().map { it.text }

    @Test
    fun invoiceLinesFitPaperWidth() {
        val terms = "Terms: Garments not collected within 30 days will be donated. We are not responsible for colour fading, " +
            "shrinkage or damage to buttons and embellishments. Liability is limited to 5 times the cleaning charge."
        listOf(384, 576).forEach { dots ->
            val f = ReceiptFormatter(dots)
            val out = f.invoice(Receipt(order, customer, lines, 100_00), shop.copy(footer = terms))
            out.filterIsInstance<PrintLine.Text>().forEach {
                val max = f.chars(it.size)
                assertTrue("'${it.text}' exceeds $max", it.text.length <= max)
                assertTrue("'${it.text}' overflows the paper", it.text.length * it.size.points / 2 <= dots)
            }
            assertTrue(out.filterIsInstance<PrintLine.Text>().any { it.size == TextSize.SMALL && it.text.startsWith("Terms:") })
        }
    }

    @Test
    fun smallerFontsFitMoreCharacters() {
        val f = ReceiptFormatter(384)
        assertEquals(32, f.chars(TextSize.NORMAL))
        assertEquals(40, f.chars(TextSize.SMALL))
        assertEquals(25, f.chars(TextSize.LARGE))
    }

    @Test
    fun invoiceShowsGstBreakdownAndBalance() {
        val out = texts(ReceiptFormatter(384).invoice(Receipt(order, customer, lines, 100_00), shop))
        assertTrue(out.contains("TAX INVOICE"))
        assertTrue(out.any { it.startsWith("CGST @9%") && it.endsWith("18.00") })
        assertTrue(out.any { it.startsWith("Balance") && it.endsWith("136.00") })
        assertTrue(out.any { it.startsWith("  2.5kg x 60.00") && it.endsWith("150.00") })
        assertTrue(out.contains("GSTIN: 29ABCDE1234F1Z5"))
    }

    @Test
    fun receiptWithoutGstOmitsTax() {
        val plain = order.copy(gstEnabled = false, taxablePaise = 200_00, cgstPaise = 0, sgstPaise = 0, totalPaise = 200_00)
        val out = texts(ReceiptFormatter(384).invoice(Receipt(plain, customer, lines, 0), shop.copy(gstEnabled = false)))
        assertTrue(out.contains("RECEIPT"))
        assertFalse(out.any { it.contains("CGST") })
    }

    @Test
    fun rowPadsAndTruncates() {
        val f = ReceiptFormatter(384)
        assertEquals("Total          10.00", f.row("Total", "10.00", 20))
        assertEquals("A very long lab 1.00", f.row("A very long label here", "1.00", 20))
        assertEquals(listOf("one two three", "four"), f.wrap("one two three four", 13))
    }

    @Test
    fun whatsAppLinkEncodesMessage() {
        val link = WhatsApp.webLink("98765 43210", "Hi Asha, ready & due ₹50")
        assertTrue(link.startsWith("https://wa.me/919876543210?text=Hi%20Asha%2C%20ready%20%26%20due%20"))
        assertFalse(link.contains("+"))
    }

    @Test
    fun readyMessageMentionsBalance() {
        val summary = OrderSummary(order, "Asha", "9876543210", paidPaise = 100_00, lineCount = 2)
        val text = Messages.forOrder(MessageKind.READY, summary, "Sparkle Laundry")
        assertTrue(text.contains("INV/26-27/0001 is ready"))
        assertTrue(text.contains("₹136.00"))
        val paid = Messages.forOrder(MessageKind.READY, summary.copy(paidPaise = 236_00), "Sparkle Laundry")
        assertFalse(paid.contains("Amount due"))
    }
}
