package com.otus.mail.feature.inbox.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.otus.mail.feature.inbox.domain.PhotoCleaning
import com.otus.mail.feature.inbox.domain.PickedFile
import kotlinx.coroutines.CompletableDeferred

/**
 * Whether picked photos attach without their metadata and names, as [choice] says. Under
 * [PhotoCleaning.Ask], [question] holds how many photos wait for [answer].
 */
class PhotoCleaningQuestion(
    private val choice: () -> PhotoCleaning,
) {
    var question by mutableStateOf<Int?>(null)
        private set
    private var reply: CompletableDeferred<Boolean>? = null

    /** Answers [question]: [clean] photos attach without their metadata and under random names. */
    fun answer(clean: Boolean) {
        question = null
        reply?.complete(clean)
    }

    /** [files] with each cleanable photo [PickedFile.cleaned] when the choice, or the answer, says so. */
    suspend fun cleanedAsChosen(files: List<PickedFile>): List<PickedFile> {
        val photos = files.count { it.cleanable }
        val clean =
            photos > 0 &&
                when (choice()) {
                    PhotoCleaning.Always -> true
                    PhotoCleaning.Never -> false
                    PhotoCleaning.Ask -> ask(photos)
                }
        return if (clean) files.map { if (it.cleanable) it.cleaned() else it } else files
    }

    private suspend fun ask(photos: Int): Boolean {
        val answer = CompletableDeferred<Boolean>().also { reply = it }
        question = photos
        return try {
            answer.await()
        } finally {
            question = null
        }
    }
}
