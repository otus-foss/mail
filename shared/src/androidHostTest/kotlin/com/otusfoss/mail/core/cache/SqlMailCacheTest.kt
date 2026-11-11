package com.otusfoss.mail.core.cache

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

/** Runs the cache contract on a real SQLite database, kept in memory. */
class SqlMailCacheTest : MailCacheContractTest() {
    override fun newCache(): MailCache {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MailDatabase.Schema.create(driver)
        return SqlMailCache(driver)
    }
}
