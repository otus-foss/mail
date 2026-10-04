package com.otus.mail.designsystem.elements

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.otus.mail.designsystem.theme.MotionTokens

/**
 * Replace a label with a clipped vertical slide and fade. Increasing order moves upward;
 * decreasing order moves downward. The caller supplies ordering, without calendar knowledge here.
 * Unchanged text does not animate. Compose's motion duration scale controls reduced motion.
 */
@Composable
fun AnimatedText(
    text: String,
    order: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    textAlign: TextAlign = TextAlign.Center,
) {
    AnimatedContent(
        targetState = OrderedLabel(text, order),
        modifier = modifier.clipToBounds().clearAndSetSemantics { this.text = AnnotatedString(text) },
        contentKey = { it.text },
        contentAlignment = Alignment.Center,
        transitionSpec = {
            val direction = if (targetState.order >= initialState.order) 1 else -1
            val incoming =
                slideInVertically(tween(MotionTokens.TEXT_REPLACEMENT_MILLIS, easing = MotionTokens.enter)) {
                    direction * it
                } + fadeIn(tween(MotionTokens.TEXT_REPLACEMENT_MILLIS, easing = MotionTokens.enter))
            val outgoing =
                slideOutVertically(tween(MotionTokens.TEXT_EXIT_MILLIS, easing = MotionTokens.exit)) {
                    -direction * it
                } + fadeOut(tween(MotionTokens.TEXT_EXIT_MILLIS, easing = MotionTokens.exit))
            (incoming togetherWith outgoing).using(null)
        },
        label = "Text replacement",
    ) { label ->
        Text(label.text, style = style, textAlign = textAlign)
    }
}

private data class OrderedLabel(
    val text: String,
    val order: Int,
)
