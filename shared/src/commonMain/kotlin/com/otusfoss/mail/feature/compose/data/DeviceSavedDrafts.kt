package com.otusfoss.mail.feature.compose.data

import com.otusfoss.mail.core.messages.domain.DeviceDrafts
import com.otusfoss.mail.core.messages.domain.Draft
import com.otusfoss.mail.core.messages.domain.Signature
import com.otusfoss.mail.core.messages.domain.isDeviceDraftId
import com.otusfoss.mail.feature.compose.domain.SavedDrafts

/**
 * Loads a draft that waits on the device from [device], and any other from [server]. A draft the
 * server took since it was listed loads from the Email it became.
 */
internal class DeviceSavedDrafts(
    private val device: DeviceDrafts,
    private val server: SavedDrafts,
) : SavedDrafts {
    override suspend fun load(
        accountId: String,
        emailId: String,
        whole: Boolean,
    ): Draft? {
        if (!isDeviceDraftId(emailId)) return server.load(accountId, emailId, whole)
        val waiting = device.list.load(emailId)
        return when {
            waiting == null -> device.list.arrivedAs(emailId)?.let { server.load(accountId, it, whole) }
            whole -> waiting.whole()
            else -> waiting
        }
    }
}

/** The draft with its signature and quote as part of its text and HTML, as a saved copy holds them. */
private fun Draft.whole(): Draft =
    copy(body = textContent(), html = htmlContent().orEmpty(), quote = "", signature = Signature())
