package com.otusfoss.mail.feature.mailsettings.presentation.components

import com.otusfoss.core.designsystem.resources.Res
import com.otusfoss.core.designsystem.resources.symbol_bolt
import com.otusfoss.core.designsystem.resources.symbol_filter_list
import com.otusfoss.core.designsystem.resources.symbol_folder
import com.otusfoss.core.designsystem.resources.symbol_folder_open
import com.otusfoss.core.designsystem.resources.symbol_label
import com.otusfoss.core.designsystem.resources.symbol_mail
import com.otusfoss.mail.core.mailbox.pathText
import com.otusfoss.mail.core.messages.domain.ActionTarget
import com.otusfoss.mail.core.messages.domain.BarButton
import com.otusfoss.mail.core.messages.domain.ListFilter
import com.otusfoss.mail.core.messages.domain.MailAction
import com.otusfoss.mail.core.messages.presentation.actions.label
import com.otusfoss.mail.core.messages.presentation.label
import org.jetbrains.compose.resources.DrawableResource

/** A folder a button can go to: its [path] from the top level down, and its icon [color] name. */
class FolderOption(
    val path: List<String>,
    val color: String?,
)

/**
 * What the buttons the user completes choose from: the accounts' own [folders], and the [filters]
 * a list can turn on and off.
 */
internal class ButtonChoices(
    val folders: List<FolderOption>,
    val filters: List<ListFilter> = emptyList(),
) {
    /** The folder [button] goes to; null when no account has it any more. */
    fun folder(button: BarButton.ForFolder): FolderOption? =
        folders.firstOrNull { folder -> folder.path.map { it.lowercase() } == button.key }
}

/**
 * A kind of button the user completes with a choice of their own, such as the folder of a Move
 * to button. [prompt] is its row in a picker, [title] names its choice, and [target] is the group
 * it belongs to.
 */
internal enum class CustomKind(
    val prompt: String,
    val title: String,
    val target: ActionTarget,
    val symbol: DrawableResource,
) {
    MoveTo("Move to folder…", "Move to", ActionTarget.Messages, Res.drawable.symbol_folder),
    LabelAs("Label as folder…", "Label as", ActionTarget.Messages, Res.drawable.symbol_label),
    Chain("Chain of actions…", "Chain", ActionTarget.Messages, Res.drawable.symbol_bolt),
    Filter("Filter…", "Filter", ActionTarget.List, Res.drawable.symbol_filter_list),
    GoTo("Go to folder…", "Go to", ActionTarget.List, Res.drawable.symbol_folder_open),
    WriteTo("Write to…", "Write to", ActionTarget.Anywhere, Res.drawable.symbol_mail),
    ;

    /** Whether the kind picks one folder, so it has nothing to offer while there are none. */
    val picksFolder: Boolean
        get() = this == MoveTo || this == LabelAs || this == GoTo
}

/** The kind of a button the user completed; null for a plain action. */
internal val BarButton.customKind: CustomKind?
    get() =
        when (this) {
            is BarButton.MoveTo -> CustomKind.MoveTo
            is BarButton.LabelAs -> CustomKind.LabelAs
            is BarButton.GoTo -> CustomKind.GoTo
            is BarButton.Filter -> CustomKind.Filter
            is BarButton.WriteTo -> CustomKind.WriteTo
            is BarButton.Chain -> CustomKind.Chain
            is MailAction -> null
        }

/**
 * The user's own choice in a button, as its picker shows it: a folder's path, a filter, the
 * addresses, or a chain's name.
 */
internal val BarButton.choice: String
    get() =
        when (this) {
            is BarButton.ForFolder -> path.pathText()
            is BarButton.Filter -> filter.label
            is BarButton.WriteTo -> addresses.joinToString(", ")
            is BarButton.Chain -> name
            is MailAction -> label
        }
