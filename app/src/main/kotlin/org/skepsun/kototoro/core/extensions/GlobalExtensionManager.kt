package org.skepsun.kototoro.core.extensions

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.skepsun.kototoro.parsers.ContentLoaderContext
import org.skepsun.kototoro.parsers.ContentParser
import org.skepsun.kototoro.parsers.model.ContentSource
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class PluginMangaSource(
    val originalSource: MangaSource,
    val jarName: String
) : MangaSource by originalSource {
    val id: String get() = "$jarName:${originalSource.name}"
}

data class PluginContentSource(
    val originalSource: ContentSource,
    val jarName: String
) : ContentSource by originalSource {
    val id: String get() = "$jarName:${originalSource.name}"
}

object GlobalExtensionManager {
    private val mangaPlugins = ConcurrentHashMap<String, LoadedJarPlugin>()
    private val contentPlugins = ConcurrentHashMap<String, LoadedJarPlugin>()

    private val _mangaSources = MutableStateFlow<List<PluginMangaSource>>(emptyList())
    val mangaSources: StateFlow<List<PluginMangaSource>> = _mangaSources.asStateFlow()

    private val _contentSources = MutableStateFlow<List<PluginContentSource>>(emptyList())
    val contentSources: StateFlow<List<PluginContentSource>> = _contentSources.asStateFlow()

    fun initialize(context: Context) {
        val pluginDir = File(context.filesDir, "plugins")
        val plugins = JarExtensionLoader.loadFromDirectory(context, pluginDir)

        val newMangaSources = mutableListOf<PluginMangaSource>()
        val newContentSources = mutableListOf<PluginContentSource>()

        mangaPlugins.clear()
        contentPlugins.clear()

        for (plugin in plugins) {
            if (plugin.isMangaParser) {
                mangaPlugins[plugin.jarName] = plugin
                val wrapped = plugin.sources.map { PluginMangaSource(it as MangaSource, plugin.jarName) }
                newMangaSources.addAll(wrapped)
            } else {
                contentPlugins[plugin.jarName] = plugin
                val wrapped = plugin.sources.map { PluginContentSource(it as ContentSource, plugin.jarName) }
                newContentSources.addAll(wrapped)
            }
        }

        _mangaSources.value = newMangaSources
        _contentSources.value = newContentSources
    }

    fun getMangaParser(source: MangaSource, context: MangaLoaderContext): MangaParser {
        val pluginSource = source as? PluginMangaSource ?: 
            _mangaSources.value.find { it.originalSource == source || it.name == source.name }
            ?: throw IllegalArgumentException("No PluginMangaSource found for: ${source.name}")
        val plugin = mangaPlugins[pluginSource.jarName] ?: throw IllegalStateException("JAR missing: ${pluginSource.jarName}")
        return JarExtensionLoader.instantiateMangaParser(plugin, pluginSource.originalSource, context)
    }

    fun getContentParser(source: ContentSource, context: ContentLoaderContext): ContentParser {
        val pluginSource = source as? PluginContentSource ?: 
            _contentSources.value.find { it.originalSource == source || it.name == source.name }
            ?: throw IllegalArgumentException("No PluginContentSource found for: ${source.name}")
        val plugin = contentPlugins[pluginSource.jarName] ?: throw IllegalStateException("JAR missing: ${pluginSource.jarName}")
        return JarExtensionLoader.instantiateContentParser(plugin, pluginSource.originalSource, context)
    }
}
