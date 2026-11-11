package com.otusfoss.mail.core.cache

class InMemoryMailCacheTest : MailCacheContractTest() {
    override fun newCache(): MailCache = InMemoryMailCache()
}
