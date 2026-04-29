package org.skepsun.kototoro.settings.sources.unified

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.settings.SettingsActivity

@AndroidEntryPoint
class UnifiedSourcesFragment : Fragment() {

	private val viewModel by viewModels<UnifiedSourcesViewModel>()
	private var pendingFileImportKind: UnifiedSourceKind? = null

	private val openRepositoryFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
		if (uri == null) return@registerForActivityResult
		val kind = pendingFileImportKind ?: return@registerForActivityResult
		pendingFileImportKind = null
		persistReadPermission(uri)
		viewModel.addRepositoryFromFile(kind, uri)
	}
	private val openLocalJar = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
		if (uri == null) return@registerForActivityResult
		persistReadPermission(uri)
		viewModel.importLocalJar(uri)
	}
	private val installLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
		viewModel.onInstallActivityResult()
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View {
		return ComposeView(requireContext()).apply {
			setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
			setContent {
				KototoroTheme {
					UnifiedSourcesRoute(
						viewModel = viewModel,
						onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() },
						onBrowseSource = { item -> router.openList(item.source, null, null) },
						onOpenSourceSettings = { item -> router.openSourceSettings(item.source) },
						onOpenRepositoryFile = ::openRepositoryFilePicker,
						onOpenLocalJarPicker = ::openLocalJarPicker,
						onStartInstall = { intent ->
							runCatching { installLauncher.launch(intent) }
								.onFailure { Toast.makeText(requireContext(), it.message, Toast.LENGTH_SHORT).show() }
						},
						onStartUninstall = { intent ->
							runCatching { startActivity(intent) }
								.onFailure { Toast.makeText(requireContext(), it.message, Toast.LENGTH_SHORT).show() }
						},
					)
				}
			}
		}
	}

	override fun onResume() {
		super.onResume()
		(activity as? SettingsActivity)?.setSectionTitle(getString(R.string.extension_management))
		(activity as? SettingsActivity)?.setSectionToolbarActions(null)
	}

	override fun onPause() {
		(activity as? SettingsActivity)?.setSectionToolbarActions(null)
		super.onPause()
	}

	private fun openRepositoryFilePicker(kind: UnifiedSourceKind) {
		pendingFileImportKind = kind
		openRepositoryFile.launch(
			arrayOf(
				"application/json",
				"text/plain",
				"application/javascript",
				"text/javascript",
				"*/*",
			),
		)
	}

	private fun openLocalJarPicker() {
		openLocalJar.launch(
			arrayOf(
				"application/java-archive",
				"application/zip",
				"*/*",
			),
		)
	}

	private fun persistReadPermission(uri: Uri) {
		runCatching {
			requireContext().contentResolver.takePersistableUriPermission(
				uri,
				Intent.FLAG_GRANT_READ_URI_PERMISSION,
			)
		}
	}
}

internal fun UnifiedSourceKind.supportsJsonImport(): Boolean {
	return when (this) {
		UnifiedSourceKind.LEGADO,
		UnifiedSourceKind.TVBOX,
		UnifiedSourceKind.JS,
		UnifiedSourceKind.LNREADER -> true
		else -> false
	}
}
