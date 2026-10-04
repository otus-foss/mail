package com.otus.mail.designsystem.elements

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.LayoutDirection
import com.otus.mail.designsystem.theme.CalendarTokens

/** Draw one week's slice of a continuous month outline, with convex and concave rounded corners. */
fun Modifier.calendarMonthBackground(
    firstWeekday: Int,
    days: Int,
    weekOffset: Int,
    color: Color,
): Modifier =
    drawWithCache {
        val vertices = monthVertices(size, firstWeekday, days, weekOffset)
        val radius = minOf(CalendarTokens.monthCornerRadius.toPx(), size.width / CalendarTokens.DAYS_PER_WEEK / 2)
        val path = roundedMonthPath(vertices, radius, size.width, layoutDirection == LayoutDirection.Rtl)
        onDrawBehind { clipRect { drawPath(path, color) } }
    }

private fun monthVertices(
    size: Size,
    firstWeekday: Int,
    days: Int,
    weekOffset: Int,
): List<Offset> {
    val lastCell = firstWeekday + days - 1
    val lastRow = lastCell / CalendarTokens.DAYS_PER_WEEK
    val endColumn = lastCell % CalendarTokens.DAYS_PER_WEEK + 1
    val startX = size.width * firstWeekday / CalendarTokens.DAYS_PER_WEEK
    val endX = size.width * endColumn / CalendarTokens.DAYS_PER_WEEK
    val top = -weekOffset * size.height
    val lastTop = top + lastRow * size.height
    val bottom = lastTop + size.height
    return listOf(
        Offset(startX, top),
        Offset(size.width, top),
        Offset(size.width, lastTop),
        Offset(endX, lastTop),
        Offset(endX, bottom),
        Offset(0f, bottom),
        Offset(0f, top + size.height),
        Offset(startX, top + size.height),
    ).distinct()
}

private fun roundedMonthPath(
    vertices: List<Offset>,
    radius: Float,
    width: Float,
    rtl: Boolean,
): Path {
    val points = if (rtl) vertices.map { Offset(width - it.x, it.y) } else vertices
    return Path().apply {
        points.forEachIndexed { index, point ->
            val previous = points[(index + points.size - 1) % points.size]
            val next = points[(index + 1) % points.size]
            val incoming = point - previous
            val outgoing = next - point
            val corner = minOf(radius, incoming.getDistance() / 2, outgoing.getDistance() / 2)
            val before = point - incoming / incoming.getDistance() * corner
            val after = point + outgoing / outgoing.getDistance() * corner
            if (index == 0) moveTo(before.x, before.y) else lineTo(before.x, before.y)
            quadraticTo(point.x, point.y, after.x, after.y)
        }
        close()
    }
}
