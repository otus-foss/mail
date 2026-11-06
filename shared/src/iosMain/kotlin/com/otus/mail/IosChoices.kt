package com.otus.mail

import com.otus.storage.StoredChoice
import com.otus.storage.UserDefaultsValues

// Each choice keeps its own user defaults key. Changing one loses what users saved.

internal fun themeChoice(): StoredChoice = StoredChoice(UserDefaultsValues("otus.mail."), "theme")

internal fun animationChoice(): StoredChoice = StoredChoice(UserDefaultsValues("otus."), "animation_speed")

internal fun dateFormatChoice(): StoredChoice = StoredChoice(UserDefaultsValues("otus."), "date_format")
