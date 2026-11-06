package com.otus.mail.feature.folders.data

import com.otus.jmap.GetMethod
import com.otus.jmap.JmapCapability
import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.JmapJson
import com.otus.jmap.login.JmapLogin
import com.otus.jmap.login.LoginStore
import com.otus.mail.core.mail.MailAccount
import com.otus.mail.core.mail.MailboxObject
import com.otus.mail.core.mail.MailboxType
import com.otus.mail.core.mail.mailAccount
import com.otus.mail.feature.folders.domain.AccountFolderRules
import com.otus.mail.feature.folders.domain.FolderRule
import com.otus.mail.feature.folders.domain.FolderRuleRepository
import com.otus.mail.feature.folders.domain.RuleAvailability
import com.otus.mail.feature.folders.domain.RuleFailure
import com.otus.mail.feature.folders.domain.RuleOutcome
import com.otus.mail.feature.folders.domain.RuleScriptFacts
import com.otus.mail.feature.folders.domain.RuleSupport
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlin.time.Clock

/**
 * Folder rules as a marked block in each account's active Sieve script, managed over JMAP
 * (RFC 9661). Otus changes only its block. A save uploads the new script as a blob, has the
 * server validate it, and then updates the script naming the state it was read in, so a change
 * made meanwhile on another device is refused instead of overwritten.
 */
class JmapFolderRuleRepository(
    private val connections: JmapConnections,
    /** The UTC offset, such as `+0200`, that day and time conditions are written in. */
    private val zone: () -> String = ::deviceZone,
) : FolderRuleRepository {
    private val existing = ExistingMail(connections)

    override suspend fun accounts(): List<AccountFolderRules> =
        coroutineScope { folderStorage { connections.savedLogins() }.map { async { load(it) } }.awaitAll() }

    private suspend fun load(login: JmapLogin): AccountFolderRules =
        try {
            val account = connections.mailAccount(login)
            when (val availability = availability(account)) {
                is RuleAvailability.Available -> loadRules(account, availability)
                else -> AccountFolderRules(login.id, login.name, availability)
            }
        } catch (error: JmapException) {
            AccountFolderRules(login.id, login.name, RuleAvailability.Failed(error.toRuleFailure()))
        }

    private suspend fun loadRules(
        account: MailAccount,
        availability: RuleAvailability.Available,
    ): AccountFolderRules {
        val read = read(account, availability.support)
        val plan =
            read.plan
                ?: return AccountFolderRules(
                    account.login.id,
                    account.login.name,
                    RuleAvailability.Failed(RuleFailure.VacationActive),
                )
        return AccountFolderRules(
            accountId = account.login.id,
            accountName = account.login.name,
            availability = availability,
            rules = plan.rules.inFolderOrder(read.folders).associate { it.id to it.rules },
            script =
                RuleScriptFacts(
                    scriptId = plan.target?.id,
                    scriptName = plan.target?.name ?: RULES_SCRIPT,
                    active = plan.target?.isActive == true,
                    hasBlock = plan.hasBlock,
                    changedElsewhere = plan.changedElsewhere,
                    migrated = plan.migrated,
                    state = read.state,
                ),
        )
    }

    override suspend fun save(
        account: AccountFolderRules,
        rules: Map<String, List<FolderRule>>,
    ): RuleOutcome {
        val support = account.support ?: return RuleOutcome.Failed(RuleFailure.NotPermitted)
        return try {
            folderStorage { connections.mailAccount(account.accountId) }
                ?.let { write(it, support, account.script?.state, rules) }
                ?: RuleOutcome.Failed(RuleFailure.NotFound)
        } catch (error: JmapException) {
            RuleOutcome.Failed(error.toRuleFailure())
        }
    }

    private suspend fun write(
        account: MailAccount,
        support: RuleSupport,
        state: String?,
        rules: Map<String, List<FolderRule>>,
    ): RuleOutcome {
        val read = read(account, support)
        val plan = read.plan
        val block = RuleBlock(rules.inFolderOrder(read.folders), zone())
        val bytes = plan?.withBlock(encodeBlock(block, support), encodeRequire(block, support))?.encodeToByteArray()
        return when {
            plan == null || bytes == null -> RuleOutcome.Failed(RuleFailure.VacationActive)
            state != null && read.state != state -> RuleOutcome.Failed(RuleFailure.ChangedElsewhere)
            support.maxScriptBytes?.let { bytes.size > it } == true -> RuleOutcome.Failed(RuleFailure.TooLarge)
            else -> store(account, plan.target, bytes, read.state)
        }
    }

    /** Uploads and validates [bytes] first, so a script the server refuses never replaces the one that runs. */
    private suspend fun store(
        account: MailAccount,
        target: SieveScriptObject?,
        bytes: ByteArray,
        state: String,
    ): RuleOutcome {
        val blob = account.connection.upload(account.id, bytes, SIEVE_MEDIA_TYPE)
        val refusal = account.connection.call(SieveValidateMethod(account.id, blob.blobId))
        val error = refusal ?: account.connection.call(scriptSet(account.id, target, blob.blobId, state)).firstError
        return when {
            refusal != null -> RuleOutcome.Failed(RuleFailure.Invalid, refusal.description)
            error != null -> RuleOutcome.Failed(error.toRuleFailure(), error.description)
            else -> RuleOutcome.Saved
        }
    }

    /** The scripts and their state, the folders, and the plan, which reads script text as needed. */
    private suspend fun read(
        account: MailAccount,
        support: RuleSupport,
    ): ScriptRead {
        val scripts = account.connection.call(GetMethod(SieveScriptType, account.id, properties = SCRIPT_PROPERTIES))
        val mailboxes = account.connection.call(GetMethod(MailboxType, account.id, properties = MAILBOX_PROPERTIES))
        val plan = planScript(scripts.list, support) { download(account, it, support) }
        return ScriptRead(scripts.state, mailboxes.list, plan)
    }

    private suspend fun download(
        account: MailAccount,
        script: SieveScriptObject,
        support: RuleSupport,
    ): String {
        val limit = (support.maxScriptBytes ?: MAX_SCRIPT_BYTES).coerceIn(1, MAX_SCRIPT_BYTES)
        return account.connection
            .download(account.id, script.blobId, SIEVE_MEDIA_TYPE, limit)
            .bytes
            .decodeToString()
    }

    override suspend fun existingCount(
        accountId: String,
        folderId: String,
        rule: FolderRule,
    ): Int? = existing.count(accountId, folderId, rule)

    override suspend fun moveExisting(
        accountId: String,
        folderId: String,
        rule: FolderRule,
    ): Int? = existing.move(accountId, folderId, rule)

    private class ScriptRead(
        val state: String,
        val folders: List<MailboxObject>,
        val plan: ScriptPlan?,
    )

    private companion object {
        const val MAX_SCRIPT_BYTES = 1024L * 1024
        val SCRIPT_PROPERTIES = listOf("id", "name", "blobId", "isActive")
        val MAILBOX_PROPERTIES = listOf("id", "name", "parentId", "role", "sortOrder")
    }
}

/** Rules need Sieve with `fileinto`; other extensions only unlock more conditions and filing by id. */
private fun availability(account: MailAccount): RuleAvailability {
    val sieve =
        account.session.accounts[account.id]
            ?.capabilityObjects
            ?.get(JmapCapability.SIEVE)
            ?: return RuleAvailability.RulesUnavailable
    val capability = runCatching { JmapJson.decodeFromJsonElement(SieveCapability.serializer(), sieve) }.getOrNull()
    val support = RuleSupport(capability?.sieveExtensions.orEmpty().toSet(), capability?.maxSizeScript)
    return if ("fileinto" in support.extensions) {
        RuleAvailability.Available(support)
    } else {
        RuleAvailability.RulesUnavailable
    }
}

/** The device's current UTC offset as Sieve's `:zone` takes it (RFC 5260, section 4.1), such as `+0200`. */
internal fun deviceZone(): String {
    val seconds =
        TimeZone
            .currentSystemDefault()
            .offsetAt(Clock.System.now())
            .totalSeconds
    val minutes = kotlin.math.abs(seconds) / SECONDS_PER_MINUTE
    val sign = if (seconds < 0) "-" else "+"
    val hours = (minutes / MINUTES_PER_HOUR).toString().padStart(2, '0')
    return sign + hours + (minutes % MINUTES_PER_HOUR).toString().padStart(2, '0')
}

private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60
