package com.otus.mail

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.Color
import com.otus.designsystem.theme.AccentPreset
import com.otus.designsystem.theme.AppIconStyle

private const val MONOCHROME = "Monochrome"

/**
 * Shows the launcher entry whose icon matches [style] and [accent] and hides the rest. Each icon
 * is an activity alias in the manifest, named after an [AccentPreset] or [MONOCHROME]; a custom
 * accent shows the closest preset's icon.
 */
fun Context.showLauncherIcon(
    style: AppIconStyle,
    accent: Color,
) {
    val shown = if (style == AppIconStyle.Monochrome) MONOCHROME else AccentPreset.closest(accent).name
    (AccentPreset.entries.map { it.name } + MONOCHROME).forEach { name ->
        val component = ComponentName(packageName, "com.otus.mail.icon.$name")
        val state =
            if (name == shown) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
        if (packageManager.getComponentEnabledSetting(component) != state) {
            packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        }
    }
}
