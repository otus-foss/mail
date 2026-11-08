package com.otus.mail.feature.theme.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_report
import com.otus.designsystem.shared.feedback.components.InfoStrip
import com.otus.mail.feature.theme.presentation.LowContrast
import com.otus.settings.presentation.title
import kotlin.math.floor

/**
 * Warns on the theme page while [issues] holds pairs of the user's colors that are hard to read,
 * such as "Low contrast: Muted text on page" over "2.1:1, needs 4.5:1 · 2 more". It names the
 * worst pair and counts the rest, and shows nothing while every pair reads well.
 */
@Composable
fun LowContrastWarning(
    issues: List<LowContrast>,
    modifier: Modifier = Modifier,
) {
    val worst = issues.firstOrNull() ?: return
    InfoStrip(
        Res.drawable.symbol_report,
        title = "Low contrast: ${worst.foreground.title} on ${worst.background.title.lowercase()}",
        subtitle = lowContrastDetail(worst, more = issues.size - 1),
        action = null,
        modifier = modifier,
    )
}

/** The ratio a pair reaches and the one it needs, such as "2.1:1, needs 4.5:1 · 2 more". */
internal fun lowContrastDetail(
    issue: LowContrast,
    more: Int,
): String {
    val detail = "${ratioText(issue.ratio)}, needs ${ratioText(issue.required)}"
    return if (more > 0) "$detail · $more more" else detail
}

/**
 * [ratio] as WCAG writes it, such as "4.4:1" or "3:1". It rounds down, so a pair just below
 * 4.5:1 never reads as if it reached it.
 */
private fun ratioText(ratio: Float): String {
    val tenths = floor(ratio * TENTHS).toInt()
    val whole = tenths / TENTHS
    val fraction = tenths % TENTHS
    return if (fraction == 0) "$whole:1" else "$whole.$fraction:1"
}

private const val TENTHS = 10
