package org.opendroidpdf.core

import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import org.opendroidpdf.SearchResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Surface for coordinating document search via [MuPdfRepository].
 * Keeps query helpers in Kotlin so UI layers can migrate off direct MuPDFCore access.
 */
class SearchController(private val repository: MuPdfRepository) {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun pageCount(): Int = repository.getPageCount()

    fun searchPage(pageIndex: Int, query: String?): Array<RectF> {
        if (query.isNullOrBlank()) {
            return emptyArray()
        }
        return repository.searchPage(pageIndex, query)
    }

    fun startSearch(
        query: String,
        direction: Int,
        startIndex: Int,
        callbacks: SearchCallbacks
    ): SearchJob {
        val pageCount = pageCount()
        val normalizedStart = normalizeIndex(startIndex, pageCount)
        val future = executor.submit<SearchResult?> {
            if (pageCount <= 0 || query.isBlank()) {
                mainHandler.post { callbacks.onComplete(null) }
                return@submit null
            }
            var index = normalizedStart
            var firstResult: SearchResult? = null
            var cancelled = false
            do {
                if (Thread.currentThread().isInterrupted) {
                    cancelled = true
                    break
                }
                mainHandler.post { callbacks.onProgress(index + 1) }
                val hits = searchPage(index, query)
                if (hits.isNotEmpty()) {
                    val result = SearchResult(query, index, hits, direction)
                    if (direction == 1) {
                        result.focusFirst()
                    } else {
                        result.focusLast()
                    }
                    if (firstResult == null) {
                        firstResult = result
                        mainHandler.post {
                            callbacks.onResult(result)
                            callbacks.onFirstResult(result)
                        }
                    } else {
                        mainHandler.post { callbacks.onResult(result) }
                    }
                }
                index = normalizeIndex(index + direction, pageCount)
            } while (index != normalizedStart)

            if (cancelled) {
                mainHandler.post { callbacks.onCancelled() }
            } else {
                mainHandler.post { callbacks.onComplete(firstResult) }
            }
            firstResult
        }
        return SearchJob(future)
    }

    private fun normalizeIndex(index: Int, pageCount: Int): Int {
        if (pageCount <= 0) return 0
        var result = index % pageCount
        if (result < 0) {
            result += pageCount
        }
        return result
    }

    class SearchJob internal constructor(private val future: Future<*>) {
        fun cancel() {
            future.cancel(true)
        }

        fun isCancelled(): Boolean = future.isCancelled
    }
}

interface SearchCallbacks {
    fun onProgress(pageIndex: Int)
    fun onResult(result: SearchResult)
    fun onFirstResult(result: SearchResult)
    fun onComplete(firstResult: SearchResult?)
    fun onCancelled()
}
