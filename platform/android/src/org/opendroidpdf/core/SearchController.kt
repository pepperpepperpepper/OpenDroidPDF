package org.opendroidpdf.core

import android.graphics.RectF
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.opendroidpdf.SearchResult
import org.opendroidpdf.app.AppCoroutines

/**
 * Surface for coordinating document search via [MuPdfRepository].
 * Now coroutine-based to be lifecycle-aware and avoid ad-hoc handlers/executors.
 */
class SearchController(private val repository: MuPdfRepository) {

    private var currentJob: Job? = null

    fun pageCount(): Int = repository.getPageCount()

    fun searchPage(pageIndex: Int, query: String?): Array<RectF> {
        if (query.isNullOrBlank()) {
            return emptyArray()
        }
        return repository.searchPage(pageIndex, query)
    }

    @JvmOverloads
    fun startSearch(
        query: String,
        direction: Int,
        startIndex: Int,
        callbacks: SearchCallbacks,
        scope: CoroutineScope = AppCoroutines.mainScope()
    ): SearchJob {
        currentJob?.cancel()
        val pageCount = pageCount()
        val normalizedStart = normalizeIndex(startIndex, pageCount)
        val job = scope.launch(Dispatchers.IO) {
            if (pageCount <= 0 || query.isBlank()) {
                AppCoroutines.launchMain { callbacks.onComplete(null) }
                return@launch
            }
            var index = normalizedStart
            var firstResult: SearchResult? = null
            do {
                if (!isActive) {
                    AppCoroutines.launchMain { callbacks.onCancelled() }
                    return@launch
                }
                val progressIndex = index + 1
                AppCoroutines.launchMain { callbacks.onProgress(progressIndex) }
                val hits = searchPage(index, query)
                if (hits.isNotEmpty()) {
                    val result = SearchResult(query, index, hits, direction)
                    if (direction == 1) result.focusFirst() else result.focusLast()
                    if (firstResult == null) {
                        firstResult = result
                        AppCoroutines.launchMain {
                            callbacks.onResult(result)
                            callbacks.onFirstResult(result)
                        }
                    } else {
                        AppCoroutines.launchMain { callbacks.onResult(result) }
                    }
                }
                index = normalizeIndex(index + direction, pageCount)
            } while (index != normalizedStart)

            AppCoroutines.launchMain { callbacks.onComplete(firstResult) }
        }
        currentJob = job
        return SearchJob(job)
    }

    private fun normalizeIndex(index: Int, pageCount: Int): Int {
        if (pageCount <= 0) return 0
        var result = index % pageCount
        if (result < 0) {
            result += pageCount
        }
        return result
    }

    class SearchJob internal constructor(private val job: Job) {
        fun cancel() { job.cancel() }
        fun isCancelled(): Boolean = !job.isActive
    }
}

interface SearchCallbacks {
    fun onProgress(pageIndex: Int)
    fun onResult(result: SearchResult)
    fun onFirstResult(result: SearchResult)
    fun onComplete(firstResult: SearchResult?)
    fun onCancelled()
}
