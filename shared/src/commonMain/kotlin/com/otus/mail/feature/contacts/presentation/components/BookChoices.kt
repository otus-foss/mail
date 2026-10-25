package com.otus.mail.feature.contacts.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.otus.designsystem.components.PickMark
import com.otus.designsystem.components.PickRow
import com.otus.designsystem.components.RowDensity
import com.otus.designsystem.components.RowGroup
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.PageTokens
import com.otus.designsystem.theme.SheetTokens
import com.otus.mail.feature.contacts.domain.BookKey
import com.otus.mail.feature.contacts.domain.ContactBook
import com.otus.mail.feature.contacts.presentation.owner

/**
 * Address books to pick one from: per account or sharer, a small `text2` label over a card of its
 * books, so each row names only the book and books of the same name in two accounts stay apart.
 */
@Composable
internal fun BookChoices(
    books: List<ContactBook>,
    selected: BookKey?,
    onSelect: (ContactBook) -> Unit,
) {
    books.groupBy { it.owner() }.forEach { (owner, owned) ->
        Column {
            Text(
                owner,
                Modifier.padding(start = SheetTokens.inset, end = SheetTokens.inset, bottom = PageTokens.rowGap),
                color = OtusColors.text2,
                fontSize = SheetTokens.detailSize,
                maxLines = 1,
            )
            RowGroup(
                modifier = Modifier.padding(horizontal = SheetTokens.inset),
                rows =
                    owned.map { book ->
                        {
                            PickRow(
                                book.name,
                                null,
                                PickMark(book.key == selected),
                                { onSelect(book) },
                                density = RowDensity.Compact,
                            )
                        }
                    },
            )
        }
    }
}

/** A sheet section's heading: larger and bolder than the labels inside it, with room above it. */
@Composable
internal fun ChoiceHeading(text: String) {
    Text(
        text,
        Modifier.padding(start = SheetTokens.inset, end = SheetTokens.inset, top = PageTokens.rowGap),
        fontWeight = FontWeight.SemiBold,
    )
}
