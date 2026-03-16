package org.skepsun.kototoro.core.parser.tvbox

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Build
import android.util.Log
import com.github.tvbox.osc.base.App
import com.github.catvod.crawler.Spider
import dalvik.system.DexClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.skepsun.kototoro.core.db.entity.JsonSourceEntity
import org.skepsun.kototoro.core.db.entity.JsonSourceType
import org.skepsun.kototoro.core.jsonsource.JsonMangaSource
import org.skepsun.kototoro.core.model.jsonsource.TVBoxStoredConfig
import org.skepsun.kototoro.core.network.CommonHeaders
import org.skepsun.kototoro.core.network.jsonsource.LegadoHttpClient
import org.skepsun.kototoro.tvbox.bridge.TVBoxJarSpiderRequest
import org.skepsun.kototoro.tvbox.bridge.TVBoxJarSpiderResponse
import org.skepsun.kototoro.tvbox.bridge.TVBoxJarSpiderWorkerProtocol
import java.io.File
import java.io.InputStream
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.zip.ZipFile

internal class TVBoxJarSpiderExecutor(
	private val context: Context,
	private val httpClient: LegadoHttpClient,
	private val request: TVBoxJarSpiderRequest,
) {

	companion object {
		private const val TAG = "TVBoxJarExecutor"
		private const val CACHE_MAX_AGE_MS = 7L * 24L * 60L * 60L * 1000L
		private const val MAX_IPC_BODY_BYTES = 256 * 1024
		private val loadedJarCache = ConcurrentHashMap<String, LoadedJar>()
		private val spiderCache = ConcurrentHashMap<String, CachedSpider>()
		private val loadedJarCacheMutex = Mutex()
		private val spiderCacheMutex = Mutex()
	}

	private val source = JsonMangaSource(
		JsonSourceEntity(
			id = request.sourceId,
			name = request.sourceDisplayName,
			type = JsonSourceType.TVBOX,
			config = request.sourceConfig,
			createdAt = 0L,
			updatedAt = 0L,
		),
	)
	private val config = TVBoxStoredConfig.parse(request.sourceConfig)
	@Volatile
	private var createSpiderFailureDetail: String? = null

	suspend fun execute(): TVBoxJarSpiderResponse = withContext(Dispatchers.IO) {
		if (!config.site.api.startsWith("csp_", ignoreCase = true)) {
			return@withContext errorResponse("TVBox source is not a csp_* jar spider")
		}
		val loadedJar = ensureLoadedJar() ?: return@withContext errorResponse("Unable to load TVBox spider jar")
		prepareHostRuntime(loadedJar)
		createSpiderFailureDetail = null
		val cachedSpider = ensureSpider(loadedJar) ?: return@withContext errorResponse(
			buildString {
				append("Unable to create TVBox spider instance")
				createSpiderFailureDetail?.takeIf { it.isNotBlank() }?.let {
					append(": ")
					append(it)
				}
			},
		)
		when (request.action) {
			TVBoxJarSpiderWorkerProtocol.ACTION_HOME -> payloadResponse(
				invokeSpider(cachedSpider, "homeContent(true)", request.timeoutMs) {
					it.homeContent(true)
				}.orEmpty(),
			)

			TVBoxJarSpiderWorkerProtocol.ACTION_HOME_VOD -> payloadResponse(
				invokeSpider(cachedSpider, "homeVideoContent()", request.timeoutMs) {
					it.homeVideoContent()
				}.orEmpty(),
			)

			TVBoxJarSpiderWorkerProtocol.ACTION_CATEGORY -> executeCategory(cachedSpider)
			TVBoxJarSpiderWorkerProtocol.ACTION_SEARCH -> executeSearch(cachedSpider)
			TVBoxJarSpiderWorkerProtocol.ACTION_DETAIL -> executeDetail(cachedSpider)
			TVBoxJarSpiderWorkerProtocol.ACTION_PLAY -> executePlay(cachedSpider)
			TVBoxJarSpiderWorkerProtocol.ACTION_PROXY -> executeProxy(cachedSpider.spider, loadedJar)
			else -> errorResponse("Unsupported TVBox spider action: ${request.action}")
		}
	}

	private fun executeCategory(cachedSpider: CachedSpider): TVBoxJarSpiderResponse {
		val categoryId = request.categoryId.orEmpty().ifBlank {
			return errorResponse("TVBox category request is missing categoryId")
		}
		val page = request.page ?: 1
		return payloadResponse(
			invokeSpider(
				cachedSpider = cachedSpider,
				action = "categoryContent(tid=$categoryId, pg=$page)",
				timeoutMs = request.timeoutMs,
			) {
				it.categoryContent(categoryId, page.toString(), true, hashMapOf())
			}.orEmpty(),
		)
	}

	private fun executeSearch(cachedSpider: CachedSpider): TVBoxJarSpiderResponse {
		val query = request.query.orEmpty().ifBlank {
			return errorResponse("TVBox search request is missing query")
		}
		val page = request.page ?: 1
		return payloadResponse(
			invokeSpider(
				cachedSpider = cachedSpider,
				action = "searchContent(query=$query, page=$page)",
				timeoutMs = request.timeoutMs,
			) { spider ->
				runCatching {
					spider.searchContent(query, false, page.toString())
				}.getOrElse {
					spider.searchContent(query, false)
				}
			}.orEmpty(),
		)
	}

	private fun executeDetail(cachedSpider: CachedSpider): TVBoxJarSpiderResponse {
		val itemId = request.itemId.orEmpty().ifBlank {
			return errorResponse("TVBox detail request is missing itemId")
		}
		return payloadResponse(
			invokeSpider(
				cachedSpider = cachedSpider,
				action = "detailContent(ids=$itemId)",
				timeoutMs = request.timeoutMs,
			) {
				it.detailContent(listOf(itemId))
			}.orEmpty(),
		)
	}

	private fun executePlay(cachedSpider: CachedSpider): TVBoxJarSpiderResponse {
		val flag = request.flag.orEmpty().ifBlank {
			return errorResponse("TVBox play request is missing flag")
		}
		val playId = request.playId.orEmpty().ifBlank {
			return errorResponse("TVBox play request is missing playId")
		}
		return payloadResponse(
			invokeSpider(
				cachedSpider = cachedSpider,
				action = "playerContent(flag=$flag, id=$playId)",
				timeoutMs = request.timeoutMs,
			) {
				it.playerContent(flag, playId, emptyList())
			}.orEmpty(),
		)
	}

	private fun prepareHostRuntime(loadedJar: LoadedJar) {
		App.init(context)
		App.configureRuntimeDirs(
			loadedJar.guardResources?.hostCacheDir,
			loadedJar.guardResources?.hostCodeCacheDir,
			loadedJar.guardResources?.hostFilesDir,
		)
		App.configureRuntimeClassLoader(loadedJar.classLoader)
	}

	private suspend fun ensureSpider(loadedJar: LoadedJar): CachedSpider? = withContext(Dispatchers.IO) {
		val cacheKey = request.sourceId.toString()
		val className = "com.github.catvod.spider.${config.site.api.removePrefix("csp_")}"
		val extLiteral = buildExtLiteral()
		spiderCache[cacheKey]?.takeIf {
			it.className == className &&
				it.extLiteral == extLiteral &&
				it.jarCacheKey == loadedJar.spec.cacheKey
		}?.let { cached ->
			Log.i(TAG, "Reusing TVBox spider instance for ${source.name}: key=$cacheKey class=$className jarKey=${loadedJar.spec.cacheKey}")
			return@withContext cached
		}
		spiderCacheMutex.withLock {
			spiderCache[cacheKey]?.takeIf {
				it.className == className &&
					it.extLiteral == extLiteral &&
					it.jarCacheKey == loadedJar.spec.cacheKey
			}?.let { cached ->
				Log.i(TAG, "Reusing TVBox spider instance after lock for ${source.name}: key=$cacheKey class=$className jarKey=${loadedJar.spec.cacheKey}")
				return@withLock cached
			}
			spiderCache.remove(cacheKey)?.let { stale ->
				disposeCachedSpider(
					cacheKey = cacheKey,
					cachedSpider = stale,
					reason = "class/ext/jar changed",
				)
			}
			val spider = createSpider(loadedJar) ?: return@withLock null
			val executor = Executors.newSingleThreadExecutor { runnable ->
				Thread(runnable, "tvbox-jar-spider-$cacheKey").apply {
					isDaemon = true
				}
			}
			CachedSpider(
				jarCacheKey = loadedJar.spec.cacheKey,
				className = className,
				extLiteral = extLiteral,
				spider = spider,
				executor = executor,
			).also { cached ->
				spiderCache[cacheKey] = cached
				Log.i(TAG, "Cached TVBox spider instance for ${source.name}: key=$cacheKey class=$className jarKey=${loadedJar.spec.cacheKey}")
			}
		}
	}

	private fun disposeCachedSpider(
		cacheKey: String,
		cachedSpider: CachedSpider,
		reason: String,
	) {
		Log.i(TAG, "Disposing cached TVBox spider instance: key=$cacheKey reason=$reason class=${cachedSpider.className}")
		runCatching { cachedSpider.spider.cancelByTag() }
		runCatching { cachedSpider.spider.destroy() }
		cachedSpider.executor.shutdownNow()
	}

	private suspend fun createSpider(loadedJar: LoadedJar): Spider? = withContext(Dispatchers.IO) {
		val className = "com.github.catvod.spider.${config.site.api.removePrefix("csp_")}"
		createSpiderFailureDetail = null
		Log.i(TAG, "Creating TVBox spider worker for ${source.name}: class=$className jar=${loadedJar.spec.url}")
		loadedJar.guardResources?.let { resources ->
			Log.i(
				TAG,
				"Using TVBox Guard host resources for ${source.name}: nativeDir=${resources.nativeLibraryDir.absolutePath} guardDir=${resources.guardDataDir.absolutePath} preferred=${resources.preferredLibraryPath ?: "-"}",
			)
		}
		App.init(context)
		App.configureRuntimeDirs(
			loadedJar.guardResources?.hostCacheDir,
			loadedJar.guardResources?.hostCodeCacheDir,
			loadedJar.guardResources?.hostFilesDir,
		)
		logHostBridgeState()
		val bridgeApp = App.getInstance()
		Log.i(
			TAG,
			"Using TVBox host context for ${source.name}: bridgeApp=${bridgeApp.javaClass.name}",
		)
		Log.i(TAG, "Pre-initializing TVBox spider runtime for ${source.name}: class=$className mode=tvboxos")
		invokeStaticInit(loadedJar.classLoader, bridgeApp, "tvboxos_init")
		val resolvedDexLoader = resolveDexNativeLoader(
			classLoader = loadedJar.classLoader,
			contextCandidates = listOf(bridgeApp),
			guardResources = loadedJar.guardResources,
		)
		seedInitInstanceState(
			classLoader = loadedJar.classLoader,
			hostContext = bridgeApp,
			preferredDexLoader = resolvedDexLoader,
			overwriteExisting = false,
		)
		val directGuardDelegate = if (className.endsWith("Guard", ignoreCase = true)) {
			runCatching { resolveGuardDelegate(loadedJar.classLoader, className) }
				.onFailure {
					Log.w(TAG, "Failed to resolve TVBox Guard delegate before instantiation for ${source.name}: class=$className", it)
				}
				.getOrNull()
		} else {
			null
		}
		val instantiate = {
			withContextClassLoader(loadedJar.classLoader) {
			Log.i(TAG, "Instantiating TVBox spider constructor for ${source.name}: class=$className")
			loadedJar.classLoader.loadClass(className)
				.getDeclaredConstructor()
				.newInstance() as? Spider
			}
		}
		val spider = directGuardDelegate ?: runCatching(instantiate).recoverCatching { error ->
			if (requiresStaticInit(error)) {
				Log.w(
					TAG,
					"TVBox spider class $className hit static initialization failure for ${source.name}: ${describeFailureChain(error)}",
				)
				Log.i(TAG, "Retrying TVBox spider instantiation after static Init fallback for ${source.name}: class=$className")
				invokeStaticInit(loadedJar.classLoader, bridgeApp, "retry_after_failure")
				instantiate()
			} else {
				throw error
			}
		}.getOrElse {
			Log.w(TAG, "Unable to instantiate TVBox spider class $className for ${source.name}", it)
			createSpiderFailureDetail = "instantiate ${describeFailureChain(it)}"
			null
		} ?: return@withContext null
		if (directGuardDelegate != null) {
			Log.i(
				TAG,
				"Using resolved TVBox Guard delegate directly for ${source.name}: requested=$className impl=${directGuardDelegate.javaClass.name}",
			)
		} else {
			repairGuardDelegateIfNeeded(loadedJar.classLoader, spider)
			Log.i(TAG, "Constructed TVBox spider instance for ${source.name}: class=$className impl=${spider.javaClass.name}")
		}
		val extLiteral = buildExtLiteral()
		val spiderInitContext = bridgeApp
		runCatching {
			withContextClassLoader(loadedJar.classLoader) {
				spider.init(spiderInitContext, extLiteral)
			}
		}.onFailure {
			Log.w(TAG, "TVBox spider init failed for ${source.name}", it)
			logReflectiveFailure("init", it)
			createSpiderFailureDetail = buildString {
				append("init ")
				append(describeFailureChain(it))
				append(" [")
				append(snapshotGuardRuntimeState(loadedJar.classLoader, spider))
				append(']')
			}
			return@withContext null
		}
		if (directGuardDelegate == null) {
			repairGuardDelegateIfNeeded(loadedJar.classLoader, spider)
		}
		Log.i(TAG, "Initialized TVBox spider instance for ${source.name}: class=$className impl=${spider.javaClass.name}")
		spider
	}

	private fun seedInitInstanceState(
		classLoader: ClassLoader,
		hostContext: Context,
		preferredDexLoader: DexClassLoader?,
		overwriteExisting: Boolean = true,
	) {
		runCatching {
			val initClass = classLoader.loadClass("com.github.catvod.spider.Init")
			val initSingleton = initClass.getMethod("get").invoke(null) ?: return@runCatching
			initClass.declaredFields.forEach { field ->
				field.isAccessible = true
				if (!overwriteExisting && field.get(initSingleton) != null) {
					return@forEach
				}
				when {
					Application::class.java.isAssignableFrom(field.type) && hostContext is Application -> {
						field.set(initSingleton, hostContext)
						Log.i(TAG, "Seeded TVBox Init application field for ${source.name}: field=${field.name} value=${hostContext.javaClass.name}")
					}

					DexClassLoader::class.java.isAssignableFrom(field.type) -> {
						val loaderValue = preferredDexLoader ?: (classLoader as? DexClassLoader) ?: return@forEach
						field.set(initSingleton, loaderValue)
						Log.i(TAG, "Seeded TVBox Init dex loader field for ${source.name}: field=${field.name} value=${loaderValue.javaClass.name}")
					}

					ClassLoader::class.java.isAssignableFrom(field.type) -> {
						val loaderValue = classLoader
						field.set(initSingleton, loaderValue)
						Log.i(TAG, "Seeded TVBox Init class loader field for ${source.name}: field=${field.name} value=${loaderValue.javaClass.name}")
					}
				}
			}
		}.onFailure {
			Log.w(TAG, "Failed to seed TVBox Init singleton state for ${source.name}", it)
		}
	}

	private fun resolveDexNativeLoader(
		classLoader: ClassLoader,
		contextCandidates: List<Context>,
		guardResources: GuardRuntimeResources?,
	): DexClassLoader? {
		return runCatching {
			withContextClassLoader(classLoader) {
				val dexNativeClass = classLoader.loadClass("com.github.catvod.spider.DexNative")
				val method = dexNativeClass.getMethod("getLoader", Any::class.java)
				fun probe(candidate: Any, label: String): DexClassLoader? {
					val result = runCatching { method.invoke(null, candidate) }
						.onFailure {
							Log.w(
								TAG,
								"TVBox DexNative loader probe failed for ${source.name}: candidate=$label message=${it.message}",
							)
						}
						.getOrNull()
					return when (result) {
						is DexClassLoader -> {
							Log.i(
								TAG,
								"Resolved TVBox DexNative loader for ${source.name}: candidate=$label loader=${result.javaClass.name}",
							)
							result
						}

						null -> {
							Log.w(
								TAG,
								"TVBox DexNative loader is null for ${source.name}: candidate=$label",
							)
							null
						}

						else -> {
							Log.w(
								TAG,
								"TVBox DexNative loader has unexpected type for ${source.name}: candidate=$label type=${result.javaClass.name}",
							)
							null
						}
					}
				}

				contextCandidates.forEach { candidate ->
					probe(candidate, candidate.javaClass.name)?.let { return@withContextClassLoader it }
				}

				val cacheDirCandidates = buildList {
					add(App.getInstance().cacheDir)
					add(context.cacheDir)
					guardResources?.hostCacheDir?.let { add(it) }
				}.distinctBy { it.absolutePath }
				val classLoaderCandidates = buildList {
					add(classLoader)
					add(App.getInstance().classLoader)
					add(context.classLoader)
				}.distinctBy { System.identityHashCode(it) }
				val hostCachePathCandidates = listOf(
					"/data/user/0/${App.HOST_PACKAGE_NAME}/cache",
					"/data/data/${App.HOST_PACKAGE_NAME}/cache",
				)
				classLoaderCandidates.forEachIndexed { loaderIndex, loaderCandidate ->
					cacheDirCandidates.forEachIndexed { cacheIndex, cacheDirCandidate ->
						val bridge = DexNativeLoaderBridge(
							classLoader = loaderCandidate,
							cacheDir = cacheDirCandidate,
						)
						val label = "bridge(loader[$loaderIndex]=${loaderCandidate.javaClass.simpleName},cache[$cacheIndex]=${cacheDirCandidate.absolutePath})"
						probe(bridge, label)?.let { return@withContextClassLoader it }
						hostCachePathCandidates.forEachIndexed { pathIndex, fakeHostCachePath ->
							val displayMasquerade = DexNativeLoaderBridge(
								classLoader = loaderCandidate,
								cacheDir = DisplayMasqueradingFile(
									realFile = cacheDirCandidate,
									displayPath = fakeHostCachePath,
								),
							)
							probe(
								displayMasquerade,
								"bridgeDisplay(loader[$loaderIndex]=${loaderCandidate.javaClass.simpleName},cache[$cacheIndex],host[$pathIndex]=$fakeHostCachePath)",
							)?.let { return@withContextClassLoader it }
							val pureMasquerade = DexNativeLoaderBridge(
								classLoader = loaderCandidate,
								cacheDir = PureMasqueradingFile(
									realFile = cacheDirCandidate,
									displayPath = fakeHostCachePath,
								),
							)
							probe(
								pureMasquerade,
								"bridgePure(loader[$loaderIndex]=${loaderCandidate.javaClass.simpleName},cache[$cacheIndex],host[$pathIndex]=$fakeHostCachePath)",
							)?.let { return@withContextClassLoader it }
						}
					}
				}
				null
			}
		}.getOrElse {
			Log.w(TAG, "Failed to resolve TVBox DexNative loader for ${source.name}", it)
			null
		}
	}

	private fun probeGuardCompatibilityClasses(classLoader: ClassLoader) {
		val requiredClasses = listOf(
			"com.github.catvod.debug.MainActivity",
			"com.whl.quickjs.android.QuickJSLoader",
			"com.whl.quickjs.android.QuickJSLoader\$Console",
			"com.whl.quickjs.wrapper.JSObject",
			"com.whl.quickjs.wrapper.JSCallFunction",
		)
		requiredClasses.forEach { className ->
			runCatching {
				val resolvedClass = Class.forName(className, false, classLoader)
				Log.i(
					TAG,
					"TVBox Guard host compatibility class resolved for ${source.name}: class=$className loader=${resolvedClass.classLoader?.javaClass?.name ?: "bootstrap"}",
				)
			}.onFailure {
				Log.w(
					TAG,
					"TVBox Guard host compatibility class missing for ${source.name}: class=$className message=${it.message}",
				)
			}
		}
		runCatching {
			val mainActivity = Class.forName("com.github.catvod.debug.MainActivity", false, classLoader)
			val methodNames = mainActivity.declaredMethods.map { it.name }.sorted()
			Log.i(TAG, "TVBox Guard MainActivity compatibility methods for ${source.name}: methods=$methodNames")
		}.onFailure {
			Log.w(TAG, "Failed to inspect TVBox Guard MainActivity compatibility methods for ${source.name}", it)
		}
		runCatching {
			val quickJsLoader = Class.forName("com.whl.quickjs.android.QuickJSLoader", false, classLoader)
			val methodNames = quickJsLoader.declaredMethods.map { it.name }.sorted()
			Log.i(TAG, "TVBox Guard QuickJS compatibility methods for ${source.name}: methods=$methodNames")
		}.onFailure {
			Log.w(TAG, "Failed to inspect TVBox Guard QuickJS compatibility methods for ${source.name}", it)
		}
	}

	private fun probeGuardResourceVisibility(classLoader: ClassLoader) {
		val resourceNames = listOf(
			"assets/wexguard_v8.so",
			"assets/wexguard_v7.so",
			"assets/wexshinidie.guard",
		)
		resourceNames.forEach { resourceName ->
			val resourceUrl = runCatching { classLoader.getResource(resourceName)?.toString() }
				.getOrElse { "<error:${it.javaClass.simpleName}:${it.message}>" }
			val resourceReadable = runCatching {
				classLoader.getResourceAsStream(resourceName)?.use { input ->
					val buffer = ByteArray(32)
					input.read(buffer)
				} != null
			}.getOrElse {
				Log.w(
					TAG,
					"TVBox Guard runtime resource probe failed for ${source.name}: resource=$resourceName message=${it.message}",
				)
				false
			}
			Log.i(
				TAG,
				"TVBox Guard runtime resource visibility for ${source.name}: resource=$resourceName url=$resourceUrl readable=$resourceReadable loader=${classLoader.javaClass.name}",
			)
		}
	}

	private fun probeHostContextConsistency(contextCandidates: List<Context>) {
		contextCandidates.forEach { candidate ->
			val packageName = runCatching { candidate.packageName }
				.getOrElse { "<error:${it.javaClass.simpleName}:${it.message}>" }
			val appInfoSummary = runCatching {
				candidate.applicationInfo.let {
					"packageName=${it.packageName} processName=${it.processName} className=${it.className}"
				}
			}.getOrElse { "<error:${it.javaClass.simpleName}:${it.message}>" }
			val packageManagerLookup = runCatching {
				val appInfo = candidate.packageManager.getApplicationInfo(packageName, 0)
				"ok:${appInfo.packageName}/${appInfo.processName}"
			}.getOrElse {
				"error:${it.javaClass.simpleName}:${it.message}"
			}
			val packageContextLookup = runCatching {
				val packageContext = candidate.createPackageContext(packageName, 0)
				"ok:${packageContext.javaClass.name}/${packageContext.packageName}"
			}.getOrElse {
				"error:${it.javaClass.simpleName}:${it.message}"
			}
			Log.i(
				TAG,
				"TVBox host context consistency for ${source.name}: candidate=${candidate.javaClass.name} packageName=$packageName appInfo=$appInfoSummary pmLookup=$packageManagerLookup packageContext=$packageContextLookup",
			)
		}
	}

	private fun repairGuardDelegateIfNeeded(classLoader: ClassLoader, spider: Spider) {
		val delegateField = generateSequence(spider.javaClass as Class<*>?) { it.superclass }
			.flatMap { targetClass ->
				targetClass.declaredFields.asSequence()
			}
			.firstOrNull { field ->
				!Modifier.isStatic(field.modifiers) && Spider::class.java.isAssignableFrom(field.type)
			}
			?: return
		delegateField.isAccessible = true
		val currentDelegate = runCatching { delegateField.get(spider) as? Spider }.getOrNull()
		if (currentDelegate != null) {
			Log.i(
				TAG,
				"TVBox Guard delegate already initialized for ${source.name}: field=${delegateField.name} impl=${currentDelegate.javaClass.name}",
			)
			return
		}
		val repairedDelegate = runCatching {
			resolveGuardDelegate(classLoader, spider.javaClass.name)
		}.getOrElse {
			Log.w(TAG, "Failed to resolve TVBox Guard delegate for ${source.name}: class=${spider.javaClass.name}", it)
			null
		} ?: run {
			Log.w(TAG, "TVBox Guard delegate is null after repair attempt for ${source.name}: class=${spider.javaClass.name}")
			return
		}
		runCatching {
			delegateField.set(spider, repairedDelegate)
			Log.i(
				TAG,
				"Repaired TVBox Guard delegate for ${source.name}: field=${delegateField.name} impl=${repairedDelegate.javaClass.name}",
			)
		}.onFailure {
			Log.w(TAG, "Failed to inject TVBox Guard delegate for ${source.name}", it)
		}
	}

	private fun resolveGuardDelegate(classLoader: ClassLoader, guardClassName: String): Spider? {
		return withContextClassLoader(classLoader) {
			val initClass = classLoader.loadClass("com.github.catvod.spider.Init")
			val dexNativeClass = classLoader.loadClass("com.github.catvod.spider.DexNative")
			val initGetSpider = initClass.getMethod("getSpider", String::class.java)
			val initLoader = initClass.getMethod("loader").invoke(null)
			Log.i(
				TAG,
				"Resolving TVBox Guard delegate for ${source.name}: guardClass=$guardClassName initLoader=${initLoader?.javaClass?.name ?: "null"}",
			)
			val directCandidates = buildGuardDelegateCandidates(guardClassName)
			directCandidates.forEach { candidate ->
				val directDelegate = runCatching { initGetSpider.invoke(null, candidate) as? Spider }
					.onFailure {
						Log.w(
							TAG,
							"TVBox Guard delegate Init.getSpider probe failed for ${source.name}: candidate=$candidate message=${it.message}",
						)
					}
					.getOrNull()
				if (directDelegate != null) {
					Log.i(
						TAG,
						"Resolved TVBox Guard delegate for ${source.name} via Init.getSpider: candidate=$candidate impl=${directDelegate.javaClass.name}",
					)
					return@withContextClassLoader directDelegate
				}
				Log.w(
					TAG,
					"TVBox Guard delegate Init.getSpider returned null for ${source.name}: candidate=$candidate",
				)
			}
			if (initLoader == null) {
				return@withContextClassLoader null
			}
			val nativeGetSpider = dexNativeClass.getMethod("getSpider", Any::class.java, String::class.java)
			directCandidates.forEach { candidate ->
				val nativeDelegate = runCatching { nativeGetSpider.invoke(null, initLoader, candidate) as? Spider }
					.onFailure {
						Log.w(
							TAG,
							"TVBox Guard delegate DexNative.getSpider probe failed for ${source.name}: candidate=$candidate loader=${initLoader.javaClass.name} message=${it.message}",
						)
					}
					.getOrNull()
				if (nativeDelegate != null) {
					Log.i(
						TAG,
						"Resolved TVBox Guard delegate for ${source.name} via DexNative.getSpider: candidate=$candidate impl=${nativeDelegate.javaClass.name}",
					)
					return@withContextClassLoader nativeDelegate
				}
				Log.w(
					TAG,
					"TVBox Guard delegate DexNative.getSpider returned null for ${source.name}: candidate=$candidate loader=${initLoader.javaClass.name}",
				)
			}
			null
		}
	}

	private fun buildGuardDelegateCandidates(guardClassName: String): List<String> {
		return buildList {
			add(guardClassName)
			if (guardClassName.endsWith("Guard")) {
				add(guardClassName.removeSuffix("Guard"))
			}
			val simpleName = guardClassName.substringAfterLast('.')
			if (simpleName.endsWith("Guard")) {
				add(simpleName.removeSuffix("Guard"))
			}
			add(simpleName)
		}.distinct()
	}

	private fun <T> withContextClassLoader(classLoader: ClassLoader, block: () -> T): T {
		val thread = Thread.currentThread()
		val previous = thread.contextClassLoader
		thread.contextClassLoader = classLoader
		return try {
			block()
		} finally {
			thread.contextClassLoader = previous
		}
	}

	private fun seedStaticContextIfPossible(classLoader: ClassLoader, className: String) {
		runCatching {
			val targetClass = Class.forName(className, false, classLoader)
			val staticFields = targetClass.declaredFields.filter { Modifier.isStatic(it.modifiers) }
			if (staticFields.isNotEmpty()) {
				Log.i(
					TAG,
					"Inspecting TVBox static fields for ${source.name}: class=$className fields=${staticFields.joinToString { "${it.name}:${it.type.name}" }}",
				)
			}
			val contextFields = staticFields
				.filter { field ->
					Context::class.java.isAssignableFrom(field.type) ||
						field.type.name == "android.app.Application" ||
						(field.type == Any::class.java && field.name.contains("context", ignoreCase = true))
				}
			if (staticFields.isEmpty()) {
				Log.i(TAG, "TVBox spider static context seeding skipped for ${source.name}: class=$className has no static fields")
			} else if (contextFields.isEmpty()) {
				Log.i(TAG, "TVBox spider static context seeding found no context-like fields for ${source.name}: class=$className")
			}
			contextFields.forEach { field ->
					field.isAccessible = true
					runCatching { field.set(null, context) }
						.onSuccess {
							Log.i(TAG, "Seeded TVBox static context for ${source.name}: class=$className field=${field.name}")
						}
						.onFailure { error ->
							Log.w(
								TAG,
								"Failed to seed TVBox static context for ${source.name}: class=$className field=${field.name} message=${error.message}",
							)
						}
				}
		}.onFailure {
			Log.i(TAG, "TVBox spider static context seeding unavailable for ${source.name}: class=$className message=${it.message}")
		}
	}

	private fun requiresStaticInit(error: Throwable): Boolean {
		return generateSequence(error) { it.cause }.any { cause ->
			val message = cause.message.orEmpty()
			cause is ExceptionInInitializerError ||
				message.contains("getCacheDir()", ignoreCase = true) ||
				message.contains("DexNative", ignoreCase = true)
		}
	}

	private fun invokeStaticInit(classLoader: ClassLoader, initContext: Context, stage: String) {
		runCatching {
			val failureHolder = arrayOfNulls<Throwable>(1)
			val initThread = Thread(
				{
					runCatching {
						withContextClassLoader(classLoader) {
							val initClass = classLoader.loadClass("com.github.catvod.spider.Init")
							val initMethod = initClass.getMethod("init", Context::class.java)
							initMethod.invoke(null, initContext)
						}
					}.onFailure {
						failureHolder[0] = it
					}
				},
				"tvbox-init-$stage",
			)
			initThread.contextClassLoader = classLoader
			initThread.start()
			initThread.join()
			failureHolder[0]?.let { throw it }
			if (initThread.isAlive) {
				error("TVBox Init thread did not finish for ${source.name}: stage=$stage")
			}
			Log.i(TAG, "TVBox spider Init invoked for ${source.name}: stage=$stage context=${initContext.javaClass.name}")
		}.onFailure {
			Log.w(TAG, "TVBox spider Init failed for ${source.name}: stage=$stage message=${it.message}", it)
		}
	}

	private fun snapshotGuardRuntimeState(classLoader: ClassLoader, spider: Spider): String {
		val delegateSummary = runCatching {
			val delegateField = generateSequence(spider.javaClass as Class<*>?) { it.superclass }
				.flatMap { targetClass -> targetClass.declaredFields.asSequence() }
				.firstOrNull { field ->
					!Modifier.isStatic(field.modifiers) && Spider::class.java.isAssignableFrom(field.type)
				}
			if (delegateField == null) {
				"delegateField=<missing>"
			} else {
				delegateField.isAccessible = true
				val delegate = delegateField.get(spider) as? Spider
				"delegateField=${delegateField.name} delegate=${delegate?.javaClass?.name ?: "null"}"
			}
		}.getOrElse { "delegateField=<error:${it.javaClass.simpleName}:${it.message}>" }
		val initSummary = runCatching {
			val initClass = classLoader.loadClass("com.github.catvod.spider.Init")
			val initSingleton = initClass.getMethod("get").invoke(null)
			initClass.declaredFields
				.filterNot { Modifier.isStatic(it.modifiers) }
				.joinToString { field ->
					field.isAccessible = true
					val value = field.get(initSingleton)
					val rendered = when (value) {
						null -> "null"
						is File -> value.absolutePath
						else -> "${value.javaClass.name}@${System.identityHashCode(value)}"
					}
					"${field.name}=$rendered"
				}
		}.getOrElse { "init=<error:${it.javaClass.simpleName}:${it.message}>" }
		return "spider=${spider.javaClass.name}, $delegateSummary, $initSummary"
	}

	private fun logHostBridgeState() {
		val cacheDir = runCatching { App.getInstance().cacheDir.absolutePath }
			.getOrElse { "<unavailable:${it.javaClass.simpleName}:${it.message}>" }
		val filesDir = runCatching { App.getInstance().filesDir.absolutePath }
			.getOrElse { "<unavailable:${it.javaClass.simpleName}:${it.message}>" }
		val bridgeClassLoader = runCatching { App.getInstance().classLoader.javaClass.name }
			.getOrElse { "<unavailable:${it.javaClass.simpleName}:${it.message}>" }
		val bridgeIdentity = runCatching {
			App.getInstance().applicationInfo.let {
				"packageName=${App.getInstance().packageName} appInfo.packageName=${it.packageName} processName=${it.processName} className=${it.className}"
			}
		}.getOrElse { "<unavailable:${it.javaClass.simpleName}:${it.message}>" }
		val applicationInfo = runCatching {
			context.applicationInfo.let { "sourceDir=${it.sourceDir} nativeLibraryDir=${it.nativeLibraryDir}" }
		}.getOrElse { "<unavailable:${it.javaClass.simpleName}:${it.message}>" }
		Log.i(
			TAG,
			"TVBox host bridge state for ${source.name}: context=${context.javaClass.name} appContext=${context.applicationContext.javaClass.name} cacheDir=$cacheDir filesDir=$filesDir classLoader=$bridgeClassLoader bridgeIdentity=$bridgeIdentity appInfo=$applicationInfo",
		)
	}

	private fun describeFailureChain(error: Throwable, limit: Int = 6): String {
		return generateSequence(error) { it.cause }
			.take(limit)
			.joinToString(" <- ") { cause ->
				"${cause.javaClass.simpleName}:${cause.message.orEmpty().replace('\n', ' ').take(120)}"
			}
	}

	private suspend fun ensureLoadedJar(): LoadedJar? = withContext(Dispatchers.IO) {
		val jarSpec = resolveJarSpec() ?: return@withContext null
		loadedJarCache[jarSpec.cacheKey]?.let { cached ->
			Log.i(TAG, "Reusing cached TVBox jar loader for ${source.name}: jarKey=${jarSpec.cacheKey} url=${jarSpec.url}")
			return@withContext cached
		}
		loadedJarCacheMutex.withLock {
			loadedJarCache[jarSpec.cacheKey]?.let { cached ->
				Log.i(TAG, "Reusing cached TVBox jar loader after lock for ${source.name}: jarKey=${jarSpec.cacheKey} url=${jarSpec.url}")
				return@withLock cached
			}
			val cacheDir = File(context.filesDir, "tvbox_csp").apply { mkdirs() }
			val jarFile = File(cacheDir, "${jarSpec.cacheKey}.jar")
			Log.i(TAG, "Preparing TVBox spider jar for ${source.name}: url=${jarSpec.url} cache=${jarFile.absolutePath}")
			if (!isUsableJarCache(jarFile, jarSpec.md5)) {
				downloadJar(jarSpec, jarFile)
			}
			if (!jarFile.exists() || jarFile.length() <= 0L) {
				Log.w(TAG, "TVBox spider jar cache is unusable after download for ${source.name}: ${jarFile.absolutePath}")
				return@withLock null
			}
			logMd5MismatchIfNeeded(jarFile, jarSpec)
			prepareJarForLoading(jarFile)
			val guardResources = prepareGuardRuntimeResources(jarSpec, jarFile)
			App.init(context)
			App.configureRuntimeDirs(
				guardResources?.hostCacheDir,
				guardResources?.hostCodeCacheDir,
				guardResources?.hostFilesDir,
			)
			val optimizedDir = resolveOptimizedDir(guardResources)
			val classLoader = createJarClassLoader(
				dexPath = jarFile.absolutePath,
				optimizedDir = optimizedDir,
				guardResources = guardResources,
			)
			App.configureRuntimeClassLoader(classLoader)
			val proxyMethod = runCatching {
				classLoader.loadClass("com.github.catvod.spider.Proxy")
					.getMethod("proxy", Map::class.java)
			}.getOrNull()
			LoadedJar(
				spec = jarSpec,
				classLoader = classLoader,
				proxyMethod = proxyMethod,
				guardResources = guardResources,
			).also { loadedJar ->
				loadedJarCache[jarSpec.cacheKey] = loadedJar
				Log.i(TAG, "Cached TVBox jar loader for ${source.name}: jarKey=${jarSpec.cacheKey} url=${jarSpec.url}")
			}
		}
	}

	private fun createJarClassLoader(
		dexPath: String,
		optimizedDir: File,
		guardResources: GuardRuntimeResources?,
	): DexClassLoader {
		val isGuardRuntime = guardResources != null || config.site.api.removePrefix("csp_").endsWith("Guard", ignoreCase = true)
		val parentClassLoader = if (isGuardRuntime) {
			runCatching { App.getInstance().classLoader }.getOrNull() ?: context.classLoader
		} else {
			context.classLoader
		}
		val librarySearchPath = if (isGuardRuntime) null else guardResources?.nativeLibraryDir?.absolutePath
		val loader = if (isGuardRuntime) {
			DexClassLoader(
				dexPath,
				optimizedDir.absolutePath,
				librarySearchPath,
				parentClassLoader,
			)
		} else {
			ChildFirstDexClassLoader(
				dexPath,
				optimizedDir.absolutePath,
				librarySearchPath,
				parentClassLoader,
			)
		}
		Log.i(
			TAG,
			"Created TVBox jar classloader for ${source.name}: guard=$isGuardRuntime loader=${loader.javaClass.name} optimizedDir=${optimizedDir.absolutePath} parent=${parentClassLoader.javaClass.name} librarySearchPath=${librarySearchPath ?: "-"}",
		)
		return loader
	}

	private fun resolveOptimizedDir(guardResources: GuardRuntimeResources?): File {
		if (guardResources == null) {
			return File(context.codeCacheDir, "tvbox_csp_opt").apply { mkdirs() }
		}
		val baseCacheDir = guardResources.hostCacheDir.takeIf { it.exists() }
			?: runCatching { App.getInstance().cacheDir }.getOrNull()
			?: context.cacheDir
		return File(baseCacheDir, "catvod_csp").apply { mkdirs() }
	}

	private fun prepareGuardRuntimeResources(spec: JarSpec, jarFile: File): GuardRuntimeResources? {
		val runtimeRootDir = File(context.filesDir, "tvbox_guard/${spec.cacheKey}").apply { mkdirs() }
		val nativeDir = File(runtimeRootDir, "native").apply { mkdirs() }
		val guardDataDir = File(runtimeRootDir, "assets").apply { mkdirs() }
		val hostRootDir = File(runtimeRootDir, "host/data/user/0/${App.HOST_PACKAGE_NAME}").apply { mkdirs() }
		val hostCacheDir = File(hostRootDir, "cache").apply { mkdirs() }
		val hostCodeCacheDir = File(hostRootDir, "code_cache").apply { mkdirs() }
		val hostFilesDir = File(hostRootDir, "files").apply { mkdirs() }
		val nativeEntries = mutableListOf<String>()
		val guardEntries = mutableListOf<String>()
		runCatching {
			ZipFile(jarFile).use { zip ->
				val entries = zip.entries()
				while (entries.hasMoreElements()) {
					val entry = entries.nextElement()
					if (entry.isDirectory) {
						continue
					}
					val normalizedName = entry.name.replace('\\', '/')
					val fileName = normalizedName.substringAfterLast('/').trim()
					if (fileName.isBlank()) {
						continue
					}
					when {
						normalizedName.startsWith("assets/", ignoreCase = true) && fileName.endsWith(".so", ignoreCase = true) -> {
							extractZipEntry(zip, entry.name, File(nativeDir, fileName))
							ensureLoadLibraryAlias(File(nativeDir, fileName))
							nativeEntries += fileName
						}

						normalizedName.startsWith("assets/", ignoreCase = true) && fileName.endsWith(".guard", ignoreCase = true) -> {
							extractZipEntry(zip, entry.name, File(guardDataDir, fileName))
							guardEntries += fileName
						}
					}
				}
			}
		}.onFailure {
			Log.w(TAG, "Failed to inspect TVBox spider jar assets for ${source.name}: ${jarFile.absolutePath}", it)
		}
		if (nativeEntries.isEmpty() && guardEntries.isEmpty()) {
			return null
		}
		val preferredLibrary = selectPreferredGuardLibrary(nativeEntries)
			?.let { File(nativeDir, it).absolutePath }
		val resources = GuardRuntimeResources(
			runtimeRootDir = runtimeRootDir,
			nativeLibraryDir = nativeDir,
			guardDataDir = guardDataDir,
			hostCacheDir = hostCacheDir,
			hostCodeCacheDir = hostCodeCacheDir,
			hostFilesDir = hostFilesDir,
			nativeEntries = nativeEntries.sorted(),
			guardEntries = guardEntries.sorted(),
			preferredLibraryPath = preferredLibrary,
		)
		Log.i(
			TAG,
			"Prepared TVBox Guard runtime resources for ${source.name}: native=${resources.nativeEntries} guard=${resources.guardEntries} preferred=${resources.preferredLibraryPath ?: "-"} root=${runtimeRootDir.absolutePath} hostCache=${resources.hostCacheDir.absolutePath}",
		)
		return resources
	}

	private fun extractZipEntry(zip: ZipFile, entryName: String, destination: File) {
		destination.parentFile?.mkdirs()
		zip.getEntry(entryName)?.let { entry ->
			zip.getInputStream(entry).use { input ->
				destination.outputStream().use { output ->
					input.copyTo(output)
				}
			}
			destination.setReadable(true, false)
		}
	}

	private fun ensureLoadLibraryAlias(sourceFile: File) {
		val fileName = sourceFile.name
		if (!fileName.endsWith(".so", ignoreCase = true) || fileName.startsWith("lib", ignoreCase = true)) {
			return
		}
		val alias = File(sourceFile.parentFile, "lib$fileName")
		if (!alias.exists() || alias.length() != sourceFile.length()) {
			runCatching {
				sourceFile.inputStream().use { input ->
					alias.outputStream().use { output ->
						input.copyTo(output)
					}
				}
				alias.setReadable(true, false)
			}.onFailure {
				Log.w(TAG, "Failed to create TVBox Guard native alias for ${source.name}: ${alias.absolutePath}", it)
			}
		}
	}

	private fun selectPreferredGuardLibrary(nativeEntries: List<String>): String? {
		if (nativeEntries.isEmpty()) {
			return null
		}
		val supportedAbis = Build.SUPPORTED_ABIS.map { it.lowercase() }
		val prefersV8 = supportedAbis.any { abi ->
			abi.contains("arm64") || abi.contains("x86_64")
		}
		val candidates = if (prefersV8) {
			listOf("v8", "arm64", "64")
		} else {
			listOf("v7", "armeabi", "arm")
		}
		return nativeEntries.firstOrNull { entry ->
			val normalized = entry.lowercase()
			candidates.any { token -> normalized.contains(token) }
		} ?: nativeEntries.firstOrNull()
	}

	private fun isUsableJarCache(file: File, expectedMd5: String?): Boolean {
		if (!file.exists() || file.length() <= 0L) {
			return false
		}
		if (!expectedMd5.isNullOrBlank()) {
			if (file.md5Hex().equals(expectedMd5, ignoreCase = true)) {
				return true
			}
		}
		return System.currentTimeMillis() - file.lastModified() <= CACHE_MAX_AGE_MS
	}

	private suspend fun downloadJar(spec: JarSpec, destination: File) {
		val response = httpClient.get(spec.url, buildHeadersForUrl(spec.url, emptyMap()), source)
		try {
			if (!response.isSuccessful) {
				throw IllegalArgumentException("HTTP ${response.code} when loading TVBox spider jar")
			}
			val bytes = response.body?.bytes()
				?: throw IllegalArgumentException("TVBox spider jar response body is empty")
			destination.parentFile?.mkdirs()
			if (destination.exists() && !destination.setWritable(true, true)) {
				Log.w(TAG, "Unable to mark TVBox spider jar writable before overwrite: ${destination.absolutePath}")
			}
			destination.writeBytes(bytes)
			Log.i(TAG, "Downloaded TVBox spider jar for ${source.name}: bytes=${bytes.size} file=${destination.absolutePath}")
			if (!spec.md5.isNullOrBlank() && !destination.md5Hex().equals(spec.md5, ignoreCase = true)) {
				Log.w(
					TAG,
					"TVBox spider jar MD5 mismatch for ${source.name}: expected=${spec.md5}, actual=${destination.md5Hex()}, url=${spec.url}",
				)
			}
			prepareJarForLoading(destination)
		} finally {
			response.close()
		}
	}

	private fun logMd5MismatchIfNeeded(file: File, spec: JarSpec) {
		val expectedMd5 = spec.md5?.trim().orEmpty()
		if (expectedMd5.isBlank()) {
			return
		}
		val actualMd5 = runCatching { file.md5Hex() }.getOrNull() ?: return
		if (!actualMd5.equals(expectedMd5, ignoreCase = true)) {
			Log.w(
				TAG,
				"TVBox spider jar MD5 mismatch for ${source.name}, but continuing with downloaded jar: expected=$expectedMd5, actual=$actualMd5, url=${spec.url}",
			)
		}
	}

	private fun prepareJarForLoading(file: File) {
		if (!file.exists()) {
			return
		}
		file.setReadable(true, true)
		file.setExecutable(false, false)
		if (!file.setWritable(false, false) && !file.setReadOnly()) {
			Log.w(TAG, "Unable to mark TVBox spider jar read-only: ${file.absolutePath}")
		}
	}

	private fun executeProxy(
		spider: Spider,
		loadedJar: LoadedJar,
	): TVBoxJarSpiderResponse {
		val proxySpec = request.proxySpec.orEmpty().ifBlank {
			return errorResponse("TVBox proxy request is missing proxySpec")
		}
		val mergedParams = LinkedHashMap<String, String>()
		mergedParams.putAll(parseProxyParams(proxySpec))
		mergedParams.putAll(request.queryParameters)
		mergedParams.putAll(request.headers)
		mergedParams["request-headers"] = JSONObject(request.headers).toString()
		val result = when {
			mergedParams.containsKey("do") -> runCatching { spider.proxyLocal(mergedParams) }.getOrNull()
			mergedParams.containsKey("go") -> runCatching { invokeStaticProxy(loadedJar, mergedParams) }.getOrNull()
			else -> null
		}
		return proxyArrayToResponse(result)
	}

	private fun invokeStaticProxy(loadedJar: LoadedJar, params: Map<String, String>): Array<Any?>? {
		val proxyMethod = loadedJar.proxyMethod ?: return null
		val result = proxyMethod.invoke(null, params)
		return if (result is Array<*>) {
			arrayOfNulls<Any?>(result.size).also { array ->
				result.indices.forEach { index -> array[index] = result[index] }
			}
		} else {
			null
		}
	}

	private fun proxyArrayToResponse(result: Array<Any?>?): TVBoxJarSpiderResponse {
		if (result == null || result.isEmpty()) {
			return TVBoxJarSpiderResponse(
				statusCode = 500,
				contentType = "text/plain; charset=utf-8",
				body = "TVBox proxy returned empty result".toByteArray(Charsets.UTF_8),
			)
		}
		val statusCode = (result.getOrNull(0) as? Number)?.toInt() ?: 500
		val contentType = result.getOrNull(1)?.toString().orEmpty().ifBlank { "application/octet-stream" }
		val headers = (result.getOrNull(3) as? Map<*, *>)
			?.mapNotNull { (key, value) ->
				val headerKey = key?.toString()?.trim().orEmpty()
				val headerValue = value?.toString()?.trim().orEmpty()
				if (headerKey.isBlank() || headerValue.isBlank()) {
					null
				} else {
					headerKey to headerValue
				}
			}
			?.toMap()
			.orEmpty()
		val redirectUrl = headers.entries.firstNotNullOfOrNull { (key, value) ->
			value.takeIf { key.equals("Location", ignoreCase = true) }
		}
		val proxyBody = when (val rawBody = result.getOrNull(2)) {
			null -> ProxyBody()
			is ByteArray -> rawBody.toProxyBody()
			is InputStream -> rawBody.use(::writeProxyBodyFile)
			else -> rawBody.toString().toByteArray(Charsets.UTF_8).toProxyBody()
		}
		return TVBoxJarSpiderResponse(
			statusCode = statusCode,
			contentType = contentType,
			headers = headers,
			body = if (redirectUrl != null) ByteArray(0) else proxyBody.body,
			bodyFilePath = if (redirectUrl != null) null else proxyBody.filePath,
			redirectUrl = redirectUrl,
		)
	}

	private fun ByteArray.toProxyBody(): ProxyBody {
		return if (size <= MAX_IPC_BODY_BYTES) {
			ProxyBody(body = this)
		} else {
			writeProxyBodyFile(inputStream())
		}
	}

	private fun writeProxyBodyFile(input: InputStream): ProxyBody {
		val file = File(context.filesDir, "tvbox_proxy_ipc").apply { mkdirs() }
			.resolve("${UUID.randomUUID()}.bin")
		input.use { sourceInput ->
			file.outputStream().use { output ->
				sourceInput.copyTo(output)
			}
		}
		return ProxyBody(filePath = file.absolutePath)
	}

	private fun parseProxyParams(proxySpec: String): Map<String, String> {
		val raw = proxySpec.removePrefix("proxy://")
		val uri = Uri.parse("http://127.0.0.1/proxy?$raw")
		return buildMap {
			uri.queryParameterNames.forEach { name ->
				val value = uri.getQueryParameter(name)
				if (!value.isNullOrBlank()) {
					put(name, value)
				}
			}
		}
	}

	private fun payloadResponse(payload: String): TVBoxJarSpiderResponse {
		return TVBoxJarSpiderResponse(payload = payload)
	}

	private fun errorResponse(message: String): TVBoxJarSpiderResponse {
		return TVBoxJarSpiderResponse(
			errorCode = TVBoxJarSpiderWorkerProtocol.ERROR_EXECUTION,
			errorMessage = message,
		)
	}

	private fun resolveJarSpec(): JarSpec? {
		val rawValue = config.site.jar?.takeIf { it.isNotBlank() }
			?: config.root.spider?.takeIf { it.isNotBlank() }
			?: return null
		val trimmed = rawValue.trim()
		val md5Index = trimmed.indexOf(";md5;", ignoreCase = true)
		val pkIndex = trimmed.indexOf(";pk;", ignoreCase = true)
		val cutIndex = listOf(md5Index, pkIndex)
			.filter { it >= 0 }
			.minOrNull()
			?: -1
		val urlPart = if (cutIndex >= 0) trimmed.substring(0, cutIndex).trim() else trimmed
		val md5 = if (md5Index >= 0) {
			trimmed.substring(md5Index + 5).substringBefore(';').trim().ifBlank { null }
		} else {
			null
		}
		val resolvedUrl = resolveCandidateUrl(urlPart) ?: return null
		return JarSpec(
			url = resolvedUrl,
			md5 = md5,
			cacheKey = resolvedUrl.md5Hex(),
		)
	}

	private fun buildExtLiteral(): String {
		val ext = config.site.ext ?: return ""
		return when (ext) {
			is String -> ext
			is JSONObject -> ext.toString()
			is JSONArray -> ext.toString()
			else -> ext.toString()
		}
	}

	private fun buildHeadersForUrl(url: String?, extraHeaders: Map<String, String>): Map<String, String> {
		val headers = linkedMapOf<String, String>()
		headers += config.site.staticHeaders
		headers += extraHeaders
		val host = url?.toHttpUrlOrNull()?.host?.lowercase()
		if (!host.isNullOrBlank()) {
			config.root.headerRules
				.filter { host == it.host.lowercase() }
				.forEach { rule -> headers += rule.headers }
		}
		if (!headers.keys.any { it.equals(CommonHeaders.REFERER, ignoreCase = true) }) {
			url?.toHttpUrlOrNull()?.let { httpUrl ->
				headers[CommonHeaders.REFERER] = "${httpUrl.scheme}://${httpUrl.host}/"
			}
		}
		return headers
	}

	private fun resolveCandidateUrl(rawValue: String?): String? {
		val value = rawValue?.trim().orEmpty().extractPrimaryLocator()
		if (value.isBlank()) {
			return null
		}
		if (value.startsWith("http://", ignoreCase = true) ||
			value.startsWith("https://", ignoreCase = true) ||
			value.startsWith("content://", ignoreCase = true) ||
			value.startsWith("file://", ignoreCase = true)
		) {
			return value
		}
		if (value.startsWith("//")) {
			return "https:$value"
		}
		val baseHttpUrl = config.meta.sourceLocator?.toHttpUrlOrNull()
		return baseHttpUrl?.resolve(value)?.toString() ?: value
	}

	private fun invokeSpider(
		cachedSpider: CachedSpider,
		action: String,
		timeoutMs: Long,
		block: (Spider) -> String,
	): String? {
		Log.i(TAG, "TVBox spider worker start for ${source.name}: $action")
		return runCatching { executeSpiderCall(cachedSpider, action, timeoutMs, block) }
			.onSuccess { result ->
				Log.i(
					TAG,
					"TVBox spider worker succeeded for ${source.name}: $action, resultLength=${result.length}, preview=${result.take(160)}",
				)
			}
			.onFailure { error ->
				Log.w(TAG, "TVBox spider worker failed for ${source.name}: $action", error)
				logReflectiveFailure(action, error)
			}
			.getOrNull()
	}

	private fun executeSpiderCall(
		cachedSpider: CachedSpider,
		action: String,
		timeoutMs: Long,
		block: (Spider) -> String,
	): String {
		var future: Future<String>? = null
		try {
			future = cachedSpider.executor.submit<String> {
				block(cachedSpider.spider)
			}
			return future.get(timeoutMs, TimeUnit.MILLISECONDS)
		} catch (error: TimeoutException) {
			future?.cancel(true)
			Log.e(TAG, "TVBox spider worker timed out for ${source.name}: $action after ${timeoutMs}ms")
			throw error
		} catch (error: Throwable) {
			future?.cancel(true)
			throw error
		}
	}

	private fun logReflectiveFailure(action: String, error: Throwable) {
		when (error) {
			is InvocationTargetException -> {
				val target = error.targetException ?: error.cause
				Log.e(
					TAG,
					"TVBox spider worker target failure for ${source.name}: action=$action, target=${target?.javaClass?.name}, message=${target?.message}",
					target ?: error,
				)
			}

			is NoClassDefFoundError -> {
				Log.e(
					TAG,
					"TVBox spider worker missing class for ${source.name}: action=$action, message=${error.message}",
					error,
				)
			}

			is ClassNotFoundException -> {
				Log.e(
					TAG,
					"TVBox spider worker class not found for ${source.name}: action=$action, message=${error.message}",
					error,
				)
			}
		}
		error.cause?.let { cause ->
			if (cause !== error) {
				logReflectiveFailure("$action.cause", cause)
			}
		}
	}

	private data class JarSpec(
		val url: String,
		val md5: String?,
		val cacheKey: String,
	)

	private data class LoadedJar(
		val spec: JarSpec,
		val classLoader: ClassLoader,
		val proxyMethod: Method?,
		val guardResources: GuardRuntimeResources?,
	)

	private data class CachedSpider(
		val jarCacheKey: String,
		val className: String,
		val extLiteral: String,
		val spider: Spider,
		val executor: java.util.concurrent.ExecutorService,
	)

	private data class ProxyBody(
		val body: ByteArray = ByteArray(0),
		val filePath: String? = null,
	)

	private data class GuardRuntimeResources(
		val runtimeRootDir: File,
		val nativeLibraryDir: File,
		val guardDataDir: File,
		val hostCacheDir: File,
		val hostCodeCacheDir: File,
		val hostFilesDir: File,
		val nativeEntries: List<String>,
		val guardEntries: List<String>,
		val preferredLibraryPath: String?,
	)

	private class TracingBridgeContext(
		base: Context,
		private val sourceName: String,
		val probeLabel: String,
		private val overriddenClassLoader: ClassLoader? = null,
		private val overriddenCacheDir: File? = null,
	) : ContextWrapper(base) {
		private val callCounts = LinkedHashMap<String, Int>()

		private fun <T> trace(name: String, value: () -> T): T {
			callCounts[name] = (callCounts[name] ?: 0) + 1
			val count = callCounts.getValue(name)
			if (count <= 3) {
				Log.i(TAG, "TVBox DexNative tracing probe for $sourceName: probe=$probeLabel method=$name count=$count")
			}
			return value()
		}

		fun dumpSummary() {
			Log.i(
				TAG,
				"TVBox DexNative tracing summary for $sourceName: probe=$probeLabel calls=${if (callCounts.isEmpty()) "<none>" else callCounts.entries.joinToString { "${it.key}=${it.value}" }}",
			)
		}

		override fun getApplicationContext(): Context = trace("getApplicationContext") { this }

		override fun getPackageName(): String = trace("getPackageName") { super.getPackageName() }

		override fun getApplicationInfo() = trace("getApplicationInfo") { super.getApplicationInfo() }

		override fun getPackageManager() = trace("getPackageManager") { super.getPackageManager() }

		override fun getCacheDir(): File = trace("getCacheDir") { overriddenCacheDir ?: super.getCacheDir() }

		override fun getCodeCacheDir(): File = trace("getCodeCacheDir") { super.getCodeCacheDir() }

		override fun getFilesDir(): File = trace("getFilesDir") { super.getFilesDir() }

		override fun getDir(name: String, mode: Int): File = trace("getDir($name,$mode)") { super.getDir(name, mode) }

		override fun getDatabasePath(name: String): File = trace("getDatabasePath($name)") { super.getDatabasePath(name) }

		override fun getClassLoader(): ClassLoader = trace("getClassLoader") { overriddenClassLoader ?: super.getClassLoader() }

		override fun getPackageCodePath(): String = trace("getPackageCodePath") { super.getPackageCodePath() }

		override fun getPackageResourcePath(): String = trace("getPackageResourcePath") { super.getPackageResourcePath() }

		override fun createPackageContext(packageName: String, flags: Int): Context {
			return trace("createPackageContext($packageName,$flags)") { super.createPackageContext(packageName, flags) }
		}
	}

	private class DexNativeLoaderBridge(
		private val classLoader: ClassLoader,
		private val cacheDir: File,
	) {
		@Suppress("unused")
		fun getClassLoader(): ClassLoader = classLoader

		@Suppress("unused")
		fun getCacheDir(): File = cacheDir
	}

	private open class BaseMasqueradingFile(
		private val realFile: File,
		private val displayPath: String,
		superPath: String,
	) : File(superPath) {
		override fun getAbsolutePath(): String = displayPath

		override fun getCanonicalPath(): String = displayPath

		override fun toString(): String = displayPath

		override fun getName(): String = displayPath.substringAfterLast('/')

		override fun getParent(): String? = displayPath.substringBeforeLast('/', "")
			.ifBlank { null }

		override fun getParentFile(): File? = getParent()?.let(::File)

		override fun exists(): Boolean = realFile.exists()

		override fun isDirectory(): Boolean = realFile.isDirectory

		override fun isFile(): Boolean = realFile.isFile

		override fun canRead(): Boolean = realFile.canRead()

		override fun canWrite(): Boolean = realFile.canWrite()

		override fun mkdirs(): Boolean = realFile.mkdirs()
	}

	private class DisplayMasqueradingFile(
		realFile: File,
		displayPath: String,
	) : BaseMasqueradingFile(
		realFile = realFile,
		displayPath = displayPath,
		superPath = realFile.absolutePath,
	) {
		override fun getPath(): String = super.getAbsolutePath()
	}

	private class PureMasqueradingFile(
		realFile: File,
		displayPath: String,
	) : BaseMasqueradingFile(
		realFile = realFile,
		displayPath = displayPath,
		superPath = displayPath,
	) {
		override fun getPath(): String = super.getAbsolutePath()
	}
}

private fun String.extractPrimaryLocator(): String {
	val trimmed = trim()
	if (trimmed.isBlank()) {
		return trimmed
	}
	val markers = listOf(";md5;", ";pk;")
	markers.forEach { marker ->
		val index = trimmed.indexOf(marker, ignoreCase = true)
		if (index >= 0) {
			return trimmed.substring(0, index).trim()
		}
	}
	val separatorIndex = trimmed.indexOf(';')
	return if (separatorIndex >= 0) {
		trimmed.substring(0, separatorIndex).trim()
	} else {
		trimmed
	}
}

private fun String.md5Hex(): String {
	val digest = MessageDigest.getInstance("MD5").digest(toByteArray(Charsets.UTF_8))
	return digest.joinToString("") { "%02x".format(it) }
}

private fun File.md5Hex(): String {
	val digest = MessageDigest.getInstance("MD5")
	inputStream().use { input ->
		val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
		while (true) {
			val read = input.read(buffer)
			if (read <= 0) {
				break
			}
			digest.update(buffer, 0, read)
		}
	}
	return digest.digest().joinToString("") { "%02x".format(it) }
}
