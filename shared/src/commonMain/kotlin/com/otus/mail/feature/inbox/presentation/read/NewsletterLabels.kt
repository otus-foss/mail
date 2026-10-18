package com.otus.mail.feature.inbox.presentation.read

import com.otus.mail.feature.inbox.domain.MailingList

/**
 * The newsletter strip's subtitle: "Mailing list" and how often the list writes, as in
 * "Mailing list · about once a week"; only "Mailing list" while that is unknown.
 */
fun MailingList.subtitle(): String {
    val often =
        perWeek?.let { rate ->
            when {
                rate >= DAILY -> "about every day"
                rate >= FEW_A_WEEK -> "a few times a week"
                rate >= WEEKLY -> "about once a week"
                rate >= MONTHLY -> "about once a month"
                else -> "now and then"
            }
        }
    return listOfNotNull("Mailing list", often).joinToString(" · ")
}

private const val DAILY = 5.0
private const val FEW_A_WEEK = 1.5
private const val WEEKLY = 0.6
private const val MONTHLY = 0.15
