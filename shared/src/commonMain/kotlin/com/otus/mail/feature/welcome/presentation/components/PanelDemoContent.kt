package com.otus.mail.feature.welcome.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import com.otus.designsystem.elements.SearchField
import com.otus.mail.designsystem.resources.Res
import com.otus.mail.designsystem.resources.symbol_alternate_email
import com.otus.mail.designsystem.resources.symbol_attach_file
import com.otus.mail.designsystem.resources.symbol_calendar_add_on
import com.otus.mail.designsystem.resources.symbol_inbox
import com.otus.mail.designsystem.resources.symbol_notifications
import com.otus.mail.designsystem.resources.symbol_person
import org.jetbrains.compose.resources.DrawableResource

enum class PanelDemoMode { Inbox, Search, Metadata }

data class DemoChip(
    val label: String,
    val icon: DrawableResource,
)

fun demoChips(mode: PanelDemoMode): List<DemoChip> =
    when (mode) {
        PanelDemoMode.Inbox -> {
            listOf(
                DemoChip("Unread", Res.drawable.symbol_notifications),
                DemoChip("Updates", Res.drawable.symbol_notifications),
                DemoChip("Personal", Res.drawable.symbol_person),
                DemoChip("A much longer label", Res.drawable.symbol_inbox),
            )
        }

        PanelDemoMode.Search -> {
            listOf(
                DemoChip("People", Res.drawable.symbol_person),
                DemoChip("Date", Res.drawable.symbol_calendar_add_on),
                DemoChip("Inboxes", Res.drawable.symbol_inbox),
                DemoChip("Mails", Res.drawable.symbol_alternate_email),
            )
        }

        PanelDemoMode.Metadata -> {
            listOf(
                DemoChip("2 attachments", Res.drawable.symbol_attach_file),
                DemoChip("Work", Res.drawable.symbol_inbox),
                DemoChip("Today", Res.drawable.symbol_calendar_add_on),
            )
        }
    }

@Composable
fun PanelDemoContent(
    mode: PanelDemoMode,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    when (mode) {
        PanelDemoMode.Inbox -> {
            Text("Inbox")
        }

        PanelDemoMode.Search -> {
            SearchField(query, onQueryChange, "Search your mailbox…", onSearch = onSearch)
        }

        PanelDemoMode.Metadata -> {
            Column {
                Text("Design review")
                Text("Alex · alex@example.com")
            }
        }
    }
}
