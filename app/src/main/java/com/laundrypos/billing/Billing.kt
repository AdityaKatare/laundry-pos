package com.laundrypos.billing

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Totals(
    val grossPaise: Long,
    val discountPaise: Long,
    val taxablePaise: Long,
    val cgstPaise: Long,
    val sgstPaise: Long,
    val roundOffPaise: Long,
    val totalPaise: Long,
)

object Billing {
    fun lineAmount(ratePaise: Long, qtyMilli: Long): Long = divRound(ratePaise * qtyMilli, 1000)

    fun totals(
        lineAmounts: List<Long>,
        discountPaise: Long,
        gstEnabled: Boolean,
        gstRateBp: Int,
        pricesIncludeGst: Boolean,
    ): Totals {
        val gross = lineAmounts.sum()
        val discount = discountPaise.coerceIn(0, gross)
        val net = gross - discount
        val rate = if (gstEnabled) gstRateBp.toLong() else 0L
        val taxable = if (pricesIncludeGst) divRound(net * 10_000, 10_000 + rate) else net
        val half = divRound(taxable * rate, 20_000)
        val exact = taxable + 2 * half
        val total = divRound(exact, 100) * 100
        return Totals(gross, discount, taxable, half, half, total - exact, total)
    }

    private fun divRound(a: Long, b: Long): Long = (a + b / 2) / b
}

object Money {
    fun format(paise: Long): String {
        val sign = if (paise < 0) "-" else ""
        val abs = kotlin.math.abs(paise)
        return "$sign${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    }

    fun rupees(paise: Long): String = "₹" + format(paise)

    fun parse(text: String): Long? = parseScaled(text, 2)

    fun parseQty(text: String): Long? = parseScaled(text, 3)

    fun formatQty(qtyMilli: Long): String =
        BigDecimal(qtyMilli).movePointLeft(3).stripTrailingZeros().toPlainString()

    fun formatPercentBp(bp: Int): String =
        BigDecimal(bp).movePointLeft(2).stripTrailingZeros().toPlainString()

    private fun parseScaled(text: String, scale: Int): Long? {
        val value = text.trim().replace(",", "").toBigDecimalOrNull() ?: return null
        if (value.signum() < 0) return null
        return runCatching { value.movePointRight(scale).setScale(0, RoundingMode.HALF_UP).longValueExact() }.getOrNull()
    }
}

object InvoiceNumber {
    fun financialYear(date: LocalDate): String {
        val start = if (date.monthValue >= 4) date.year else date.year - 1
        return "%02d-%02d".format(start % 100, (start + 1) % 100)
    }

    fun format(prefix: String, fy: String, seq: Int): String =
        "${prefix.trim().ifEmpty { "INV" }}/$fy/${seq.toString().padStart(4, '0')}"
}

object Phones {
    fun normalize(raw: String): String {
        val digits = raw.filter(Char::isDigit)
        return when {
            digits.length == 12 && digits.startsWith("91") -> digits.substring(2)
            digits.length == 11 && digits.startsWith("0") -> digits.substring(1)
            else -> digits
        }
    }

    fun isValid(raw: String): Boolean = normalize(raw).length == 10

    fun international(raw: String): String = normalize(raw).let { if (it.length == 10) "91$it" else it }
}

object Dates {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val dayFormat = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
    private val dateTimeFormat = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH)

    fun day(millis: Long): String = dayFormat.format(Instant.ofEpochMilli(millis).atZone(zone))

    fun day(date: LocalDate): String = dayFormat.format(date)

    fun dateTime(millis: Long): String = dateTimeFormat.format(Instant.ofEpochMilli(millis).atZone(zone))

    fun localDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun startOf(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun dueAt(daysFromToday: Int): Long =
        LocalDate.now(zone).plusDays(daysFromToday.toLong()).atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
}
