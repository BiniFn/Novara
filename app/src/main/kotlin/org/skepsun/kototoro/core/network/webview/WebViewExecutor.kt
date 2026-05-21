package org.skepsun.kototoro.core.network.webview

import android.content.Context
import android.util.AndroidRuntimeException
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.CookieManager
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import org.skepsun.kototoro.core.exceptions.CloudFlareException
import org.skepsun.kototoro.core.network.CommonHeaders
import org.skepsun.kototoro.core.network.cookies.MutableCookieJar
import org.skepsun.kototoro.core.network.proxy.ProxyProvider
import org.skepsun.kototoro.core.network.webview.adblock.AdBlock
import org.skepsun.kototoro.core.parser.ContentRepository
import org.skepsun.kototoro.core.parser.ParserContentRepository
import org.skepsun.kototoro.core.parser.kotatsu.KotatsuParserRepository
import org.skepsun.kototoro.core.parser.tvbox.TVBoxPlayback
import org.skepsun.kototoro.core.parser.legado.LegadoNetworkUtils
import org.skepsun.kototoro.core.ui.util.ForegroundActivityHolder
import org.skepsun.kototoro.core.util.ext.configureForParser
import org.skepsun.kototoro.core.util.ext.printStackTraceDebug
import org.skepsun.kototoro.browser.cloudflare.CloudFlareClient
import org.skepsun.kototoro.browser.cloudflare.CloudFlareInterceptClient
import org.skepsun.kototoro.parsers.config.ConfigKey
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.parsers.network.CloudFlareHelper
import org.skepsun.kototoro.parsers.util.runCatchingCancellable
import java.lang.ref.WeakReference
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Cookie
import java.util.concurrent.TimeUnit
import org.json.JSONObject

@Singleton
class WebViewExecutor @Inject constructor(
	@ApplicationContext private val context: Context,
	private val proxyProvider: ProxyProvider,
	private val cookieJar: MutableCookieJar,
    private val adBlock: AdBlock,
    private val foregroundActivityHolder: ForegroundActivityHolder,
	private val mangaRepositoryFactoryProvider: Provider<ContentRepository.Factory>,
) {

	private var webViewCached: WeakReference<WebView>? = null
	private val mutex = Mutex()
    private val recentFailureUntil = ConcurrentHashMap<String, Long>()

	val defaultUserAgent: String? by lazy {
		try {
			WebSettings.getDefaultUserAgent(context)
		} catch (e: AndroidRuntimeException) {
			e.printStackTraceDebug()
			// Probably WebView is not available
			null
		}
	}

	/**
	 * Execute a same-origin GET request in a real WebView context and return response data.
	 * Useful for sources where Cloudflare still challenges OkHttp even with valid cookies.
	 */
	suspend fun fetchWithBrowserContext(
		url: String,
		userAgent: String? = null,
		headers: Map<String, String> = emptyMap(),
		settleDelayMs: Long = 1200,
		timeoutMs: Long = 30000,
	): BrowserFetchResult? = mutex.withLock {
		withContext(Dispatchers.Main.immediate) {
			val target = url.toHttpUrlOrNull() ?: return@withContext null
			val webView = obtainWebView()
			try {
				android.util.Log.d("WebViewExecutor", "fetchWithBrowserContext start: $url")
				webView.configureForParser(userAgent, blockImages = true)
				withTimeout(timeoutMs) {
					// Ensure browser context is established on the same origin first.
					suspendCancellableCoroutine<Unit> { cont ->
						webView.webViewClient = object : WebViewClient() {
							override fun onPageFinished(view: WebView?, loadedUrl: String?) {
								if (cont.isActive) {
									cont.resume(Unit)
								}
							}
						}
						val baseUrl = target.newBuilder()
							.encodedPath("/")
							.query(null)
							.fragment(null)
							.build()
							.toString()
						webView.loadUrl(baseUrl)
					}
					kotlinx.coroutines.delay(settleDelayMs)

					val resolvedOriginHost = webView.url?.toHttpUrlOrNull()?.host ?: target.host
					val targetUrlToFetch = if (resolvedOriginHost != target.host) {
						target.newBuilder().host(resolvedOriginHost).build().toString()
					} else {
						url
					}

					val allowedHeaders = headers.filterKeys { key ->
						!key.equals("Referer", ignoreCase = true) && !key.equals("Origin", ignoreCase = true)
					}
					val jsHeaders = JSONObject(allowedHeaders).toString()
					val js = """
						window.__kototoroFetchResult = null;
						(async () => {
						  try {
						  	// Wait for document to be ready if it's not already
						    if (document.readyState === 'loading') {
						        await new Promise(resolve => {
						            document.addEventListener('DOMContentLoaded', resolve);
						            setTimeout(resolve, 3000);
						        });
						    }
						    const response = await fetch(${JSONObject.quote(targetUrlToFetch)}, {
						      method: 'GET',
						      credentials: 'include',
						      headers: $jsHeaders,
						    });
						    const text = await response.text();
						    const responseHeaders = {};
						    response.headers.forEach((value, key) => { responseHeaders[key] = value; });
						    window.__kototoroFetchResult = JSON.stringify({
						      ok: true,
						      status: response.status,
						      statusText: response.statusText || '',
						      url: response.url || ${JSONObject.quote(url)},
						      headers: responseHeaders,
						      body: text || '',
						    });
						  } catch (e) {
						    window.__kototoroFetchResult = JSON.stringify({
						      ok: false,
						      error: String(e),
						      errorName: e?.name ? String(e.name) : '',
						      errorMessage: e?.message ? String(e.message) : '',
						      errorStack: e?.stack ? String(e.stack) : '',
						    });
						  }
						})();
					""".trimIndent()

					webView.evaluateJavascript(js, null)
					var raw = ""
					while (isActive) {
						val pollResult = suspendCancellableCoroutine<String> { cont ->
							webView.evaluateJavascript("window.__kototoroFetchResult") { result ->
								if (cont.isActive) {
									cont.resume(decodeJavascriptString(result))
								}
							}
						}
						if (pollResult.isNotBlank() && pollResult != "null") {
							raw = pollResult
							webView.evaluateJavascript("window.__kototoroFetchResult = null;", null)
							break
						}
						kotlinx.coroutines.delay(100)
					}
					if (raw.isBlank()) {
						android.util.Log.w(
							"WebViewExecutor",
							"fetchWithBrowserContext empty JS result"
						)
						return@withTimeout tryNavigationFetchFallback(webView, targetUrlToFetch, headers)
					}
					val json = runCatching { JSONObject(raw) }.onFailure {
						android.util.Log.w(
							"WebViewExecutor",
							"fetchWithBrowserContext JSON parse failed: ${it.message}; rawPreview=${raw.take(200)}",
						)
					}.getOrNull() ?: return@withTimeout tryNavigationFetchFallback(webView, targetUrlToFetch, headers)
					val fetchStatus = json.optInt("status")
					val fetchBody = json.optString("body")
					val isCloudflareBlock = (fetchStatus == 403 || fetchStatus == 503) && 
						(fetchBody.contains("cf-browser-verification") || 
						 fetchBody.contains("Just a moment") || 
						 fetchBody.contains("__cf_chl_opt") ||
						 fetchBody.contains("Adscore"))

					if (!json.optBoolean("ok") || isCloudflareBlock) {
						android.util.Log.w(
							"WebViewExecutor",
							"fetchWithBrowserContext failed or hit WAF (ok=${json.optBoolean("ok")}, status=$fetchStatus, isCF=$isCloudflareBlock). Falling back to navigation.",
						)
						return@withTimeout tryNavigationFetchFallback(webView, targetUrlToFetch, headers)
					}
					val responseHeadersObj = json.optJSONObject("headers")
					val responseHeaders = linkedMapOf<String, String>()
					if (responseHeadersObj != null) {
						val keys = responseHeadersObj.keys()
						while (keys.hasNext()) {
							val key = keys.next()
							responseHeaders[key] = responseHeadersObj.optString(key)
						}
					}
					BrowserFetchResult(
						status = json.optInt("status"),
						statusText = json.optString("statusText"),
						url = json.optString("url"),
						headers = responseHeaders,
						body = json.optString("body"),
					)
				}
			} finally {
				android.util.Log.d("WebViewExecutor", "fetchWithBrowserContext end: $url")
				webView.reset()
			}
		}
	}

	private suspend fun tryNavigationFetchFallback(
		webView: WebView,
		url: String,
		headers: Map<String, String>,
	): BrowserFetchResult? {
		android.util.Log.i("WebViewExecutor", "fetchWithBrowserContext fallback to navigation: $url")
		var statusCode: Int? = null
		var statusText: String? = null
		suspendCancellableCoroutine<Unit> { cont ->
			webView.webViewClient = object : WebViewClient() {
				override fun onReceivedHttpError(
					view: WebView?,
					request: WebResourceRequest?,
					errorResponse: android.webkit.WebResourceResponse?
				) {
					if (request?.isForMainFrame == true) {
						statusCode = errorResponse?.statusCode
						statusText = errorResponse?.reasonPhrase
					}
				}

				override fun onPageFinished(view: WebView?, loadedUrl: String?) {
					if (cont.isActive) {
						cont.resume(Unit)
					}
				}
			}
			if (headers.isNotEmpty()) {
				webView.loadUrl(url, headers)
			} else {
				webView.loadUrl(url)
			}
		}
		kotlinx.coroutines.delay(500)

		val contentType = suspendCancellableCoroutine<String> { cont ->
			webView.evaluateJavascript("document.contentType || ''") { result ->
				cont.resume(decodeJavascriptString(result))
			}
		}
		val body = suspendCancellableCoroutine<String> { cont ->
			webView.evaluateJavascript(
				"""(function() {
					const html = document.documentElement ? document.documentElement.outerHTML : '';
					if (html.includes('cf-browser-verification') || html.includes('__cf_chl_opt') || html.includes('turnstile') || html.includes('cf_chl') || html.includes('Cloudflare') || html.includes('Ray ID')) {
						return html;
					}
					
					// Detect if the response is actually JSON dumped into the browser
					const text = document.body ? (document.body.innerText || document.body.textContent || '').trim() : '';
					if ((text.startsWith('{') && text.endsWith('}')) || (text.startsWith('[') && text.endsWith(']'))) {
						try {
							JSON.parse(text);
							return text; // It's valid JSON, return stripped of WebView HTML wrappers
						} catch(e) { }
					}
					
					return html; // Return full HTML for JSoup parsers
				})()"""
			) { result ->
				cont.resume(decodeJavascriptString(result))
			}
		}
		if (body.isBlank()) {
			android.util.Log.w("WebViewExecutor", "navigation fallback produced empty body")
			return null
		}
		val responseHeaders = linkedMapOf<String, String>()
		if (contentType.isNotBlank()) {
			responseHeaders["content-type"] = contentType
		}
		val isCloudflare = body.contains("cf-browser-verification") || body.contains("__cf_chl_opt") || body.contains("turnstile") || body.contains("cf_chl", ignoreCase = true) || body.contains("Cloudflare") || body.contains("Ray ID")
		if (isCloudflare) {
			responseHeaders["server"] = "cloudflare"
		}
		val code = if (isCloudflare) 403 else (statusCode ?: 200)
		val message = statusText.orEmpty()
		android.util.Log.i(
			"WebViewExecutor",
			"navigation fallback success: status=$code contentType=${contentType.ifBlank { "<empty>" }} bodyLength=${body.length} isCloudflare=$isCloudflare",
		)
		return BrowserFetchResult(
			status = code,
			statusText = message,
			url = url,
			headers = responseHeaders,
			body = body,
		)
	}

	suspend fun evaluateJs(baseUrl: String?, script: String): String? = mutex.withLock {
		withContext(Dispatchers.Main.immediate) {
			val webView = obtainWebView()
			try {
				if (!baseUrl.isNullOrEmpty()) {
					suspendCoroutine { cont ->
						webView.webViewClient = ContinuationResumeWebViewClient(cont)
						webView.loadDataWithBaseURL(baseUrl, " ", "text/html", null, null)
					}
				}
				suspendCoroutine { cont ->
					webView.evaluateJavascript(script) { result ->
						cont.resume(result?.takeUnless { it == "null" })
					}
				}
			} finally {
				webView.reset()
			}
		}
	}

	suspend fun tryResolveCaptcha(exception: CloudFlareException, timeout: Long): Boolean {
        val cooldownHost = runCatching { URI(exception.url).host?.lowercase() }.getOrNull()
        if (cooldownHost != null) {
            val now = System.currentTimeMillis()
            val skipUntil = recentFailureUntil[cooldownHost]
            if (skipUntil != null) {
                if (skipUntil > now) {
                    Log.d(TAG, "Skipping captcha auto-resolve for $cooldownHost (cooled down for ${skipUntil - now}ms)")
                    return false
                }
                recentFailureUntil.remove(cooldownHost)
            }
        }
        val resolved = mutex.withLock {
            if (cooldownHost != null) {
                val skipUntil = recentFailureUntil[cooldownHost]
                if (skipUntil != null && skipUntil > System.currentTimeMillis()) {
                    return@withLock false
                }
            }
            runCatchingCancellable { proxyProvider.applyWebViewConfig() }.onFailure { it.printStackTraceDebug() }
            withContext(Dispatchers.Main.immediate) {
                val activity = foregroundActivityHolder.current
                val webView: WebView
                val host: ViewGroup?
                val isThrowaway: Boolean
                if (activity != null) {
                    webView = WebView(activity).apply { configureForParser(null) }
                    host = attachToHost(webView, activity)
                    isThrowaway = true
                } else {
                    webView = obtainWebView()
                    host = null
                    isThrowaway = false
                }
                try {
                    exception.source.getUserAgent()?.let {
                        webView.settings.userAgentString = it
                    }
                    val useInterception = shouldUseCloudFlareInterception(exception.source)
                    val resolved = withTimeoutOrNull(timeout) {
                        suspendCancellableCoroutine { cont ->
                            webView.webViewClient = createCloudFlareClient(
                                webView = webView,
                                exception = exception,
                                continuation = cont,
                                useInterception = useInterception,
                            )
                            webView.loadUrl(exception.url)
                        }
                    }
                    if (resolved == null) {
                        Log.w(TAG, "Captcha auto-resolve timed out for ${exception.url}, dumping page HTML:")
                        dumpPageHtml(webView)
                    }
                    resolved == true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    exception.addSuppressed(e)
                    e.printStackTraceDebug()
                    false
                } finally {
                    if (isThrowaway) {
                        runCatching { webView.stopLoading() }
                        webView.webViewClient = WebViewClient()
                        host?.let { detachFromHost(webView, it) }
                        runCatching { webView.destroy() }
                    } else {
                        webView.reset()
                    }
                }
            }
        }
        if (cooldownHost != null) {
            if (resolved) {
                recentFailureUntil.remove(cooldownHost)
            } else {
                recentFailureUntil[cooldownHost] = System.currentTimeMillis() + FAILURE_COOLDOWN_MS
            }
        }
        return resolved
	}

	/**
	 * Load a URL via WebView and return the page HTML after JavaScript execution.
	 * Used for sources that require webView: true.
	 *
	 * @param url The URL to load
	 * @param headers Optional headers to set
	 * @param delayMs Delay in milliseconds to wait after page load for JS execution
	 * @param timeoutMs Total timeout in milliseconds
	 * @param webJs Optional custom JavaScript to execute instead of outerHTML
	 * @param blockImages Whether to block images to speed up loading
	 * @return The page HTML content (or JS result)
	 */
	suspend fun loadPageHtml(
		url: String,
		headers: Map<String, String>? = null,
		delayMs: Long = 2500,
		timeoutMs: Long = 60000,
		webJs: String? = null,
		blockImages: Boolean = true
	): String = mutex.withLock {
		withContext(Dispatchers.Main.immediate) {
			val webView = obtainWebView()
			try {
				// Configure with common browser settings plus image blocking
				webView.configureForParser(headers?.get("User-Agent"), blockImages = blockImages)

				withTimeout(timeoutMs) {
					// Load the page and wait for it to finish
					suspendCancellableCoroutine<Unit> { cont ->
						webView.webViewClient = object : WebViewClient() {
							override fun onPageFinished(view: WebView?, loadedUrl: String?) {
								if (cont.isActive) {
									cont.resume(Unit)
								}
							}
						}
						if (headers != null && headers.isNotEmpty()) {
							webView.loadUrl(url, headers)
						} else {
							webView.loadUrl(url)
						}
					}

					// Wait for initial JavaScript to execute
					kotlinx.coroutines.delay(delayMs)
					
					val extractionJs = webJs?.takeIf { it.isNotBlank() } ?: "document.documentElement.outerHTML"
					
					// Poll for the actual content to be available (some sites use anti-adblock that takes time)
					// Match Legado's retry mechanism: up to 30 attempts
					var result = ""
					var attempts = 0
					val maxAttempts = 30
					while (attempts < maxAttempts) {
						result = suspendCancellableCoroutine<String> { cont ->
							webView.evaluateJavascript(extractionJs) { jsResult ->
								val unescaped = jsResult?.let {
									if (it == "null") ""
									else if (it.startsWith("\"") && it.endsWith("\"")) {
										// Basic JSON unescaping for the string result
										it.substring(1, it.length - 1)
											.replace("\\u003C", "<")
											.replace("\\u003E", ">")
											.replace("\\n", "\n")
											.replace("\\t", "\t")
											.replace("\\\"", "\"")
											.replace("\\\\", "\\")
									} else it
								} ?: ""
								cont.resume(unescaped)
							}
						}
						
						// If user provided custom JS, we don't know the "ready" condition, just return it
						if (webJs != null && webJs.isNotBlank()) break
						
						// Default extraction: Check if content element has actual text
						val hasContent = suspendCancellableCoroutine<Boolean> { cont ->
							webView.evaluateJavascript(
								"""(function() {
									var el = document.getElementById('TextContent') || document.querySelector('#TextContent') || document.querySelector('.content') || document.querySelector('#content');
									if (!el) return false;
									var text = el.innerText || el.textContent || '';
									return text.trim().length > 100;
								})()"""
							) { jsResult ->
								cont.resume(jsResult == "true")
							}
						}
						
						if (hasContent) {
							android.util.Log.d("WebViewExecutor", "[WebView] Content ready after ${attempts + 1} attempts")
							break
						}
						
						attempts++
						if (attempts < maxAttempts) {
							android.util.Log.d("WebViewExecutor", "[WebView] Content not ready, waiting... (attempt $attempts/$maxAttempts)")
							kotlinx.coroutines.delay(1000)
						}
					}
					
					android.util.Log.d("WebViewExecutor", "[WebView] Extracted length=${result.length}, preview=${result.take(200).replace("\n", " ")}")
					result
				}
			} finally {
				webView.reset()
			}
		}
	}

	suspend fun sniffMediaUrl(
		url: String,
		headers: Map<String, String>? = null,
		delayMs: Long = 2500,
		timeoutMs: Long = 20000,
	): SniffedMediaResult? = mutex.withLock {
		withContext(Dispatchers.Main.immediate) {
			val webView = obtainWebView()
			try {
				webView.configureForParser(headers?.get(CommonHeaders.USER_AGENT), blockImages = true)
				withTimeout(timeoutMs) {
					suspendCancellableCoroutine { cont ->
						val pageFinished = AtomicBoolean(false)
						val candidateUrl = AtomicReference<String?>(null)

						fun tryResume(result: SniffedMediaResult?) {
							if (cont.isActive) {
								cont.resume(result)
							}
						}

						fun captureCandidate(rawUrl: String?) {
							val normalized = rawUrl?.takeIf(TVBoxPlayback::looksLikeDirectPlaybackUrl) ?: return
							if (candidateUrl.compareAndSet(null, normalized)) {
								val mergedHeaders = headers.orEmpty().toMutableMap()
								if (!mergedHeaders.keys.any { it.equals(CommonHeaders.REFERER, ignoreCase = true) }) {
									mergedHeaders[CommonHeaders.REFERER] = url
								}
								CookieManager.getInstance().getCookie(normalized)?.takeIf { it.isNotBlank() }?.let { cookie ->
									mergedHeaders[CommonHeaders.COOKIE] = cookie
								}
								tryResume(SniffedMediaResult(url = normalized, headers = mergedHeaders))
							}
						}

						webView.webViewClient = object : WebViewClient() {
							override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): android.webkit.WebResourceResponse? {
								captureCandidate(request?.url?.toString())
								return null
							}

							override fun onPageFinished(view: WebView?, loadedUrl: String?) {
								if (!pageFinished.compareAndSet(false, true)) {
									return
								}
								kotlinx.coroutines.CoroutineScope(cont.context).launch(Dispatchers.Main.immediate) {
									kotlinx.coroutines.delay(delayMs)
									if (!cont.isActive || candidateUrl.get() != null) {
										return@launch
									}
									val html = suspendCancellableCoroutine<String> { htmlCont ->
										webView.evaluateJavascript("document.documentElement.outerHTML") { jsResult ->
											htmlCont.resume(decodeJavascriptString(jsResult))
										}
									}
									val embeddedUrl = TVBoxPlayback.extractEmbeddedMediaUrl(html)
									if (embeddedUrl != null) {
										captureCandidate(embeddedUrl)
									} else {
										tryResume(null)
									}
								}
							}
						}
						if (!headers.isNullOrEmpty()) {
							webView.loadUrl(url, headers)
						} else {
							webView.loadUrl(url)
						}
					}
				}
			} finally {
				webView.reset()
			}
		}
	}

	private suspend fun obtainWebView(): WebView {
		webViewCached?.get()?.let {
			return it
		}
		return withContext(Dispatchers.Main.immediate) {
			webViewCached?.get()?.let {
				return@withContext it
			}
			WebView(context).also {
				it.configureForParser(null)
				webViewCached = WeakReference(it)
				proxyProvider.applyWebViewConfig()
				it.onResume()
				it.resumeTimers()
			}
		}
	}

    @MainThread
    private fun attachToHost(webView: WebView, activity: android.app.Activity): ViewGroup? {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        runCatching {
            (webView.parent as? ViewGroup)?.removeView(webView)
            // 自动 CF 解析需要真实窗口宿主，但不能把挑战页闪到当前界面上。
            webView.alpha = 0f
            webView.visibility = View.INVISIBLE
            webView.isClickable = false
            webView.isFocusable = false
            webView.isFocusableInTouchMode = false
            content.addView(
                webView,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
        }.onFailure {
            it.printStackTraceDebug()
            return null
        }
        return content
    }

    @MainThread
    private fun detachFromHost(webView: WebView, host: ViewGroup) {
        runCatching { host.removeView(webView) }.onFailure { it.printStackTraceDebug() }
    }

	private fun ContentSource.getUserAgent(): String? {
		val repository = mangaRepositoryFactoryProvider.get().create(this) as? ParserContentRepository
        return repository?.getRequestHeaders()?.get(CommonHeaders.USER_AGENT)
            ?: (mangaRepositoryFactoryProvider.get().create(this) as? KotatsuParserRepository)
                ?.getRequestHeaders()
                ?.get(CommonHeaders.USER_AGENT)
	}

    @MainThread
    private fun createCloudFlareClient(
        webView: WebView,
        exception: CloudFlareException,
        continuation: kotlin.coroutines.Continuation<Boolean>,
        useInterception: Boolean,
    ): CloudFlareClient {
        val handler = Handler(Looper.getMainLooper())
        var finished = false
        val resumeOnce: (Boolean) -> Unit = { result ->
            if (!finished) {
                finished = true
                handler.removeCallbacksAndMessages(null)
                continuation.resume(result)
            }
        }
        val initialClearance = CloudFlareHelper.getClearanceCookie(cookieJar, exception.url)
        val challengeDeadline = System.currentTimeMillis() + MAX_CHALLENGE_MS
        val check = object : Runnable {
            override fun run() {
                if (finished) return
                val clearance = CloudFlareHelper.getClearanceCookie(cookieJar, exception.url)
                if (clearance != null && clearance != initialClearance) {
                    resumeOnce(true)
                    return
                }
                webView.evaluateJavascript(CF_STATE_JS) { raw ->
                    if (finished) return@evaluateJavascript
                    when (raw?.removeSurrounding("\"")) {
                        "ok" -> resumeOnce(true)
                        "error" -> resumeOnce(false)
                        else -> if (System.currentTimeMillis() >= challengeDeadline) {
                            resumeOnce(false)
                        } else {
                            handler.removeCallbacks(this)
                            handler.postDelayed(this, CHALLENGE_POLL_INTERVAL_MS)
                        }
                    }
                }
            }
        }
        val callback = object : org.skepsun.kototoro.browser.cloudflare.CloudFlareCallback {
            override fun onLoadingStateChanged(isLoading: Boolean) = Unit
            override fun onHistoryChanged() = Unit

            override fun onPageLoaded() {
                if (finished) return
                handler.removeCallbacks(check)
                handler.postDelayed(check, 100L)
            }

            override fun onCheckPassed() = resumeOnce(true)

            override fun onLoopDetected() = Unit
        }
        return if (useInterception) {
            CloudFlareInterceptClient(
                cookieJar = cookieJar,
                callback = callback,
                adBlock = adBlock,
                targetUrl = exception.url,
            )
        } else {
            CloudFlareClient(
                cookieJar = cookieJar,
                callback = callback,
                adBlock = adBlock,
                targetUrl = exception.url,
            )
        }
    }

    private suspend fun shouldUseCloudFlareInterception(source: ContentSource): Boolean {
        val repository = mangaRepositoryFactoryProvider.get().create(source) as? ParserContentRepository ?: return false
        val key = repository.getConfigKeys()
            .filterIsInstance<ConfigKey.InterceptCloudflare>()
            .firstOrNull()
            ?: return false
        return repository.getConfig()[key]
    }

    @MainThread
    private suspend fun dumpPageHtml(webView: WebView) {
        runCatchingCancellable {
            val html = withTimeoutOrNull(2_000L) {
                suspendCancellableCoroutine<String?> { cont ->
                    webView.evaluateJavascript("document.documentElement.outerHTML") { result ->
                        cont.resume(result)
                    }
                }
            }
            Log.w(TAG, html.orEmpty())
        }.onFailure { it.printStackTraceDebug() }
    }

	suspend fun loginAndCheck(
		loginUrl: String,
		checkStatus: suspend (url: String, title: String) -> Boolean,
		onSuccess: (() -> Unit)? = null,
		cookiesDomain: String? = null,
		timeoutMs: Long = TimeUnit.SECONDS.toMillis(20),
	): Boolean = mutex.withLock {
		return runCatching {
			withContext(Dispatchers.Main.immediate) {
				val webView = obtainWebView()
				try {
					val result = withTimeout(timeoutMs) {
						suspendCancellableCoroutine<Boolean> { cont ->
							webView.webViewClient = object : WebViewClient() {
								override fun onPageFinished(view: WebView?, url: String?) {
									val currentUrl = url ?: ""
									val title = view?.title ?: ""
									kotlinx.coroutines.CoroutineScope(cont.context).launch {
										val ok = runCatching { checkStatus(currentUrl, title) }.getOrDefault(false)
										if (ok && cont.isActive) {
											cont.resume(true)
										}
									}
								}
							}
							webView.loadUrl(loginUrl)
						}
					}
					if (!result) return@withContext false
						val domain = cookiesDomain ?: loginUrl.toHttpUrlOrNull()?.host ?: return@withContext true
						val rootDomain = LegadoNetworkUtils.getSubDomain("https://$domain")
						// 同步 WebView Cookie 到应用 CookieJar
						cookieJar.removeCookies(loginUrl.toHttpUrlOrNull() ?: return@withContext true) { true }
						android.webkit.CookieManager.getInstance().getCookie(loginUrl)?.let { raw ->
							val httpUrl = "https://$rootDomain".toHttpUrlOrNull() ?: return@let
							raw.split(";").map { it.trim() }.forEach { line ->
								val parts = line.split("=", limit = 2)
								if (parts.size == 2) {
									val name = parts[0]
									val value = parts[1]
									val c = runCatching {
										Cookie.Builder()
											.domain(httpUrl.host)
											.path("/")
											.name(name)
											.value(value)
											.secure()
											.build()
									}.getOrNull()
								if (c != null) {
									cookieJar.saveFromResponse(httpUrl, listOf(c))
								}
							}
						}
					}
					onSuccess?.invoke()
					true
				} finally {
					webView.reset()
				}
			}
		}.getOrDefault(false)
	}

	@MainThread
	private fun WebView.reset() {
		stopLoading()
		webViewClient = WebViewClient()
		settings.userAgentString = defaultUserAgent
		loadDataWithBaseURL(null, " ", "text/html", null, null)
		clearHistory()
	}

    companion object {
        private const val TAG = "WebViewExecutor"
        private const val CHALLENGE_POLL_INTERVAL_MS = 700L
        private const val MAX_CHALLENGE_MS = 11_000L
        private const val FAILURE_COOLDOWN_MS = 30_000L
    }

	data class SniffedMediaResult(
		val url: String,
		val headers: Map<String, String>,
	)

	data class BrowserFetchResult(
		val status: Int,
		val statusText: String,
		val url: String,
		val headers: Map<String, String>,
		val body: String,
	)

	private fun decodeJavascriptString(value: String?): String {
		if (value.isNullOrBlank() || value == "null") {
			return ""
		}
		return try {
			org.json.JSONTokener(value).nextValue() as? String ?: value
		} catch (e: Exception) {
			value
		}
	}
}
