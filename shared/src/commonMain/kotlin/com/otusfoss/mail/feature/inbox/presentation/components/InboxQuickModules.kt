package com.otusfoss.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.otusfoss.core.designsystem.mail.attachments.components.AttachmentKind
import com.otusfoss.core.designsystem.mail.quickview.components.QuickCount
import com.otusfoss.core.designsystem.mail.quickview.components.QuickCounts
import com.otusfoss.core.designsystem.mail.quickview.components.QuickFace
import com.otusfoss.core.designsystem.mail.quickview.components.QuickFaceLook
import com.otusfoss.core.designsystem.mail.quickview.components.QuickFaces
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_attach_file
import com.otusfoss.core.designsystem.resources.symbol_bookmark
import com.otusfoss.core.designsystem.resources.symbol_flag
import com.otusfoss.core.designsystem.resources.symbol_hourglass_empty
import com.otusfoss.core.designsystem.resources.symbol_reply
import com.otusfoss.mail.core.messages.domain.MailAttachment
import com.otusfoss.mail.core.messages.domain.MailScopeChoice
import com.otusfoss.mail.core.messages.domain.QuickFacet
import com.otusfoss.mail.core.messages.domain.SenderKey
import com.otusfoss.mail.core.messages.presentation.components.chipAvatar
import com.otusfoss.mail.core.messages.presentation.components.firstName
import com.otusfoss.mail.core.messages.presentation.icon
import com.otusfoss.mail.core.messages.presentation.kind
import com.otusfoss.mail.core.settings.presentation.icon
import com.otusfoss.mail.feature.inbox.domain.InboxFilters
import com.otusfoss.mail.feature.inbox.domain.frequentSenders
import org.jetbrains.compose.resources.DrawableResource

/** Needs reply, Waiting, Flagged, and Later (Files while Read later is off), counted as the filter panel counts. */
@Composable
internal fun QuickCountsModule(sources: QuickSources) {
    val facets =
        listOf(
            QuickFacet.NeedsMyReply,
            QuickFacet.AwaitingReply,
            QuickFacet.Flagged,
            if (sources.page.filterOptions.readLater) QuickFacet.ReadLater else QuickFacet.Attachments,
        )
    val filters = sources.filters
    val counts by produceState(emptyMap<QuickFacet, Long>(), filters, sources.page.scope?.chosen) {
        value = facets.associateWith { filters.countOf(InboxFilters(quick = setOf(it))) ?: 0L }
    }
    QuickCounts(
        facets.map { facet ->
            QuickCount(facet.quickLabel, facet.quickIcon, counts[facet]?.toInt() ?: 0) {
                sources.show(InboxFilters(quick = setOf(facet)))
            }
        },
    )
}

/** The people the user deals with most in the loaded mail; a face shows the mail they sent. */
@Composable
internal fun QuickPeopleModule(sources: QuickSources) {
    val own = sources.page.rowLook.own
    val people = remember(sources.scoped, own) { frequentSenders(sources.scoped, own) }
    QuickFaces(
        people.map { (person, unread) ->
            QuickFace(
                person.firstName,
                "Mail from ${person.name.ifBlank { person.address }}" + if (unread) ", unread" else "",
                QuickFaceLook.Person(person.chipAvatar),
                { sources.show(InboxFilters(from = setOf(SenderKey(person.name, setOf(person.address))))) },
                unread = unread,
            )
        },
        slots = FACE_SLOTS,
    )
}

/** The user's own addresses with their unread counts; one shows only its mail. */
@Composable
internal fun QuickAddressesModule(sources: QuickSources) {
    val scope = sources.page.scope ?: return
    val choices =
        scope.accounts
            .flatMap { account ->
                account.addresses.map { address ->
                    MailScopeChoice.Address(account.accountId, address) to
                        chipAvatar(account.avatarOf(address), account.name, address)
                }
            }.take(FILE_SLOTS)
    QuickFaces(
        choices.map { (choice, avatar) ->
            val unread = scope.count(choice)?.toIntOrNull() ?: 0
            QuickFace(
                choice.address.withoutTopDomain(),
                "${choice.address}, $unread unread",
                QuickFaceLook.Person(avatar),
                {
                    sources.close()
                    scope.onChoose(choice)
                },
                count = unread,
            )
        },
        slots = FILE_SLOTS,
    )
}

/** The newest files in the loaded mail; one opens the mail it came with. */
@Composable
internal fun QuickFilesModule(sources: QuickSources) {
    val files =
        remember(sources.scoped) {
            sources.scoped
                .flatMap { message -> message.attributes.attachments.map { name -> message to name } }
                .take(FILE_SLOTS)
        }
    QuickFaces(
        files.map { (message, name) ->
            QuickFace(
                name,
                "$name, from ${message.sender.name.ifBlank { message.sender.address }}",
                QuickFaceLook.File(fileKind(name)),
                {
                    sources.close()
                    sources.onOpenMessage(message)
                },
            )
        },
        slots = FILE_SLOTS,
    )
}

private val QuickFacet.quickLabel: String
    get() =
        when (this) {
            QuickFacet.NeedsMyReply -> "Needs reply"
            QuickFacet.AwaitingReply -> "Waiting"
            QuickFacet.Flagged -> "Flagged"
            QuickFacet.ReadLater -> "Later"
            else -> "Files"
        }

private val QuickFacet.quickIcon: DrawableResource
    get() =
        when (this) {
            QuickFacet.NeedsMyReply -> Res.drawable.symbol_reply
            QuickFacet.AwaitingReply -> Res.drawable.symbol_hourglass_empty
            QuickFacet.Flagged -> Res.drawable.symbol_flag
            QuickFacet.ReadLater -> Res.drawable.symbol_bookmark
            else -> Res.drawable.symbol_attach_file
        }

/** The address without its top-level domain, such as `otus@rubylabs`, short enough for a face. */
private fun String.withoutTopDomain(): String =
    if ('@' in this && '.' in substringAfter('@')) substringBeforeLast('.') else this

/** The icon kind for a file named [name], read from its extension as the attachment strip does. */
private fun fileKind(name: String): AttachmentKind =
    AttachmentKind.valueOf(MailAttachment(name, "", 0L, "").kind().name)

private const val FACE_SLOTS = 5

private const val FILE_SLOTS = 4
