package com.telvanta.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class OfferRow(val id: Long, val name: String, val category: String, val price: Double, val originalPrice: Double?, val source: String, val verified: Boolean)
data class CustomerRow(val id: Long, val name: String, val phone: String, val notes: String)
data class OrderRow(val id: Long, val customer: String, val offer: String, val price: Double, val status: String, val createdAt: Long)
data class TransactionRow(val id: Long, val orderId: Long, val amount: Double, val status: String, val reference: String, val createdAt: Long)

/** Device-local business records only. This class does not initiate payments or fulfil bundles. */
class LocalDatabase(context: Context) : SQLiteOpenHelper(context, "telvanta.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE offers (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, category TEXT NOT NULL, original_price REAL, selling_price REAL NOT NULL, source TEXT NOT NULL DEFAULT 'Manual', verified INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE customers (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT NOT NULL UNIQUE, notes TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE orders (id INTEGER PRIMARY KEY AUTOINCREMENT, customer_phone TEXT NOT NULL, offer_name TEXT NOT NULL, selling_price REAL NOT NULL, status TEXT NOT NULL DEFAULT 'PENDING', created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, order_id INTEGER NOT NULL, amount REAL NOT NULL, status TEXT NOT NULL DEFAULT 'UNVERIFIED', reference TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, FOREIGN KEY(order_id) REFERENCES orders(id))")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("CREATE TABLE IF NOT EXISTS transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, order_id INTEGER NOT NULL, amount REAL NOT NULL, status TEXT NOT NULL DEFAULT 'UNVERIFIED', reference TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, FOREIGN KEY(order_id) REFERENCES orders(id))")
    }
    fun count(table: String): Int {
        require(table in setOf("offers", "customers", "orders", "transactions"))
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $table", null).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }
    fun offers(): List<OfferRow> = readableDatabase.rawQuery("SELECT id,name,category,selling_price,original_price,source,verified FROM offers ORDER BY name", null).use { c ->
        buildList { while (c.moveToNext()) add(OfferRow(c.getLong(0), c.getString(1), c.getString(2), c.getDouble(3), if (c.isNull(4)) null else c.getDouble(4), c.getString(5), c.getInt(6) == 1)) }
    }
    fun saveOffer(id: Long?, name: String, category: String, price: Double, originalPrice: Double? = null, source: String = "Manual", verified: Boolean = false) {
        require(name.isNotBlank() && category.isNotBlank() && price >= 0 && (originalPrice == null || originalPrice >= 0) && source.isNotBlank())
        val v = ContentValues().apply {
            put("name", name.trim()); put("category", category.trim()); put("selling_price", price)
            if (originalPrice == null) putNull("original_price") else put("original_price", originalPrice)
            put("source", source.trim()); put("verified", if (verified) 1 else 0)
        }
        if (id == null) writableDatabase.insertOrThrow("offers", null, v)
        else writableDatabase.update("offers", v, "id=?", arrayOf(id.toString()))
    }
    fun deleteOffer(id: Long) { writableDatabase.delete("offers", "id=?", arrayOf(id.toString())) }
    fun customers(search: String = ""): List<CustomerRow> = readableDatabase.rawQuery(
        "SELECT id,name,phone,notes FROM customers WHERE name LIKE ? OR phone LIKE ? ORDER BY name",
        arrayOf("%$search%", "%$search%")
    ).use { c -> buildList { while (c.moveToNext()) add(CustomerRow(c.getLong(0),c.getString(1),c.getString(2),c.getString(3))) } }
    fun saveCustomer(id: Long?, name: String, phone: String, notes: String) {
        require(name.isNotBlank() && phone.isNotBlank())
        val v = ContentValues().apply { put("name",name.trim()); put("phone",phone.trim()); put("notes",notes.trim()) }
        if (id == null) writableDatabase.insertOrThrow("customers", null, v)
        else writableDatabase.update("customers", v, "id=?", arrayOf(id.toString()))
    }
    fun deleteCustomer(id: Long) { writableDatabase.delete("customers", "id=?", arrayOf(id.toString())) }
    fun orders(): List<OrderRow> = readableDatabase.rawQuery("SELECT id,customer_phone,offer_name,selling_price,status,created_at FROM orders ORDER BY id DESC", null).use { c ->
        buildList { while(c.moveToNext()) add(OrderRow(c.getLong(0),c.getString(1),c.getString(2),c.getDouble(3),c.getString(4),c.getLong(5))) }
    }
    fun createOrder(phone: String, offer: String, price: Double): Long {
        require(phone.isNotBlank() && offer.isNotBlank() && price >= 0)
        val v = ContentValues().apply { put("customer_phone",phone.trim()); put("offer_name",offer); put("selling_price",price); put("status","PENDING"); put("created_at",System.currentTimeMillis()) }
        return writableDatabase.insertOrThrow("orders",null,v)
    }
    fun updateOrderStatus(id: Long, status: String) {
        require(status in setOf("PENDING","PAYMENT_UNVERIFIED","CANCELLED","FAILED","DELIVERED_MANUALLY_RECORDED"))
        val v=ContentValues().apply { put("status",status) }
        writableDatabase.update("orders",v,"id=?",arrayOf(id.toString()))
    }
    fun transactions(): List<TransactionRow> = readableDatabase.rawQuery("SELECT id,order_id,amount,status,reference,created_at FROM transactions ORDER BY id DESC", null).use { c ->
        buildList { while(c.moveToNext()) add(TransactionRow(c.getLong(0),c.getLong(1),c.getDouble(2),c.getString(3),c.getString(4),c.getLong(5))) }
    }
    fun recordTransaction(orderId: Long, amount: Double, reference: String) {
        require(amount >= 0)
        val v=ContentValues().apply { put("order_id",orderId); put("amount",amount); put("status","UNVERIFIED"); put("reference",reference.trim()); put("created_at",System.currentTimeMillis()) }
        writableDatabase.insertOrThrow("transactions",null,v)
    }
}