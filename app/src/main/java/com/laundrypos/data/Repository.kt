package com.laundrypos.data

import androidx.room.withTransaction
import com.laundrypos.billing.Billing
import com.laundrypos.billing.Dates
import com.laundrypos.billing.InvoiceNumber
import com.laundrypos.billing.Money
import com.laundrypos.billing.Phones
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

data class DraftLine(
    val name: String,
    val service: String,
    val unit: PricingUnit,
    val ratePaise: Long,
    val qtyMilli: Long,
) {
    val amountPaise: Long get() = Billing.lineAmount(ratePaise, qtyMilli)

    fun sameItem(other: DraftLine): Boolean =
        name == other.name && service == other.service && unit == other.unit && ratePaise == other.ratePaise
}

data class OrderDraft(
    val phone: String,
    val name: String,
    val address: String,
    val lines: List<DraftLine>,
    val discountPaise: Long,
    val dueAt: Long,
    val notes: String,
)

data class Receipt(
    val order: Order,
    val customer: Customer,
    val lines: List<OrderLine>,
    val paidPaise: Long,
)

data class DayReport(
    val date: LocalDate,
    val billed: CountAmount,
    val collected: CountAmount,
    val collectedByMode: List<ModeAmount>,
    val deliveredCount: Int,
    val readyCount: Int,
    val outstandingPaise: Long,
)

@Serializable
data class BackupData(
    val version: Int = 1,
    val exportedAt: Long,
    val settings: ShopSettings,
    val customers: List<Customer>,
    val prices: List<PriceItem>,
    val orders: List<Order>,
    val lines: List<OrderLine>,
    val payments: List<Payment>,
)

class Repository(private val db: AppDatabase, private val settings: SettingsStore) {
    val customers = db.customers()
    val prices = db.prices()
    val orders = db.orders()
    val payments = db.payments()

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    suspend fun seedIfEmpty() {
        if (prices.count() == 0) prices.insertAll(DefaultPrices.items)
    }

    suspend fun findCustomer(phone: String): Customer? = customers.byPhone(Phones.normalize(phone))

    suspend fun createOrder(draft: OrderDraft): Long = db.withTransaction {
        require(draft.lines.isNotEmpty())
        val now = System.currentTimeMillis()
        val phone = Phones.normalize(draft.phone)
        val existing = customers.byPhone(phone)
        val customerId = if (existing == null) {
            customers.insert(Customer(name = draft.name, phone = phone, address = draft.address, createdAt = now))
        } else {
            if (existing.name != draft.name || existing.address != draft.address) {
                customers.update(existing.copy(name = draft.name, address = draft.address))
            }
            existing.id
        }

        val shop = settings.current
        val fy = InvoiceNumber.financialYear(Dates.localDate(now))
        val seq = (orders.maxSeq(fy) ?: 0) + 1
        val totals = Billing.totals(
            draft.lines.map { it.amountPaise },
            draft.discountPaise,
            shop.gstEnabled,
            shop.gstRateBp,
            shop.pricesIncludeGst,
        )
        val orderId = orders.insert(
            Order(
                invoiceNo = InvoiceNumber.format(shop.invoicePrefix, fy, seq),
                fy = fy,
                seq = seq,
                customerId = customerId,
                createdAt = now,
                dueAt = draft.dueAt,
                status = OrderStatus.RECEIVED,
                statusAt = now,
                notes = draft.notes,
                grossPaise = totals.grossPaise,
                discountPaise = totals.discountPaise,
                taxablePaise = totals.taxablePaise,
                cgstPaise = totals.cgstPaise,
                sgstPaise = totals.sgstPaise,
                roundOffPaise = totals.roundOffPaise,
                totalPaise = totals.totalPaise,
                gstEnabled = shop.gstEnabled,
                gstRateBp = shop.gstRateBp,
            )
        )
        orders.insertLines(
            draft.lines.map {
                OrderLine(
                    orderId = orderId,
                    name = it.name,
                    service = it.service,
                    unit = it.unit,
                    qtyMilli = it.qtyMilli,
                    ratePaise = it.ratePaise,
                    amountPaise = it.amountPaise,
                )
            }
        )
        orderId
    }

    suspend fun receipt(orderId: Long): Receipt? {
        val summary = orders.summary(orderId) ?: return null
        val customer = customers.byId(summary.order.customerId) ?: return null
        return Receipt(summary.order, customer, orders.lines(orderId), summary.paidPaise)
    }

    suspend fun setStatus(orderId: Long, status: OrderStatus) {
        orders.setStatus(orderId, status.name, System.currentTimeMillis())
    }

    suspend fun addPayment(orderId: Long, amountPaise: Long, mode: String) {
        payments.insert(Payment(orderId = orderId, amountPaise = amountPaise, mode = mode, paidAt = System.currentTimeMillis()))
    }

    suspend fun dayReport(date: LocalDate): DayReport {
        val from = Dates.startOf(date)
        val to = Dates.startOf(date.plusDays(1))
        return DayReport(
            date = date,
            billed = orders.billedBetween(from, to),
            collected = payments.collectedBetween(from, to),
            collectedByMode = payments.byModeBetween(from, to),
            deliveredCount = orders.deliveredBetween(from, to),
            readyCount = orders.readyCount(),
            outstandingPaise = orders.outstanding(),
        )
    }

    suspend fun exportBackup(): String = json.encodeToString(
        BackupData.serializer(),
        BackupData(
            exportedAt = System.currentTimeMillis(),
            settings = settings.current,
            customers = customers.all(),
            prices = prices.all(),
            orders = orders.all(),
            lines = orders.allLines(),
            payments = payments.all(),
        ),
    )

    suspend fun importBackup(text: String) {
        val data = json.decodeFromString(BackupData.serializer(), text)
        db.withTransaction {
            payments.deleteAll()
            orders.deleteAllLines()
            orders.deleteAll()
            customers.deleteAll()
            prices.deleteAll()
            customers.insertAll(data.customers)
            prices.insertAll(data.prices)
            orders.insertAll(data.orders)
            orders.insertLines(data.lines)
            payments.insertAll(data.payments)
        }
        settings.save(data.settings)
    }

    suspend fun invoicesCsv(): String {
        val header = "Invoice No,Date,Status,Customer,Phone,Gross,Discount,Taxable,CGST,SGST,Round Off,Total,Paid,Balance"
        val rows = orders.allSummaries().map { s ->
            val o = s.order
            listOf(
                o.invoiceNo,
                Dates.day(o.createdAt),
                o.status.name,
                s.customerName,
                s.customerPhone,
                Money.format(o.grossPaise),
                Money.format(o.discountPaise),
                Money.format(o.taxablePaise),
                Money.format(o.cgstPaise),
                Money.format(o.sgstPaise),
                Money.format(o.roundOffPaise),
                Money.format(o.totalPaise),
                Money.format(s.paidPaise),
                Money.format(s.balancePaise),
            ).joinToString(",") { csvCell(it) }
        }
        return (listOf(header) + rows).joinToString("\r\n")
    }

    private fun csvCell(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
}
