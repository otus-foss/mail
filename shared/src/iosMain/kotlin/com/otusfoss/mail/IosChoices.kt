package com.otusfoss.mail

import com.otusfoss.core.storage.StoredChoice
import com.otusfoss.core.storage.UserDefaultsValues

// Each choice keeps its own user defaults key. Changing one loses what users saved.

internal fun themeChoice(): StoredChoice = StoredChoice(UserDefaultsValues("otus.mail."), "theme")

internal fun animationChoice(): StoredChoice = StoredChoice(UserDefaultsValues("otus."), "animation_speed")

internal fun dateFormatChoice(): StoredChoice = StoredChoice(UserDefaultsValues("otus."), "date_format")
