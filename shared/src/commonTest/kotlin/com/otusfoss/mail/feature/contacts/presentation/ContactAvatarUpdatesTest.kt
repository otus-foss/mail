package com.otusfoss.mail.feature.contacts.presentation

import com.otusfoss.core.avatar.AVATAR_PHOTO_KEY
import com.otusfoss.core.avatar.AvatarImage
import com.otusfoss.core.avatar.AvatarImageLoader
import com.otusfoss.core.avatar.AvatarMark
import com.otusfoss.core.avatar.AvatarRequest
import com.otusfoss.core.avatar.AvatarService
import com.otusfoss.core.avatar.AvatarStyle
import com.otusfoss.core.avatar.avatarOptions
import com.otusfoss.core.avatar.withAvatarStyle
import com.otusfoss.core.jscontact.Card
import com.otusfoss.core.jscontact.EmailAddress
import com.otusfoss.core.jscontact.Media
import com.otusfoss.core.jscontact.MediaKind
import com.otusfoss.mail.feature.contacts.domain.BookKey
import com.otusfoss.mail.feature.contacts.domain.CardKey
import com.otusfoss.mail.feature.contacts.domain.ContactBook
import com.otusfoss.mail.feature.contacts.domain.ContactCard
import com.otusfoss.mail.feature.contacts.domain.ContactsSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ContactAvatarUpdatesTest {
    private val ownBook = BookKey("ada", "a", "own")
    private val sharedBook = BookKey("ada", "shared", "b9")
    private val otherLoginBook = BookKey("bob", "m", "b1")
    private val boss = PhotoAddress("ada", "boss@corp.example.com")

    private fun photoCard(
        key: CardKey,
        book: BookKey,
        mediaKey: String,
        base64: String,
    ) = ContactCard(
        key,
        Card(
            emails = mapOf("e1" to EmailAddress(boss.email)),
            media = mapOf(mediaKey to Media(MediaKind.Photo, uri = "data:image/png;base64,$base64")),
        ),
        listOf(book),
    )

    private fun snapshot(
        books: List<ContactBook>,
        vararg cards: ContactCard,
    ) = ContactsSnapshot(books, cards.toList(), hasContacts = true)

    private val personal = ContactBook(ownBook, "Personal", "Ada")
    private val team = ContactBook(sharedBook, "Team", "Ada", canWrite = false, sharedBy = "Bob")
    private val bobs = ContactBook(otherLoginBook, "Personal", "Bob")

    @Test
    fun websiteStyleOnSharedCardFetchesNothingWithWebsiteIconsOff() =
        runTest {
            val asked = mutableListOf<String>()
            val loader =
                object : AvatarImageLoader {
                    override suspend fun load(url: String): AvatarImage? {
                        asked += url
                        return null
                    }

                    override suspend fun websiteIcon(domain: String): AvatarImage? {
                        asked += "websiteIcon:$domain"
                        return null
                    }
                }
            val service = AvatarService(images = loader)
            val card =
                Card(
                    emails = mapOf("e1" to EmailAddress("news@marker.example.com")),
                    addressBookIds = mapOf("b9" to true),
                ).withAvatarStyle(AvatarStyle("Blue", AvatarMark.Website))
            val contacts = snapshot(listOf(team), ContactCard(CardKey("ada", "shared", "c1"), card, listOf(sharedBook)))
            ContactAvatarUpdates(service, backgroundScope, { false }, {}).shown(contacts)
            runCurrent()
            service.resolve(AvatarRequest("ada", "news@marker.example.com"), avatarOptions(websiteIcons = false))
            assertEquals(emptyList(), asked)
        }

    @Test
    fun ownPhotoWinsOverSharedAvatarCard() {
        val contacts =
            snapshot(
                listOf(personal, team),
                photoCard(CardKey("ada", "a", "c1"), ownBook, "photo", "AQ=="),
                photoCard(CardKey("ada", "shared", "c2"), sharedBook, AVATAR_PHOTO_KEY, "Ag=="),
            )
        assertContentEquals(byteArrayOf(1), shownBytes(contacts, allAccounts = false))
    }

    @Test
    fun thisLoginsPhotoWinsOverAnotherLoginsAvatarCard() {
        val contacts =
            snapshot(
                listOf(personal, bobs),
                photoCard(CardKey("ada", "a", "c1"), ownBook, "photo", "AQ=="),
                photoCard(CardKey("bob", "m", "c2"), otherLoginBook, AVATAR_PHOTO_KEY, "Ag=="),
            )
        assertContentEquals(byteArrayOf(1), shownBytes(contacts, allAccounts = true))
    }

    @Test
    fun ownAvatarCardWinsOverOwnPlainCard() {
        val contacts =
            snapshot(
                listOf(personal),
                photoCard(CardKey("ada", "a", "c1"), ownBook, "photo", "AQ=="),
                photoCard(CardKey("ada", "a", "c2"), ownBook, AVATAR_PHOTO_KEY, "Ag=="),
            )
        assertContentEquals(byteArrayOf(2), shownBytes(contacts, allAccounts = false))
    }

    @Test
    fun anotherLoginsCardShowsWhenThisLoginHasNone() {
        val contacts =
            snapshot(listOf(personal, bobs), photoCard(CardKey("bob", "m", "c2"), otherLoginBook, "photo", "Ag=="))
        assertContentEquals(byteArrayOf(2), shownBytes(contacts, allAccounts = true))
    }

    @Test
    fun anotherLoginsCardNeverShowsWithoutAllAccounts() {
        val contacts =
            snapshot(
                listOf(personal, bobs),
                photoCard(CardKey("bob", "m", "c2"), otherLoginBook, AVATAR_PHOTO_KEY, "Ag=="),
            )
        assertNull(contactPhotos(contacts, allAccounts = false)[boss])
    }

    @Test
    fun cardInUnknownBookDoesNotCountAsOwn() {
        val unknown = BookKey("ada", "a", "gone")
        val contacts =
            snapshot(
                listOf(personal),
                photoCard(CardKey("ada", "a", "c1"), ownBook, "photo", "AQ=="),
                photoCard(CardKey("ada", "a", "c2"), unknown, AVATAR_PHOTO_KEY, "Ag=="),
            )
        assertContentEquals(byteArrayOf(1), shownBytes(contacts, allAccounts = false))
    }

    @Test
    fun sharedCardKeepsColorStyleButNotWebsiteStyle() {
        fun styled(mark: AvatarMark) =
            snapshot(
                listOf(team),
                ContactCard(
                    CardKey("ada", "shared", "c1"),
                    Card(emails = mapOf("e1" to EmailAddress(boss.email))).withAvatarStyle(AvatarStyle("Blue", mark)),
                    listOf(sharedBook),
                ),
            )
        val letters = AvatarMark.Letters("TB")
        assertEquals(AvatarStyle("Blue", letters), contactPhotos(styled(letters), allAccounts = false)[boss]?.style)
        assertNull(contactPhotos(styled(AvatarMark.Website), allAccounts = false)[boss])
    }

    private fun shownBytes(
        contacts: ContactsSnapshot,
        allAccounts: Boolean,
    ) = contactPhotos(contacts, allAccounts)[boss]?.photo?.inlineBytes()
}
