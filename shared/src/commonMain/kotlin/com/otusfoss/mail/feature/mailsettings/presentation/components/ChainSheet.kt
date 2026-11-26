package com.otusfoss.mail.feature.mailsettings.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_drive_file_move
import com.otusfoss.core.designsystem.resources.symbol_flag
import com.otusfoss.core.designsystem.resources.symbol_folder
import com.otusfoss.core.designsystem.resources.symbol_label
import com.otusfoss.core.designsystem.resources.symbol_mark_email_read
import com.otusfoss.core.designsystem.shared.controls.elements.UiAction
import com.otusfoss.core.designsystem.shared.input.elements.InputField
import com.otusfoss.core.designsystem.shared.overlays.components.SheetFrame
import com.otusfoss.core.designsystem.shared.overlays.elements.sheetInset
import com.otusfoss.core.designsystem.shared.rows.components.RowDensity
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceOptions
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceRow
import com.otusfoss.core.designsystem.shared.selection.components.ChoiceSheet
import com.otusfoss.core.designsystem.shared.selection.components.ValuePicker
import com.otusfoss.core.designsystem.shared.settings.components.SettingRow
import com.otusfoss.core.designsystem.shared.settings.components.SettingToggleRow
import com.otusfoss.core.designsystem.theme.colors.FolderColor
import com.otusfoss.core.designsystem.theme.tokens.overlays.SheetTokens
import com.otusfoss.mail.core.mailbox.pathText
import com.otusfoss.mail.core.messages.domain.BarButton
import com.otusfoss.mail.core.messages.domain.MailAction
import com.otusfoss.mail.core.messages.domain.filesInChain
import com.otusfoss.mail.core.messages.domain.isChain
import com.otusfoss.mail.core.messages.presentation.actions.label

/**
 * A chain being built: its [name], whether it marks the messages [read] and [flag]s them, the
 * [label] it adds, and the step that files them [last]. It starts as [chain], or empty.
 */
private class ChainDraft(
    chain: BarButton.Chain?,
) {
    private val saved = chain?.steps.orEmpty()
    var name by mutableStateOf(chain?.name.orEmpty())
    var read by mutableStateOf(MailAction.Read in saved)
    var flag by mutableStateOf(MailAction.Flag in saved)
    var label by mutableStateOf(saved.firstNotNullOfOrNull { it as? BarButton.LabelAs })
    var last by mutableStateOf(saved.lastOrNull { it.filesInChain })

    /** The chain as built so far; null until it has a name and a step. */
    val chain: BarButton.Chain?
        get() =
            listOfNotNull(MailAction.Read.takeIf { read }, MailAction.Flag.takeIf { flag }, label, last)
                .takeIf { name.isNotBlank() && it.isChain() }
                ?.let { BarButton.Chain(name.trim(), it) }
}

/** The step of a chain a sheet is picking. */
private enum class ChainPick { Label, Last }

/**
 * Builds a chain, starting from [current]: its name, what it marks, the label it adds from
 * [folders], and how it files the messages at the end. Save runs [onPick] with the chain once it
 * has a name and at least one step.
 */
@Composable
internal fun ChainSheet(
    current: BarButton.Chain?,
    folders: List<FolderOption>,
    onPick: (BarButton) -> Unit,
    onDismiss: () -> Unit,
) {
    val draft = remember { ChainDraft(current) }
    var picking by remember { mutableStateOf<ChainPick?>(null) }
    val chain = draft.chain
    SheetFrame(
        CustomKind.Chain.title,
        onDismiss,
        done =
            UiAction("Save", enabled = chain != null, onClick = {
                chain?.let(onPick)
                onDismiss()
            }),
    ) {
        Column(Modifier.sheetInset(), verticalArrangement = Arrangement.spacedBy(SheetTokens.gap)) {
            InputField("Name", draft.name, { draft.name = it }, placeholder = "File receipt")
            ChainSteps(draft) { picking = it }
        }
    }
    when (picking) {
        ChainPick.Label -> LabelStepSheet(draft, folders) { picking = null }
        ChainPick.Last -> LastStepSheet(draft, folders) { picking = null }
        null -> Unit
    }
}

/** The chain's steps in the order they run; the label and the last step open their choices through [onPick]. */
@Composable
private fun ChainSteps(
    draft: ChainDraft,
    onPick: (ChainPick) -> Unit,
) {
    Column {
        SettingToggleRow("Mark read", draft.read, { draft.read = it }, icon = Res.drawable.symbol_mark_email_read)
        SettingToggleRow("Flag", draft.flag, { draft.flag = it }, icon = Res.drawable.symbol_flag)
        SettingRow("Label as", onClick = null, icon = Res.drawable.symbol_label, density = RowDensity.Compact) {
            ValuePicker(draft.label?.path?.pathText() ?: NO_LABEL, { onPick(ChainPick.Label) })
        }
        SettingRow("Then", onClick = null, icon = Res.drawable.symbol_drive_file_move, density = RowDensity.Compact) {
            ValuePicker(draft.last?.label ?: NO_LAST_STEP, { onPick(ChainPick.Last) })
        }
    }
}

@Composable
private fun LabelStepSheet(
    draft: ChainDraft,
    folders: List<FolderOption>,
    onDismiss: () -> Unit,
) {
    ChoiceSheet(
        "Label as",
        ChoiceOptions(listOf(null) + folders.map { BarButton.LabelAs(it.path) }, draft.label),
        row = { it?.folderRow(folders) ?: ChoiceRow(NO_LABEL) },
        onSelect = {
            draft.label = it
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

/** Nothing, the three folders every account has, then a move to each of the user's own [folders]. */
@Composable
private fun LastStepSheet(
    draft: ChainDraft,
    folders: List<FolderOption>,
    onDismiss: () -> Unit,
) {
    val filing = listOf<BarButton?>(null, MailAction.Archive, MailAction.Trash, MailAction.Spam)
    ChoiceSheet(
        "Then",
        ChoiceOptions(filing + folders.map { BarButton.MoveTo(it.path) }, draft.last),
        row = { step -> (step as? BarButton.MoveTo)?.folderRow(folders) ?: ChoiceRow(step?.label ?: NO_LAST_STEP) },
        onSelect = {
            draft.last = it
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

/** A folder step's row, named as the step reads, with its icon in the folder's color among [folders]. */
private fun BarButton.ForFolder.folderRow(folders: List<FolderOption>): ChoiceRow {
    val color = ButtonChoices(folders).folder(this)?.color
    return ChoiceRow(label, icon = Res.drawable.symbol_folder, iconTint = FolderColor.named(color).tint)
}

private const val NO_LABEL = "None"
private const val NO_LAST_STEP = "Nothing"
