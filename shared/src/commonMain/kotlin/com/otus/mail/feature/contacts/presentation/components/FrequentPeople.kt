package com.otus.mail.feature.contacts.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.otus.designsystem.components.PersonAvatar
import com.otus.designsystem.theme.AvatarTokens
import com.otus.designsystem.theme.OtusColors
import com.otus.designsystem.theme.PageTokens
import com.otus.designsystem.theme.SheetTokens
import com.otus.mail.feature.contacts.domain.CardKey
import com.otus.mail.feature.contacts.domain.ContactPerson
import com.otus.mail.feature.contacts.presentation.avatar

/**
 * The people the user deals with most, as large faces at the top of the list: two over three,
 * like the Olympic rings upside down, so the first two stand out. Fewer people fill from the top.
 */
@Composable
internal fun FrequentPeople(
    people: List<ContactPerson>,
    onOpen: (CardKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = PageTokens.rowGap),
        verticalArrangement = Arrangement.spacedBy(PageTokens.rowGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        listOf(people.take(TOP_ROW), people.drop(TOP_ROW)).filter { it.isNotEmpty() }.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(SheetTokens.gap)) {
                row.forEach { person -> Face(person) { onOpen(person.main.key) } }
            }
        }
    }
}

@Composable
private fun Face(
    person: ContactPerson,
    onClick: () -> Unit,
) {
    Column(
        Modifier.width(AvatarTokens.featuredWidth).clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(PageTokens.rowGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PersonAvatar(person.avatar, AvatarTokens.featuredSize)
        Text(
            person.name.substringBefore(' ').ifEmpty { person.name },
            color = OtusColors.text2,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val TOP_ROW = 2
