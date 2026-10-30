package com.laundrypos.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class PricingUnit { PIECE, KG }

enum class OrderStatus { RECEIVED, READY, DELIVERED, CANCELLED }

@Serializable
@Entity(tableName = "customers", indices = [Index(value = ["phone"], unique = true)])
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val address: String = "",
    val gstin: String = "",
    val createdAt: Long,
)

@Serializable
@Entity(tableName = "price_items")
data class PriceItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val service: String,
    val unit: PricingUnit,
    val ratePaise: Long,
    val active: Boolean = true,
)

@Serializable
@Entity(
    tableName = "orders",
    indices = [Index("customerId"), Index(value = ["invoiceNo"], unique = true), Index(value = ["fy", "seq"], unique = true)],
)
data class Order(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceNo: String,
    val fy: String,
    val seq: Int,
    val customerId: Long,
    val createdAt: Long,
    val dueAt: Long,
    val status: OrderStatus,
    val statusAt: Long,
    val notes: String,
    val grossPaise: Long,
    val discountPaise: Long,
    val taxablePaise: Long,
    val cgstPaise: Long,
    val sgstPaise: Long,
    val roundOffPaise: Long,
    val totalPaise: Long,
    val gstEnabled: Boolean,
    val gstRateBp: Int,
)

@Serializable
@Entity(tableName = "order_lines", indices = [Index("orderId")])
data class OrderLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val name: String,
    val service: String,
    val unit: PricingUnit,
    val qtyMilli: Long,
    val ratePaise: Long,
    val amountPaise: Long,
)

@Serializable
@Entity(tableName = "payments", indices = [Index("orderId")])
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val amountPaise: Long,
    val mode: String,
    val paidAt: Long,
)

data class OrderSummary(
    @Embedded val order: Order,
    val customerName: String,
    val customerPhone: String,
    val paidPaise: Long,
    val lineCount: Int,
) {
    val balancePaise: Long
        get() = if (order.status == OrderStatus.CANCELLED) 0 else order.totalPaise - paidPaise
}

data class CustomerSummary(
    @Embedded val customer: Customer,
    val duePaise: Long,
    val orderCount: Int,
)

data class CountAmount(val count: Int, val amount: Long)

data class ModeAmount(val mode: String, val amount: Long)
