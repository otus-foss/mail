package com.otus.mail.feature.folders.presentation

/** Neighbours in the same style become one segment. */
internal fun List<ChipSegment>.merged(): List<ChipSegment> =
    fold(emptyList()) { merged, segment ->
        val last = merged.lastOrNull()
        val joined = last?.takeIf { it.style == segment.style }?.copy(text = last.text + segment.text)
        if (joined != null) merged.dropLast(1) + joined else merged + segment
    }

/** A wildcard pattern with each `*` and `?` in accent, so it reads as a placeholder. */
internal fun wildcards(pattern: String): List<ChipSegment> =
    WILDCARD.split(pattern).let { parts ->
        val marks = WILDCARD.findAll(pattern).map { it.value }.toList()
        parts.flatMapIndexed { index, part ->
            bright(part) + marks.getOrNull(index)?.let { listOf(ChipSegment(it, SegmentStyle.Accent)) }.orEmpty()
        }
    }

private val WILDCARD = Regex("[*?]")

internal fun dim(text: String): List<ChipSegment> = segment(text, SegmentStyle.Dim)

internal fun bright(text: String): List<ChipSegment> = segment(text, SegmentStyle.Bright)

private fun segment(
    text: String,
    style: SegmentStyle,
): List<ChipSegment> = if (text.isEmpty()) emptyList() else listOf(ChipSegment(text, style))
