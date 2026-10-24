package com.telvanta.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Local-only storage; deliberately no network or payment integration. */
class LocalDatabase(context: Context) : SQLiteOpenHelper(context, "telvanta.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE offers (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            category TEXT NOT NULL,
            original_price REAL,
            selling_price REAL NOT NULL,
            source TEXT NOT NULL DEFAULT 'Manual',
            verified INTEGER NOT NULL DEFAULT 0
        )""")
        db.execSQL("""CREATE TABLE customers (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            phone TEXT NOT NULL UNIQUE,
            notes TEXT NOT NULL DEFAULT ''
        )""")
        db.execSQL("""CREATE TABLE orders (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            customer_phone TEXT NOT NULL,
            offer_name TEXT NOT NULL,
            selling_price REAL NOT NULL,
            status TEXT NOT NULL DEFAULT 'PENDING',
            created_at INTEGER NOT NULL
        )""")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Add explicit, versioned migrations before changing the schema.
    }
    fun addOffer(name: String, category: String, originalPrice: Double?, sellingPrice: Double): Long {
        require(name.isNotBlank() && category.isNotBlank() && sellingPrice >= 0)
        val values = ContentValues().apply {
            put("name", name.trim())
            put("category", category.trim())
            if (originalPrice != null) put("original_price", originalPrice)
            put("selling_price", sellingPrice)
            put("source", "Manual")
            put("verified", 0)
        }
        return writableDatabase.insertOrThrow("offers", null, values)
    }
    fun count(table: String): Int {
        require(table in setOf("offers", "customers", "orders"))
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }
}
