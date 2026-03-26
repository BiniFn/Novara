package org.skepsun.kototoro.core.extensions

import android.content.Context
import dalvik.system.DexClassLoader
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.skepsun.kototoro.parsers.ContentLoaderContext
import org.skepsun.kototoro.parsers.ContentParser
import org.skepsun.kototoro.parsers.model.ContentSource
import java.io.File
import java.lang.reflect.Method

/**
 * A custom ClassLoader that enforces parent delegation for the shared 'parser-api' classes.
 * This ensures that both the Host App and the Plugin JAR use the exact same Class references
 * in memory for interfaces like MangaParser, MangaSource, etc., allowing for zero-overhead
 * direct casting without java.lang.reflect.Proxy wrappers.
 */
class PluginClassLoader(
    dexPath: String,
    optimizedDirectory: String?,
    librarySearchPath: String?,
    parent: ClassLoader
) : DexClassLoader(dexPath, optimizedDirectory, librarySearchPath, parent) {

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        // First, don't delegate the plugin's own generated parser factory and sources list back to the host,
        // because they are unique to each jar!
        if (name == "org.skepsun.kototoro.parsers.ContentParserFactoryKt" ||
            name == "org.koitharu.kotatsu.parsers.MangaParserFactoryKt" ||
            name == "org.skepsun.kototoro.parsers.model.ContentParserSource" ||
            name == "org.koitharu.kotatsu.parsers.model.MangaParserSource") {
            val c = findLoadedClass(name) ?: findClass(name)
            return c
        }

        // Enforce parent delegation for the entire parser-api shared library namespace
        if (name.startsWith("org.koitharu.kotatsu.parsers.model.") ||
            name.startsWith("org.koitharu.kotatsu.parsers.config.") ||
            name.startsWith("org.koitharu.kotatsu.parsers.webview.") ||
            name == "org.koitharu.kotatsu.parsers.MangaLoaderContext" ||
            name == "org.koitharu.kotatsu.parsers.MangaParser" ||
            name.startsWith("org.koitharu.kotatsu.parsers.util.LinkResolver") ||
            name.startsWith("org.skepsun.kototoro.parsers.model.") ||
            name.startsWith("org.skepsun.kototoro.parsers.config.") ||
            name == "org.skepsun.kototoro.parsers.ContentLoaderContext" ||
            name == "org.skepsun.kototoro.parsers.ContentParser" ||
            name.startsWith("org.skepsun.kototoro.parsers.util.LinkResolver")
        ) {
            return super.loadClass(name, resolve)
        }

        // For site implementations or core base classes embedded in the plugin, try loading from the plugin first.
        // This isolates the plugins from each other (e.g. yaka vs redo) and from the host.
        if (name.startsWith("org.koitharu.kotatsu.parsers.site.") ||
            name.startsWith("org.koitharu.kotatsu.parsers.core.") ||
            name.startsWith("org.koitharu.kotatsu.parsers.util.") ||
            name.startsWith("org.koitharu.kotatsu.parsers.network.") ||
            name.startsWith("org.koitharu.kotatsu.parsers.exception.") ||
            name.startsWith("org.koitharu.kotatsu.parsers.MangaParserFactory") ||
            name.startsWith("org.skepsun.kototoro.parsers.site.") ||
            name.startsWith("org.skepsun.kototoro.parsers.core.") ||
            name.startsWith("org.skepsun.kototoro.parsers.util.") ||
            name.startsWith("org.skepsun.kototoro.parsers.network.") ||
            name.startsWith("org.skepsun.kototoro.parsers.exception.") ||
            name.startsWith("org.skepsun.kototoro.parsers.ContentParserFactory")
        ) {
            try {
                return findClass(name)
            } catch (_: ClassNotFoundException) {
            }
        }
        return super.loadClass(name, resolve)
    }
}

data class LoadedJarPlugin(
    val jarName: String,
    val classLoader: PluginClassLoader,
    val isMangaParser: Boolean,
    val factoryMethod: Method,
    val sources: List<Any> // Either List<MangaSource> or List<ContentSource>
)

object JarExtensionLoader {

    fun loadFromDirectory(context: Context, pluginDir: File): List<LoadedJarPlugin> {
        val cacheDir = context.codeCacheDir.absolutePath
        val parentClassLoader = context.classLoader
        val plugins = mutableListOf<LoadedJarPlugin>()

        if (!pluginDir.exists()) return emptyList()

        val jarFiles = pluginDir.listFiles { file -> file.extension == "jar" } ?: emptyArray()

        for (jarFile in jarFiles) {
            jarFile.setReadOnly() // Fix Android 14+ classloader restrictions
            val dexClassLoader = PluginClassLoader(
                jarFile.absolutePath,
                cacheDir,
                null,
                parentClassLoader
            )

            // Try Kotatsu Parser Architecture
            try {
                val factoryClass = dexClassLoader.loadClass("org.koitharu.kotatsu.parsers.MangaParserFactoryKt")
                // Search for the generated enum by loading it
                val enumPath = tryFindEnumClass(dexClassLoader, "org.koitharu.kotatsu.parsers.model.MangaParserSource")
                if (enumPath != null) {
                    val mangaLoaderContextClass = dexClassLoader.loadClass("org.koitharu.kotatsu.parsers.MangaLoaderContext")
                    val newParserMethod = factoryClass.declaredMethods.first { it.name.startsWith("newParser") && it.parameterTypes.size == 2 }
                    val enumConstants = enumPath.enumConstants?.filterIsInstance<MangaSource>() ?: emptyList()
                    
                    if (enumConstants.isNotEmpty()) {
                        plugins.add(LoadedJarPlugin(jarFile.name, dexClassLoader, true, newParserMethod, enumConstants))
                        continue // Success, move to next jar
                    }
                }
            } catch (e: Exception) {
                // Ignore, try Kototoro architecture
                android.util.Log.e("KototoroInit", "Failed loading Kotatsu architecture from ${jarFile.name}: ${e.message}", e)
            }

            // Try Kototoro Parser Architecture
            try {
                val factoryClass = dexClassLoader.loadClass("org.skepsun.kototoro.parsers.ContentParserFactoryKt")
                val enumPath = tryFindEnumClass(dexClassLoader, "org.skepsun.kototoro.parsers.model.ContentParserSource")
                if (enumPath != null) {
                    val contentLoaderContextClass = dexClassLoader.loadClass("org.skepsun.kototoro.parsers.ContentLoaderContext")
                    val newParserMethod = factoryClass.declaredMethods.first { it.name.startsWith("newParser") && it.parameterTypes.size == 2 }
                    val enumConstants = enumPath.enumConstants?.filterIsInstance<ContentSource>() ?: emptyList()
                    
                    if (enumConstants.isNotEmpty()) {
                        plugins.add(LoadedJarPlugin(jarFile.name, dexClassLoader, false, newParserMethod, enumConstants))
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("KototoroInit", "Failed loading Kototoro architecture from ${jarFile.name}: ${e.message}", e)
                e.printStackTrace()
            }
        }
        return plugins
    }

    private fun tryFindEnumClass(cl: ClassLoader, name: String): Class<*>? {
        return try {
            cl.loadClass(name)
        } catch (e: ClassNotFoundException) {
            null
        }
    }

    fun instantiateMangaParser(plugin: LoadedJarPlugin, source: MangaSource, context: MangaLoaderContext): MangaParser {
        val enumClass = plugin.factoryMethod.parameterTypes[0]
        val matchingEnum = enumClass.enumConstants?.find { (it as? MangaSource)?.name == source.name }
            ?: throw IllegalArgumentException("Source missing in JAR: ${source.name}")
        plugin.factoryMethod.isAccessible = true
        return plugin.factoryMethod.invoke(null, matchingEnum, context) as MangaParser
    }

    fun instantiateContentParser(plugin: LoadedJarPlugin, source: ContentSource, context: ContentLoaderContext): ContentParser {
        val enumClass = plugin.factoryMethod.parameterTypes[0]
        val matchingEnum = enumClass.enumConstants?.find { (it as? ContentSource)?.name == source.name }
            ?: throw IllegalArgumentException("Source missing in JAR: ${source.name}")
        plugin.factoryMethod.isAccessible = true
        return plugin.factoryMethod.invoke(null, matchingEnum, context) as ContentParser
    }
}
