package com.otusfoss.mail.feature.onboarding.presentation.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.otusfoss.core.accounts.domain.AccountService
import com.otusfoss.core.accounts.presentation.AccountsState
import com.otusfoss.core.accounts.presentation.ConnectStatus
import com.otusfoss.core.accounts.presentation.components.ConnectAccountFields
import com.otusfoss.core.accounts.presentation.components.ConnectButton
import com.otusfoss.core.accounts.presentation.components.rememberConnectDraft
import com.otusfoss.core.designsystem.shared.controls.elements.ActionButton
import com.otusfoss.core.designsystem.shared.controls.elements.TextLink
import com.otusfoss.core.designsystem.shared.input.elements.isKeyboardUp
import com.otusfoss.core.designsystem.shared.onboarding.components.OnboardingStepBody

/**
 * Signs in to the account, which the first run cannot go on without. A successful sign-in moves
 * on by itself; coming back here with an account connected offers Continue as well. The link
 * that says which servers work hides while the keyboard is up.
 */
@Composable
internal fun ColumnScope.ConnectStep(
    accounts: AccountsState,
    service: AccountService,
    onNext: () -> Unit,
) {
    var providers by rememberSaveable { mutableStateOf(false) }
    val draft = rememberConnectDraft()
    val typing = isKeyboardUp()
    LaunchedEffect(accounts.status) {
        if (accounts.status is ConnectStatus.Connected) onNext()
    }
    OnboardingStepBody(
        "Connect your account",
        footer = {
            ConnectButton(draft, accounts)
            if (accounts.accounts.isNotEmpty()) ActionButton("Continue", onNext, Modifier.fillMaxWidth())
            // While typing, the footer holds only the button, so it does not crowd the fields.
            if (!typing) TextLink(PROVIDERS_LABEL, { providers = true }, Modifier.align(Alignment.CenterHorizontally))
        },
    ) {
        ConnectAccountFields(draft, accounts, service)
    }
    if (providers) ProvidersSheet { providers = false }
}

/**
 * Picks how new mail reaches the device in [delivery], then asks for the permission to show
 * alerts through [onAllow], where the device needs asking.
 */
@Composable
internal fun ColumnScope.NotificationsStep(
    delivery: @Composable () -> Unit,
    onAllow: (() -> Unit)?,
    onNext: () -> Unit,
) {
    OnboardingStepBody(
        "Get mail as it arrives",
        footer = {
            if (onAllow == null) {
                ActionButton("Continue", onNext, Modifier.fillMaxWidth(), primary = true)
            } else {
                ActionButton(
                    "Allow notifications",
                    {
                        onAllow()
                        onNext()
                    },
                    Modifier.fillMaxWidth(),
                    primary = true,
                )
                TextLink("Not now", onNext, Modifier.align(Alignment.CenterHorizontally))
            }
        },
    ) {
        delivery()
    }
}
