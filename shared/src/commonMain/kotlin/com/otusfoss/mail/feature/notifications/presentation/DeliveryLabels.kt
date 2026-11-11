package com.otusfoss.mail.feature.notifications.presentation

import com.otusfoss.core.designsystem.shared.selection.components.ChoiceRow
import com.otusfoss.mail.feature.notifications.domain.PERIODIC_CHECK
import com.otusfoss.mail.feature.notifications.domain.PushDistributor

/** How often Mail checks without pushes, as the delivery choices and notes say it. */
internal val CHECKS = "about every ${PERIODIC_CHECK.inWholeMinutes} minutes"

/** How a delivery choice reads: the service and where it comes from, or Off and what that means. */
internal fun PushDistributor?.choiceRow(periodicChecks: Boolean): ChoiceRow =
    this?.let { ChoiceRow(it.name, if (it.isGoogle || it.isApple) "Built into Otus" else "Through a separate app") }
        ?: ChoiceRow("Off", if (periodicChecks) "Checks $CHECKS" else "No new-mail alerts")
