package com.otus.mail.feature.folders.data

import com.otus.mail.feature.folders.domain.FolderRule
import com.otus.mail.feature.folders.domain.RuleSupport

/**
 * Which script holds the Otus block and what it says now. [target] is null when the account has
 * no script yet and saving creates "Otus Mail". [text] is the target's current Sieve, which a save
 * keeps outside the block; it is empty when the old rules script becomes just the block.
 */
internal data class ScriptPlan(
    val target: SieveScriptObject?,
    val text: String,
    val rules: Map<String, List<FolderRule>>,
    val hasBlock: Boolean,
    val changedElsewhere: Boolean = false,
    val migrated: Boolean = false,
) {
    /** The new script: [block] at its end and [require] at its top, everything else as it was. */
    fun withBlock(
        block: String,
        require: String?,
    ): String = spliceBlock(text, block, require)
}

/**
 * Plans where the rules live among [scripts], reading a script's Sieve with [read] only when
 * needed. Null when Stalwart's reserved `vacation` script is the active one: it cannot be edited
 * through `SieveScript/set`, so the vacation reply must be turned off before rules can run.
 *
 * The block goes into the active script. Without one, it goes into "Otus Mail", which is created
 * when missing. An account that still runs the old "Otus Mail" script (`# otus-rules-v1`) has its
 * rules converted. When that script kept another one running through `include`, the block moves
 * to the end of that kept script, which becomes the active one; otherwise "Otus Mail" becomes just
 * the block. An old script that is not the one running holds rules that were off, so they stay.
 */
internal suspend fun planScript(
    scripts: List<SieveScriptObject>,
    support: RuleSupport,
    read: suspend (SieveScriptObject) -> String,
): ScriptPlan? {
    val active = scripts.firstOrNull { it.isActive }
    val base = active ?: scripts.firstOrNull { it.name == RULES_SCRIPT }
    return when {
        active?.isVacation == true -> null
        base == null -> ScriptPlan(null, "", emptyMap(), hasBlock = false)
        else -> planFor(base, scripts, support, read)
    }
}

private suspend fun planFor(
    base: SieveScriptObject,
    scripts: List<SieveScriptObject>,
    support: RuleSupport,
    read: suspend (SieveScriptObject) -> String,
): ScriptPlan {
    val text = read(base)
    val legacy = readLegacyRules(text)?.takeIf { base.name == RULES_SCRIPT && base.isActive }
    val kept = legacy?.let { old -> scripts.firstOrNull { it.name == old.keep && it.id != base.id && !it.isVacation } }
    return when {
        findBlock(text) != null -> blockPlan(base, text, support)
        legacy == null -> ScriptPlan(base, text, emptyMap(), hasBlock = false)
        kept == null -> ScriptPlan(base, "", legacy.rules, hasBlock = false, migrated = true)
        else -> migrationInto(kept, read(kept), legacy, support)
    }
}

/** A kept script that already holds a block was migrated before; its block wins. */
private fun migrationInto(
    kept: SieveScriptObject,
    text: String,
    legacy: LegacyRules,
    support: RuleSupport,
): ScriptPlan =
    if (findBlock(text) != null) {
        blockPlan(kept, text, support)
    } else {
        ScriptPlan(kept, text, legacy.rules, hasBlock = false, migrated = true)
    }

private fun blockPlan(
    script: SieveScriptObject,
    text: String,
    support: RuleSupport,
): ScriptPlan {
    val read = decodeBlock(blockText(text).orEmpty(), support)
    val rules = read.block.folders.associate { it.id to it.rules }
    return ScriptPlan(script, text, rules, hasBlock = true, changedElsewhere = read.changedElsewhere)
}

/** Stalwart turns its vacation reply into a script with this name. */
private val SieveScriptObject.isVacation: Boolean
    get() = name.equals(VACATION_SCRIPT, ignoreCase = true)

internal const val VACATION_SCRIPT = "vacation"
