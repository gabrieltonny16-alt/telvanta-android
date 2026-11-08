package com.telvanta.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class OfferRow(val id: Long, val name: String, val category: String, val price: Double)
data class CustomerRow(val id: Long, val name: String, val phone: String, val notes: String)
data class OrderRow(val id: Long, val customer: String, val offer: String, val price: Double, val status: String, val createdAt: Long)
data class TransactionRow(val id: Long, val orderId: Long, val amount: Double, val status: String, val reference: String, val createdAt: Long)

/** Offline-first records plus a durable, idempotent synchronization outbox. */
class LocalDatabase(context: Context) : SQLiteOpenHelper(context, "telvanta.db", null, 4) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE offers (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, category TEXT NOT NULL, original_price REAL, selling_price REAL NOT NULL, source TEXT NOT NULL DEFAULT 'Manual', verified INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE customers (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT NOT NULL UNIQUE, notes TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE orders (id INTEGER PRIMARY KEY AUTOINCREMENT, customer_phone TEXT NOT NULL, offer_name TEXT NOT NULL, selling_price REAL NOT NULL, status TEXT NOT NULL DEFAULT 'PENDING', created_at INTEGER NOT NULL)")
        createTransactionsTable(db)
        createIndexes(db)
        createSyncTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, order_id INTEGER NOT NULL, amount REAL NOT NULL, status TEXT NOT NULL DEFAULT 'UNVERIFIED', reference TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, FOREIGN KEY(order_id) REFERENCES orders(id))")
        }
        if (oldVersion < 3) {
            db.beginTransaction()
            try {
                db.execSQL("CREATE TABLE IF NOT EXISTS transactions_legacy_orphaned (id INTEGER PRIMARY KEY, order_id INTEGER NOT NULL, amount REAL NOT NULL, status TEXT NOT NULL, reference TEXT NOT NULL, created_at INTEGER NOT NULL)")
                db.execSQL("INSERT OR IGNORE INTO transactions_legacy_orphaned(id,order_id,amount,status,reference,created_at) SELECT t.id,t.order_id,t.amount,t.status,t.reference,t.created_at FROM transactions t WHERE NOT EXISTS (SELECT 1 FROM orders o WHERE o.id=t.order_id)")
                db.execSQL("CREATE TABLE transactions_v3 (id INTEGER PRIMARY KEY AUTOINCREMENT, order_id INTEGER NOT NULL, amount REAL NOT NULL CHECK(amount >= 0), status TEXT NOT NULL DEFAULT 'UNVERIFIED', reference TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, FOREIGN KEY(order_id) REFERENCES orders(id) ON DELETE RESTRICT)")
                db.execSQL("INSERT INTO transactions_v3(id,order_id,amount,status,reference,created_at) SELECT t.id,t.order_id,t.amount,t.status,t.reference,t.created_at FROM transactions t WHERE EXISTS (SELECT 1 FROM orders o WHERE o.id=t.order_id)")
                db.execSQL("DROP TABLE transactions")
                db.execSQL("ALTER TABLE transactions_v3 RENAME TO transactions")
                createIndexes(db)
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
        if (oldVersion < 4) createSyncTables(db)
    }

    private fun createTransactionsTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, order_id INTEGER NOT NULL, amount REAL NOT NULL CHECK(amount >= 0), status TEXT NOT NULL DEFAULT 'UNVERIFIED', reference TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, FOREIGN KEY(order_id) REFERENCES orders(id) ON DELETE RESTRICT)")
    }
    private fun createSyncTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_state (entity_type TEXT NOT NULL, local_id INTEGER NOT NULL, remote_id TEXT NOT NULL, server_revision INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(entity_type,local_id), UNIQUE(entity_type,remote_id))")
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_outbox (operation_id TEXT PRIMARY KEY, entity_type TEXT NOT NULL, local_id INTEGER NOT NULL, entity_id TEXT NOT NULL, action TEXT NOT NULL, expected_revision INTEGER, payload TEXT NOT NULL, created_at INTEGER NOT NULL, UNIQUE(entity_type,local_id))")
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    }
    private fun createIndexes(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders(created_at DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_orders_customer_phone ON orders(customer_phone)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_transactions_order_id ON transactions(order_id)")
    }

    fun count(table: String): Int {
        require(table in setOf("offers", "customers", "orders", "transactions"))
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $table", null).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }
    fun offers(): List<OfferRow> = readableDatabase.rawQuery("SELECT id,name,category,selling_price FROM offers ORDER BY name", null).use { c ->
        buildList { while (c.moveToNext()) add(OfferRow(c.getLong(0), c.getString(1), c.getString(2), c.getDouble(3))) }
    }
    fun saveOffer(id: Long?, name: String, category: String, price: Double) {
        require(name.isNotBlank() && category.isNotBlank() && price.isFinite() && price >= 0)
        val v = ContentValues().apply { put("name", name.trim()); put("category", category.trim()); put("selling_price", price) }
        val localId = if (id == null) {
            v.put("source", "Manual"); v.put("verified", 0)
            writableDatabase.insertOrThrow("offers", null, v)
        } else {
            writableDatabase.update("offers", v, "id=?", arrayOf(id.toString())); id
        }
        enqueue("offer", localId, JSONObject().put("name",name.trim()).put("category",category.trim()).put("priceKes",price))
    }
    fun deleteOffer(id: Long) { enqueueDelete("offer",id); writableDatabase.delete("offers","id=?",arrayOf(id.toString())) }

    fun customers(search: String = ""): List<CustomerRow> = readableDatabase.rawQuery(
        "SELECT id,name,phone,notes FROM customers WHERE name LIKE ? OR phone LIKE ? ORDER BY name",
        arrayOf("%$search%", "%$search%")
    ).use { c -> buildList { while (c.moveToNext()) add(CustomerRow(c.getLong(0),c.getString(1),c.getString(2),c.getString(3))) } }
    fun saveCustomer(id: Long?, name: String, phone: String, notes: String) {
        require(name.isNotBlank() && phone.isNotBlank())
        val v = ContentValues().apply { put("name",name.trim()); put("phone",phone.trim()); put("notes",notes.trim()) }
        val localId = if (id == null) writableDatabase.insertOrThrow("customers",null,v)
        else { writableDatabase.update("customers",v,"id=?",arrayOf(id.toString())); id }
        enqueue("customer",localId,JSONObject().put("name",name.trim()).put("phone",phone.trim()).put("notes",notes.trim()))
    }
    fun deleteCustomer(id: Long) { enqueueDelete("customer",id); writableDatabase.delete("customers","id=?",arrayOf(id.toString())) }

    fun orders(): List<OrderRow> = readableDatabase.rawQuery("SELECT id,customer_phone,offer_name,selling_price,status,created_at FROM orders ORDER BY id DESC", null).use { c ->
        buildList { while(c.moveToNext()) add(OrderRow(c.getLong(0),c.getString(1),c.getString(2),c.getDouble(3),c.getString(4),c.getLong(5))) }
    }
    fun createOrder(phone: String, offer: String, price: Double): Long {
        require(phone.isNotBlank() && offer.isNotBlank() && price.isFinite() && price >= 0)
        val created = System.currentTimeMillis()
        val v = ContentValues().apply { put("customer_phone",phone.trim()); put("offer_name",offer.trim()); put("selling_price",price); put("status","PENDING"); put("created_at",created) }
        val id = writableDatabase.insertOrThrow("orders",null,v)
        enqueue("order",id,orderPayload(phone,offer,price,"PENDING",created))
        return id
    }
    fun updateOrderStatus(id: Long, status: String) {
        require(status in setOf("PENDING","PAYMENT_UNVERIFIED","CANCELLED","FAILED"))
        val v=ContentValues().apply { put("status",status) }
        writableDatabase.update("orders",v,"id=?",arrayOf(id.toString()))
        readableDatabase.rawQuery("SELECT customer_phone,offer_name,selling_price,created_at FROM orders WHERE id=?",arrayOf(id.toString())).use { c ->
            if(c.moveToFirst()) enqueue("order",id,orderPayload(c.getString(0),c.getString(1),c.getDouble(2),status,c.getLong(3)))
        }
    }
    private fun orderPayload(phone:String,offer:String,price:Double,status:String,created:Long) =
        JSONObject().put("phone",phone.trim()).put("offer",offer.trim()).put("priceKes",price).put("status",status).put("createdAt",created)

    fun transactions(): List<TransactionRow> = readableDatabase.rawQuery("SELECT id,order_id,amount,status,reference,created_at FROM transactions ORDER BY id DESC", null).use { c ->
        buildList { while(c.moveToNext()) add(TransactionRow(c.getLong(0),c.getLong(1),c.getDouble(2),c.getString(3),c.getString(4),c.getLong(5))) }
    }
    fun recordTransaction(orderId: Long, amount: Double, reference: String) {
        require(amount.isFinite() && amount >= 0)
        val created = System.currentTimeMillis()
        val v=ContentValues().apply { put("order_id",orderId); put("amount",amount); put("status","UNVERIFIED"); put("reference",reference.trim()); put("created_at",created) }
        val id=writableDatabase.insertOrThrow("transactions",null,v)
        val orderRemoteId=remoteId("order",orderId)
        enqueue("transaction_note",id,JSONObject().put("orderRemoteId",orderRemoteId).put("amountKes",amount).put("reference",reference.trim()).put("status","UNVERIFIED").put("createdAt",created))
    }

    private fun enqueue(type:String,localId:Long,payload:JSONObject) {
        val db=writableDatabase
        var remoteId:String?=null
        var revision=0L
        db.rawQuery("SELECT remote_id,server_revision FROM sync_state WHERE entity_type=? AND local_id=?",arrayOf(type,localId.toString())).use { c ->
            if(c.moveToFirst()){remoteId=c.getString(0);revision=c.getLong(1)}
        }
        if(remoteId==null){remoteId=UUID.randomUUID().toString();db.insertOrThrow("sync_state",null,ContentValues().apply{put("entity_type",type);put("local_id",localId);put("remote_id",remoteId);put("server_revision",0)})}
        val existing=db.rawQuery("SELECT operation_id,expected_revision FROM sync_outbox WHERE entity_type=? AND local_id=?",arrayOf(type,localId.toString())).use { c -> if(c.moveToFirst()) c.getString(0) to if(c.isNull(1)) null else c.getLong(1) else null }
        val operationId=existing?.first?:UUID.randomUUID().toString()
        val expected=existing?.second?:if(revision>0)revision else null
        val values=ContentValues().apply{
            put("operation_id",operationId);put("entity_type",type);put("local_id",localId);put("entity_id",remoteId);put("action","upsert");put("payload",payload.toString());put("created_at",System.currentTimeMillis())
            if(expected==null)putNull("expected_revision") else put("expected_revision",expected)
        }
        db.insertWithOnConflict("sync_outbox",null,values,SQLiteDatabase.CONFLICT_REPLACE)
    }
    private fun enqueueDelete(type:String,localId:Long) {
        val db=writableDatabase
        val state=db.rawQuery("SELECT remote_id,server_revision FROM sync_state WHERE entity_type=? AND local_id=?",arrayOf(type,localId.toString())).use { c -> if(c.moveToFirst()) c.getString(0) to c.getLong(1) else null }
        if(state==null)return
        if(state.second==0L){db.delete("sync_outbox","entity_type=? AND local_id=?",arrayOf(type,localId.toString()));db.delete("sync_state","entity_type=? AND local_id=?",arrayOf(type,localId.toString()));return}
        val existing=db.rawQuery("SELECT operation_id FROM sync_outbox WHERE entity_type=? AND local_id=?",arrayOf(type,localId.toString())).use{c->if(c.moveToFirst())c.getString(0)else null}
        val values=ContentValues().apply{put("operation_id",existing?:UUID.randomUUID().toString());put("entity_type",type);put("local_id",localId);put("entity_id",state.first);put("action","delete");put("expected_revision",state.second);put("payload","{}");put("created_at",System.currentTimeMillis())}
        db.insertWithOnConflict("sync_outbox",null,values,SQLiteDatabase.CONFLICT_REPLACE)
    }
    private fun remoteId(type:String,localId:Long):String {
        readableDatabase.rawQuery("SELECT remote_id FROM sync_state WHERE entity_type=? AND local_id=?",arrayOf(type,localId.toString())).use{c->if(c.moveToFirst())return c.getString(0)}
        throw IllegalStateException("The related order must be queued for sync before its transaction note")
    }

    fun pendingSyncOperations():JSONArray {
        val array=JSONArray()
        readableDatabase.rawQuery("SELECT operation_id,entity_type,entity_id,action,expected_revision,payload FROM sync_outbox ORDER BY created_at",null).use{c->
            while(c.moveToNext()){
                val op=JSONObject().put("operationId",c.getString(0)).put("entityType",c.getString(1)).put("entityId",c.getString(2)).put("action",c.getString(3))
                if(c.isNull(4))op.put("expectedRevision",JSONObject.NULL)else op.put("expectedRevision",c.getLong(4))
                op.put("payload",JSONObject(c.getString(5)));array.put(op)
            }
        }
        return array
    }
    fun pendingCount():Int=readableDatabase.rawQuery("SELECT COUNT(*) FROM sync_outbox",null).use{c->if(c.moveToFirst())c.getInt(0)else 0}
    fun markSyncResult(operationId:String,outcome:String,revision:Long?) {
        val db=writableDatabase
        val row=db.rawQuery("SELECT entity_type,local_id,action FROM sync_outbox WHERE operation_id=?",arrayOf(operationId)).use{c->if(c.moveToFirst())Triple(c.getString(0),c.getLong(1),c.getString(2))else null}?:return
        if(outcome=="APPLIED"){
            if(row.third=="delete")db.delete("sync_state","entity_type=? AND local_id=?",arrayOf(row.first,row.second.toString()))
            else if(revision!=null){db.update("sync_state",ContentValues().apply{put("server_revision",revision)},"entity_type=? AND local_id=?",arrayOf(row.first,row.second.toString()))}
            db.delete("sync_outbox","operation_id=?",arrayOf(operationId)
            )
        }
    }
    fun syncCursor():Long=readableDatabase.rawQuery("SELECT value FROM sync_meta WHERE key='cursor'",null).use{c->if(c.moveToFirst())c.getString(0).toLongOrNull()?:0L else 0L}
    fun setSyncCursor(cursor:Long){writableDatabase.insertWithOnConflict("sync_meta",null,ContentValues().apply{put("key","cursor");put("value",cursor.toString())},SQLiteDatabase.CONFLICT_REPLACE)}

    /** Returns false if the event must be retried later (for example, an order is not yet present). */
    fun applyRemoteEvent(event:JSONObject):Boolean {
        val type=event.optString("entityType");val remote=event.optString("entityId");val revision=event.optLong("revision",1);val deleted=event.optBoolean("deleted",false);val payload=event.optJSONObject("payload")?:JSONObject()
        val db=writableDatabase
        val pending=db.rawQuery("SELECT 1 FROM sync_outbox WHERE entity_type=? AND entity_id=?",arrayOf(type,remote)).use{it.moveToFirst()}
        if(pending)return true
        val localId=db.rawQuery("SELECT local_id FROM sync_state WHERE entity_type=? AND remote_id=?",arrayOf(type,remote)).use{c->if(c.moveToFirst())c.getLong(0)else null}
        if(deleted){if(localId!=null){val table=tableFor(type);db.delete(table,"id=?",arrayOf(localId.toString()));db.delete("sync_state","entity_type=? AND local_id=?",arrayOf(type,localId.toString()))};return true}
        val values=ContentValues()
        val table=tableFor(type)
        val id:Long
        when(type){
            "offer"->{values.put("name",payload.optString("name"));values.put("category",payload.optString("category","Data"));values.put("selling_price",payload.optDouble("priceKes",payload.optDouble("price",0.0)))}
            "customer"->{values.put("name",payload.optString("name"));values.put("phone",payload.optString("phone"));values.put("notes",payload.optString("notes",""))}
            "order"->{values.put("customer_phone",payload.optString("phone"));values.put("offer_name",payload.optString("offer"));values.put("selling_price",payload.optDouble("priceKes",0.0));values.put("status",payload.optString("status","PENDING"));values.put("created_at",payload.optLong("createdAt",System.currentTimeMillis()))}
            "transaction_note"->{
                val orderRemote=payload.optString("orderRemoteId")
                val orderLocal=db.rawQuery("SELECT local_id FROM sync_state WHERE entity_type='order' AND remote_id=?",arrayOf(orderRemote)).use{c->if(c.moveToFirst())c.getLong(0)else null}?:return false
                values.put("order_id",orderLocal);values.put("amount",payload.optDouble("amountKes",0.0));values.put("status","UNVERIFIED");values.put("reference",payload.optString("reference",""));values.put("created_at",payload.optLong("createdAt",System.currentTimeMillis()))
            }
            else->return true
        }
        id=if(localId==null)db.insertOrThrow(table,null,values)else{db.update(table,values,"id=?",arrayOf(localId.toString()));localId}
        val state=ContentValues().apply{put("entity_type",type);put("local_id",id);put("remote_id",remote);put("server_revision",revision)}
        db.insertWithOnConflict("sync_state",null,state,SQLiteDatabase.CONFLICT_REPLACE)
        return true
    }
    private fun tableFor(type:String)=when(type){"offer"->"offers";"customer"->"customers";"order"->"orders";"transaction_note"->"transactions";else->throw IllegalArgumentException("Unsupported entity type")}
}
