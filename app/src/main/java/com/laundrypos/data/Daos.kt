package com.laundrypos.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

private const val CUSTOMER_SUMMARY = "SELECT c.*, " +
    "COALESCE((SELECT SUM(o.totalPaise - COALESCE((SELECT SUM(p.amountPaise) FROM payments p WHERE p.orderId = o.id), 0)) " +
    "FROM orders o WHERE o.customerId = c.id AND o.status != 'CANCELLED'), 0) AS duePaise, " +
    "(SELECT COUNT(*) FROM orders o WHERE o.customerId = c.id) AS orderCount " +
    "FROM customers c"

private const val ORDER_SUMMARY = "SELECT o.*, c.name AS customerName, c.phone AS customerPhone, " +
    "COALESCE((SELECT SUM(p.amountPaise) FROM payments p WHERE p.orderId = o.id), 0) AS paidPaise, " +
    "(SELECT COUNT(*) FROM order_lines l WHERE l.orderId = o.id) AS lineCount " +
    "FROM orders o JOIN customers c ON c.id = o.customerId"

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers WHERE phone = :phone LIMIT 1")
    suspend fun byPhone(phone: String): Customer?

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun byId(id: Long): Customer?

    @Query(
        CUSTOMER_SUMMARY + " WHERE :q = '' OR c.name LIKE '%' || :q || '%' OR c.phone LIKE '%' || :q || '%' " +
            "ORDER BY c.name COLLATE NOCASE LIMIT 300"
    )
    fun search(q: String): Flow<List<CustomerSummary>>

    @Query(CUSTOMER_SUMMARY + " WHERE c.id = :id")
    fun observeSummary(id: Long): Flow<CustomerSummary?>

    @Insert
    suspend fun insert(customer: Customer): Long

    @Update
    suspend fun update(customer: Customer)

    @Query("SELECT * FROM customers")
    suspend fun all(): List<Customer>

    @Insert
    suspend fun insertAll(items: List<Customer>)

    @Query("DELETE FROM customers")
    suspend fun deleteAll()
}

@Dao
interface PriceItemDao {
    @Query("SELECT * FROM price_items WHERE active = 1 ORDER BY service COLLATE NOCASE, name COLLATE NOCASE")
    fun observeActive(): Flow<List<PriceItem>>

    @Query("SELECT * FROM price_items ORDER BY service COLLATE NOCASE, name COLLATE NOCASE")
    fun observeAll(): Flow<List<PriceItem>>

    @Query("SELECT COUNT(*) FROM price_items")
    suspend fun count(): Int

    @Insert
    suspend fun insert(item: PriceItem): Long

    @Update
    suspend fun update(item: PriceItem)

    @Delete
    suspend fun delete(item: PriceItem)

    @Query("SELECT * FROM price_items")
    suspend fun all(): List<PriceItem>

    @Insert
    suspend fun insertAll(items: List<PriceItem>)

    @Query("DELETE FROM price_items")
    suspend fun deleteAll()
}

@Dao
interface OrderDao {
    @Insert
    suspend fun insert(order: Order): Long

    @Insert
    suspend fun insertLines(lines: List<OrderLine>)

    @Query("SELECT MAX(seq) FROM orders WHERE fy = :fy")
    suspend fun maxSeq(fy: String): Int?

    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun byId(id: Long): Order?

    @Query("SELECT * FROM order_lines WHERE orderId = :orderId ORDER BY id")
    suspend fun lines(orderId: Long): List<OrderLine>

    @Query("SELECT * FROM order_lines WHERE orderId = :orderId ORDER BY id")
    fun observeLines(orderId: Long): Flow<List<OrderLine>>

    @Query("UPDATE orders SET status = :status, statusAt = :at WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, at: Long)

    @Query(
        ORDER_SUMMARY + " WHERE (:status = '' OR o.status = :status) AND (:q = '' OR c.phone LIKE '%' || :q || '%' " +
            "OR c.name LIKE '%' || :q || '%' OR o.invoiceNo LIKE '%' || :q || '%') ORDER BY o.createdAt DESC LIMIT 300"
    )
    fun search(status: String, q: String): Flow<List<OrderSummary>>

    @Query(ORDER_SUMMARY + " WHERE o.customerId = :customerId ORDER BY o.createdAt DESC")
    fun observeForCustomer(customerId: Long): Flow<List<OrderSummary>>

    @Query(ORDER_SUMMARY + " WHERE o.id = :id")
    fun observeSummary(id: Long): Flow<OrderSummary?>

    @Query(ORDER_SUMMARY + " WHERE o.id = :id")
    suspend fun summary(id: Long): OrderSummary?

    @Query(ORDER_SUMMARY + " ORDER BY o.fy, o.seq")
    suspend fun allSummaries(): List<OrderSummary>

    @Query(
        "SELECT COUNT(*) AS count, COALESCE(SUM(totalPaise), 0) AS amount FROM orders " +
            "WHERE createdAt >= :from AND createdAt < :to AND status != 'CANCELLED'"
    )
    suspend fun billedBetween(from: Long, to: Long): CountAmount

    @Query("SELECT COUNT(*) FROM orders WHERE status = 'DELIVERED' AND statusAt >= :from AND statusAt < :to")
    suspend fun deliveredBetween(from: Long, to: Long): Int

    @Query("SELECT COUNT(*) FROM orders WHERE status = 'READY'")
    suspend fun readyCount(): Int

    @Query(
        "SELECT COALESCE(SUM(o.totalPaise - COALESCE((SELECT SUM(p.amountPaise) FROM payments p WHERE p.orderId = o.id), 0)), 0) " +
            "FROM orders o WHERE o.status != 'CANCELLED'"
    )
    suspend fun outstanding(): Long

    @Query("SELECT * FROM orders")
    suspend fun all(): List<Order>

    @Query("SELECT * FROM order_lines")
    suspend fun allLines(): List<OrderLine>

    @Insert
    suspend fun insertAll(items: List<Order>)

    @Query("DELETE FROM orders")
    suspend fun deleteAll()

    @Query("DELETE FROM order_lines")
    suspend fun deleteAllLines()
}

@Dao
interface PaymentDao {
    @Insert
    suspend fun insert(payment: Payment): Long

    @Query("SELECT * FROM payments WHERE orderId = :orderId ORDER BY paidAt")
    fun observeForOrder(orderId: Long): Flow<List<Payment>>

    @Query("SELECT COUNT(*) AS count, COALESCE(SUM(amountPaise), 0) AS amount FROM payments WHERE paidAt >= :from AND paidAt < :to")
    suspend fun collectedBetween(from: Long, to: Long): CountAmount

    @Query(
        "SELECT mode, COALESCE(SUM(amountPaise), 0) AS amount FROM payments " +
            "WHERE paidAt >= :from AND paidAt < :to GROUP BY mode ORDER BY mode"
    )
    suspend fun byModeBetween(from: Long, to: Long): List<ModeAmount>

    @Query("SELECT * FROM payments")
    suspend fun all(): List<Payment>

    @Insert
    suspend fun insertAll(items: List<Payment>)

    @Query("DELETE FROM payments")
    suspend fun deleteAll()
}
