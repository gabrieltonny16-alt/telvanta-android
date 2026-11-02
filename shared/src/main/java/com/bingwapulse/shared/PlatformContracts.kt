package com.bingwapulse.shared

import java.security.SecureRandom
import java.util.UUID

/** Shared backend-neutral contracts used by BingwaPulse and Connect Pulse. */
enum class AgentRole { OWNER, ADMIN, SUPERVISOR, AGENT, VIEWER }
enum class DevicePlatform { ANDROID, IOS, WEB }
enum class DeviceConnectionState { PENDING_APPROVAL, AUTHORIZED, REVOKED, EXPIRED }
enum class OfferCategory { DATA, SMS, MINUTES, COMBO }
enum class OrderChannel { ADMIN_APP, MANAGEMENT_WEB, SITE_LINK }
enum class OrderState {
    CREATED, AWAITING_PAYMENT, PAYMENT_REVIEW, PAID, PROCESSING, FULFILLED, FAILED, CANCELLED
}

data class BusinessAccount(val id: String, val displayName: String)

data class AgentMembership(
    val accountId: String,
    val userId: String,
    val role: AgentRole,
    val enabled: Boolean = true
)

data class AuthorizedDevice(
    val id: String,
    val accountId: String,
    val userId: String,
    val displayName: String,
    val platform: DevicePlatform,
    val state: DeviceConnectionState,
    val createdAtEpochMillis: Long,
    val lastSeenAtEpochMillis: Long? = null
)

data class ProviderOffer(
    val id: String,
    val providerId: String,
    val providerOfferCode: String?,
    val name: String,
    val category: OfferCategory,
    val bundleDescription: String,
    val referencePriceKes: Long?
)

data class AgentOffer(
    val id: String,
    val accountId: String,
    val agentUserId: String,
    val providerOfferId: String,
    val sellingPriceKes: Long,
    val publishedOnSiteLink: Boolean = false,
    val active: Boolean = true
)

data class CustomerRecord(
    val id: String,
    val accountId: String,
    val displayName: String?,
    val phoneNumber: String
)

data class OrderRecord(
    val id: String,
    val accountId: String,
    val agentUserId: String,
    val customerId: String?,
    val agentOfferId: String,
    val channel: OrderChannel,
    val state: OrderState,
    val amountKes: Long,
    val createdAtEpochMillis: Long
)

/** Short-lived pairing challenge; a trusted backend must enforce expiry and single use. */
data class DevicePairingChallenge(
    val challengeId: String,
    val accountId: String,
    val createdByUserId: String,
    val code: String,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val consumed: Boolean = false
) {
    fun isUsable(nowEpochMillis: Long): Boolean =
        !consumed && nowEpochMillis >= createdAtEpochMillis && nowEpochMillis < expiresAtEpochMillis
}

data class DevicePairingRequest(
    val challengeId: String,
    val code: String,
    val deviceDisplayName: String,
    val platform: DevicePlatform = DevicePlatform.ANDROID
)

data class DevicePairingApproval(
    val challengeId: String,
    val deviceId: String,
    val accountId: String,
    val approvedByUserId: String,
    val approvedAtEpochMillis: Long
)

object PairingCodeGenerator {
    private val random = SecureRandom()
    /** Generates a six-digit code. This alone does not authorize a device. */
    fun generate(): String = (100_000 + random.nextInt(900_000)).toString()
}

object OfferPriceRules {
    /** Duplicate active prices are checked per agent, not globally across agents. */
    fun hasDuplicateActivePrice(
        offers: List<AgentOffer>,
        agentUserId: String,
        sellingPriceKes: Long,
        excludingOfferId: String? = null
    ): Boolean = offers.any {
        it.agentUserId == agentUserId &&
            it.active &&
            it.sellingPriceKes == sellingPriceKes &&
            it.id != excludingOfferId
    }
}

object Ids {
    fun newId(): String = UUID.randomUUID().toString()
}
