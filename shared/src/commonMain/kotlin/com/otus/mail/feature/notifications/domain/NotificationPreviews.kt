package com.otus.mail.feature.notifications.domain

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
 * or for mail sent to one of the user's addresses, keyed by login id and lower-case address.
 */
data class PreviewChoices(
    val all: NotificationPreviews = NotificationPreviews.default,
    val logins: Map<String, NotificationPreviews> = emptyMap(),
    val addresses: Map<String, NotificationPreviews> = emptyMap(),
) {
    /**
     * What an alert for mail of [loginId] sent to [recipients] shows. An address's choice wins over
     * its login's, which wins over the choice for all mail. A wildcard address such as
     * `*@example.org` covers every address of its domain without a choice of its own. When the mail
     * went to several addresses with choices, the more private one wins.
     */
    fun forMail(
        loginId: String,
        recipients: List<String>,
    ): NotificationPreviews {
        val chosen = recipients.mapNotNull { forAddress(it.lowercase()) }
        return when {
            NotificationPreviews.Hidden in chosen -> NotificationPreviews.Hidden
            chosen.isNotEmpty() -> chosen.first()
            else -> forLogin(loginId)
        }
    }

    private fun forAddress(address: String): NotificationPreviews? =
        addresses[address] ?: address.substringAfterLast('@', "").takeIf { it.isNotEmpty() }?.let { addresses["*@$it"] }

    /** What mail of [loginId] shows when no address choice applies. */
    fun forLogin(loginId: String): NotificationPreviews = logins[loginId] ?: all

    fun withLogin(
        loginId: String,
        choice: NotificationPreviews?,
    ): PreviewChoices = copy(logins = if (choice == null) logins - loginId else logins + (loginId to choice))

    fun withAddress(
        address: String,
        choice: NotificationPreviews?,
    ): PreviewChoices {
        val key = address.lowercase()
        return copy(addresses = if (choice == null) addresses - key else addresses + (key to choice))
    }

    /** The choices as stored text, under [ALL], `login:<id>`, and `address:<address>`. */
    fun encode(): Map<String, String> =
        buildMap {
            put(ALL, all.storageValue)
            logins.forEach { (id, choice) -> put(LOGIN_PREFIX + id, choice.storageValue) }
            addresses.forEach { (address, choice) -> put(ADDRESS_PREFIX + address, choice.storageValue) }
        }

    companion object {
        /** The key earlier versions kept the one choice under, so it carries over. */
        const val ALL = "previews"
        private const val LOGIN_PREFIX = "login:"
        private const val ADDRESS_PREFIX = "address:"

        fun decode(stored: Map<String, String>): PreviewChoices {
            fun prefixed(prefix: String) =
                stored
                    .filterKeys { it.startsWith(prefix) }
                    .mapNotNull { (key, value) ->
                        NotificationPreviews.decodeOrNull(value)?.let {
                            key.removePrefix(prefix) to
                                it
                        }
                    }.toMap()
            return PreviewChoices(
                NotificationPreviews.decodeOrNull(stored[ALL]) ?: NotificationPreviews.default,
                prefixed(LOGIN_PREFIX),
                prefixed(ADDRESS_PREFIX),
            )
        }
    }
}

/** App-private storage for the [PreviewChoices]. */
interface NotificationPreviewStore {
    fun read(): PreviewChoices

    fun write(choices: PreviewChoices)
}
