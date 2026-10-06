package com.otus.mail.designsystem.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.otus.mail.designsystem.theme.MotionTokens
import com.otus.mail.designsystem.theme.SidebarTokens
import kotlin.math.abs

/**
 * Whether the [Sidebar] is open, and how far it currently shows. Buttons call [open] and [close];
 * swipes move it directly and then settle on the nearer side, or on the side a fling points to.
 */
@Stable
class SidebarState(
    initiallyOpen: Boolean = false,
) {
    /** The side the sidebar is at or heading to. */
    var isOpen by mutableStateOf(initiallyOpen)
        private set

    /** 0 when hidden, 1 when fully shown. */
    var progress by mutableFloatStateOf(if (initiallyOpen) 1f else 0f)
        private set

    /** Panel width in pixels, which converts a swipe distance into progress. */
    internal var widthPx by mutableFloatStateOf(0f)

    private val mutex = MutatorMutex()

    suspend fun open() = animateTo(open = true)

    suspend fun close() = animateTo(open = false)

    /** Stops a running animation so a swipe can take over. */
    internal suspend fun stop() = mutex.mutate(MutatePriority.UserInput) {}

    /** [delta] is the movement toward the open side, in pixels. */
    internal fun dragBy(delta: Float) {
        if (widthPx > 0f) progress = (progress + delta / widthPx).coerceIn(0f, 1f)
    }

    /** [velocity] is the release speed toward the open side, in pixels per second. */
    internal suspend fun settle(
        velocity: Float,
        flingThreshold: Float,
    ) {
        val open =
            when {
                velocity > flingThreshold -> true
                velocity < -flingThreshold -> false
                else -> progress > HALFWAY
            }
        animateTo(open)
    }

    private suspend fun animateTo(open: Boolean) =
        mutex.mutate {
            isOpen = open
            val target = if (open) 1f else 0f
            // A sidebar released part-way only travels the remaining distance, at the same pace.
            val remaining = abs(target - progress)
            val millis = if (open) SidebarTokens.OPEN_MILLIS else SidebarTokens.CLOSE_MILLIS
            val easing = if (open) MotionTokens.enter else MotionTokens.exit
            val spec = tween<Float>((millis * remaining).toInt(), easing = easing)
            animate(progress, target, animationSpec = spec) { value, _ -> progress = value }
        }

    companion object {
        private const val HALFWAY = 0.5f

        val Saver: Saver<SidebarState, Boolean> = Saver(save = { it.isOpen }, restore = { SidebarState(it) })
    }
}

@Composable
fun rememberSidebarState(): SidebarState = rememberSaveable(saver = SidebarState.Saver) { SidebarState() }
