package com.telvanta.app

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalDatabaseTest {
    private lateinit var context: Context
    private lateinit var db: LocalDatabase

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("telvanta.db")
        db = LocalDatabase(context)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase("telvanta.db")
    }

    @Test
    fun offerCreateEditAndDuplicatePriceRulesAreEnforced() {
        db.saveOffer(null, "Daily Data", "DATA", 20.0)
        val first = db.offers().single()

        db.saveOffer(first.id, "Daily Data Updated", "DATA", 20.0)
        assertEquals("Daily Data Updated", db.offers().single().name)

        val error = assertThrows(IllegalArgumentException::class.java) {
            db.saveOffer(null, "Another Bundle", "DATA", 20.0)
        }
        assertTrue(error.message.orEmpty().contains("already assigned"))

        db.saveOffer(null, "Weekly Data", "DATA", 50.0)
        assertThrows(IllegalArgumentException::class.java) {
            db.saveOffer(first.id, "Daily Data Updated", "DATA", 50.0)
        }
        assertEquals(2, db.count("offers"))
        assertEquals(20.0, db.offers().first { it.id == first.id }.price, 0.0)
    }

    @Test
    fun offerImportRejectsDuplicatePricesWithoutPartialWrites() {
        assertThrows(IllegalArgumentException::class.java) {
            db.importOffers(
                listOf(
                    ImportedOffer("Bundle A", "DATA", null, 10.0),
                    ImportedOffer("Bundle B", "DATA", null, 10.0)
                )
            )
        }
        assertEquals(0, db.count("offers"))

        db.saveOffer(null, "Existing", "DATA", 30.0)
        assertThrows(IllegalArgumentException::class.java) {
            db.importOffers(
                listOf(
                    ImportedOffer("New A", "DATA", null, 40.0),
                    ImportedOffer("New B", "DATA", null, 30.0)
                )
            )
        }
        assertEquals(1, db.count("offers"))
    }

    @Test
    fun customerSearchBlacklistAndUpsertRemainConsistent() {
        db.saveCustomer(null, "Jane Doe", "0712345678", "Regular customer")
        val customer = db.customers().single()
        db.setCustomerBlacklisted(customer.id, true)

        val found = db.customers("Jane").single()
        assertEquals("0712345678", found.phone)
        assertTrue(found.blacklisted)
        assertTrue(db.customers("0712").single().blacklisted)
        assertTrue(db.customers("missing").isEmpty())

        db.upsertCustomer("Jane Updated", "0712345678", "Updated notes")
        assertEquals(1, db.count("customers"))
        assertEquals("Jane Updated", db.customers().single().name)
        assertFalse(db.customers().single().blacklisted)
    }

    @Test
    fun duplicateCustomerPhoneIsRejected() {
        db.saveCustomer(null, "Jane", "0712345678", "")
        assertThrows(android.database.SQLException::class.java) {
            db.saveCustomer(null, "Janet", "0712345678", "")
        }
        assertEquals(1, db.count("customers"))
    }

    @Test
    fun orderAndTransactionReferencesAreValidatedAndHistoryIsJoined() {
        db.saveCustomer(null, "Jane Doe", "0712345678", "")
        val orderId = db.createOrder("0712345678", "Daily Data", 20.0, "Admin app")
        db.recordTransaction(orderId, 20.0, "ABC1234567")

        assertThrows(IllegalArgumentException::class.java) {
            db.recordTransaction(orderId, 20.0, "ABC1234567")
        }
        assertThrows(IllegalArgumentException::class.java) {
            db.recordTransaction(99999, 20.0, "OTHER12345")
        }

        val history = db.transactionHistory().single()
        assertEquals("Jane Doe", history.customer)
        assertEquals("Daily Data", history.offer)
        assertEquals(20.0, history.amount, 0.0)
        assertEquals("UNVERIFIED", history.transactionStatus)
        assertEquals("PENDING", history.orderStatus)
        assertEquals("ABC1234567", history.reference)
        assertEquals("Admin app", history.source)
    }

    @Test
    fun orderStatusAcceptsOnlyExplicitSupportedStates() {
        val orderId = db.createOrder("0712345678", "Daily Data", 20.0)
        db.updateOrderStatus(orderId, "PAYMENT_UNVERIFIED")
        assertEquals("PAYMENT_UNVERIFIED", db.orders().single().status)

        assertThrows(IllegalArgumentException::class.java) {
            db.updateOrderStatus(orderId, "PAID")
        }
        assertThrows(IllegalArgumentException::class.java) {
            db.updateOrderStatus(orderId, "RANDOM_STATE")
        }
    }
}
