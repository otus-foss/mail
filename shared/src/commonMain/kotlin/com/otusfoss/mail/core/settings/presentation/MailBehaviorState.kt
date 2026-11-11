package com.otusfoss.mail.core.settings.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otusfoss.mail.core.settings.domain.ActionBehavior
import com.otusfoss.mail.core.settings.domain.AvatarBehavior
import com.otusfoss.mail.core.settings.domain.ListBehavior
import com.otusfoss.mail.core.settings.domain.MailBehavior
import com.otusfoss.mail.core.settings.domain.MailBehaviorStore
import com.otusfoss.mail.core.settings.domain.PrivacyBehavior
import com.otusfoss.mail.core.settings.domain.ReadBehavior
import com.otusfoss.mail.core.settings.domain.SidebarBehavior
import com.otusfoss.mail.core.settings.domain.WriteBehavior

/** The saved Mail settings. Changes are written to [store] before they are shown. */
class MailBehaviorState(
    private val store: MailBehaviorStore? = null,
    initial: MailBehavior = store?.read() ?: MailBehavior(),
) {
    var behavior by mutableStateOf(initial)
        private set

    fun update(change: (MailBehavior) -> MailBehavior) {
        val next = change(behavior)
        store?.write(next)
        behavior = next
    }

    fun updateList(change: (ListBehavior) -> ListBehavior) = update { it.copy(list = change(it.list)) }

    fun updateRead(change: (ReadBehavior) -> ReadBehavior) = update { it.copy(read = change(it.read)) }

    fun updateWrite(change: (WriteBehavior) -> WriteBehavior) = update { it.copy(write = change(it.write)) }

    fun updateActions(change: (ActionBehavior) -> ActionBehavior) = update { it.copy(actions = change(it.actions)) }

    fun updateAvatars(change: (AvatarBehavior) -> AvatarBehavior) = update { it.copy(avatars = change(it.avatars)) }

    fun updatePrivacy(change: (PrivacyBehavior) -> PrivacyBehavior) = update { it.copy(privacy = change(it.privacy)) }

    fun updateSidebar(change: (SidebarBehavior) -> SidebarBehavior) = update { it.copy(sidebar = change(it.sidebar)) }
}
