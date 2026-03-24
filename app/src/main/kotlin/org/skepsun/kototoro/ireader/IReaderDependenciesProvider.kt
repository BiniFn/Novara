package org.skepsun.kototoro.ireader

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.BrowserUserAgent
import ireader.core.http.BrowserEngine
import ireader.core.http.CloudflareBypassHandler
import ireader.core.http.CookieSynchronizer
import ireader.core.http.HttpClientsInterface
import ireader.core.http.NetworkConfig
import ireader.core.http.NoOpCloudflareBypassHandler
import ireader.core.http.SSLConfiguration
import ireader.core.prefs.Preference
import ireader.core.prefs.PreferenceStore
import ireader.core.source.Dependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule

/**
 * Provides a [Dependencies] instance for IReader extensions loaded in Kototoro.
 *
 * This is a minimal implementation that satisfies the constructor requirement of
 * IReader's HttpSource base class. Extensions use Dependencies to access HTTP
 * clients and preferences.
 */
object IReaderDependenciesProvider {

    @Volatile
    private var dependencies: Dependencies? = null

    fun get(context: Context, kototoroClient: okhttp3.OkHttpClient): Dependencies {
        return dependencies ?: synchronized(this) {
            dependencies ?: createDependencies(context, kototoroClient).also { dependencies = it }
        }
    }

    private fun createDependencies(context: Context, kototoroClient: okhttp3.OkHttpClient): Dependencies {
        // We need to use Kototoro's configured OkHttpClient so that plugins 
        // can benefit from proxy settings, cookie manager, and CloudFlare bypass.
        val httpClients = KotoHttpClients(kototoroClient)
        val preferences = InMemoryPreferenceStore()
        return Dependencies(
            httpClients = httpClients,
            preferences = preferences,
        )
    }
}

/**
 * Minimal HttpClientsInterface for IReader extensions running in Kototoro.
 * Provides basic Ktor HttpClient with OkHttp engine.
 */
private class KotoHttpClients(
    kototoroClient: okhttp3.OkHttpClient
) : HttpClientsInterface {
    override val browser: BrowserEngine = BrowserEngine()
    override val config: NetworkConfig = NetworkConfig()
    override val sslConfig: SSLConfiguration = SSLConfiguration()
    override val cookieSynchronizer: CookieSynchronizer
        get() = throw UnsupportedOperationException("CookieSynchronizer not available in Kototoro bridge")
    override val cloudflareBypassHandler: CloudflareBypassHandler = NoOpCloudflareBypassHandler

    override val default: HttpClient = HttpClient(OkHttp) {
        engine {
            preconfigured = kototoroClient
        }
    }

    override val cloudflareClient: HttpClient = default
}

/**
 * Simple in-memory PreferenceStore for IReader extensions.
 * Values are not persisted across app restarts.
 */
private class InMemoryPreferenceStore : PreferenceStore {

    private val store = mutableMapOf<String, Any?>()

    override fun getString(key: String, defaultValue: String): Preference<String> =
        InMemoryPreference(store, key, defaultValue)

    override fun getLong(key: String, defaultValue: Long): Preference<Long> =
        InMemoryPreference(store, key, defaultValue)

    override fun getInt(key: String, defaultValue: Int): Preference<Int> =
        InMemoryPreference(store, key, defaultValue)

    override fun getFloat(key: String, defaultValue: Float): Preference<Float> =
        InMemoryPreference(store, key, defaultValue)

    override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
        InMemoryPreference(store, key, defaultValue)

    override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
        InMemoryPreference(store, key, defaultValue)

    override fun <T> getObject(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T
    ): Preference<T> = InMemoryPreference(store, key, defaultValue)

    override fun <T> getJsonObject(
        key: String,
        defaultValue: T,
        serializer: KSerializer<T>,
        serializersModule: SerializersModule
    ): Preference<T> = InMemoryPreference(store, key, defaultValue)
}

@Suppress("UNCHECKED_CAST")
private class InMemoryPreference<T>(
    private val store: MutableMap<String, Any?>,
    private val key: String,
    private val defaultVal: T,
) : Preference<T> {

    override fun key(): String = key

    override fun get(): T = (store[key] as? T) ?: defaultVal

    override fun set(value: T) {
        store[key] = value
    }

    override fun isSet(): Boolean = store.containsKey(key)

    override fun delete() {
        store.remove(key)
    }

    override fun defaultValue(): T = defaultVal

    override fun changes(): Flow<T> = flowOf(get())

    override fun stateIn(scope: CoroutineScope): StateFlow<T> =
        MutableStateFlow(get())
}
