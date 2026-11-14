package com.bingwapulse.shared

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformContractsTest {
    private fun offer(
        id: String,
        agent: String = "agent-a",
        price: Long = 100,
        active: Boolean = true
    ) = AgentOffer(
        id = id,
        accountId = "account-a",
        agentUserId = agent,
        providerOfferId = "provider-$id",
        sellingPriceKes = price,
        active = active
    )

    @Test
    fun duplicateActivePriceIsRejectedForSameAgent() {
        assertTrue(
            OfferPriceRules.hasDuplicateActivePrice(
                listOf(offer("existing")),
                agentUserId = "agent-a",
                sellingPriceKes = 100
            )
        )
    }

    @Test
    fun samePriceIsAllowedForDifferentAgents() {
        assertFalse(
            OfferPriceRules.hasDuplicateActivePrice(
                listOf(offer("existing", agent = "agent-a")),
                agentUserId = "agent-b",
                sellingPriceKes = 100
            )
        )
    }

    @Test
    fun inactiveOffersDoNotBlockPriceReuse() {
        assertFalse(
            OfferPriceRules.hasDuplicateActivePrice(
                listOf(offer("inactive", active = false)),
                agentUserId = "agent-a",
                sellingPriceKes = 100
            )
        )
    }

    @Test
    fun editingOfferExcludesItsOwnIdButDetectsOtherConflicts() {
        val offers = listOf(offer("editing"), offer("other", price = 150))
        assertFalse(OfferPriceRules.hasDuplicateActivePrice(offers, "agent-a", 100, "editing"))
        assertTrue(OfferPriceRules.hasDuplicateActivePrice(offers, "agent-a", 150, "editing"))
    }

    @Test
    fun pairingChallengeHonorsExpiryAndSingleUse() {
        val challenge = DevicePairingChallenge(
            challengeId = "challenge",
            accountId = "account",
            createdByUserId = "owner",
            code = "123456",
            createdAtEpochMillis = 1_000,
            expiresAtEpochMillis = 2_000
        )
        assertFalse(challenge.isUsable(999))
        assertTrue(challenge.isUsable(1_000))
        assertTrue(challenge.isUsable(1_999))
        assertFalse(challenge.isUsable(2_000))
        assertFalse(challenge.copy(consumed = true).isUsable(1_500))
    }

    @Test
    fun generatedPairingCodesAreSixDigits() {
        repeat(100) {
            val code = PairingCodeGenerator.generate()
            assertTrue(code.matches(Regex("[0-9]{6}")))
        }
    }
}
