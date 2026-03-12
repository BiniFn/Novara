package org.skepsun.kototoro.settings.sources.aniyomi

import android.os.Bundle
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.settings.sources.extensions.BaseInstalledExtensionsFragment

/**
 * Fragment for managing Aniyomi extensions.
 * 
 * Shows a list of installed Aniyomi extensions and their sources,
 * allowing users to see which extensions are loaded.
 */
@AndroidEntryPoint
class AniyomiExtensionsFragment : BaseInstalledExtensionsFragment<AniyomiExtensionsViewModel>() {

	override val viewModel by viewModels<AniyomiExtensionsViewModel>()
	override val emptyTitleRes: Int = R.string.no_aniyomi_extensions
	override val emptyTextRes: Int = R.string.no_aniyomi_extensions_text
	override val extensionCountRes: Int = R.string.aniyomi_extension_count
	override val sourceCountRes: Int = R.string.aniyomi_source_count
}
