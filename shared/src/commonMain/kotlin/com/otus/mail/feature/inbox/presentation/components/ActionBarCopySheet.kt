package com.otus.mail.feature.inbox.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otus.designsystem.shared.overlays.components.SheetConfirm
import com.otus.designsystem.shared.overlays.components.SheetFrame
import com.otus.designsystem.shared.selection.components.PickMark
import com.otus.designsystem.shared.selection.components.PickRow
import com.otus.designsystem.shared.selection.components.PickStyle
import com.otus.designsystem.shared.settings.components.PreviewCard
import com.otus.designsystem.theme.tokens.overlays.SheetTokens
import com.otus.mail.feature.inbox.domain.ActionBarLayout
import com.otus.mail.feature.inbox.domain.ActionBarMode
import com.otus.mail.feature.inbox.domain.ActionBarPlace
import com.otus.mail.feature.inbox.domain.MailBehavior
import com.otus.mail.feature.inbox.presentation.MailBehaviorState

/**
 * Copies [from]'s bar to the screens the user ticks, each getting what it can offer. Done replaces
 * their bars; closing any other way changes nothing. Screens that can offer none of the buttons
 * are named in a note instead of listed.
 */
@Composable
internal fun ActionBarCopySheet(
    state: MailBehaviorState,
    from: ActionBarPlace,
    onDismiss: () -> Unit,
) {
    var ticked by rememberSaveable { mutableStateOf(emptyList<ActionBarPlace>()) }
    val behavior = state.behavior
    val source = behavior.savedLayout(from)
    val (targets, unusable) =
        (ActionBarPlace.entries - from).partition { behavior.copiedBar(from, it).shares(source) }
    SheetFrame(
        "Copy to",
        onDismiss,
        done =
            SheetConfirm("Done", {
                state.update { now ->
                    now.copy(actionBar = now.actionBar + ticked.associateWith { now.copiedBar(from, it) })
                }
                onDismiss()
            }),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = SheetTokens.inset),
            verticalArrangement = Arrangement.spacedBy(SheetTokens.gap),
        ) {
            SheetCaption("The ${from.title} bar", heading = false)
            PreviewCard(from.marker) { ActionBarPreview(source) }
            targets.forEach { place ->
                PickRow(
                    place.title,
                    behavior.copySummary(from, place),
                    PickMark(place in ticked, multiple = true, style = PickStyle.Card),
                    { ticked = ticked.toggled(place) },
                )
            }
            if (unusable.isNotEmpty()) {
                SheetCaption("${unusable.joinToString(" and ") { it.title }} can't use these buttons.", heading = false)
            }
            SheetCaption("Replaces the bars you tick.", heading = false)
        }
    }
}

/** Whether this copy keeps any of [source]'s buttons. */
private fun ActionBarLayout.shares(source: ActionBarLayout): Boolean =
    primary == source.primary || secondary.isNotEmpty()

/** What [to] gets from [from]'s bar, and why its main button differs when it does. */
private fun MailBehavior.copySummary(
    from: ActionBarPlace,
    to: ActionBarPlace,
): String {
    val source = savedLayout(from)
    val copy = copiedBar(from, to)
    val buttons = listOf(copy.secondary.joinToString { it.label }, copy.primary.label).filter { it.isNotEmpty() }
    return buildList {
        add(if (copy == source) "An exact copy." else buttons.joinToString(" · ") + ".")
        if (copy.primary != source.primary) {
            add("${source.primary.label} isn't offered there, so ${copy.primary.label} stays the main button.")
        }
        if (to == ActionBarPlace.Everywhere && actionBarMode == ActionBarMode.Adaptive) {
            add("Used when Layout is Same everywhere.")
        }
    }.joinToString(" ")
}
