package com.otusfoss.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.otusfoss.core.designsystem.shared.overlays.elements.sheetInset
import com.otusfoss.core.designsystem.shared.pages.components.belowControlPanel
import com.otusfoss.core.designsystem.shared.panels.components.AccountStack
import com.otusfoss.core.designsystem.shared.panels.components.PanelSheet
import com.otusfoss.core.designsystem.shared.panels.components.PanelSheetHeader
import com.otusfoss.core.designsystem.shared.panels.elements.PanelNavigation
import com.otusfoss.core.designsystem.shared.panels.elements.PanelTitle
import com.otusfoss.core.designsystem.theme.colors.OtusColors
import com.otusfoss.core.designsystem.theme.tokens.core.Spacing
import com.otusfoss.core.designsystem.theme.tokens.layout.PanelTokens
import com.otusfoss.core.designsystem.theme.tokens.selection.FilterTokens
import com.otusfoss.core.designsystem.theme.typography.TypeScale
import com.otusfoss.mail.core.messages.domain.MailScopeChoice
import com.otusfoss.mail.core.messages.presentation.components.MailScopeChoices
import com.otusfoss.mail.core.messages.presentation.components.MailScopeSwitch
import com.otusfoss.mail.core.messages.presentation.label
import com.otusfoss.mail.core.settings.presentation.label

/**
 * The account switcher, unfolded from the inbox's control panel [collapsedHeight] tall: All mail,
 * the pinned addresses, and each account with its addresses. A choice applies at once; All mail
 * or an address also closes the panel, while an account keeps it open, since its row also opens
 * its addresses. Back, the chevron, the stack, a tap outside, or a swipe up closes it.
 */
@Composable
internal fun InboxAccountSwitcher(
    scope: MailScopeSwitch,
    pageTitle: String,
    open: Boolean,
    collapsedHeight: Dp,
    onDismiss: () -> Unit,
) {
    val header = scope.header
    PanelSheet(
        open,
        collapsedHeight,
        onDismiss,
        header = {
            PanelSheetHeader(
                navigation = { PanelNavigation("Close account switcher", onDismiss) },
                modifier = Modifier.semantics { paneTitle = "Show mail from" },
                actions = { AccountStack(header.avatars, "Close account switcher", onDismiss) },
            ) {
                Column(Modifier.padding(start = PanelTokens.titleStart)) {
                    PanelTitle(pageTitle)
                    Text(header.title, color = OtusColors.text2, fontSize = TypeScale.label)
                }
            }
        },
        Modifier
            .belowControlPanel()
            .padding(bottom = FilterTokens.screenInset),
    ) {
        Column(
            Modifier
                .weight(
                    1f,
                    fill = false,
                ).verticalScroll(rememberScrollState())
                .sheetInset(),
            verticalArrangement = Arrangement.spacedBy(Spacing.iconGap),
        ) { MailScopeChoices(scope.closingOnPick(onDismiss)) }
    }
}

/** This switch, closing the switcher through [onDismiss] once a choice that is no account is made. */
private fun MailScopeSwitch.closingOnPick(onDismiss: () -> Unit): MailScopeSwitch =
    MailScopeSwitch(accounts, chosen, pins, count, { choice ->
        onChoose(choice)
        if (choice !is MailScopeChoice.Account) onDismiss()
    }, onTogglePin)
