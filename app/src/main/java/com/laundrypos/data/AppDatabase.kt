package com.laundrypos.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Customer::class, PriceItem::class, Order::class, OrderLine::class, Payment::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customers(): CustomerDao
    abstract fun prices(): PriceItemDao
    abstract fun orders(): OrderDao
    abstract fun payments(): PaymentDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "laundry.db").build()
    }
}
