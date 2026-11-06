package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_swipe_left
import com.otus.designsystem.resources.symbol_swipe_right
import com.otus.designsystem.resources.symbol_view_agenda
import com.otus.designsystem.shared.selection.components.ChoiceOptions
import com.otus.designsystem.shared.selection.components.ChoiceRow
import com.otus.designsystem.shared.selection.components.ChoiceSheet
import com.otus.designsystem.shared.settings.components.SettingPageRow
import com.otus.designsystem.shared.settings.components.SettingRow
import com.otus.designsystem.shared.settings.components.SettingsSection
import com.otus.designsystem.shared.settings.elements.ValueButton
import com.otus.mail.core.messages.domain.MailAction
import com.otus.mail.core.messages.presentation.actions.label
import com.otus.mail.core.settings.domain.MailBehavior
import com.otus.mail.core.settings.presentation.MailBehaviorState
import org.jetbrains.compose.resources.DrawableResource

/**
 * The Mail behavior page's Actions section: both swipes and the action bar's page, which
 * [onOpenActionBar] opens. Each swipe opens a choice of actions; the rows name the physical
 * direction for the current layout direction.
 */
@Composable
fun ActionSettings(
    state: MailBehaviorState,
    onOpenActionBar: () -> Unit,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var choosing by rememberSaveable { mutableStateOf<SwipeSide?>(null) }
    val behavior = state.behavior
    SettingsSection("Actions") {
        SwipeSide.entries.forEach { side ->
            SettingRow(side.title(rtl), { choosing = side }, icon = side.icon(rtl)) {
                ValueButton(side.of(behavior).swipeLabel)
            }
        }
        SettingPageRow("Action bar", onOpenActionBar, icon = Res.drawable.symbol_view_agenda)
    }
    val side = choosing ?: return
    SwipeChoice(state, side, side.title(rtl)) { choosing = null }
}

@Composable
private fun SwipeChoice(
    state: MailBehaviorState,
    side: SwipeSide,
    title: String,
    onDismiss: () -> Unit,
) {
    ChoiceSheet(
        title = title,
        choices =
            ChoiceOptions(
                listOf(null) + state.behavior.swipeChoices,
                selected = side.of(state.behavior),
                default = side.default,
            ),
        row = { swipe -> ChoiceRow(swipe.swipeLabel, "Default".takeIf { swipe == side.default }) },
        onSelect = { swipe -> state.update { side.with(it, swipe) } },
        onDismiss = onDismiss,
    )
}

internal enum class SwipeSide {
    StartToEnd,
    EndToStart,
    ;

    fun title(rtl: Boolean): String = if ((this == StartToEnd) != rtl) "Swipe right" else "Swipe left"

    fun icon(rtl: Boolean): DrawableResource =
        if ((this == StartToEnd) != rtl) Res.drawable.symbol_swipe_right else Res.drawable.symbol_swipe_left

    /** What this swipe does until the user changes it. */
    val default: MailAction?
        get() = of(MailBehavior())

    fun of(behavior: MailBehavior): MailAction? =
        when (this) {
            StartToEnd -> behavior.startToEnd
            EndToStart -> behavior.endToStart
        }

    fun with(
        behavior: MailBehavior,
        swipe: MailAction?,
    ): MailBehavior =
        when (this) {
            StartToEnd -> behavior.copy(startToEnd = swipe)
            EndToStart -> behavior.copy(endToStart = swipe)
        }
}

/** A swipe's name in settings; no action is "Nothing". */
internal val MailAction?.swipeLabel: String
    get() = this?.label ?: "Nothing"
