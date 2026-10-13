package com.otus.mail.feature.updates.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import com.otus.designsystem.elements.SidebarEntry
import com.otus.designsystem.resources.Res
import com.otus.designsystem.resources.symbol_arrow_downward
import com.otus.mail.feature.updates.domain.NightlyBuild

/**
 * A sidebar row that opens [build]'s APK link. The browser downloads it, and Android's installer
 * takes over from the download.
 */
@Composable
fun NightlyUpdateEntry(build: NightlyBuild) {
    val uriHandler = LocalUriHandler.current
    SidebarEntry(
        "Get nightly ${build.number}",
        Res.drawable.symbol_arrow_downward,
        selected = false,
        onClick = { uriHandler.openUri(build.download) },
    )
}
