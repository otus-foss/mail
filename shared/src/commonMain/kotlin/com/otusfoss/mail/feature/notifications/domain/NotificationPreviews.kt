package com.otusfoss.mail.feature.notifications.domain

import com.otusfoss.mail.core.mail.EmailAddressKey

/**
 * What a new-mail alert shows. [Device] shows the sender and subject and leaves the lock screen to
 * the device's own setting, which may show them, show only "New mail", or show nothing. [Hidden]
 * says only "New mail", locked or unlocked.
 */
enum class NotificationPreviews {
    Device,
    Hidden,
    ;

    val storageValue: String get() = name.lowercase()

    companion object {
        val default: NotificationPreviews = Device

        /**
         * The choice saved as [stored], else null. Choices of earlier versions map to the nearest
         * one: Always and When unlocked to [Device], Never to [Hidden].
         */
        fun decodeOrNull(stored: String?): NotificationPreviews? =
            when (stored) {
                "always", "unlocked" -> Device
                "never" -> Hidden
                else -> entries.firstOrNull { it.storageValue == stored }
            }
    }
}

/**
 * The [NotificationPreviews] for all mail, and the choices that override it for one login's mail
 * or for that login's mail sent to one of its addresses. [addresses] is keyed by login id, then
 * lower-case address, because a sender can name any address in To or Cc: an address's choice must
 * not reach another login's mail. [unclaimed] holds address choices saved before they were kept per
 * login, until [claim] gives them to the logins that own the addresses.
 */
data class PreviewChoices(
    val all: NotificationPreviews = NotificationPreviews.default,
    val logins: Map<String, NotificationPreviews> = emptyMap(),
    val addresses: Map<String, Map<String, NotificationPreviews>> = emptyMap(),
    val unclaimed: Map<String, NotificationPreviews> = emptyMap(),
) {
    /**
     * What an alert for mail of [loginId] sent to [recipients] shows. Each recipient counts with the
     * login's own choice for it, else the login's choice, which falls back to the choice for all
     * mail, and the more private one wins. A wildcard address such as `*@example.org` covers every
     * address of its domain without a choice of its own. An [unclaimed] choice counts only when it
     * hides, because its login is not known yet.
     */
    fun forMail(
        loginId: String,
        recipients: List<String>,
    ): NotificationPreviews {
        val addressed = recipients.map(String::lowercase)
        val own = addresses[loginId].orEmpty()
        val login = forLogin(loginId)
        val shown =
            addressed.map { own.choiceFor(it) ?: login }.ifEmpty { listOf(login) } +
                addressed.mapNotNull { unclaimed.choiceFor(it) }.filter { it == NotificationPreviews.Hidden }
        return if (NotificationPreviews.Hidden in shown) NotificationPreviews.Hidden else NotificationPreviews.Device
    }

    /** What mail of [loginId] shows when no address choice applies. */
    fun forLogin(loginId: String): NotificationPreviews = logins[loginId] ?: all

    /** The choice [loginId] has of its own for mail sent to [address], without wildcards. */
    fun forAddress(
        loginId: String,
        address: String,
    ): NotificationPreviews? = addresses[loginId]?.get(EmailAddressKey(address).value)

    fun withLogin(
        loginId: String,
        choice: NotificationPreviews?,
    ): PreviewChoices = copy(logins = if (choice == null) logins - loginId else logins + (loginId to choice))

    fun withAddress(
        loginId: String,
        address: String,
        choice: NotificationPreviews?,
    ): PreviewChoices {
        val key = EmailAddressKey(address).value
        val own = addresses[loginId].orEmpty().let { if (choice == null) it - key else it + (key to choice) }
        return copy(addresses = if (own.isEmpty()) addresses - loginId else addresses + (loginId to own))
    }

    /**
     * Gives each [unclaimed] choice to every login in [owners], login id to its addresses, that
     * owns its address, unless that login already has a choice for it. Choices no login owns are
     * dropped, since only the owning login's mail may use them.
     */
    fun claim(owners: Map<String, List<String>>): PreviewChoices {
        if (unclaimed.isEmpty()) return this
        val claimed =
            owners
                .mapValues { (loginId, owned) ->
                    owned
                        .map(String::lowercase)
                        .mapNotNull { address -> unclaimed[address]?.let { address to it } }
                        .toMap() + addresses[loginId].orEmpty()
                }.filterValues { it.isNotEmpty() }
        return copy(addresses = addresses + claimed, unclaimed = emptyMap())
    }

    /**
     * The choices as stored text, under [ALL], `login:<id>`, `login-address:<address> <id>`, and
     * `address:<address>` for [unclaimed] ones. An address cannot hold a space, so the first space
     * ends it; a login id can.
     */
    fun encode(): Map<String, String> =
        buildMap {
            put(ALL, all.storageValue)
            logins.forEach { (id, choice) -> put(LOGIN_PREFIX + id, choice.storageValue) }
            addresses.forEach { (id, own) ->
                own.forEach { (address, choice) -> put("$LOGIN_ADDRESS_PREFIX$address $id", choice.storageValue) }
            }
            unclaimed.forEach { (address, choice) -> put(UNCLAIMED_PREFIX + address, choice.storageValue) }
        }

    companion object {
        /** The key earlier versions kept the one choice under, so it carries over. */
        const val ALL = "previews"
        private const val LOGIN_PREFIX = "login:"
        private const val LOGIN_ADDRESS_PREFIX = "login-address:"

        /** Earlier versions kept address choices under this prefix for every login at once. */
        private const val UNCLAIMED_PREFIX = "address:"

        fun decode(stored: Map<String, String>): PreviewChoices {
            fun prefixed(prefix: String) =
                stored
                    .filterKeys { it.startsWith(prefix) }
                    .mapNotNull { (key, value) ->
                        NotificationPreviews.decodeOrNull(value)?.let { key.removePrefix(prefix) to it }
                    }.toMap()
            val owned =
                prefixed(LOGIN_ADDRESS_PREFIX)
                    .filterKeys { ' ' in it }
                    .entries
                    .groupBy({ it.key.substringAfter(' ') }, { it.key.substringBefore(' ') to it.value })
                    .mapValues { (_, choices) -> choices.toMap() }
            return PreviewChoices(
                NotificationPreviews.decodeOrNull(stored[ALL]) ?: NotificationPreviews.default,
                prefixed(LOGIN_PREFIX),
                owned,
                prefixed(UNCLAIMED_PREFIX),
            )
        }
    }
}

/** The choice for [address] in these choices keyed by lower-case address, else its domain's wildcard. */
private fun Map<String, NotificationPreviews>.choiceFor(address: String): NotificationPreviews? =
    this[address] ?: address.substringAfterLast('@', "").takeIf { it.isNotEmpty() }?.let { this["*@$it"] }

/** App-private storage for the [PreviewChoices]. */
interface NotificationPreviewStore {
    fun read(): PreviewChoices

    fun write(choices: PreviewChoices)
}

/** Drops [loginId]'s choices, when its account is removed. */
fun NotificationPreviewStore.forgetAccount(loginId: String) {
    val choices = read()
    write(choices.copy(logins = choices.logins - loginId, addresses = choices.addresses - loginId))
}
