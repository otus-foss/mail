package com.otusfoss.mail.feature.notifications.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.otusfoss.core.accounts.domain.ConnectedAccount
import com.otusfoss.core.designsystem.shared.pages.components.Subpage
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceOptions
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceRow
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceSheet
import com.otusfoss.core.designsystem.shared.settings.components.SettingRow
import com.otusfoss.core.designsystem.shared.settings.components.SettingsSection
import com.otusfoss.core.designsystem.shared.settings.elements.ValueButton
import com.otusfoss.mail.feature.notifications.domain.NotificationPreviews
import com.otusfoss.mail.feature.notifications.domain.PreviewChoices
import com.otusfoss.mail.feature.notifications.presentation.NotificationSettingsState
import com.otusfoss.mail.feature.notifications.presentation.PreviewTarget
import com.otusfoss.mail.feature.notifications.presentation.addresses
import com.otusfoss.mail.feature.notifications.presentation.description
import com.otusfoss.mail.feature.notifications.presentation.short
import com.otusfoss.mail.feature.notifications.presentation.title

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
