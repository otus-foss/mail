package com.otus.mail.core.cache

class InMemoryMailCacheTest : MailCacheContractTest() {
    override fun newCache(): MailCache = InMemoryMailCache()
}
