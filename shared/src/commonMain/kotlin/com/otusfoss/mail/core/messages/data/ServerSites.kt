package com.otusfoss.mail.core.messages.data

import com.otusfoss.core.avatar.AvatarDomainResolver
import com.otusfoss.core.jmap.login.JmapLogin
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Each login's server domain, which a sender's DMARC result must come from to show a brand logo
 * (see [dmarcPassDomain]): the registrable domain of its JMAP host, or the whole host when it has
 * none or [resolver] fails. Finding one reads the bundled Public Suffix List, so only loads [find]
 * it, and the cached list at launch reads what is [known] and shows icons until then.
 */
class ServerSites(
    private val resolver: AvatarDomainResolver,
) {
    private val sites = MutableStateFlow(emptyMap<String, String>())

    /** The domain a load found for login [loginId], or empty before one did, which trusts no field. */
    fun known(loginId: String): String = sites.value[loginId].orEmpty()

    suspend fun find(login: JmapLogin): String =
        sites.value[login.id] ?: run {
            val host = runCatching { Url(login.sessionUrl).host }.getOrDefault("")
            val site =
                runCatching { resolver.resolve(host) }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrNull() ?: host
            sites.update { it + (login.id to site) }
            site
        }
}
