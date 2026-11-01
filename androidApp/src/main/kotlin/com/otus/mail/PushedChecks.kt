package com.otus.mail

import java.util.concurrent.atomic.AtomicLong

/**
 * The pushes that still need a new-mail check. A check covers every push that arrived before it
 * started, so of the checks pushes queue during one, only the first still runs; the rest end at
 * once. Counts start again with the process, and the first check after that always runs.
 */
internal class PushedChecks {
    private val received = AtomicLong()
    private val covered = AtomicLong(-1)

    /** Call it for each push, before its check is queued. */
    fun received() {
        received.incrementAndGet()
    }

    /**
     * Whether a check should run now: always for a periodic one, and for a [pushed] one while a push
     * arrived after the last check started. A check that runs covers every push so far.
     */
    fun start(pushed: Boolean): Boolean {
        val pushes = received.get()
        val due = !pushed || covered.get() < pushes
        if (due) covered.set(pushes)
        return due
    }
}
