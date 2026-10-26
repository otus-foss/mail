package com.otus.mail.feature.contacts.presentation

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.avatar.AvatarPhotoPicker
import com.otus.jscontact.Card
import com.otus.jscontact.CardKind
import com.otus.jscontact.Media
import com.otus.jscontact.MediaKind
import com.otus.jscontact.Name
import com.otus.mail.feature.contacts.domain.BookKey
import com.otus.mail.feature.contacts.domain.CardKey
import com.otus.mail.feature.contacts.domain.ContactBook
import com.otus.mail.feature.contacts.domain.ContactCard
import com.otus.mail.feature.contacts.domain.ContactDraft
import com.otus.mail.feature.contacts.domain.ContactEdit
import com.otus.mail.feature.contacts.domain.ContactFile
import com.otus.mail.feature.contacts.domain.ContactFiles
import com.otus.mail.feature.contacts.domain.ContactLabel
import com.otus.mail.feature.contacts.domain.ContactPerson
import com.otus.mail.feature.contacts.domain.ContactRepository
import com.otus.mail.feature.contacts.domain.ContactsSnapshot
import com.otus.mail.feature.contacts.domain.DraftValue
import com.otus.mail.feature.contacts.domain.ImportMode
import com.otus.mail.feature.contacts.domain.ImportPlan
import com.otus.mail.feature.contacts.domain.MergePlan
import com.otus.mail.feature.contacts.domain.applyEdits
import com.otus.mail.feature.contacts.domain.duplicates
import com.otus.mail.feature.contacts.domain.people
import com.otus.mail.feature.contacts.domain.withPhoto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.io.encoding.Base64
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Shows [keys] as one person without changing the cards. */
internal fun ContactsState.showAsOne(keys: Set<CardKey>) = sync.link(keys)

/** Lists [keys] as separate people again, undoing [showAsOne]. */
internal fun ContactsState.showSeparately(keys: Collection<CardKey>) = sync.unlink(keys)

/** Stops suggesting that [keys] are the same person. */
internal fun ContactsState.notDuplicates(keys: Set<CardKey>) = sync.dismiss(keys)
