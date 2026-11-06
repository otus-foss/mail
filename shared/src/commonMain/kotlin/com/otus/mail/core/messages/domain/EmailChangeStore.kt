package com.otus.mail.core.messages.domain

import com.otus.jmap.JmapJson
import com.otus.mail.core.storage.StoreSpec
import com.otus.mail.core.storage.TextCodec
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject

/**
 * Where marks and moves wait for the server: each login's `Email/set` patches by email id, as one
 * JSON value per login. It must survive the process.
 */
object EmailChangeStore : StoreSpec<String, Map<String, JsonObject>>(
    "pending_changes",
    TextCodec.Text,
    TextCodec.json(MapSerializer(String.serializer(), JsonObject.serializer()), JmapJson),
    commit = true,
)
