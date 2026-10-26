package com.otus.mail.feature.contacts.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.otus.designsystem.components.FeaturedAvatar
import com.otus.designsystem.components.FeaturedAvatars
import com.otus.mail.feature.contacts.domain.CardKey
import com.otus.mail.feature.contacts.domain.ContactPerson
import com.otus.mail.feature.contacts.presentation.avatar

/** The people the user deals with most, as large faces with their first names at the top of the list. */
@Composable
internal fun FrequentPeople(
    people: List<ContactPerson>,
    onOpen: (CardKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    FeaturedAvatars(
        people.map { person ->
            FeaturedAvatar(person.name.substringBefore(' ').ifEmpty { person.name }, person.avatar) {
                onOpen(person.main.key)
            }
        },
        modifier,
    )
}
