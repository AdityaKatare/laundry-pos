package com.laundrypos

import com.laundrypos.billing.Billing
import com.laundrypos.billing.InvoiceNumber
import com.laundrypos.billing.Money
import com.laundrypos.billing.Phones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BillingTest {
    @Test
    fun lineAmountForPiecesAndKg() {
        assertEquals(120_00L, Billing.lineAmount(40_00, 3_000))
        assertEquals(200_00L, Billing.lineAmount(80_00, 2_500))
        assertEquals(98_00L, Billing.lineAmount(80_00, 1_225))
    }

    @Test
    fun noGstRoundsToRupee() {
        val t = Billing.totals(listOf(98_00, 45_50), 0, gstEnabled = false, gstRateBp = 1800, pricesIncludeGst = false)
        assertEquals(143_50L, t.taxablePaise)
        assertEquals(0L, t.cgstPaise)
        assertEquals(144_00L, t.totalPaise)
        assertEquals(50L, t.roundOffPaise)
    }

    @Test
    fun gstExclusiveSplitsCgstAndSgst() {
        val t = Billing.totals(listOf(150_00, 60_00), 10_00, gstEnabled = true, gstRateBp = 1800, pricesIncludeGst = false)
        assertEquals(210_00L, t.grossPaise)
        assertEquals(200_00L, t.taxablePaise)
        assertEquals(18_00L, t.cgstPaise)
        assertEquals(18_00L, t.sgstPaise)
        assertEquals(236_00L, t.totalPaise)
        assertEquals(0L, t.roundOffPaise)
    }

    @Test
    fun gstInclusiveBacksOutTax() {
        val t = Billing.totals(listOf(236_00), 0, gstEnabled = true, gstRateBp = 1800, pricesIncludeGst = true)
        assertEquals(200_00L, t.taxablePaise)
        assertEquals(18_00L, t.cgstPaise)
        assertEquals(236_00L, t.totalPaise)
    }

    @Test
    fun discountCannotExceedGross() {
        val t = Billing.totals(listOf(50_00), 80_00, gstEnabled = false, gstRateBp = 0, pricesIncludeGst = false)
        assertEquals(50_00L, t.discountPaise)
        assertEquals(0L, t.totalPaise)
    }

    @Test
    fun financialYearRollsOverInApril() {
        assertEquals("26-27", InvoiceNumber.financialYear(LocalDate.of(2026, 10, 26)))
        assertEquals("25-26", InvoiceNumber.financialYear(LocalDate.of(2026, 3, 31)))
        assertEquals("26-27", InvoiceNumber.financialYear(LocalDate.of(2026, 4, 1)))
        assertEquals("99-00", InvoiceNumber.financialYear(LocalDate.of(2099, 5, 1)))
    }

    @Test
    fun invoiceNumberFitsGstLimit() {
        val number = InvoiceNumber.format("SWASH", "26-27", 1234)
        assertEquals("SWASH/26-27/1234", number)
        assertTrue(number.length <= 16)
        assertEquals("INV/26-27/0007", InvoiceNumber.format(" ", "26-27", 7))
    }

    @Test
    fun moneyParsingAndFormatting() {
        assertEquals(1234_50L, Money.parse("1,234.5"))
        assertEquals(10_00L, Money.parse("10"))
        assertNull(Money.parse("-5"))
        assertNull(Money.parse("abc"))
        assertEquals("-12.05", Money.format(-12_05))
        assertEquals(2_500L, Money.parseQty("2.5"))
        assertEquals("2.5", Money.formatQty(2_500))
        assertEquals("3", Money.formatQty(3_000))
        assertEquals("9", Money.formatPercentBp(900))
        assertEquals("2.5", Money.formatPercentBp(250))
    }

    @Test
    fun indianPhoneNormalization() {
        assertEquals("9876543210", Phones.normalize("+91 98765 43210"))
        assertEquals("9876543210", Phones.normalize("09876543210"))
        assertEquals("919876543210", Phones.international("98765-43210"))
        assertTrue(Phones.isValid("98765 43210"))
        assertFalse(Phones.isValid("12345"))
    }
}
