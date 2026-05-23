package org.skepsun.kototoro.settings.sources.unified

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.view.WindowInsetsCompat
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.ui.BaseActivity
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.databinding.ActivityUnifiedSourcesBinding

@AndroidEntryPoint
class UnifiedSourcesActivity : BaseActivity<ActivityUnifiedSourcesBinding>() {

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

	private val uninstallLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
		viewModel.onUninstallActivityResult()
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityUnifiedSourcesBinding.inflate(layoutInflater))
		val initialKind = intent.resolveInitialRepositoryKind()
		val initialUrl = intent.resolveInitialRepositoryUrl()
		viewBinding.composeView.setContent {
			KototoroTheme {
				UnifiedSourcesContent(
					initialAddRepositoryKind = initialKind,
					initialAddRepositoryUrl = initialUrl,
					modifier = Modifier.fillMaxSize(),
				)
			}
		}
	}

	@Composable
	fun UnifiedSourcesContent(
		initialAddRepositoryKind: UnifiedSourceKind? = null,
		initialAddRepositoryUrl: String? = null,
		modifier: Modifier = Modifier,
	) {
		var searchActive by remember { mutableStateOf(false) }
		var activePanel by remember { mutableStateOf<UnifiedToolbarFilterPanel?>(null) }
		UnifiedSourcesRoute(
			searchActive = searchActive,
			onSearchActiveChange = { searchActive = it },
			activePanel = activePanel,
			onActivePanelChange = { activePanel = it },
			initialAddRepositoryKind = initialAddRepositoryKind,
			initialAddRepositoryUrl = initialAddRepositoryUrl,
			viewModel = viewModel,
			onBrowseSource = { item -> router.openList(item.source, null, null) },
			onOpenSourceSettings = { item -> router.openSourceSettings(item.source) },
			onOpenRepositoryFile = ::openRepositoryFilePicker,
			onOpenLocalJarPicker = ::openLocalJarPicker,
			onStartInstall = { intent ->
				runCatching { installLauncher.launch(intent) }
					.onFailure { Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show() }
			},
			onStartUninstall = { intent ->
				runCatching { uninstallLauncher.launch(intent) }
					.onFailure { Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show() }
			},
			modifier = modifier,
		)
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		return insets
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
			contentResolver.takePersistableUriPermission(
				uri,
				Intent.FLAG_GRANT_READ_URI_PERMISSION,
			)
		}
	}

	companion object {

		private const val EXTRA_INITIAL_REPOSITORY_KIND = "initial_repository_kind"
		private const val EXTRA_INITIAL_REPOSITORY_URL = "initial_repository_url"
		private const val HOST_ADD_REPO = "add-repo"

		fun newIntent(
			context: Context,
			initialRepositoryKind: UnifiedSourceKind? = null,
			initialRepositoryUrl: String? = null,
		): Intent {
			return Intent(context, UnifiedSourcesActivity::class.java).apply {
				if (initialRepositoryKind != null) {
					putExtra(EXTRA_INITIAL_REPOSITORY_KIND, initialRepositoryKind.name)
				}
				if (initialRepositoryUrl != null) {
					putExtra(EXTRA_INITIAL_REPOSITORY_URL, initialRepositoryUrl)
				}
			}
		}

		private fun Intent.resolveInitialRepositoryKind(): UnifiedSourceKind? {
			val extraKind = getStringExtra(EXTRA_INITIAL_REPOSITORY_KIND)
				?.let { runCatching { enumValueOf<UnifiedSourceKind>(it) }.getOrNull() }
			if (extraKind != null) {
				return extraKind
			}
			if (action != Intent.ACTION_VIEW || data?.host != HOST_ADD_REPO) {
				return null
			}
			return when (data?.scheme) {
				"aniyomi", "anikku" -> UnifiedSourceKind.ANIYOMI
				else -> UnifiedSourceKind.MIHON
			}
		}

		private fun Intent.resolveInitialRepositoryUrl(): String? {
			return getStringExtra(EXTRA_INITIAL_REPOSITORY_URL)
				?: data
					?.takeIf { action == Intent.ACTION_VIEW && it.host == HOST_ADD_REPO }
					?.getQueryParameter("url")
		}
	}
}
