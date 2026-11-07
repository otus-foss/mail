package com.otus.mail.feature.mailsettings.presentation.components

import androidx.compose.runtime.Composable
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_swipe_left
import com.otus.designsystem.resources.symbol_swipe_right
import com.otus.designsystem.resources.symbol_view_agenda
import com.otus.designsystem.shared.selection.components.ChoiceOptions
import com.otus.designsystem.shared.selection.components.ChoiceRow
import com.otus.designsystem.shared.selection.components.ChoiceSheet
import com.otus.mail.core.messages.domain.MailAction
import com.otus.mail.core.messages.presentation.actions.label
import com.otus.mail.core.settings.domain.ActionBehavior
import com.otus.mail.core.settings.presentation.MailBehaviorState
import com.otus.mail.core.settings.presentation.icon
import com.otus.mail.core.settings.presentation.label
import com.otus.settings.presentation.components.ChoiceRowSpec
import com.otus.settings.presentation.components.PageRowSpec
import com.otus.settings.presentation.components.SettingRowSpec
import org.jetbrains.compose.resources.DrawableResource

/**
 * The Mail behavior page's Actions rows: both swipes and the action bar's page, which
 * [openActionBar] opens. Each swipe opens a choice of actions; the rows name the physical
 * direction, which [rtl] flips.
 */
internal fun actionRows(
    state: MailBehaviorState,
    rtl: Boolean,
    openActionBar: (setting: String?) -> Unit,
): List<SettingRowSpec> {
    val swipes = (listOf(null) + state.behavior.actions.swipeChoices(state.behavior.readLater)).map { it.swipeLabel }
    return SwipeSide.entries.map { swipeRow(state, it, rtl, swipes) } +
        PageRowSpec(
            "Action bar",
            openActionBar,
            icon = Res.drawable.symbol_view_agenda,
            keywords = listOf("Toolbar", "Buttons"),
        ) { actionBarSettings(state) }
}

/** The row for [side]'s swipe, which offers [swipes]. */
private fun swipeRow(
    state: MailBehaviorState,
    side: SwipeSide,
    rtl: Boolean,
    swipes: List<String>,
): SettingRowSpec =
    ChoiceRowSpec(
        side.title(rtl),
        side.of(state.behavior.actions).swipeLabel,
        { SwipeChoice(state, side, side.title(rtl), it) },
        swipes,
        side.icon(rtl),
        keywords = listOf("Gestures"),
    )

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
                listOf(null) + state.behavior.actions.swipeChoices(state.behavior.readLater),
                selected = side.of(state.behavior.actions),
                default = side.default,
            ),
        row = { swipe -> ChoiceRow(swipe.swipeLabel, "Default".takeIf { swipe == side.default }) },
        onSelect = { swipe -> state.updateActions { side.with(it, swipe) } },
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
        get() = of(ActionBehavior())

    fun of(actions: ActionBehavior): MailAction? =
        when (this) {
            StartToEnd -> actions.startToEnd
            EndToStart -> actions.endToStart
        }

    fun with(
        actions: ActionBehavior,
        swipe: MailAction?,
    ): ActionBehavior =
        when (this) {
            StartToEnd -> actions.copy(startToEnd = swipe)
            EndToStart -> actions.copy(endToStart = swipe)
        }
}

/** A swipe's name in settings; no action is "Nothing". */
internal val MailAction?.swipeLabel: String
    get() = this?.label ?: "Nothing"
