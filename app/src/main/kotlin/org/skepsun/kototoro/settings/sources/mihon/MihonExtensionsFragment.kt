package org.skepsun.kototoro.settings.sources.mihon

import android.os.Bundle
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.settings.sources.extensions.BaseInstalledExtensionsFragment

/**
 * Fragment for managing Mihon extensions.
 * 
 * Shows a list of installed Mihon extensions and their sources,
 * allowing users to see which extensions are loaded.
 */
@AndroidEntryPoint
class MihonExtensionsFragment : BaseInstalledExtensionsFragment<MihonExtensionsViewModel>() {

	override val viewModel by viewModels<MihonExtensionsViewModel>()
	override val emptyTitleRes: Int = R.string.no_mihon_extensions
	override val emptyTextRes: Int = R.string.no_mihon_extensions_text
	override val extensionCountRes: Int = R.string.mihon_extension_count
	override val sourceCountRes: Int = R.string.mihon_source_count
}
