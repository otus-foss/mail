package com.otusfoss.mail

import android.content.Context
import android.net.ConnectivityManager
import com.otusfoss.mail.core.messages.domain.NetworkCost

/**
 * What Android says the active network costs, asked each time, since the network changes. A
 * device without a connectivity service counts as metered and saving data, so nothing downloads
 * ahead there.
 */
internal class AndroidNetworkCost(
    context: Context,
) : NetworkCost {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    override val metered: Boolean get() = connectivity?.isActiveNetworkMetered ?: true

    override val dataSaver: Boolean
        get() = connectivity?.restrictBackgroundStatus != ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED
}
