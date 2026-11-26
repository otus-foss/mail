package com.otusfoss.mail.feature.mailsettings.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_swipe_left
import com.otusfoss.core.designsystem.resources.symbol_swipe_right
import com.otusfoss.core.designsystem.resources.symbol_view_agenda
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceOptions
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceRow
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceSheet
import com.otusfoss.core.settings.presentation.components.ChoiceRowSpec
import com.otusfoss.core.settings.presentation.components.PageRowSpec
import com.otusfoss.core.settings.presentation.components.SettingRowSpec
import com.otusfoss.mail.core.messages.domain.BarButton
import com.otusfoss.mail.core.messages.presentation.actions.label
import com.otusfoss.mail.core.settings.domain.ActionBehavior
import com.otusfoss.mail.core.settings.presentation.MailBehaviorState
import com.otusfoss.mail.core.settings.presentation.icon
import com.otusfoss.mail.core.settings.presentation.label
import org.jetbrains.compose.resources.DrawableResource

/**
 * The Mail behavior page's Actions rows: both swipes and the action bar's page, which
 * [openActionBar] opens. Each swipe opens a choice of actions, where Move to and Label as pick one
 * of [folders]; the rows name the physical direction, which [rtl] flips.
 */
internal fun actionRows(
    state: MailBehaviorState,
    rtl: Boolean,
    folders: List<FolderOption>,
    openActionBar: (setting: String?) -> Unit,
): List<SettingRowSpec> =
    SwipeSide.entries.map { swipeRow(state, it, rtl, folders) } +
        PageRowSpec(
            "Action bar",
            openActionBar,
            icon = Res.drawable.symbol_view_agenda,
            keywords = listOf("Toolbar", "Buttons"),
        ) { actionBarSettings(state) }

/** The row for [side]'s swipe. */
private fun swipeRow(
    state: MailBehaviorState,
    side: SwipeSide,
    rtl: Boolean,
    folders: List<FolderOption>,
): SettingRowSpec =
    ChoiceRowSpec(
        side.title(rtl),
        side.of(state.behavior.actions).swipeLabel,
        { SwipeChoice(state, side, side.title(rtl), folders, it) },
        state.swipeOptions(side, folders).map { it.label },
        side.icon(rtl),
        keywords = listOf("Gestures"),
    )

/** A row of a swipe's choices: [Fixed] is nothing or a plain action, and [Custom] opens a kind's sheet. */
private sealed interface SwipeOption {
    val label: String

    data class Fixed(
        val button: BarButton?,
    ) : SwipeOption {
        override val label: String
            get() = button.swipeLabel
    }

    /** The row of [kind], which names the swipe's own button, [current], once the swipe is of that kind. */
    data class Custom(
        val kind: CustomKind,
        val current: BarButton?,
    ) : SwipeOption {
        override val label: String
            get() = current?.label ?: kind.prompt
    }
}

/** The kinds of button a swipe can be besides a plain action. */
private val SWIPE_KINDS = listOf(CustomKind.MoveTo, CustomKind.LabelAs, CustomKind.Chain)

/**
 * What [side]'s swipe can be: nothing, each plain action, then a row per kind. A kind that picks
 * one of [folders] shows only while there are folders, or while the swipe is of that kind.
 */
private fun MailBehaviorState.swipeOptions(
    side: SwipeSide,
    folders: List<FolderOption>,
): List<SwipeOption> {
    val current = side.of(behavior.actions)
    val kinds = SWIPE_KINDS.filter { !it.picksFolder || folders.isNotEmpty() || current?.customKind == it }
    return (listOf(null) + behavior.actions.swipeChoices(behavior.readLater)).map(SwipeOption::Fixed) +
        kinds.map { kind -> SwipeOption.Custom(kind, current?.takeIf { it.customKind == kind }) }
}

@Composable
private fun SwipeChoice(
    state: MailBehaviorState,
    side: SwipeSide,
    title: String,
    folders: List<FolderOption>,
    onDismiss: () -> Unit,
) {
    var choosing by rememberSaveable { mutableStateOf<CustomKind?>(null) }
    val current = side.of(state.behavior.actions)
    val options = state.swipeOptions(side, folders)
    val default = SwipeOption.Fixed(side.default)
    ChoiceSheet(
        title = title,
        choices = ChoiceOptions(options, options.firstOrNull { it.holds(current) } ?: default, default),
        row = { option -> ChoiceRow(option.label, "Default".takeIf { option == default }) },
        onSelect = { option ->
            when (option) {
                is SwipeOption.Fixed -> state.updateActions { side.with(it, option.button) }
                is SwipeOption.Custom -> choosing = option.kind
            }
        },
        onDismiss = onDismiss,
    )
    choosing?.let { kind ->
        CustomButtonSheet(
            kind,
            current?.takeIf { it.customKind == kind },
            ButtonChoices(folders),
            onPick = { picked -> state.updateActions { side.with(it, picked) } },
            onDismiss = { choosing = null },
        )
    }
}

/** Whether this row stands for the swipe [button]: its kind's row for a button the user completed. */
private fun SwipeOption.holds(button: BarButton?): Boolean =
    when (this) {
        is SwipeOption.Fixed -> this.button == button
        is SwipeOption.Custom -> current != null
    }

internal enum class SwipeSide {
    StartToEnd,
    EndToStart,
    ;

    fun title(rtl: Boolean): String = if ((this == StartToEnd) != rtl) "Swipe right" else "Swipe left"

    fun icon(rtl: Boolean): DrawableResource =
        if ((this == StartToEnd) != rtl) Res.drawable.symbol_swipe_right else Res.drawable.symbol_swipe_left

    /** What this swipe does until the user changes it. */
    val default: BarButton?
        get() = of(ActionBehavior())

    fun of(actions: ActionBehavior): BarButton? =
        when (this) {
            StartToEnd -> actions.startToEnd
            EndToStart -> actions.endToStart
        }

    fun with(
        actions: ActionBehavior,
        swipe: BarButton?,
    ): ActionBehavior =
        when (this) {
            StartToEnd -> actions.copy(startToEnd = swipe)
            EndToStart -> actions.copy(endToStart = swipe)
        }
}

/** A swipe's name in settings; no action is "Nothing". */
internal val BarButton?.swipeLabel: String
    get() = this?.label ?: "Nothing"
