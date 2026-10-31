package com.otus.mail

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.Color
import com.otus.designsystem.theme.brand.AppIconStyle
import com.otus.designsystem.theme.colors.AccentPreset

/**
 * Shows the launcher entry whose icon matches [style] and [accent] and hides the rest. Each icon
 * is an activity alias in the manifest, named after an [AccentPreset] for [AppIconStyle.Accent]
 * and after the style otherwise; a custom accent shows the closest preset's icon.
 */
fun Context.showLauncherIcon(
    style: AppIconStyle,
    accent: Color,
) {
    val shown = if (style == AppIconStyle.Accent) AccentPreset.closest(accent).name else style.name
    AccentPreset.entries
        .map { it.name }
        .plus(AppIconStyle.entries.filter { it != AppIconStyle.Accent }.map { it.name })
        .forEach { name ->
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
