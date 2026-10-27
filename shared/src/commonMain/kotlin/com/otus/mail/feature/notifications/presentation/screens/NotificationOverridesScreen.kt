package com.otus.mail.feature.notifications.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.otus.accounts.domain.ConnectedAccount
import com.otus.designsystem.components.ChoiceOptions
import com.otus.designsystem.components.ChoiceRow
import com.otus.designsystem.components.ChoiceSheet
import com.otus.designsystem.components.SettingRow
import com.otus.designsystem.components.SettingsSection
import com.otus.designsystem.components.Subpage
import com.otus.designsystem.elements.ValueButton
import com.otus.mail.feature.notifications.domain.NotificationPreviews
import com.otus.mail.feature.notifications.domain.PreviewChoices
import com.otus.mail.feature.notifications.presentation.NotificationSettingsState
import com.otus.mail.feature.notifications.presentation.PreviewTarget
import com.otus.mail.feature.notifications.presentation.addresses
import com.otus.mail.feature.notifications.presentation.description
import com.otus.mail.feature.notifications.presentation.short
import com.otus.mail.feature.notifications.presentation.title

/**
 * Previews per account and per address, each following the setting for all mail until the user
 * gives it its own. A row says "Same" while it follows, and names its own choice once it has one.
 */
@Composable
fun NotificationOverridesScreen(
    state: NotificationSettingsState,
    accounts: List<ConnectedAccount>,
    onBack: () -> Unit,
) {
    var editing by remember { mutableStateOf<PreviewTarget?>(null) }
    val previews = state.previews ?: PreviewChoices()
    Subpage("Accounts and addresses", onBack) {
        accounts.forEach { account ->
            SettingsSection(account.name) {
                SettingRow(
                    "All mail of this account",
                    { editing = PreviewTarget.Login(account.id, account.name) },
                    description = previews.forLogin(account.id).short,
                ) { ValueButton(previews.logins[account.id]?.short ?: "Same") }
                account.addresses.forEach { address ->
                    val own = previews.forAddress(account.id, address)
                    SettingRow(
                        address,
                        { editing = PreviewTarget.Address(address, account.id, account.name) },
                        description = (own ?: previews.forLogin(account.id)).short,
                    ) { ValueButton(own?.short ?: "Same") }
                }
            }
        }
    }
    editing?.let { target ->
        val inherited = if (target is PreviewTarget.Address) previews.forLogin(target.loginId) else previews.all
        ChoiceSheet(
            target.title,
            ChoiceOptions(
                listOf(null) + NotificationPreviews.entries,
                selected = target.current(previews),
                default = null,
            ),
            row = {
                it?.let { choice -> ChoiceRow(choice.title, choice.description) }
                    ?: ChoiceRow(target.same, "Currently ${inherited.short}")
            },
            onSelect = { choice ->
                state.choosePreviews { target.apply(it, choice) }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}
