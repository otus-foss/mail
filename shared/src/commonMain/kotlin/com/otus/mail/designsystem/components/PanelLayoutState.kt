package com.otus.mail.designsystem.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import kotlin.math.abs

/** Retain live coordinates. Resolve every part of the panel together during drawing. */
internal class PanelLayoutState {
    private var revision by mutableIntStateOf(0)
    private var panel: LayoutCoordinates? = null
    private var header: LayoutCoordinates? = null
    private var rail: LayoutCoordinates? = null
    private var viewport: LayoutCoordinates? = null
    private val items = mutableMapOf<Any, PanelHole>()

    fun trackPanel(coordinates: LayoutCoordinates) {
        panel = coordinates
        revision++
    }

    fun trackHeader(coordinates: LayoutCoordinates) {
        header = coordinates
        revision++
    }

    fun trackRail(coordinates: LayoutCoordinates) {
        rail = coordinates
        revision++
    }

    fun trackViewport(coordinates: LayoutCoordinates) {
        viewport = coordinates
        revision++
    }

    fun trackItem(
        id: Any,
        coordinates: LayoutCoordinates,
        visibility: PillVisibility,
    ) {
        items.getOrPut(id) { PanelHole(visibility) }.coordinates = coordinates
        revision++
    }

    fun removeItem(id: Any) {
        items.remove(id)
        revision++
    }

    /** This state read invalidates drawing after placement or scrolling, never composition. */
    fun frame(
        overlap: Float,
        gap: Float,
        border: Float,
    ): PanelFrame? {
        val anchors = anchors() ?: return null
        val parent = anchors.panel
        val clip = anchors.viewport
        val bottom = anchors.header.bottom + (anchors.rail.center.y - anchors.header.bottom) * overlap
        val holes =
            items.mapNotNull { (id, item) ->
                val scrolling = id != ActionKey
                val pill = parent.boundsOf(item.coordinates)?.inflate(gap) ?: return@mapNotNull null
                val visible = if (scrolling) pill.left < clip.right && pill.right > clip.left else true
                val presence = item.resolve(covered = visible && pill.top < bottom + border)
                if (presence == 0f) null else PanelHoleFrame(pill.scaleAboutCenter(presence), scrolling)
            }
        return PanelFrame(bottom, clip, holes)
    }

    /** Distance that visible rail pills extend past each viewport edge, independent of scroll extent. */
    fun railOverflow(): RailOverflow {
        val anchors = anchors() ?: return RailOverflow(0f, 0f)
        val clip = anchors.viewport
        val pills =
            items.mapNotNull { (id, item) ->
                anchors.panel.boundsOf(item.coordinates).takeIf { id != ActionKey && item.visibility.value() > 0f }
            }
        return RailOverflow(
            left = (clip.left - (pills.minOfOrNull { it.left } ?: clip.left)).coerceAtLeast(0f),
            right = ((pills.maxOfOrNull { it.right } ?: clip.right) - clip.right).coerceAtLeast(0f),
        )
    }

    private fun anchors(): PanelAnchors? {
        revision
        val parent = panel?.takeIf { it.isAttached } ?: return null
        val headerBounds = parent.boundsOf(header)
        val railBounds = parent.boundsOf(rail)
        val clip = parent.boundsOf(viewport)
        return if (headerBounds != null && railBounds != null && clip != null) {
            PanelAnchors(parent, headerBounds, railBounds, clip)
        } else {
            null
        }
    }
}

private data class PanelAnchors(
    val panel: LayoutCoordinates,
    val header: Rect,
    val rail: Rect,
    val viewport: Rect,
)

/** A pill's fade value and whether its group is entering or staying, rather than leaving. */
internal class PillVisibility(
    val value: () -> Float,
    val wanted: () -> Boolean,
)

/**
 * A hole always matches its pill. It switches on or off only while the surface does not cover it,
 * where the change is invisible. Under the surface it grows or shrinks with the pill's fade instead,
 * reaching its target exactly when the fade does, so no frame shows a hole popping in or out.
 */
private class PanelHole(
    val visibility: PillVisibility,
) {
    var coordinates: LayoutCoordinates? = null
    private var presence = visibility.value()
    private var lastFade = presence

    fun resolve(covered: Boolean): Float {
        val target = if (visibility.wanted()) 1f else 0f
        val fade = visibility.value().coerceIn(0f, 1f)
        val remaining = abs(target - lastFade)
        presence =
            when {
                !covered || remaining == 0f -> target
                else -> target + (presence - target) * (abs(target - fade) / remaining).coerceAtMost(1f)
            }
        lastFade = fade
        return presence
    }
}

internal data class PanelHoleFrame(
    val bounds: Rect,
    val scrolling: Boolean,
)

internal data class PanelFrame(
    val bottom: Float,
    val viewport: Rect,
    val holes: List<PanelHoleFrame>,
)

internal data class RailOverflow(
    val left: Float,
    val right: Float,
)

private fun LayoutCoordinates.boundsOf(child: LayoutCoordinates?): Rect? =
    child?.takeIf { it.isAttached }?.let { localBoundingBoxOf(it, clipBounds = false) }

private fun Rect.scaleAboutCenter(scale: Float): Rect =
    Rect(
        center.x - width * scale / 2,
        center.y - height * scale / 2,
        center.x + width * scale / 2,
        center.y + height * scale / 2,
    )
