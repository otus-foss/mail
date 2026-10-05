package com.otus.mail.core.avatar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Loads a bundled, ASCII-normalized Public Suffix List once. No runtime network lookup is needed. */
class PublicSuffixDomainResolver(
    private val loadRules: suspend () -> String,
) : AvatarDomainResolver {
    private val lock = Mutex()
    private var rules: PublicSuffixRules? = null

    override suspend fun resolve(host: String): String? {
        val parsed =
            lock.withLock {
                rules ?: withContext(Dispatchers.Default) { PublicSuffixRules(loadRules()) }.also { rules = it }
            }
        return parsed.registrableDomain(host)
    }
}

/** PSL longest-rule matching, including private suffixes, wildcards, and exception rules. */
internal class PublicSuffixRules(
    text: String,
) {
    private val exact = mutableSetOf<String>()
    private val wildcards = mutableSetOf<String>()
    private val exceptions = mutableSetOf<String>()

    init {
        text
            .lineSequence()
            .map { it.trim().substringBefore(' ').substringBefore('\t') }
            .filter { it.isNotEmpty() && !it.startsWith("//") }
            .forEach { rule ->
                when {
                    rule.startsWith("!") -> exceptions.add(rule.drop(1).lowercase())
                    rule.startsWith("*.") -> wildcards.add(rule.drop(2).lowercase())
                    else -> exact.add(rule.lowercase())
                }
            }
    }

    fun registrableDomain(host: String): String? {
        if (!isPublicAvatarHost(host)) return null
        val labels = host.lowercase().split('.')
        var suffixLength = 1
        var exceptionDomain: String? = null
        for (index in labels.indices) {
            val suffix = labels.drop(index).joinToString(".")
            if (suffix in exceptions) {
                exceptionDomain = suffix
                break
            }
            if (suffix in exact || labels.drop(index + 1).joinToString(".") in wildcards) {
                suffixLength = maxOf(suffixLength, labels.size - index)
            }
        }
        return exceptionDomain
            ?: labels.takeIf { it.size > suffixLength }?.takeLast(suffixLength + 1)?.joinToString(".")
    }
}
