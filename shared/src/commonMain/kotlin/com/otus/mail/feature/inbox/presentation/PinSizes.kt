package com.otus.mail.feature.inbox.presentation

import com.otus.designsystem.mail.sidebar.elements.SidebarTileSize
import com.otus.mail.feature.inbox.domain.PinSize

val PinSize.tileSize: SidebarTileSize
    get() =
        when (this) {
            PinSize.Small -> SidebarTileSize.Small
            PinSize.Wide -> SidebarTileSize.Wide
            PinSize.Full -> SidebarTileSize.Full
        }

val SidebarTileSize.pinSize: PinSize
    get() =
        when (this) {
            SidebarTileSize.Small -> PinSize.Small
            SidebarTileSize.Wide -> PinSize.Wide
            SidebarTileSize.Full -> PinSize.Full
        }
