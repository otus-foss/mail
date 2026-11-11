package com.otusfoss.mail.feature.notifications.domain

/** Registration and server subscription state for one login. */
enum class PushDeliveryStatus {
    Connecting,
    Active,
    ServerRefused,
    VapidRequired,
    InvalidKey,
    Unavailable,
    RegistrationFailed,
}
