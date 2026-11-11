package com.otusfoss.mail.core.settings.domain

/**
 * What the Read view's quick view shows above its Move to row, when the control panel is pulled
 * down, on a message without files. A message's files take the module's place.
 */
enum class ReadQuickModule(
    val storageValue: String,
) {
    /** Everyone in the conversation besides the user, with a dot for what they wrote that is unread. */
    Thread("thread"),
    ;

    companion object {
        /** One row shows, under the message's details, so the quick view stays short. */
        const val LIMIT: Int = 1

        val Defaults: List<ReadQuickModule> = listOf(Thread)

        /** [modules] as one line, such as `thread,sender`. */
        fun encode(modules: List<ReadQuickModule>): String = modules.joinToString(",") { it.storageValue }

        fun decode(saved: String): List<ReadQuickModule> =
            decodeModules(saved, entries, ReadQuickModule::storageValue, LIMIT)
    }
}
