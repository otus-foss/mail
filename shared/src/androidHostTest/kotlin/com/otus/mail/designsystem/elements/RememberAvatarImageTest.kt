package com.otus.mail.designsystem.elements

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class RememberAvatarImageTest {
    @Test
    fun imageCachedBetweenRememberAndEffectAppearsInTheRow() =
        runTest {
            val bytes = byteArrayOf(1)
            val image = AvatarTestImage()
            val cache = DecodedAvatarCache { image }
            var shown: ImageBitmap? = null
            withComposition { composition, clock ->
                composition.setContent {
                    val decoded = rememberAvatarImage(bytes, cache)
                    SideEffect { shown = decoded }
                }
                assertNull(shown)
                // Another row finishes its decode before this row's effect starts.
                cache.load(bytes)
                runCurrent()
                Snapshot.sendApplyNotifications()
                runCurrent()
                clock.sendFrame(0L)
                runCurrent()
                assertSame(image, shown)
            }
        }

    private suspend fun TestScope.withComposition(block: suspend (Composition, BroadcastFrameClock) -> Unit) {
        val clock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + clock)
        val runner = launch(clock) { recomposer.runRecomposeAndApplyChanges() }
        val composition = Composition(EmptyApplier(), recomposer)
        try {
            block(composition, clock)
        } finally {
            composition.dispose()
            recomposer.cancel()
            runner.join()
        }
    }

    private class EmptyApplier : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(
            index: Int,
            instance: Unit,
        ) = Unit

        override fun insertBottomUp(
            index: Int,
            instance: Unit,
        ) = Unit

        override fun remove(
            index: Int,
            count: Int,
        ) = Unit

        override fun move(
            from: Int,
            to: Int,
            count: Int,
        ) = Unit

        override fun onClear() = Unit
    }
}
