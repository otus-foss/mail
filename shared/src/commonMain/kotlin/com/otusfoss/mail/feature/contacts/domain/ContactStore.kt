package com.otusfoss.mail.feature.contacts.domain

import com.otusfoss.core.jscontact.JsContactJson
import com.otusfoss.mail.core.storage.StoreField
import com.otusfoss.mail.core.storage.StoreSpec
import com.otusfoss.mail.core.storage.TextCodec
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * The device's own contact data: changes waiting for the server, cards shown as one person,
 * duplicate pairs the user said are different people, and each login's frequent scores. Values are
 * JSON text under fixed keys; one that no longer reads counts as none rather than stopping the app.
 */
object ContactStore : StoreSpec<String, String>("contacts", TextCodec.Text, TextCodec.Text) {
    val EDITS = StoreField("edits", TextCodec.json(ListSerializer(ContactEdit.serializer()), JsContactJson))
    val LINKS = StoreField("links", KEY_SETS)
    val DISMISSED = StoreField("dismissed", KEY_SETS)
    val FREQUENT =
        StoreField(
            "frequent",
            TextCodec.json(MapSerializer(String.serializer(), FrequentScan.serializer()), JsContactJson),
        )

    /**
     * [saved] without [loginId]'s waiting changes, its cards among those shown as one and dismissed
     * pairs, and its frequent scores, so nothing of a removed account stays on the device.
     */
    fun forget(
        saved: Map<String, String>,
        loginId: String,
    ): Map<String, String> {
        fun others(field: StoreField<List<Set<CardKey>>>) =
            field.entry(
                field
                    .of(saved)
                    .orEmpty()
                    .map { set -> set.filterNot { it.loginId == loginId }.toSet() }
                    .filter { it.size > 1 },
            )
        return saved +
            listOfNotNull(
                EDITS.entry(EDITS.of(saved).orEmpty().filterNot { it.key.loginId == loginId }),
                others(LINKS),
                others(DISMISSED),
                FREQUENT.of(saved)?.takeIf { loginId in it }?.let { FREQUENT.entry(it - loginId) },
            )
    }
}

/** Sets of card keys as JSON, for [ContactLinks] and dismissed duplicates. */
private val KEY_SETS =
    TextCodec
        .json(ListSerializer(ListSerializer(CardKey.serializer())), JsContactJson)
        .map<List<Set<CardKey>>>({ lists -> lists.map { it.toSet() } }, { sets -> sets.map { it.toList() } })
