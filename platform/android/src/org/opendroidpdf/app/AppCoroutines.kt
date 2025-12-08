package org.opendroidpdf.app

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Central coroutine utilities so legacy Handler/Executor usage can migrate
 * toward lifecycle-aware scopes. Java callers can use the @JvmStatic helpers
 * to post work to the main thread (optionally delayed) and cancel Jobs safely.
 */
object AppCoroutines {
    private val appJob = SupervisorJob()
    private val appMainScope = CoroutineScope(appJob + Dispatchers.Main.immediate)
    private val appIoScope = CoroutineScope(appJob + Dispatchers.IO)

    @JvmStatic
    fun mainScope(): CoroutineScope = appMainScope

    @JvmStatic
    fun newMainScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @JvmStatic
    fun ioScope(): CoroutineScope = appIoScope

    @JvmStatic
    fun lifecycleScope(owner: LifecycleOwner): CoroutineScope = owner.lifecycleScope

    @JvmStatic
    fun launchMain(scope: CoroutineScope = appMainScope, block: Runnable): Job =
        scope.launch(Dispatchers.Main.immediate) { block.run() }

    @JvmStatic
    fun launchMainDelayed(scope: CoroutineScope = appMainScope, delayMs: Long, block: Runnable): Job =
        scope.launch(Dispatchers.Main.immediate) {
            delay(delayMs)
            block.run()
        }

    @JvmStatic
    fun launchIo(scope: CoroutineScope = appIoScope, block: Runnable): Job =
        scope.launch(Dispatchers.IO) { block.run() }

    @JvmStatic
    fun cancel(job: Job?) {
        job?.cancel()
    }

    @JvmStatic
    fun cancelScope(scope: CoroutineScope?) {
        scope?.cancel()
    }
}
