package com.otus.mail.feature.folders.presentation

import com.otus.designsystem.shared.filters.components.RuleSegment
import com.otus.designsystem.shared.filters.components.RuleSegmentStyle

/** Neighbours in the same style become one segment. */
internal fun List<RuleSegment>.merged(): List<RuleSegment> =
    fold(emptyList()) { merged, segment ->
        val last = merged.lastOrNull()
        val joined = last?.takeIf { it.style == segment.style }?.copy(text = last.text + segment.text)
        if (joined != null) merged.dropLast(1) + joined else merged + segment
    }

/** A wildcard pattern with each `*` and `?` in accent, so it reads as a placeholder. */
internal fun wildcards(pattern: String): List<RuleSegment> =
    WILDCARD.split(pattern).let { parts ->
        val marks = WILDCARD.findAll(pattern).map { it.value }.toList()
        parts.flatMapIndexed { index, part ->
            bright(part) + marks.getOrNull(index)?.let { listOf(RuleSegment(it, RuleSegmentStyle.Accent)) }.orEmpty()
        }
    }

private val WILDCARD = Regex("[*?]")

internal fun dim(text: String): List<RuleSegment> = segment(text, RuleSegmentStyle.Dim)

internal fun bright(text: String): List<RuleSegment> = segment(text, RuleSegmentStyle.Bright)

private fun segment(
    text: String,
    style: RuleSegmentStyle,
): List<RuleSegment> = if (text.isEmpty()) emptyList() else listOf(RuleSegment(text, style))
