package com.otus.mail.feature.accounts.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.PageNote
import com.otus.designsystem.components.PageSection
import com.otus.designsystem.components.Subpage
import com.otus.designsystem.elements.ActionButton
import com.otus.designsystem.elements.InputField
import com.otus.designsystem.elements.InputKind
import com.otus.designsystem.theme.PageTokens
import com.otus.mail.feature.accounts.domain.ConnectFailure
import com.otus.mail.feature.accounts.domain.JmapAccountDraft
import com.otus.mail.feature.accounts.domain.JmapServerAddress
import com.otus.mail.feature.accounts.domain.isValidUsername
import com.otus.mail.feature.accounts.domain.parseJmapServer
import com.otus.mail.feature.accounts.presentation.AccountsState
import com.otus.mail.feature.accounts.presentation.ConnectStatus
import com.otus.mail.feature.accounts.presentation.components.ConnectedAccountRow
import com.otus.mail.feature.accounts.presentation.confirmHostMessage
import com.otus.mail.feature.accounts.presentation.message

/** Connected JMAP accounts, and a form that verifies a new one before saving it. */
@Composable
fun AccountSetupScreen(
    state: AccountsState,
    onBack: () -> Unit,
) {
    Subpage("Email accounts", onBack) {
        state.storageError?.let {
            PageNote(it)
            ActionButton("Reload accounts", state::reload)
        }
        if (state.accounts.isEmpty()) {
            PageNote("No accounts connected yet.")
        } else {
            PageSection("Connected") {
                state.accounts.forEach { account -> ConnectedAccountRow(account) { state.remove(account.id) } }
            }
        }
        PageSection("Add a JMAP account") { AddAccountForm(state) }
    }
}

@Composable
private fun AddAccountForm(state: AccountsState) {
    var server by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    // Kept out of saved instance state on purpose.
    var password by remember { mutableStateOf("") }
    val address = parseJmapServer(server)
    val status = state.status
    LaunchedEffect(status) {
        if (status is ConnectStatus.Connected) {
            server = ""
            username = ""
            password = ""
        }
    }
    PageNote("Works with any JMAP server, such as Stalwart. Use an app password if your server offers one.")
    InputField("Server", server, {
        server = it
    }, placeholder = "mail.example.com", supportingText = serverHint(address), kind = InputKind.Url)
    InputField(
        "Username",
        username,
        { username = it },
        placeholder = "you@example.com",
        supportingText = ConnectFailure.InvalidUsername.message().takeIf { ':' in username },
        kind = InputKind.Email,
    )
    InputField("Password", password, { password = it }, kind = InputKind.Password)
    val connecting = status == ConnectStatus.Connecting
    val ready = address is JmapServerAddress.Valid && isValidUsername(username.trim()) && password.isNotEmpty()
    ActionButton(
        if (connecting) "Connecting…" else "Connect",
        {
            if (address is JmapServerAddress.Valid) {
                state.connect(
                    JmapAccountDraft(address.sessionUrl, username.trim(), password),
                )
            }
        },
        Modifier.fillMaxWidth(),
        primary = true,
        enabled = ready && !connecting,
    )
    if (status is ConnectStatus.ConfirmHost) {
        ConfirmHostPrompt(status.host, state)
    } else {
        statusText(status)?.let {
            PageNote(it)
        }
    }
}

@Composable
private fun ConfirmHostPrompt(
    host: String,
    state: AccountsState,
) {
    PageNote(confirmHostMessage(host))
    Row(horizontalArrangement = Arrangement.spacedBy(PageTokens.rowGap)) {
        ActionButton("Cancel", state::cancelHost, Modifier.weight(1f))
        ActionButton("Trust $host", state::trustHost, Modifier.weight(1f), primary = true)
    }
}

private fun statusText(status: ConnectStatus): String? =
    when (status) {
        ConnectStatus.Idle, ConnectStatus.Connecting -> null
        is ConnectStatus.Connected -> "Connected ${status.name}."
        is ConnectStatus.Failed -> status.reason.message()
        is ConnectStatus.ConfirmHost -> confirmHostMessage(status.host)
    }

private fun serverHint(address: JmapServerAddress): String =
    when (address) {
        JmapServerAddress.Empty -> "Your provider's host name, or its full JMAP session URL."
        JmapServerAddress.Insecure -> "JMAP requires a secure connection. Use https://."
        JmapServerAddress.Invalid -> "This doesn't look like a server address."
        is JmapServerAddress.Valid -> "Connects to ${address.sessionUrl}"
    }
