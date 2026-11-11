package com.otusfoss.mail.feature.onboarding.presentation

/** The first run's steps, in order. Their count is fixed, so the progress never changes its total. */
enum class OnboardingStep {
    Welcome,
    Connect,
    Privacy,
    Notifications,
    Look,
    Ready,
    ;

    /** This step's place, counted from 1, and how many steps there are. */
    val progress: Pair<Int, Int> get() = ordinal + 1 to entries.size
}
