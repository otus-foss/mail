package com.otus.mail.feature.folders.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.avatar.generatedAvatar
import com.otus.designsystem.components.AccountGroupRow
import com.otus.designsystem.components.ChipAvatar
import com.otus.designsystem.components.ChoiceOptions
import com.otus.designsystem.components.ChoiceRow
import com.otus.designsystem.components.ChoiceSheet
import com.otus.designsystem.components.Collapsible
import com.otus.designsystem.components.ScopePicker
import com.otus.mail.feature.folders.domain.AccountFolders

/**
 * Which account's folders the page shows: All accounts while [chosen] is null, else one account.
 * The card opens a sheet with All accounts first, then each account; [onChoose] gets the account
 * id, or null for All accounts. [folderCount] is how many folders the page lists.
 */
@Composable
fun FolderAccountPicker(
    accounts: List<AccountFolders>,
    chosen: AccountFolders?,
    folderCount: Int,
    onChoose: (String?) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    ScopePicker(
        title = chosen?.accountName ?: ALL_ACCOUNTS,
        avatars = (chosen?.let(::listOf) ?: accounts).map { it.avatar() },
        onClick = { open = true },
        subtitle =
            listOfNotNull(
                accounts.size.counted("account").takeIf {
                    chosen == null
                },
                folderCount.folders(),
            ).joinToString(" · "),
        onClickLabel = "Choose account",
    )
    if (open) {
        ChoiceSheet(
            title = "Show folders of",
            choices = ChoiceOptions(listOf(null) + accounts.map { it.accountId }, chosen?.accountId),
            row = { id -> ChoiceRow(accounts.firstOrNull { it.accountId == id }?.accountName ?: ALL_ACCOUNTS) },
            onSelect = onChoose,
            onDismiss = { open = false },
        )
    }
}

/**
 * One account's folders in All accounts, under a row with its avatar, name, and [action]; tapping
 * the row folds them away.
 */
@Composable
fun AccountFolderGroup(
    account: AccountFolders,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    action: (@Composable () -> Unit)?,
    content: @Composable () -> Unit,
) {
    AccountGroupRow(
        account.accountName,
        account.avatar(),
        expanded,
        onExpandedChange,
        action = action,
    )
    Collapsible(expanded) { Column { content() } }
}

private fun AccountFolders.avatar(): ChipAvatar =
    generatedAvatar(accountName, "").let { ChipAvatar(it.initials, it.colorIndex) }

private fun Int.folders(): String = counted("folder")

private fun Int.counted(noun: String): String = if (this == 1) "1 $noun" else "$this ${noun}s"

private const val ALL_ACCOUNTS = "All accounts"
