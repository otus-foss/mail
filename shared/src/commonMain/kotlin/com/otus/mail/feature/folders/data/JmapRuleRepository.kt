package com.otus.mail.feature.folders.data

import com.otus.jmap.GetMethod
import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.JmapJson
import com.otus.jmap.SetMethod
import com.otus.jmap.login.JmapLogin
import com.otus.jmap.login.LoginStore
import com.otus.mail.feature.folders.domain.AccountRules
import com.otus.mail.feature.folders.domain.ApplyOutcome
import com.otus.mail.feature.folders.domain.MailRule
import com.otus.mail.feature.folders.domain.RuleAvailability
import com.otus.mail.feature.folders.domain.RuleChange
import com.otus.mail.feature.folders.domain.RuleFailure
import com.otus.mail.feature.folders.domain.RuleOutcome
import com.otus.mail.feature.folders.domain.RuleRepository
import com.otus.mail.feature.folders.domain.RuleSupport
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Rules as one Sieve script per account, managed over JMAP (RFC 9661). The script is uploaded as a
 * blob and then created or updated; activation happens in the same call. Every save names the
 * state it was based on, so a change made meanwhile on another device is refused, not overwritten.
 */
class JmapRuleRepository(
    connections: JmapConnections,
    logins: LoginStore,
) : RuleRepository {
    private val accounts = MailAccounts(connections, logins)

    override suspend fun accounts(): List<AccountRules> =
        coroutineScope { accounts.logins().map { async { load(it) } }.awaitAll() }

    private suspend fun load(login: JmapLogin): AccountRules =
        try {
            val account = accounts.open(login)
            when (val availability = availability(account)) {
                is RuleAvailability.Available -> loadScripts(account, availability)
                else -> AccountRules(login.id, login.name, availability)
            }
        } catch (error: JmapException) {
            AccountRules(login.id, login.name, RuleAvailability.Failed(error.toRuleFailure()))
        }

    private suspend fun loadScripts(
        account: MailAccount,
        availability: RuleAvailability.Available,
    ): AccountRules {
        val scripts =
            account.connection.call(
                GetMethod(SieveScriptType, account.accountId, properties = SCRIPT_PROPERTIES),
            )
        val ours = scripts.list.firstOrNull { it.name == RULES_SCRIPT }
        val active = scripts.list.firstOrNull { it.isActive }
        val read = ours?.let { readScript(account, it, availability.support) }
        return AccountRules(
            accountId = account.login.id,
            accountName = account.login.name,
            availability = availability,
            rules = read?.script?.rules.orEmpty(),
            installed = ours != null,
            running = ours?.isActive == true,
            activeOther = active?.takeIf { it.id != ours?.id }?.let { it.name ?: "another script" },
            keep = read?.script?.keep,
            changedElsewhere = read?.changedElsewhere == true,
            version = scripts.state,
        )
    }

    private suspend fun readScript(
        account: MailAccount,
        script: SieveScriptObject,
        support: RuleSupport,
    ): ReadRules {
        val limit = (support.maxScriptBytes ?: MAX_SCRIPT_BYTES).coerceIn(1, MAX_SCRIPT_BYTES)
        val blob = account.connection.download(account.accountId, script.blobId, SIEVE_MEDIA_TYPE, limit)
        return decodeRules(blob.bytes.decodeToString(), support)
    }

    override suspend fun save(
        account: AccountRules,
        change: RuleChange,
    ): RuleOutcome {
        val extensions = account.support?.extensions.orEmpty()
        val bytes = encodeRules(RuleScript(change.rules, change.keep), extensions).encodeToByteArray()
        val limit = account.support?.maxScriptBytes
        return if (limit != null && bytes.size > limit) {
            RuleOutcome.Failed(RuleFailure.TooLarge)
        } else {
            try {
                accounts.open(account.accountId)?.let { write(it, account.version, bytes, change) }
                    ?: RuleOutcome.Failed(RuleFailure.NotFound)
            } catch (error: JmapException) {
                RuleOutcome.Failed(error.toRuleFailure())
            }
        }
    }

    private suspend fun write(
        account: MailAccount,
        version: String?,
        bytes: ByteArray,
        change: RuleChange,
    ): RuleOutcome {
        // The script id is looked up again; the state check and ifInState catch any change meanwhile.
        val current =
            account.connection.call(
                GetMethod(SieveScriptType, account.accountId, properties = SCRIPT_PROPERTIES),
            )
        if (version != null && current.state != version) return RuleOutcome.Failed(RuleFailure.ChangedElsewhere)
        val ours = current.list.firstOrNull { it.name == RULES_SCRIPT }
        val blob = account.connection.upload(account.accountId, bytes, SIEVE_MEDIA_TYPE)
        val result = account.connection.call(scriptSet(account.accountId, ours, blob.blobId, change, current.state))
        return result.firstError?.let { RuleOutcome.Failed(it.toRuleFailure(), it.description) } ?: RuleOutcome.Saved
    }

    override suspend fun applyToInbox(
        account: AccountRules,
        rule: MailRule,
    ): ApplyOutcome =
        try {
            accounts.open(account.accountId)?.let { InboxRuleApplier(it).apply(rule) }
                ?: ApplyOutcome.Failed(RuleFailure.NotFound)
        } catch (error: JmapException) {
            ApplyOutcome.Failed(error.toRuleFailure())
        }

    private fun scriptSet(
        accountId: String,
        ours: SieveScriptObject?,
        blobId: String,
        change: RuleChange,
        state: String,
    ): SetMethod {
        val extra =
            buildMap<String, JsonElement> {
                if (change.run == true) put("onSuccessActivateScript", JsonPrimitive(ours?.id ?: "#$CREATION_ID"))
                if (change.run == false && ours?.isActive == true) put("onSuccessDeactivateScript", JsonPrimitive(true))
            }
        val blob = "blobId" to JsonPrimitive(blobId)
        return if (ours == null) {
            val script = JsonObject(mapOf("name" to JsonPrimitive(RULES_SCRIPT), blob))
            SetMethod(
                SieveScriptType,
                accountId,
                create = mapOf(CREATION_ID to script),
                ifInState = state,
                extra = extra,
            )
        } else {
            SetMethod(
                SieveScriptType,
                accountId,
                update = mapOf(ours.id to JsonObject(mapOf(blob))),
                ifInState = state,
                extra = extra,
            )
        }
    }

    private companion object {
        const val CREATION_ID = "rules"
        const val MAX_SCRIPT_BYTES = 1024L * 1024
        val SCRIPT_PROPERTIES = listOf("id", "name", "blobId", "isActive")
    }
}

/** Rules need Sieve with filing by folder id; other extensions only unlock more conditions. */
private fun availability(account: MailAccount): RuleAvailability {
    val capabilities =
        account.session.accounts[account.accountId]
            ?.capabilityObjects
            .orEmpty()
    val sieve = capabilities[JmapCapability.SIEVE] ?: return RuleAvailability.NoSieve
    val capability = runCatching { JmapJson.decodeFromJsonElement(SieveCapability.serializer(), sieve) }.getOrNull()
    val support = RuleSupport(capability?.sieveExtensions.orEmpty().toSet(), capability?.maxSizeScript)
    return if (support.canMoveToFolders) RuleAvailability.Available(support) else RuleAvailability.NoFolderRules
}
