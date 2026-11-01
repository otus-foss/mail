package com.otus.mail.feature.inbox.presentation

import com.otus.mail.feature.inbox.domain.PhotoCleaning
import com.otus.mail.feature.inbox.domain.RemoteImages

internal val RemoteImages.title: String
    get() =
        when (this) {
            RemoteImages.Always -> "Always"
            RemoteImages.Known -> "In your contacts"
            RemoteImages.Never -> "Never"
        }

internal val RemoteImages.description: String
    get() =
        when (this) {
            RemoteImages.Always -> "Senders can see when you open their mail"
            RemoteImages.Known -> "Contacts and senders you allowed"
            RemoteImages.Never -> "Always ask"
        }

internal val PhotoCleaning.title: String
    get() =
        when (this) {
            PhotoCleaning.Ask -> "Ask each time"
            PhotoCleaning.Always -> "Always remove details"
            PhotoCleaning.Never -> "Keep details"
        }

internal val PhotoCleaning.description: String
    get() =
        when (this) {
            PhotoCleaning.Ask -> "Choose for each photo"
            PhotoCleaning.Always -> "Removes location, time, and camera"
            PhotoCleaning.Never -> "Photos go out as they are"
        }
