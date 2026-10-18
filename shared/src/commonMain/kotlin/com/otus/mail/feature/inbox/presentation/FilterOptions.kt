package com.otus.mail.feature.inbox.presentation

import com.otus.designsystem.components.ChipAvatar
import com.otus.designsystem.theme.FolderColor
import com.otus.mail.feature.inbox.domain.FolderRef
import com.otus.mail.feature.inbox.domain.OwnAddress
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
 * [accounts], the folder [shortcuts] in order, whether the page can filter by [folders] at all,
 * and whether any account has a Read later folder.
 */
data class FilterOptions(
    val addresses: List<FilterAddress> = emptyList(),
    val accounts: List<FilterAccount> = emptyList(),
    val shortcuts: List<FolderKey> = emptyList(),
    val folders: Boolean = false,
    val readLater: Boolean = false,
) {
    private val byKey: Map<FolderKey, FilterFolder> by lazy {
        accounts.flatMap { it.folders }.associateBy { it.key }
    }

    fun folder(key: FolderKey): FilterFolder? = byKey[key]

    /** Each folder's label, telling apart folders that share a name. */
    val labels: Map<FolderKey, FolderLabel> by lazy { folderLabels(accounts) }

    fun account(id: String): FilterAccount? = accounts.firstOrNull { it.id == id }
}
