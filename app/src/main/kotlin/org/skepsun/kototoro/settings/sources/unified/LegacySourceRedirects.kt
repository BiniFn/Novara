package org.skepsun.kototoro.settings.sources.unified

import androidx.fragment.app.Fragment
import org.skepsun.kototoro.core.db.entity.JsonSourceType
import org.skepsun.kototoro.extensions.repo.ExternalExtensionType

internal fun Fragment.redirectToUnifiedSources(
	kind: UnifiedSourceKind? = null,
	url: String? = null,
) {
	startActivity(
		UnifiedSourcesActivity.newIntent(
			context = requireContext(),
			initialRepositoryKind = kind,
			initialRepositoryUrl = url,
		),
	)
}

internal fun ExternalExtensionType.toUnifiedSourceKind(): UnifiedSourceKind {
	return when (this) {
		ExternalExtensionType.MIHON -> UnifiedSourceKind.MIHON
		ExternalExtensionType.ANIYOMI -> UnifiedSourceKind.ANIYOMI
		ExternalExtensionType.IREADER -> UnifiedSourceKind.IREADER
		ExternalExtensionType.JAR -> UnifiedSourceKind.JAR
	}
}

internal fun JsonSourceType.toUnifiedSourceKind(): UnifiedSourceKind {
	return when (this) {
		JsonSourceType.LEGADO -> UnifiedSourceKind.LEGADO
		JsonSourceType.TVBOX -> UnifiedSourceKind.TVBOX
		JsonSourceType.JS -> UnifiedSourceKind.JS
		JsonSourceType.LNREADER -> UnifiedSourceKind.LNREADER
	}
}
