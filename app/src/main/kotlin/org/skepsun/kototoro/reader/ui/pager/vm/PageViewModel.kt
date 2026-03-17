package org.skepsun.kototoro.reader.ui.pager.vm

import android.graphics.Rect
import android.net.Uri
import androidx.annotation.WorkerThread
import com.davemorrissey.labs.subscaleview.DefaultOnImageEventListener
import com.davemorrissey.labs.subscaleview.ImageSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import okio.IOException
import org.skepsun.kototoro.core.exceptions.resolve.ExceptionResolver
import org.skepsun.kototoro.core.os.NetworkState
import org.skepsun.kototoro.core.util.ext.printStackTraceDebug
import org.skepsun.kototoro.core.util.ext.throttle
import org.skepsun.kototoro.parsers.model.ContentPage
import org.skepsun.kototoro.reader.domain.PageLoader
import org.skepsun.kototoro.reader.ui.config.ReaderSettings

class PageViewModel(
	private val loader: PageLoader,
	val settingsProducer: ReaderSettings.Producer,
	private val networkState: NetworkState,
	private val exceptionResolver: ExceptionResolver,
	private val isWebtoon: Boolean,
) : DefaultOnImageEventListener {

	data class LayerSources(
		val original: ImageSource,
		val translated: ImageSource?,
	)

	private val scope = loader.loaderScope + Dispatchers.Main.immediate
	private var job: Job? = null
	private var cachedBounds: Rect? = null
	private var boundPage: ContentPage? = null
	@Volatile
	private var pendingLayerSwitchPageId: Long? = null
	private val boundsCache = LinkedHashMap<String, Rect?>(64, 0.75f, true)

	init {
		loader.observeTranslationUpdates()
			.onEach { pageId ->
				val page = boundPage ?: return@onEach
				if (page.id != pageId) return@onEach
				if (isLoading()) {
					pendingLayerSwitchPageId = pageId
					return@onEach
				}
				switchDisplayLayer(page)
			}.launchIn(scope)
	}

	val state = MutableStateFlow<PageState>(PageState.Empty)

	fun isLoading() = job?.isActive == true

	fun onBind(page: ContentPage) {
		boundPage = page
		pendingLayerSwitchPageId = null
		val prevJob = job
		job = scope.launch(Dispatchers.Default) {
			prevJob?.cancelAndJoin()
			doLoad(page, force = false)
		}
	}

	fun retry(page: ContentPage, isFromUser: Boolean) {
		val prevJob = job
		job = scope.launch {
			prevJob?.cancelAndJoin()
			val e = (state.value as? PageState.Error)?.error
			if (e != null && ExceptionResolver.canResolve(e)) {
				if (isFromUser) {
					exceptionResolver.resolve(e)
				}
			}
			withContext(Dispatchers.Default) {
				doLoad(page, force = true)
			}
		}
	}

	fun showErrorDetails(url: String?) {
		val e = (state.value as? PageState.Error)?.error ?: return
		exceptionResolver.showErrorDetails(e, url)
	}

	fun onRecycle() {
		state.value = PageState.Empty
		cachedBounds = null
		boundPage = null
		pendingLayerSwitchPageId = null
		boundsCache.clear()
		job?.cancel()
	}

	fun refreshDisplayVariant(page: ContentPage) {
		val prevJob = job
		job = scope.launch(Dispatchers.Default) {
			prevJob?.cancelAndJoin()
			loader.invalidateTask(page.id)
			doLoad(page, force = false)
		}
	}

	fun switchDisplayLayer(page: ContentPage) {
		val currentState = state.value
		val source = when (currentState) {
			is PageState.Shown -> currentState.source
			is PageState.Loaded -> currentState.source
			else -> null
		}
		val currentUri = (source as? ImageSource.Uri)?.uri
		if (currentUri == null) {
			return
		}
		val prevJob = job
		job = scope.launch(Dispatchers.Default) {
			prevJob?.cancelAndJoin()
			val targetUri = loader.resolveDisplayVariant(
				page = page,
				currentUri = currentUri,
				showTranslated = settingsProducer.value.isTranslationShowTranslated,
			)
			if (targetUri == null || targetUri == currentUri) {
				return@launch
			}
			cachedBounds = resolveTrimmedBounds(targetUri)
			state.value = PageState.Shown(targetUri.toImageSource(cachedBounds), isConverted = false)
		}
	}

	override fun onImageLoaded() {
		state.update { currentState ->
			if (currentState is PageState.Loaded) {
				PageState.Shown(currentState.source, currentState.isConverted)
			} else {
				currentState
			}
		}
	}

	override fun onImageLoadError(e: Throwable) {
		e.printStackTraceDebug()

		state.update { currentState ->
			if (currentState is PageState.Loaded) {
				val uri = (currentState.source as? ImageSource.Uri)?.uri
				if (!currentState.isConverted && uri != null && e is IOException) {
					tryConvert(uri, e)
					PageState.Converting()
				} else {
					PageState.Error(e)
				}
			} else {
				currentState
			}
		}
	}

	private fun tryConvert(uri: Uri, e: Exception) {
		val prevJob = job
		job = scope.launch(Dispatchers.Default) {
			prevJob?.join()
			state.value = PageState.Converting()
			try {
				val newUri = loader.convertBimap(uri)
				cachedBounds = resolveTrimmedBounds(newUri)
				state.value = PageState.Loaded(newUri.toImageSource(cachedBounds), isConverted = true)
			} catch (ce: CancellationException) {
				throw ce
			} catch (e2: Throwable) {
				e2.printStackTrace()
				e.addSuppressed(e2)
				state.value = PageState.Error(e)
			}
		}
	}

	@WorkerThread
	private suspend fun doLoad(data: ContentPage, force: Boolean) = coroutineScope {
		state.value = PageState.Loading(null, -1)
		val previewJob = launch {
			val preview = loader.loadPreview(data) ?: return@launch
			state.update {
				if (it is PageState.Loading) it.copy(preview = preview) else it
			}
		}
		try {
			val task = loader.loadPageAsync(data, force)
			val progressObserver = observeProgress(this, task.progressAsFlow())
			val uri = task.await()
			progressObserver.cancelAndJoin()
			previewJob.cancel()
			val displayUri = loader.resolveDisplayVariant(
				page = data,
				currentUri = uri,
				showTranslated = settingsProducer.value.isTranslationShowTranslated,
			) ?: uri
			cachedBounds = resolveTrimmedBounds(displayUri)
			state.value = PageState.Loaded(displayUri.toImageSource(cachedBounds), isConverted = false)
			applyPendingLayerSwitchIfNeeded(data, displayUri)
		} catch (e: CancellationException) {
			throw e
		} catch (e: Throwable) {
			e.printStackTraceDebug()
			state.value = PageState.Error(e)
			if (e is IOException && !networkState.value) {
				networkState.awaitForConnection()
				retry(data, isFromUser = false)
			}
		}
	}

	private fun observeProgress(scope: CoroutineScope, progress: Flow<Float>) = progress
		.throttle(250)
		.onEach {
			val progressValue = (100 * it).toInt()
			state.update { currentState ->
				if (currentState is PageState.Loading) {
					currentState.copy(progress = progressValue)
				} else {
					currentState
				}
			}
		}.launchIn(scope)

	private fun Uri.toImageSource(bounds: Rect?): ImageSource {
		val source = ImageSource.uri(this)
		return if (bounds != null) {
			source.region(bounds)
		} else {
			source
		}
	}

	private suspend fun applyPendingLayerSwitchIfNeeded(page: ContentPage, currentUri: Uri) {
		if (pendingLayerSwitchPageId != page.id) {
			return
		}
		pendingLayerSwitchPageId = null
		val targetUri = loader.resolveDisplayVariant(
			page = page,
			currentUri = currentUri,
			showTranslated = settingsProducer.value.isTranslationShowTranslated,
		)
		if (targetUri == null || targetUri == currentUri) {
			return
		}
		cachedBounds = resolveTrimmedBounds(targetUri)
		state.value = PageState.Loaded(targetUri.toImageSource(cachedBounds), isConverted = false)
	}

	suspend fun resolveLayerSources(page: ContentPage): LayerSources? {
		val currentState = state.value
		val source = when (currentState) {
			is PageState.Shown -> currentState.source
			is PageState.Loaded -> currentState.source
			else -> null
		}
		val currentUri = (source as? ImageSource.Uri)?.uri ?: return null
		val originalUri = loader.resolveDisplayVariant(page, currentUri, showTranslated = false) ?: currentUri
		val translatedUri = loader.resolveDisplayVariant(page, currentUri, showTranslated = true)
		val original = originalUri.toImageSource(resolveTrimmedBounds(originalUri))
		val translated = translatedUri?.let { it.toImageSource(resolveTrimmedBounds(it)) }
		return LayerSources(original = original, translated = translated)
	}

	private suspend fun resolveTrimmedBounds(uri: Uri): Rect? {
		if (!settingsProducer.value.isPagesCropEnabled(isWebtoon)) {
			return null
		}
		val key = uri.toString()
		if (boundsCache.containsKey(key)) {
			return boundsCache[key]
		}
		val bounds = loader.getTrimmedBounds(uri)
		boundsCache[key] = bounds
		if (boundsCache.size > 64) {
			val eldest = boundsCache.entries.iterator().next().key
			boundsCache.remove(eldest)
		}
		return bounds
	}
}
