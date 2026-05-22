package org.skepsun.kototoro.core.exceptions.resolve

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.skepsun.kototoro.R
import org.skepsun.kototoro.browser.cloudflare.CloudFlareActivity
import org.skepsun.kototoro.core.exceptions.CloudFlareProtectedException
import org.skepsun.kototoro.core.model.UnknownContentSource
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.ui.util.ForegroundActivityHolder
import org.skepsun.kototoro.core.util.ext.printStackTraceDebug
import org.skepsun.kototoro.parsers.model.ContentSource
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaptchaAutoResolveCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val foregroundActivityHolder: ForegroundActivityHolder,
) {

    private val mutex = Mutex()
    private val inFlight = ConcurrentHashMap<ContentSource, CompletableDeferred<Boolean>>()
    private val pendingActivityResult = ConcurrentHashMap<ContentSource, CompletableDeferred<Boolean>>()
    private val recentSuccessAt = ConcurrentHashMap<ContentSource, Long>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun notifyResolveResult(source: ContentSource, success: Boolean) {
        pendingActivityResult.remove(source)?.complete(success)
    }

    suspend fun resolve(source: ContentSource, exception: CloudFlareProtectedException): Boolean {
        return resolveInternal(source, exception, allowInteractiveFallback = true, showToast = true)
    }

    suspend fun resolveInBackground(source: ContentSource, exception: CloudFlareProtectedException): Boolean {
        return resolveInternal(source, exception, allowInteractiveFallback = false, showToast = false)
    }

    private suspend fun resolveInternal(
        source: ContentSource,
        exception: CloudFlareProtectedException,
        allowInteractiveFallback: Boolean,
        showToast: Boolean,
    ): Boolean {
        inFlight[source]?.let { return it.await() }
        val lastSuccessAt = recentSuccessAt[source]
        if (lastSuccessAt != null && System.currentTimeMillis() - lastSuccessAt < RECENT_SUCCESS_COOLDOWN_MS) {
            return false
        }
        val deferred = mutex.withLock {
            inFlight[source]?.let { return@withLock it }
            val recheckSuccessAt = recentSuccessAt[source]
            if (recheckSuccessAt != null && System.currentTimeMillis() - recheckSuccessAt < RECENT_SUCCESS_COOLDOWN_MS) {
                return@withLock CompletableDeferred(false)
            }
            CompletableDeferred<Boolean>().also { fresh ->
                inFlight[source] = fresh
                if (showToast) {
                    showSolvingToast()
                }
                scope.launch {
                    runOrchestration(source, exception, allowInteractiveFallback, fresh)
                }
            }
        }
        return deferred.await()
    }

    private suspend fun runOrchestration(
        source: ContentSource,
        exception: CloudFlareProtectedException,
        allowInteractiveFallback: Boolean,
        deferred: CompletableDeferred<Boolean>,
    ) {
        try {
            val hiddenPassed = launchAndAwait(source, exception, hidden = true)
            val finalResult = if (hiddenPassed) {
                true
            } else if (allowInteractiveFallback) {
                launchAndAwait(source, exception, hidden = false)
            } else {
                false
            }
            if (finalResult) {
                recentSuccessAt[source] = System.currentTimeMillis()
            }
            deferred.complete(finalResult)
        } catch (e: Throwable) {
            e.printStackTraceDebug()
            deferred.complete(false)
        } finally {
            inFlight.remove(source)
            pendingActivityResult.remove(source)
        }
    }

    private suspend fun launchAndAwait(
        source: ContentSource,
        exception: CloudFlareProtectedException,
        hidden: Boolean,
    ): Boolean {
        if (source == UnknownContentSource) {
            return false
        }
        val launcher = foregroundActivityHolder.current
        if (hidden && launcher == null) {
            return false
        }
        val resultDeferred = CompletableDeferred<Boolean>()
        pendingActivityResult[source] = resultDeferred
        val intent = AppRouter.cloudFlareResolveIntent(context, exception, hidden = hidden).apply {
            putExtra(CloudFlareActivity.EXTRA_AUTO_RESOLVE, true)
        }
        launcher?.startActivity(intent) ?: run {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
        return resultDeferred.await()
    }

    private fun showSolvingToast() {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, R.string.captcha_solving, Toast.LENGTH_LONG).show()
        }
    }

    private companion object {
        const val RECENT_SUCCESS_COOLDOWN_MS = 30_000L
    }
}
