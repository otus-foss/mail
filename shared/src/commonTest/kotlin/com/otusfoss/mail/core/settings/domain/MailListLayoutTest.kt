package com.otusfoss.mail.core.settings.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class MailListLayoutTest {
    @Test
    fun theDefaultsPutTheSubjectFirstWithoutAPreview() {
        assertEquals(
            MailListLayout(
                order = ListOrder.SubjectFirst,
                preview = PreviewLines.Off,
                showSender = true,
                folderIcons = true,
                dateStyle = DateStyle.Relative,
            ),
            ListBehavior().layout,
        )
    }

    @Test
    fun aLayoutSurvivesEncoding() {
        val layout =
            MailListLayout(
                order = ListOrder.SenderFirst,
                preview = PreviewLines.Two,
                showSender = false,
                folderIcons = false,
                dateStyle = DateStyle.FullMonths,
                showThisYear = false,
            )
        assertEquals(layout, MailListLayout.decode(MailListLayout.encode(layout)))
    }

    @Test
    fun missingAndUnknownValuesReadAsTheDefaults() {
        assertEquals(MailListLayout(), MailListLayout.decode(""))
        assertEquals(
            MailListLayout(preview = PreviewLines.One),
            MailListLayout.decode("order=middle\npreview=one\nshowSender=maybe\nlater=true"),
        )
    }
}
