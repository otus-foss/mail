package com.otus.mail.feature.sidebar.presentation

import androidx.compose.runtime.saveable.listSaver

// Each key is saved as its length, then its levels, so no character in a folder name can split it.
internal val KeysSaver =
    listSaver<Set<List<String>>, String>(
        save = { keys -> keys.flatMap { listOf(it.size.toString()) + it } },
        restore = { saved ->
            buildSet {
                var index = 0
                while (index < saved.size) {
                    val size = saved[index].toInt()
                    add(saved.subList(index + 1, index + 1 + size).toList())
                    index += size + 1
                }
            }
        },
    )
