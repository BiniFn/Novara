package org.skepsun.kototoro.main.ui.welcome

import android.accounts.AccountManager
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import org.skepsun.kototoro.core.prefs.AppSettings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.titleResId
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.ui.sheet.BaseAdaptiveSheet
import org.skepsun.kototoro.core.ui.widgets.ChipsView
import org.skepsun.kototoro.core.util.ext.consume
import org.skepsun.kototoro.core.util.ext.getDisplayName
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.tryLaunch
import org.skepsun.kototoro.databinding.SheetWelcomeBinding
import org.skepsun.kototoro.filter.ui.model.FilterProperty
import org.skepsun.kototoro.parsers.model.ContentType
import java.util.Locale

@AndroidEntryPoint
class WelcomeSheet : BaseAdaptiveSheet<SheetWelcomeBinding>(), ChipsView.OnChipClickListener, View.OnClickListener,
	ActivityResultCallback<Uri?> {

	private val viewModel by viewModels<WelcomeViewModel>()

	private val backupSelectCall = registerForActivityResult(
		ActivityResultContracts.OpenDocument(),
		this,
	)

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): SheetWelcomeBinding {
		return SheetWelcomeBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: SheetWelcomeBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		binding.textViewWelcomeTitle.isGone = resources.getBoolean(R.bool.is_tablet)
		binding.chipsLocales.onChipClickListener = this
		binding.chipsType.onChipClickListener = this
		binding.chipBackup.setOnClickListener(this)
		binding.chipSync.setOnClickListener(this)
		binding.chipDirectories.setOnClickListener(this)

		val mirrors = resources.getStringArray(R.array.pref_github_mirror_entries).toList()
		val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, mirrors)
		binding.autoCompleteMirror.setAdapter(adapter)
		// Default to NATIVE
		binding.autoCompleteMirror.setText(mirrors[0], false)
		
		binding.buttonPluginsInit.setOnClickListener {
			android.util.Log.d("KototoroInit", "Button clicked! Mirror selected: ${binding.autoCompleteMirror.text}")
			val selectedMirrorsPosition = mirrors.indexOf(binding.autoCompleteMirror.text.toString()).coerceAtLeast(0)
			val repoUrls = mutableListOf<String>()
			if (binding.chipRepoNovara.isChecked)
				repoUrls.add("https://raw.githubusercontent.com/skepsun/kototoro-parsers/repo/index.min.json")

			if (binding.chipRepoYakateam.isChecked)
				repoUrls.add("https://raw.githubusercontent.com/skepsun/k-parsers-y/repo/index.min.json")

			if (binding.chipRepoRedo.isChecked)
				repoUrls.add("https://raw.githubusercontent.com/skepsun/k-parsers-r/repo/index.min.json")
			android.util.Log.d("KototoroInit", "Dispatching initializePlugins with urls: $repoUrls")
			viewModel.initializePlugins(selectedMirrorsPosition, repoUrls)
		}

		binding.buttonPluginsInit.isEnabled = false
		binding.checkboxPluginsDisclaimer.setOnCheckedChangeListener { _, isChecked ->
			val isInitializing = viewModel.isInitializingPlugins.value == true
			binding.buttonPluginsInit.isEnabled = isChecked && !isInitializing
		}

		viewModel.isInitializingPlugins.observe(viewLifecycleOwner) { isInitializing ->
			binding.progressBarPluginsInit.isGone = !isInitializing
			binding.buttonPluginsInit.isEnabled = !isInitializing && binding.checkboxPluginsDisclaimer.isChecked
			binding.autoCompleteMirror.isEnabled = !isInitializing
			binding.chipGroupRepos.isEnabled = !isInitializing
			binding.checkboxPluginsDisclaimer.isEnabled = !isInitializing
		}

		viewModel.locales.observe(viewLifecycleOwner, ::onLocalesChanged)
		viewModel.types.observe(viewLifecycleOwner, ::onTypesChanged)
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val typeMask = WindowInsetsCompat.Type.systemBars()
		viewBinding?.scrollView?.updatePadding(
			bottom = insets.getInsets(typeMask).bottom,
		)
		return insets.consume(v, typeMask, bottom = true)
	}

	override fun onChipClick(chip: Chip, data: Any?) {
		when (data) {
			is ContentType -> viewModel.setTypeChecked(data, !chip.isChecked)
			is Locale -> viewModel.setLocaleChecked(data, !chip.isChecked)
		}
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.chip_backup -> {
				if (!backupSelectCall.tryLaunch(arrayOf("*/*"))) {
					Snackbar.make(
						v, R.string.operation_not_supported, Snackbar.LENGTH_SHORT,
					).show()
				}
			}

			R.id.chip_sync -> {
				val am = AccountManager.get(v.context)
				val accountType = getString(R.string.account_type_sync)
				am.addAccount(accountType, accountType, null, null, requireActivity(), null, null)
			}

			    R.id.chip_directories -> {
			        router.openDirectoriesSettings()
			    }
		}
	}

	override fun onActivityResult(result: Uri?) {
		if (result != null) {
			router.showBackupRestoreDialog(result)
		}
	}

	private fun onLocalesChanged(value: FilterProperty<Locale>) {
		val chips = viewBinding?.chipsLocales ?: return
		chips.setChips(
			value.availableItems.map {
				ChipsView.ChipModel(
					title = it.getDisplayName(chips.context),
					isChecked = it in value.selectedItems,
					data = it,
				)
			},
		)
	}

	private fun onTypesChanged(value: FilterProperty<ContentType>) {
		val chips = viewBinding?.chipsType ?: return
		chips.setChips(
			value.availableItems.map {
				ChipsView.ChipModel(
					title = getString(it.titleResId),
					isChecked = it in value.selectedItems,
					data = it,
				)
			},
		)
	}
}
