package org.skepsun.kototoro.core.parser.tvbox

import dalvik.system.DexClassLoader

internal class ChildFirstDexClassLoader(
	dexPath: String,
	optimizedDirectory: String,
	librarySearchPath: String?,
	parent: ClassLoader,
) : DexClassLoader(dexPath, optimizedDirectory, librarySearchPath, parent) {

	private val parentPackages = setOf(
		"java.",
		"javax.",
		"kotlin.",
		"kotlinx.",
		"android.",
		"androidx.",
		"org.json.",
		"okhttp3.",
		"okio.",
		"org.jsoup.",
		"org.skepsun.kototoro.",
		"com.github.catvod.crawler.",
	)

	override fun loadClass(name: String, resolve: Boolean): Class<*> {
		if (parentPackages.any { name.startsWith(it) }) {
			return parent.loadClass(name)
		}
		return try {
			findLoadedClass(name) ?: findClass(name)
		} catch (_: ClassNotFoundException) {
			parent.loadClass(name)
		}
	}
}
