package com.otusfoss.mail.feature.onboarding.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_arrow_outward
import com.otusfoss.core.designsystem.shared.controls.elements.Symbol
import com.otusfoss.core.designsystem.shared.overlays.components.SheetFrame
import com.otusfoss.core.designsystem.shared.pages.components.PageNote
import com.otusfoss.core.designsystem.shared.settings.components.SettingRow
import com.otusfoss.core.designsystem.theme.tokens.layout.PageTokens
import com.otusfoss.core.designsystem.theme.tokens.overlays.SheetTokens

/** The label of the link that opens [ProvidersSheet], the same on every step that has it. */
internal const val PROVIDERS_LABEL = "Which providers work?"

/** A kind of server Otus connects to, how well it is tested, and the page that says more about it. */
private class Provider(
    val name: String,
    val tested: String,
    val url: String,
)

private val providers =
    listOf(
        Provider("Stalwart", "Thoroughly tested", "https://stalw.art"),
        Provider("Any other JMAP server", "Should work, not thoroughly tested", "https://jmap.io/software.html"),
    )

/**
 * The servers Otus connects to, each a row that opens its page in the browser, so nobody types a
 * Gmail address into the sign-in and wonders. The line below says where IMAP and SMTP stand.
 */
@Composable
internal fun ProvidersSheet(onDismiss: () -> Unit) {
    val links = LocalUriHandler.current
    SheetFrame(PROVIDERS_LABEL, onDismiss = onDismiss) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(SheetTokens.inset),
            verticalArrangement = Arrangement.spacedBy(PageTokens.rowGap),
        ) {
            providers.forEach { provider ->
                SettingRow(provider.name, { links.openUri(provider.url) }, description = provider.tested) {
                    Symbol(Res.drawable.symbol_arrow_outward)
                }
            }
            // The note is about something else than the rows, so it stands apart from them.
            Spacer(Modifier.height(SheetTokens.inset))
            PageNote("IMAP and SMTP support is on the roadmap, but will always come second.")
        }
    }
}
