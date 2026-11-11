package com.otusfoss.mail.testing

import com.otusfoss.mail.app.MailServices
import com.otusfoss.mail.core.mailbox.AccountFolders
import com.otusfoss.mail.feature.folders.presentation.FoldersState
import kotlinx.coroutines.CoroutineScope

/**
 * Folders that load on the test scheduler. The device cache reads logins on Dispatchers.IO, which
 * runCurrent() does not wait for, so tests start from an empty cache.
 */
internal fun CoroutineScope.testFolders(
    services: MailServices,
    loadAccounts: suspend () -> List<AccountFolders>,
) = FoldersState(services.folders, this, loadCached = { emptyList() }, loadAccounts = loadAccounts)
