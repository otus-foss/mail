package com.otus.mail.feature.folders.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otus.accounts.presentation.toChipAvatar
import com.otus.avatar.ResolvedAvatar
import com.otus.avatar.generatedAvatar
import com.otus.designsystem.shared.panels.components.ContextPanelScope
import com.otus.designsystem.shared.people.components.ChipAvatar
import com.otus.designsystem.shared.rows.components.AccountGroupRow
import com.otus.designsystem.shared.rows.components.Collapsible
import com.otus.designsystem.shared.selection.components.ChoiceOptions
import com.otus.designsystem.shared.selection.components.ChoiceRow
import com.otus.designsystem.shared.selection.components.ChoiceSheet
import com.otus.designsystem.shared.selection.components.PickerChip
import com.otus.mail.feature.folders.domain.AccountFolders

/**
 * Which account's folders the page shows, as a chip in its header: All accounts while [chosen] is
 * null, else one account, with their avatars. It opens a sheet with All accounts first, then each
 * account; [onChoose] gets the account id, or null for All accounts.
 */
@Composable
fun ContextPanelScope.FolderAccountPicker(
    accounts: List<AccountFolders>,
    chosen: AccountFolders?,
    onChoose: (String?) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    item("accounts") {
        PickerChip(
            avatars = (chosen?.let(::listOf) ?: accounts).map { it.avatar() },
            label = chosen?.accountName ?: ALL_ACCOUNTS,
            onClick = { open = true },
            modifier = Modifier.fillRail(),
        )
    }
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

private fun AccountFolders.avatar(): ChipAvatar = ResolvedAvatar(generatedAvatar(accountName, "")).toChipAvatar()

private const val ALL_ACCOUNTS = "All accounts"
