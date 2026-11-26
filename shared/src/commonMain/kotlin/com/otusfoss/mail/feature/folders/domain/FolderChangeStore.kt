package com.otusfoss.mail.feature.folders.domain

import com.otusfoss.core.jmap.JmapJson
import com.otusfoss.mail.core.storage.StoreSpec
import com.otusfoss.mail.core.storage.TextCodec
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject

/**
 * Where renames, moves, and new orders of folders wait for the server: each login's `Mailbox/set`
 * updates in the order they were made, each as the patches of one request by mailbox id. It must
 * survive the process.
 */
object FolderChangeStore : StoreSpec<String, List<JsonObject>>(
    "pending_folders",
    TextCodec.Text,
    TextCodec.json(ListSerializer(JsonObject.serializer()), JmapJson),
    commit = true,
)
