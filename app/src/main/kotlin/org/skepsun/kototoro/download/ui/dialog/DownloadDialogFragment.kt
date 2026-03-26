package org.skepsun.kototoro.download.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.Spinner
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentResultListener
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.getContentType
import org.skepsun.kototoro.core.model.getSaveTitleResId
import org.skepsun.kototoro.core.model.getWholeWorkOptionResId
import org.skepsun.kototoro.core.model.unwrap
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.prefs.DownloadFormat
import org.skepsun.kototoro.core.ui.AlertDialogFragment
import org.skepsun.kototoro.core.ui.widgets.TwoLinesItemView
import org.skepsun.kototoro.core.util.ext.findActivity
import org.skepsun.kototoro.core.util.ext.getDisplayMessage
import org.skepsun.kototoro.core.util.ext.getQuantityStringSafe
import org.skepsun.kototoro.core.util.ext.joinToStringWithLimit
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.core.util.ext.parentView
import org.skepsun.kototoro.core.util.ext.showOrHide
import org.skepsun.kototoro.databinding.DialogDownloadBinding
import org.skepsun.kototoro.main.ui.owners.BottomNavOwner
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.parsers.util.format
import org.skepsun.kototoro.settings.storage.DirectoryModel

@AndroidEntryPoint
class DownloadDialogFragment : AlertDialogFragment<DialogDownloadBinding>(), View.OnClickListener {

	private val viewModel by viewModels<DownloadDialogViewModel>()
	private var optionViews: Array<out TwoLinesItemView>? = null

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
		DialogDownloadBinding.inflate(inflater, container, false)

	override fun onBuildDialog(builder: MaterialAlertDialogBuilder): MaterialAlertDialogBuilder {
		val contentType = viewModel.manga.firstOrNull()?.source?.getContentType() ?: ContentType.MANGA
		return super.onBuildDialog(builder)
			.setTitle(contentType.getSaveTitleResId())
			.setCancelable(true)
	}

	override fun onViewBindingCreated(binding: DialogDownloadBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		val contentType = viewModel.manga.firstOrNull()?.source?.getContentType() ?: ContentType.MANGA
		binding.optionWholeManga.title = getString(contentType.getWholeWorkOptionResId())

		optionViews = arrayOf(
			binding.optionWholeManga,
			binding.optionWholeBranch,
			binding.optionFirstChapters,
			binding.optionUnreadChapters,
		).onEach {
			it.setOnClickListener(this)
			it.setOnButtonClickListener(this)
		}
		binding.buttonCancel.setOnClickListener(this)
		binding.buttonConfirm.setOnClickListener(this)
		binding.textViewMore.setOnClickListener(this)

		binding.textViewTip.isVisible = viewModel.manga.size == 1
		binding.textViewSummary.text = viewModel.manga.joinToStringWithLimit(binding.root.context, 120) { it.title }

		viewModel.isLoading.observe(viewLifecycleOwner, this::onLoadingStateChanged)
		viewModel.onScheduled.observeEvent(viewLifecycleOwner, this::onDownloadScheduled)
		viewModel.onError.observeEvent(viewLifecycleOwner, this::onError)
		viewModel.defaultFormat.observe(viewLifecycleOwner, this::onDefaultFormatChanged)
		viewModel.availableDestinations.observe(viewLifecycleOwner, this::onDestinationsChanged)
		viewModel.chaptersSelectOptions.observe(viewLifecycleOwner, this::onChapterSelectOptionsChanged)
		viewModel.isOptionsLoading.observe(viewLifecycleOwner, binding.progressBar::showOrHide)

		binding.switchAlignReader.isChecked = viewModel.isDownloadAlignedWithReader()
		updateAlignmentUi(binding, binding.switchAlignReader.isChecked)
		binding.switchAlignReader.setOnCheckedChangeListener { _, isChecked ->
			viewModel.setDownloadAlignedWithReader(isChecked)
			updateAlignmentUi(binding, isChecked)
		}
		binding.switchAutoRetry.isChecked = viewModel.isDownloadAutoRetryEnabled()
		binding.switchAutoRetry.setOnCheckedChangeListener { _, isChecked ->
			viewModel.setDownloadAutoRetryEnabled(isChecked)
		}

		// Setup delay slider
		binding.sliderDelay.value = viewModel.getChapterDownloadDelay().toFloat()
		updateDelayValueText(binding, viewModel.getChapterDownloadDelay())
		binding.sliderDelay.addOnChangeListener { _, value, fromUser ->
			if (fromUser) {
				val seconds = value.toInt()
				viewModel.setChapterDownloadDelay(seconds)
				updateDelayValueText(binding, seconds)
			}
		}

		// Setup download debug sliders
		binding.sliderThreads.value = viewModel.getDownloadThreads().toFloat()
		updateThreadsValueText(binding, viewModel.getDownloadThreads())
		binding.sliderThreads.addOnChangeListener { _, value, fromUser ->
			if (fromUser) {
				val count = value.toInt()
				viewModel.setDownloadThreads(count)
				updateThreadsValueText(binding, count)
			}
		}

		binding.sliderRequestDelay.value = viewModel.getDownloadRequestDelayMs().toFloat()
		updateRequestDelayValueText(binding, viewModel.getDownloadRequestDelayMs())
		binding.sliderRequestDelay.addOnChangeListener { _, value, fromUser ->
			if (fromUser) {
				val delayMs = value.toInt()
				viewModel.setDownloadRequestDelayMs(delayMs)
				updateRequestDelayValueText(binding, delayMs)
			}
		}

		binding.sliderRetryCount.value = viewModel.getDownloadRetryCount().toFloat()
		updateRetryCountValueText(binding, viewModel.getDownloadRetryCount())
		binding.sliderRetryCount.addOnChangeListener { _, value, fromUser ->
			if (fromUser) {
				val count = value.toInt()
				viewModel.setDownloadRetryCount(count)
				updateRetryCountValueText(binding, count)
			}
		}

		binding.sliderRetryDelay.value = viewModel.getDownloadRetryDelayMs().toFloat()
		updateRetryDelayValueText(binding, viewModel.getDownloadRetryDelayMs())
		binding.sliderRetryDelay.addOnChangeListener { _, value, fromUser ->
			if (fromUser) {
				val delayMs = value.toInt()
				viewModel.setDownloadRetryDelayMs(delayMs)
				updateRetryDelayValueText(binding, delayMs)
			}
		}
	}

	private fun updateDelayValueText(binding: DialogDownloadBinding, seconds: Int) {
		binding.textViewDelayValue.text = "${seconds}s"
	}

	private fun updateThreadsValueText(binding: DialogDownloadBinding, count: Int) {
		binding.textViewThreadsValue.text = count.toString()
	}

	private fun updateRequestDelayValueText(binding: DialogDownloadBinding, delayMs: Int) {
		binding.textViewRequestDelayValue.text = "${delayMs}ms"
	}

	private fun updateRetryCountValueText(binding: DialogDownloadBinding, count: Int) {
		binding.textViewRetryCountValue.text = count.toString()
	}

	private fun updateRetryDelayValueText(binding: DialogDownloadBinding, delayMs: Int) {
		binding.textViewRetryDelayValue.text = "${delayMs}ms"
	}

	private fun updateAlignmentUi(binding: DialogDownloadBinding, isAligned: Boolean) {
		binding.sliderThreads.isEnabled = !isAligned
		binding.textViewThreadsValue.isEnabled = !isAligned
		binding.sliderRequestDelay.isEnabled = !isAligned
		binding.textViewRequestDelayValue.isEnabled = !isAligned
	}

	override fun onViewStateRestored(savedInstanceState: Bundle?) {
		super.onViewStateRestored(savedInstanceState)
		showMoreOptions(requireViewBinding().textViewMore.isChecked)
		setCheckedOption(
			savedInstanceState?.getInt(KEY_CHECKED_OPTION, R.id.option_whole_manga) ?: R.id.option_whole_manga,
		)
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		optionViews?.find { it.isChecked }?.let {
			outState.putInt(KEY_CHECKED_OPTION, it.id)
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		optionViews = null
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.button_cancel -> dialog?.cancel()
			R.id.button_confirm -> router.askForDownloadOverMeteredNetwork(::schedule)

			R.id.textView_more -> {
				val binding = viewBinding ?: return
				binding.textViewMore.toggle()
				showMoreOptions(binding.textViewMore.isChecked)
			}

			R.id.button -> when (v.parentView?.id ?: return) {
				R.id.option_whole_branch -> showBranchSelection(v)
				R.id.option_first_chapters -> showFirstChaptersCountSelection(v)
				R.id.option_unread_chapters -> showUnreadChaptersCountSelection(v)
			}

			else -> if (v is TwoLinesItemView) {
				setCheckedOption(v.id)
			}
		}
	}

	private fun schedule(allowMeteredNetwork: Boolean) {
		viewBinding?.run {
			val options = viewModel.chaptersSelectOptions.value
			viewModel.confirm(
				startNow = switchStart.isChecked,
				chaptersMacro = when {
					optionWholeManga.isChecked -> options.wholeContent
					optionWholeBranch.isChecked -> options.wholeBranch ?: return@run
					optionFirstChapters.isChecked -> options.firstChapters ?: return@run
					optionUnreadChapters.isChecked -> options.unreadChapters ?: return@run
					else -> return@run
				},
				format = DownloadFormat.entries.getOrNull(spinnerFormat.selectedItemPosition),
				destination = viewModel.availableDestinations.value.getOrNull(spinnerDestination.selectedItemPosition),
				allowMetered = allowMeteredNetwork,
			)
		}
	}

	private fun onError(e: Throwable) {
		MaterialAlertDialogBuilder(context ?: return)
			.setNegativeButton(R.string.close, null)
			.setTitle(R.string.error)
			.setMessage(e.getDisplayMessage(resources))
			.show()
		dismiss()
	}

	private fun onLoadingStateChanged(value: Boolean) {
		with(requireViewBinding()) {
			buttonConfirm.isEnabled = !value
		}
	}

	private fun onDefaultFormatChanged(format: DownloadFormat?) {
		val spinner = viewBinding?.spinnerFormat ?: return
		spinner.setSelection(format?.ordinal ?: Spinner.INVALID_POSITION)
	}

	private fun onDestinationsChanged(directories: List<DirectoryModel>) {
		viewBinding?.spinnerDestination?.run {
			adapter = DestinationsAdapter(context, directories)
			setSelection(directories.indexOfFirst { it.isChecked })
		}
	}

	private fun onChapterSelectOptionsChanged(options: ChapterSelectOptions) {
		with(viewBinding ?: return) {
			// Whole manga
			optionWholeManga.subtitle = if (options.wholeContent.chaptersCount > 0) {
				resources.getQuantityStringSafe(
					R.plurals.chapters,
					options.wholeContent.chaptersCount,
					options.wholeContent.chaptersCount,
				)
			} else {
				null
			}
			// All chapters for branch
			optionWholeBranch.isVisible = options.wholeBranch != null
			options.wholeBranch?.let {
				optionWholeBranch.title = resources.getString(
					R.string.download_option_all_chapters,
					it.selectedBranch,
				)
				optionWholeBranch.subtitle = if (it.chaptersCount > 0) {
					resources.getQuantityStringSafe(
						R.plurals.chapters,
						it.chaptersCount,
						it.chaptersCount,
					)
				} else {
					null
				}
			}
			// First N chapters
			optionFirstChapters.isVisible = options.firstChapters != null
			options.firstChapters?.let {
				optionFirstChapters.title = resources.getString(
					R.string.download_option_first_n_chapters,
					resources.getQuantityStringSafe(
						R.plurals.chapters,
						it.chaptersCount,
						it.chaptersCount,
					),
				)
				optionFirstChapters.subtitle = it.branch
			}
			// Next N unread chapters
			optionUnreadChapters.isVisible = options.unreadChapters != null
			options.unreadChapters?.let {
				optionUnreadChapters.title = if (it.chaptersCount == Int.MAX_VALUE) {
					resources.getString(R.string.download_option_all_unread)
				} else {
					resources.getString(
						R.string.download_option_next_unread_n_chapters,
						resources.getQuantityStringSafe(
							R.plurals.chapters,
							it.chaptersCount,
							it.chaptersCount,
						),
					)
				}
			}
		}
	}

	private fun onDownloadScheduled(isStarted: Boolean) {
		val bundle = Bundle(1)
		bundle.putBoolean(ARG_STARTED, isStarted)
		setFragmentResult(RESULT_KEY, bundle)
		dismiss()
	}

	private fun showMoreOptions(isVisible: Boolean) = viewBinding?.apply {
		cardFormat.isVisible = isVisible
		textViewFormat.isVisible = isVisible
		cardDestination.isVisible = isVisible
		textViewDestination.isVisible = isVisible
		textViewDelay.isVisible = isVisible
		layoutDelay.isVisible = isVisible
		textViewAlignReader.isVisible = isVisible
		switchAlignReader.isVisible = isVisible
		switchAutoRetry.isVisible = isVisible
		textViewThreads.isVisible = isVisible
		layoutThreads.isVisible = isVisible
		textViewRequestDelay.isVisible = isVisible
		layoutRequestDelay.isVisible = isVisible
		textViewRetryCount.isVisible = isVisible
		layoutRetryCount.isVisible = isVisible
		textViewRetryDelay.isVisible = isVisible
		layoutRetryDelay.isVisible = isVisible
	}

	private fun setCheckedOption(id: Int) {
		for (optionView in optionViews ?: return) {
			optionView.isChecked = id == optionView.id
			optionView.isButtonEnabled = optionView.isChecked
		}
	}

	private fun showBranchSelection(v: View) {
		val option = viewModel.chaptersSelectOptions.value.wholeBranch ?: return
		val branches = option.branches.keys.toList()
		if (branches.size <= 1) {
			return
		}
		val menu = PopupMenu(v.context, v)
		for ((i, branch) in branches.withIndex()) {
			menu.menu.add(Menu.NONE, Menu.NONE, i, branch ?: getString(R.string.unknown))
		}
		menu.setOnMenuItemClickListener {
			viewModel.setSelectedBranch(branches.getOrNull(it.order))
			true
		}
		menu.show()
	}

	private fun showFirstChaptersCountSelection(v: View) {
		val option = viewModel.chaptersSelectOptions.value.firstChapters ?: return
		val menu = PopupMenu(v.context, v)
		chaptersCount(option.maxAvailableCount).forEach { i ->
			menu.menu.add(i.format())
		}
		menu.setOnMenuItemClickListener {
			viewModel.setFirstChaptersCount(
				it.title?.toString()?.toIntOrNull() ?: return@setOnMenuItemClickListener false,
			)
			true
		}
		menu.show()
	}

	private fun showUnreadChaptersCountSelection(v: View) {
		val option = viewModel.chaptersSelectOptions.value.unreadChapters ?: return
		val menu = PopupMenu(v.context, v)
		chaptersCount(option.maxAvailableCount).forEach { i ->
			menu.menu.add(i.format())
		}
		menu.menu.add(getString(R.string.chapters_all))
		menu.setOnMenuItemClickListener {
			viewModel.setUnreadChaptersCount(it.title?.toString()?.toIntOrNull() ?: Int.MAX_VALUE)
			true
		}
		menu.show()
	}

	private fun chaptersCount(max: Int) = sequence {
		yield(1)
		var seed = 5
		var step = 5
		while (seed + step <= max) {
			yield(seed)
			step = when {
				seed < 20 -> 5
				seed < 60 -> 10
				else -> 20
			}
			seed += step
		}
		if (seed < max) {
			yield(max)
		}
	}

	private class SnackbarResultListener(
		private val host: View,
	) : FragmentResultListener {

		override fun onFragmentResult(requestKey: String, result: Bundle) {
			val isStarted = result.getBoolean(ARG_STARTED, true)
			val snackbar = Snackbar.make(
				host,
				if (isStarted) R.string.download_started else R.string.download_added,
				Snackbar.LENGTH_LONG,
			)
			(host.context.findActivity() as? BottomNavOwner)?.let {
				snackbar.anchorView = it.bottomNav
			}
			val router = AppRouter.from(host)
			if (router != null) {
				snackbar.setAction(R.string.details) { router.openDownloads() }
			}
			snackbar.show()
		}
	}

	companion object {

		private const val RESULT_KEY = "DOWNLOAD_STARTED"
		private const val ARG_STARTED = "started"
		private const val KEY_CHECKED_OPTION = "checked_opt"

		fun registerCallback(
			fm: FragmentManager,
			lifecycleOwner: LifecycleOwner,
			snackbarHost: View
		) = fm.setFragmentResultListener(RESULT_KEY, lifecycleOwner, SnackbarResultListener(snackbarHost))

		fun unregisterCallback(fm: FragmentManager) = fm.clearFragmentResultListener(RESULT_KEY)
	}
}
