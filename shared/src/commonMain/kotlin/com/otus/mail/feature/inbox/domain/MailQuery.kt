package com.otus.mail.feature.inbox.domain

/**
 * [filters] with what the server needs to run them: each login's chosen and skipped folder ids
 * with subfolders already added, each login's Read later folder, each login's own [addresses],
 * and the [logins] the page shows, or null for all.
 */
data class MailQuery(
    val filters: InboxFilters = InboxFilters(),
    val folderIds: Map<String, Set<String>> = emptyMap(),
    val skipIds: Map<String, Set<String>> = emptyMap(),
    val readLaterIds: Map<String, String> = emptyMap(),
    val addresses: Map<String, Set<String>> = emptyMap(),
    val logins: Set<String>? = null,
) {
    /** Every address of the user, of any login. */
    val ownAddresses: Set<String>
        get() = addresses.values.flatten().toSet()

    /** Whether this query reads mail of [loginId] at all; choices of other logins leave it out. */
    fun reads(loginId: String): Boolean =
        (logins == null || loginId in logins) &&
            (filters.accounts.isEmpty() || loginId in filters.accounts) &&
            (filters.sentTo.isEmpty() || filters.sentTo.any { it.accountId == loginId }) &&
            (filters.folders.isEmpty() || loginId in folderIds) &&
            (ShowFacet.ReadLater !in filters.show || loginId in readLaterIds)
}

/**
 * Each login's folder ids for [refs]: the folder, and with [FolderRef.includeSubfolders] every
 * folder [descendants] names below it.
 */
fun folderIds(
    refs: Set<FolderRef>,
    descendants: (loginId: String, mailboxId: String) -> Set<String>,
): Map<String, Set<String>> =
    refs
        .groupBy { it.loginId }
        .mapValues { (login, own) ->
            own
                .flatMap { ref ->
                    setOf(ref.mailboxId) +
                        if (ref.includeSubfolders) descendants(login, ref.mailboxId) else emptySet()
                }.toSet()
        }
