package com.otusfoss.mail.feature.folders.data

import com.otusfoss.core.jmap.JmapJson
import com.otusfoss.mail.core.cache.CacheStorageException
import com.otusfoss.mail.core.cache.MailCache
import com.otusfoss.mail.core.cache.withAccount
import com.otusfoss.mail.core.mail.MailFailure
import com.otusfoss.mail.feature.folders.domain.AccountFolderRules
import com.otusfoss.mail.feature.folders.domain.RuleAvailability
import com.otusfoss.mail.feature.folders.domain.RuleFailure
import com.otusfoss.mail.feature.folders.domain.RuleScriptFacts
import com.otusfoss.mail.feature.folders.domain.RuleSupport
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

/**
 * Keeps each login's rules, as the server last gave them, in [cache], so the folders show their
 * rules while the server cannot be reached. Only a server that does not answer brings the kept
 * rules back; any other answer, such as a server without rules, shows as it is. A save still needs
 * the server, which refuses one made from rules that changed since they were kept.
 */
internal class KeptFolderRules(
    private val cache: MailCache,
) {
    /**
     * Binds [loginId]'s cache before [fromServer] runs, so the rules of a login removed or
     * reconnected meanwhile are not kept.
     */
    suspend fun load(
        loginId: String,
        fromServer: suspend () -> AccountFolderRules,
    ): AccountFolderRules =
        cache.withAccount(loginId) { held ->
            val account = fromServer()
            when {
                account.availability is RuleAvailability.Available -> account.also { keep(held, it) }
                account.availability == UNREACHABLE -> kept(held, account) ?: account
                else -> account
            }
        }

    private suspend fun keep(
        held: MailCache,
        account: AccountFolderRules,
    ) {
        val support = account.support ?: return
        val script = account.script
        val text =
            JmapJson.encodeToString(
                Kept.serializer(),
                Kept(
                    support.extensions,
                    support.maxScriptBytes,
                    blockJson(RuleBlock(account.rules.map { (id, rules) -> BlockFolder(id, "", rules) })),
                    script?.run {
                        KeptScript(scriptId, scriptName, active, hasBlock, changedElsewhere, migrated, state)
                    },
                ),
            )
        try {
            if (held.value(account.accountId, RULES_VALUE) != text) {
                held.write(account.accountId) { putValue(RULES_VALUE, text) }
            }
        } catch (expected: CacheStorageException) {
            // The rules loaded; only their offline copy is missing.
        }
    }

    private suspend fun kept(
        held: MailCache,
        account: AccountFolderRules,
    ): AccountFolderRules? {
        val kept =
            try {
                held.value(account.accountId, RULES_VALUE)?.let { JmapJson.decodeFromString(Kept.serializer(), it) }
            } catch (expected: CacheStorageException) {
                null
            } catch (expected: SerializationException) {
                null
            }
        val block = kept?.let { parseBlockJson(it.rules) }
        return block?.let {
            account.copy(
                availability = RuleAvailability.Available(RuleSupport(kept.extensions, kept.maxScriptBytes)),
                rules = block.folders.associate { it.id to it.rules },
                script =
                    kept.script?.run {
                        RuleScriptFacts(scriptId, scriptName, active, hasBlock, changedElsewhere, migrated, state)
                    },
            )
        }
    }

    private companion object {
        val UNREACHABLE = RuleAvailability.Failed(RuleFailure.Mail(MailFailure.Unreachable))
        const val RULES_VALUE = "folder-rules"
    }
}

/** What the device keeps of one login's rules: what its server can run, the rules, and their script. */
@Serializable
private class Kept(
    val extensions: Set<String>,
    val maxScriptBytes: Long? = null,
    /** The rules by folder, as the JSON line of the script's block holds them. */
    val rules: String,
    val script: KeptScript? = null,
)

@Serializable
private class KeptScript(
    val scriptId: String?,
    val scriptName: String,
    val active: Boolean,
    val hasBlock: Boolean,
    val changedElsewhere: Boolean,
    val migrated: Boolean,
    val state: String,
)
