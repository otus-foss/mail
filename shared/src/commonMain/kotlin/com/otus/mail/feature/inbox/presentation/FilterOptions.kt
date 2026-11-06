package com.otus.mail.feature.inbox.presentation

import com.otus.designsystem.shared.people.components.ChipAvatar
import com.otus.designsystem.theme.colors.FolderColor
import com.otus.mail.core.messages.domain.FolderRef
import com.otus.mail.core.messages.domain.OwnAddress
import com.otus.mail.core.messages.domain.QuickFacet
import com.otus.mail.core.settings.domain.FilterLayout
import com.otus.mail.core.settings.presentation.icon
import com.otus.mail.core.settings.presentation.label
import org.jetbrains.compose.resources.DrawableResource

/** One login's folder as the filter panel lists it. [ancestors] name its parents, top level first. */
data class FilterFolder(
    val key: FolderKey,
    val name: String,
    val ancestors: List<String>,
    val icon: DrawableResource,
    val color: FolderColor = FolderColor.Default,
    val system: Boolean = false,
    val parentId: String? = null,
    /** Every folder nested below this one, at any depth. */
    val descendants: Set<String> = emptySet(),
) {
    val depth: Int
        get() = ancestors.size

    val hasChildren: Boolean
        get() = descendants.isNotEmpty()
}

/** A folder of one login, without a subfolder choice. */
data class FolderKey(
    val loginId: String,
    val mailboxId: String,
)

val FolderRef.key: FolderKey
    get() = FolderKey(loginId, mailboxId)

/** One login in the panel, its folders in the sidebar's order: system folders first. */
data class FilterAccount(
    val id: String,
    val name: String,
    val avatar: ChipAvatar,
    val folders: List<FilterFolder>,
)

/** One of the user's addresses, with the generated avatar rows show for it. */
data class FilterAddress(
    val address: OwnAddress,
    val avatar: ChipAvatar,
)

/**
 * What the filter panel offers on a page: the user's [addresses], each login's folders in
 * [accounts], whether the page can filter by [folders] at all,
 * whether any account has a Read later folder, and the user's [layout].
 */
data class FilterOptions(
    val addresses: List<FilterAddress> = emptyList(),
    val accounts: List<FilterAccount> = emptyList(),
    val folders: Boolean = false,
    val readLater: Boolean = false,
    val layout: FilterLayout = FilterLayout(),
) {
    private val byKey: Map<FolderKey, FilterFolder> by lazy {
        accounts.flatMap { it.folders }.associateBy { it.key }
    }

    fun folder(key: FolderKey): FilterFolder? = byKey[key]

    /** Each folder's label, telling apart folders that share a name. */
    val labels: Map<FolderKey, FolderLabel> by lazy { folderLabels(accounts) }

    fun account(id: String): FilterAccount? = accounts.firstOrNull { it.id == id }

    /** Whether the Account row and page are offered: only while the page shows several logins. */
    val offersAccount: Boolean
        get() = accounts.size > 1

    /** The Quick filters the layout shows here, without Read later while no account has its folder. */
    val quick: List<QuickFacet>
        get() = layout.quick.filter { readLater || it != QuickFacet.ReadLater }
}
