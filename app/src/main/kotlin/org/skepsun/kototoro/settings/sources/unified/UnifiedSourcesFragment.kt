package org.skepsun.kototoro.settings.sources.unified

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.extensions.repo.ExternalExtensionRepo
import org.skepsun.kototoro.settings.SettingsActivity
import org.skepsun.kototoro.settings.sources.extensions.formatExtensionFingerprint

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
						onAddRepository = ::openAddRepositoryDialog,
						onRefreshRepository = { item -> viewModel.refreshRepository(item.id) },
						onDeleteRepository = ::openDeleteRepositoryDialog,
						onImportLocalJar = ::openLocalJarPicker,
					)
				}
			}
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				viewModel.events.collect { event ->
					when (event) {
						is UnifiedSourcesEvent.Message -> {
							Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
						}
						is UnifiedSourcesEvent.TrustExternalRepository -> {
							openTrustRepositoryDialog(event.repo)
						}
						is UnifiedSourcesEvent.StartInstall -> {
							runCatching { installLauncher.launch(event.intent) }
								.onFailure { Toast.makeText(requireContext(), it.message, Toast.LENGTH_SHORT).show() }
						}
						is UnifiedSourcesEvent.StartUninstall -> {
							runCatching { startActivity(event.intent) }
								.onFailure { Toast.makeText(requireContext(), it.message, Toast.LENGTH_SHORT).show() }
						}
						is UnifiedSourcesEvent.PackageStateDetails -> {
							openPackageStateDetailsDialog(event.item)
						}
					}
				}
			}
		}
	}

	override fun onResume() {
		super.onResume()
		(activity as? SettingsActivity)?.let { settingsActivity ->
			settingsActivity.setSectionTitle(getString(R.string.extension_management))
			settingsActivity.setSectionToolbarActions(createToolbarActionsView())
		}
	}

	override fun onPause() {
		(activity as? SettingsActivity)?.setSectionToolbarActions(null)
		super.onPause()
	}

	private fun openAddRepositoryDialog(preset: UnifiedSourceRepositoryItem?) {
		if (preset != null) {
			openRepositoryModeDialog(
				kind = preset.kind,
				prefillUrl = preset.url,
				prefillTitle = preset.name,
			)
			return
		}
		val entryLabels = arrayOf("Recommended repositories", "Custom repository")
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(R.string.add_repository)
			.setItems(entryLabels) { _, which ->
				when (which) {
					0 -> openRecommendedRepositoryDialog()
					else -> openCustomRepositoryKindDialog()
				}
			}
			.show()
	}

	private fun createToolbarActionsView(): View {
		return ComposeView(requireContext()).apply {
			setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
			setContent {
				KototoroTheme {
					val state by viewModel.uiState.collectAsStateWithLifecycle()
					UnifiedSourcesToolbarControls(
						state = state,
						onSearchQueryChange = viewModel::setSearchQuery,
						onPrimaryContentTypeSelected = viewModel::setPrimaryContentTypeFilter,
						onKindClick = viewModel::toggleKind,
						onLanguageClick = viewModel::toggleLanguage,
						onEnabledFilterClick = viewModel::setEnabledFilter,
						onLocationTypeClick = viewModel::toggleLocationType,
						onClearFilters = viewModel::clearFilters,
					)
				}
			}
		}
	}

	private fun openRecommendedRepositoryDialog() {
		val repositories = UnifiedRecommendedRepositories.all
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(R.string.recommended_repositories)
			.setItems(repositories.map { "${it.kind.dialogLabel()} · ${it.name}" }.toTypedArray()) { _, which ->
				val repo = repositories[which]
				openRepositoryModeDialog(
					kind = repo.kind,
					prefillUrl = repo.url,
					prefillTitle = repo.name,
				)
			}
			.show()
	}

	private fun openCustomRepositoryKindDialog() {
		val kinds = listOf(
			UnifiedSourceKind.LEGADO,
			UnifiedSourceKind.TVBOX,
			UnifiedSourceKind.LNREADER,
			UnifiedSourceKind.JAR,
			UnifiedSourceKind.MIHON,
			UnifiedSourceKind.ANIYOMI,
			UnifiedSourceKind.IREADER,
			UnifiedSourceKind.JS,
		)
		MaterialAlertDialogBuilder(requireContext())
			.setTitle("Add repository")
			.setItems(kinds.map { it.dialogLabel() }.toTypedArray()) { _, which ->
				openRepositoryModeDialog(kinds[which])
			}
			.show()
	}

	private fun openRepositoryModeDialog(
		kind: UnifiedSourceKind,
		prefillUrl: String? = null,
		prefillTitle: String? = null,
	) {
		val supportsFile = kind.supportsJsonImport()
		if (!supportsFile) {
			openUrlDialog(kind, prefillUrl, prefillTitle)
			return
		}
		val modes = arrayOf("Remote URL", "Local file", "Paste content")
		MaterialAlertDialogBuilder(requireContext())
			.setTitle("Add ${kind.dialogLabel()}")
			.setItems(modes) { _, which ->
				when (which) {
					0 -> openUrlDialog(kind, prefillUrl, prefillTitle)
					1 -> openFilePicker(kind)
					2 -> openInlineDialog(kind, prefillTitle)
				}
			}
			.show()
	}

	private fun openUrlDialog(
		kind: UnifiedSourceKind,
		prefillUrl: String?,
		prefillTitle: String?,
	) {
		val input = TextInputEditText(requireContext()).apply {
			hint = when (kind) {
				UnifiedSourceKind.LEGADO -> "https://example.com/legado.json"
				UnifiedSourceKind.TVBOX -> "http://z.qiqiv.cn/123.txt"
				else -> "https://example.com/index.min.json"
			}
			inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
			setText(prefillUrl.orEmpty())
			setSelection(text?.length ?: 0)
		}
		MaterialAlertDialogBuilder(requireContext())
			.setTitle("Repository URL")
			.setView(input.wrapWithDialogMargins())
			.setPositiveButton(android.R.string.ok) { _, _ ->
				confirmThirdPartyRepositoryDisclaimer {
					viewModel.addRepositoryFromUrl(kind, input.text?.toString().orEmpty(), prefillTitle)
				}
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	private fun openInlineDialog(kind: UnifiedSourceKind, prefillTitle: String?) {
		val input = TextInputEditText(requireContext()).apply {
			hint = "Paste JSON or JS content"
			inputType = InputType.TYPE_CLASS_TEXT or
				InputType.TYPE_TEXT_FLAG_MULTI_LINE or
				InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
			minLines = 6
		}
		MaterialAlertDialogBuilder(requireContext())
			.setTitle("Paste ${kind.dialogLabel()}")
			.setView(input.wrapWithDialogMargins())
			.setPositiveButton(android.R.string.ok) { _, _ ->
				confirmThirdPartyRepositoryDisclaimer {
					viewModel.addRepositoryFromInline(kind, input.text?.toString().orEmpty(), prefillTitle)
				}
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	private fun openFilePicker(kind: UnifiedSourceKind) {
		confirmThirdPartyRepositoryDisclaimer {
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
	}

	private fun openLocalJarPicker() {
		confirmThirdPartyRepositoryDisclaimer {
			openLocalJar.launch(
				arrayOf(
					"application/java-archive",
					"application/zip",
					"*/*",
				),
			)
		}
	}

	private fun openTrustRepositoryDialog(repo: ExternalExtensionRepo) {
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(R.string.trust_extension_repository)
			.setMessage(
				getString(
					R.string.trust_extension_repository_message,
					repo.displayName,
					repo.website,
					repo.signingKeyFingerprint.formatExtensionFingerprint(),
				),
			)
			.setPositiveButton(R.string.trust_and_add) { _, _ ->
				confirmThirdPartyRepositoryDisclaimer {
					viewModel.confirmExternalRepository(repo)
				}
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	private fun confirmThirdPartyRepositoryDisclaimer(onAccepted: () -> Unit) {
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(R.string.add_repository)
			.setMessage(R.string.welcome_plugins_disclaimer)
			.setPositiveButton(android.R.string.ok) { _, _ -> onAccepted() }
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	private fun openDeleteRepositoryDialog(repository: UnifiedSourceRepositoryItem) {
		MaterialAlertDialogBuilder(requireContext())
			.setTitle("Delete repository")
			.setMessage("Delete ${repository.name} and its imported source records where applicable?")
			.setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteRepository(repository.id) }
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	private fun openPackageStateDetailsDialog(item: UnifiedSourcePackageItem) {
		val (title, message) = when (item.state) {
			UnifiedSourcePackageState.UNTRUSTED -> getString(R.string.untrusted_extension) to getString(
				R.string.untrusted_extension_message,
				item.name,
				item.packageName.orEmpty(),
				item.installPayload?.signatureHash.orEmpty().formatExtensionFingerprint(),
			)
			UnifiedSourcePackageState.INCOMPATIBLE -> getString(R.string.incompatible_extension) to getString(
				R.string.incompatible_extension_message,
				item.name,
				item.versionName.orEmpty(),
				item.installPayload?.libVersion?.toString().orEmpty(),
			)
			else -> return
		}
		val builder = MaterialAlertDialogBuilder(requireContext())
			.setTitle(title)
			.setMessage(message)
			.setNeutralButton(R.string.manage_extension_repositories) { _, _ ->
				openAddRepositoryDialog(null)
			}
		if (item.isInstalled) {
			builder
				.setPositiveButton(R.string.remove) { _, _ -> viewModel.uninstallPackage(item.id) }
				.setNegativeButton(android.R.string.cancel, null)
		} else {
			builder.setPositiveButton(android.R.string.ok, null)
		}
		builder.show()
	}

	private fun persistReadPermission(uri: Uri) {
		runCatching {
			requireContext().contentResolver.takePersistableUriPermission(
				uri,
				Intent.FLAG_GRANT_READ_URI_PERMISSION,
			)
		}
	}

	private fun View.wrapWithDialogMargins(): View {
		val margin = resources.getDimensionPixelSize(R.dimen.margin_normal)
		return FrameLayout(requireContext()).apply {
			addView(
				this@wrapWithDialogMargins,
				FrameLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.WRAP_CONTENT,
				).apply {
					setMargins(margin, margin / 2, margin, 0)
				},
			)
		}
	}
}

private fun UnifiedSourceKind.supportsJsonImport(): Boolean {
	return when (this) {
		UnifiedSourceKind.LEGADO,
		UnifiedSourceKind.TVBOX,
		UnifiedSourceKind.JS,
		UnifiedSourceKind.LNREADER -> true
		else -> false
	}
}

private fun UnifiedSourceKind.dialogLabel(): String {
	return when (this) {
		UnifiedSourceKind.NATIVE -> "Native"
		UnifiedSourceKind.JAR -> "JAR"
		UnifiedSourceKind.MIHON -> "Mihon APK"
		UnifiedSourceKind.ANIYOMI -> "Aniyomi APK"
		UnifiedSourceKind.IREADER -> "IReader APK"
		UnifiedSourceKind.LEGADO -> "Legado JSON"
		UnifiedSourceKind.TVBOX -> "TVBox JSON"
		UnifiedSourceKind.JS -> "JS source"
		UnifiedSourceKind.LNREADER -> "LNReader"
	}
}
