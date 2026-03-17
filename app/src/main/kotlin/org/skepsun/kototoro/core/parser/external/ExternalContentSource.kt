package org.skepsun.kototoro.core.parser.external

import android.content.Context
import org.skepsun.kototoro.parsers.model.ContentSource

data class ExternalContentSource(
	val packageName: String,
	val authority: String,
) : ContentSource {

	override val name: String
		get() = "content:$packageName/$authority"

	private var cachedName: String? = null

	fun isAvailable(context: Context): Boolean {
		return context.packageManager.resolveContentProvider(authority, 0)?.isEnabled == true
	}

	fun resolveName(context: Context): String {
		cachedName?.let {
			return it
		}
		val pm = context.packageManager
		val info = pm.resolveContentProvider(authority, 0)
		return info?.loadLabel(pm)?.toString()?.also {
			cachedName = it
		} ?: authority
	}
}
