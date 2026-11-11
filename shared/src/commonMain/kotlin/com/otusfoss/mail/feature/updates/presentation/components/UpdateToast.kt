package com.otusfoss.mail.feature.updates.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.otusfoss.core.designsystem.shared.controls.elements.UiAction
import com.otusfoss.core.designsystem.shared.feedback.components.ToastMessage
import com.otusfoss.core.designsystem.shared.feedback.components.ToastState
import com.otusfoss.mail.feature.updates.presentation.NightlyUpdateState

/**
 * Says in [toasts] that a newer build is out once [update] finds one, while [enabled]. Its action
 * runs [onView], which opens what changed. Mail checks each time it comes on screen, and the toast
 * shows once for each newer build it finds.
 */
@Composable
fun UpdateToastEffect(
    update: NightlyUpdateState?,
    enabled: Boolean,
    toasts: ToastState,
    onView: () -> Unit,
) {
    val available = update?.available ?: return
    val shows by rememberUpdatedState(enabled)
    LaunchedEffect(available.number) { if (shows) toasts.show(updateToast(onView)) }
}

/**
 * The toast for a newer build; [onView] opens what changed. It leaves on the timer for toasts with
 * an action, because the sidebar's version mark keeps offering the update. Its group makes a
 * second check replace it instead of stacking another.
 */
private fun updateToast(onView: () -> Unit): ToastMessage =
    ToastMessage("Update available", UiAction("View", onClick = onView), group = UPDATE_GROUP)

private const val UPDATE_GROUP = "update"
