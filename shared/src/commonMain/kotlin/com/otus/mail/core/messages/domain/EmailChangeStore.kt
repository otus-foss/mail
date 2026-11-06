package com.otus.mail.core.messages.domain

import com.otus.storage.StringMapStore

/** Where marks and moves wait for the server, one value per login. It must survive the process. */
interface EmailChangeStore : StringMapStore
