package org.skepsun.kototoro.settings.sources.unified

import androidx.fragment.app.Fragment
import org.skepsun.kototoro.core.db.entity.JsonSourceType
import org.skepsun.kototoro.extensions.repo.ExternalExtensionType
import org.skepsun.kototoro.settings.SettingsActivity
import org.skepsun.kototoro.settings.SettingsDestination

internal fun Fragment.redirectToUnifiedSources(
	kind: UnifiedSourceKind? = null,
	url: String? = null,
) {
	val settingsActivity = activity as? SettingsActivity
	if (settingsActivity != null) {
		settingsActivity.replaceCurrentFragmentWithDestination(
			SettingsDestination.UnifiedSources(
				initialRepositoryKind = kind,
				initialRepositoryUrl = url,
			),
		)
		return
	}
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
		ExternalExtensionType.CLOUDSTREAM -> UnifiedSourceKind.CLOUDSTREAM
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
