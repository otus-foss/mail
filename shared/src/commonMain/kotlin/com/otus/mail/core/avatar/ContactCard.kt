package com.otus.mail.core.avatar

import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapType
import kotlinx.serialization.Serializable

/** RFC 9610 ContactCard and RFC 9553 JSContact fields used for avatar matching. */
internal val ContactCardType = JmapType("ContactCard", JmapCapability.CONTACTS, ContactCard.serializer())
internal val AddressBookType = JmapType("AddressBook", JmapCapability.CONTACTS, AvatarAddressBook.serializer())

@Serializable
internal data class ContactCard(
    val id: String,
    val addressBookIds: Map<String, Boolean> = emptyMap(),
    val emails: Map<String, ContactEmail> = emptyMap(),
    val media: Map<String, ContactMedia> = emptyMap(),
)

@Serializable
internal data class ContactEmail(
    val address: String,
)

@Serializable
internal data class ContactMedia(
    val kind: String,
    val blobId: String? = null,
    val uri: String? = null,
    val mediaType: String? = null,
    val pref: Int? = null,
)

@Serializable
internal data class AvatarAddressBook(
    val id: String,
    val isSubscribed: Boolean = true,
    val myRights: AvatarAddressBookRights,
)

@Serializable
internal data class AvatarAddressBookRights(
    val mayRead: Boolean = false,
)
