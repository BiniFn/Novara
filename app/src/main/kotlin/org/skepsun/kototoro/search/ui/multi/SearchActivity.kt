package org.skepsun.kototoro.search.ui.multi

import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.view.ActionMode
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.exceptions.resolve.SnackbarErrorObserver
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.prefs.AppSettings
import android.content.Intent
import org.skepsun.kototoro.core.model.parcelable.ParcelableContent
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.ui.BaseActivity
import org.skepsun.kototoro.core.ui.list.ListSelectionController
import org.skepsun.kototoro.core.ui.list.OnListItemClickListener
import org.skepsun.kototoro.core.ui.widgets.TipView
import org.skepsun.kototoro.core.util.ShareHelper
import org.skepsun.kototoro.core.util.ext.consumeAllSystemBarsInsets
import org.skepsun.kototoro.core.util.ext.invalidateNestedItemDecorations
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.observeEvent
import org.skepsun.kototoro.core.util.ext.systemBarsInsets
import org.skepsun.kototoro.databinding.ActivitySearchBinding
import org.skepsun.kototoro.list.domain.ListFilterOption
import org.skepsun.kototoro.list.ui.ContentSelectionDecoration
import org.skepsun.kototoro.list.ui.adapter.ContentListListener
import org.skepsun.kototoro.list.ui.adapter.TypedListSpacingDecoration
import org.skepsun.kototoro.list.ui.model.ListHeader
import org.skepsun.kototoro.list.ui.model.ContentListModel
import org.skepsun.kototoro.list.ui.size.DynamicItemSizeResolver
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.model.ContentTag
import org.skepsun.kototoro.search.domain.SearchKind
import org.skepsun.kototoro.search.ui.multi.adapter.SearchAdapter
import javax.inject.Inject

@AndroidEntryPoint
class SearchActivity :
	BaseActivity<ActivitySearchBinding>(),
	ContentListListener,
	ListSelectionController.Callback {

	@Inject
	lateinit var settings: AppSettings

	private val viewModel by viewModels<SearchViewModel>()
	private lateinit var selectionController: ListSelectionController
	private val isPickMode by lazy { intent.getBooleanExtra(AppRouter.KEY_PICK_MODE, false) }

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivitySearchBinding.inflate(layoutInflater))
		title = when (viewModel.kind) {
			SearchKind.SIMPLE,
			SearchKind.TITLE -> viewModel.query

			SearchKind.AUTHOR -> getString(
				R.string.inline_preference_pattern,
				getString(R.string.author),
				viewModel.query,
			)

			SearchKind.TAG -> getString(R.string.inline_preference_pattern, getString(R.string.genre), viewModel.query)
			SearchKind.ADVANCED -> getString(R.string.advanced_search)
		}

		val itemClickListener = OnListItemClickListener<SearchResultsListModel> { item, view ->
			if (item.listFilter == null) {
				router.openSearch(item.source, viewModel.query)
			} else {
				router.openList(item.source, item.listFilter, item.sortOrder)
			}
		}
		val sizeResolver = DynamicItemSizeResolver(resources, this, settings, adjustWidth = true)
		val selectionDecoration = ContentSelectionDecoration(this)
		selectionController = ListSelectionController(
			appCompatDelegate = delegate,
			decoration = selectionDecoration,
			registryOwner = this,
			callback = this,
		)
		val adapter = SearchAdapter(
			listener = this,
			itemClickListener = itemClickListener,
			sizeResolver = sizeResolver,
			selectionDecoration = selectionDecoration,
		)
		viewBinding.recyclerView.adapter = adapter
		viewBinding.recyclerView.setHasFixedSize(true)
		viewBinding.recyclerView.addItemDecoration(TypedListSpacingDecoration(this, true))

		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)
		supportActionBar?.setSubtitle(R.string.search_results)
		viewBinding.editQueryInput.setText(viewModel.query)
		viewBinding.editQueryInput.setOnEditorActionListener { _, actionId, event ->
			val isSubmit = actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
				actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
				(event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
			if (!isSubmit) return@setOnEditorActionListener false
			submitEditedQuery()
			true
		}
		viewBinding.editQueryInput.setOnKeyListener { _, keyCode, event ->
			if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
				submitEditedQuery()
				true
			} else {
				false
			}
		}

		setupAdvancedSearchPanel()

		addMenuProvider(SearchMenuProvider(this, viewModel))

		viewModel.list.observe(this, adapter)
		viewModel.onError.observeEvent(this, SnackbarErrorObserver(viewBinding.recyclerView, null))
		viewModel.activeTvBoxRepositoryTitle.observe(this) {
			updateTvBoxRepositoryLabel()
		}
		viewModel.isTvBoxSourceTypeActive.observe(this) {
			updateTvBoxRepositoryLabel()
		}
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val barsInsets = insets.systemBarsInsets
		viewBinding.toolbar.updatePadding(
			top = barsInsets.top,
			left = barsInsets.left,
			right = barsInsets.right,
		)
		viewBinding.recyclerView.setPadding(
			left = barsInsets.left,
			top = 0,
			right = barsInsets.right,
			bottom = barsInsets.bottom,
		)
		return insets.consumeAllSystemBarsInsets()
	}

	override fun onItemClick(item: ContentListModel, view: View) {
		if (!selectionController.onItemClick(item.id)) {
			val manga = item.toContentWithOverride()
			if (isPickMode) {
				setResult(RESULT_OK, Intent().putExtra(AppRouter.KEY_MANGA, ParcelableContent(manga)))
				finish()
			} else {
				val coverView = view.findViewById<View>(R.id.imageView_cover);
                                router.openDetails(manga, coverView)
			}
		}
	}

	override fun onItemLongClick(item: ContentListModel, view: View): Boolean {
		return selectionController.onItemLongClick(view, item.id)
	}

	override fun onItemContextClick(item: ContentListModel, view: View): Boolean {
		return selectionController.onItemContextClick(view, item.id)
	}

	override fun onReadClick(manga: Content, view: View) {
		if (!selectionController.onItemClick(manga.id)) {
			router.openReader(manga)
		}
	}

	override fun onTagClick(manga: Content, tag: ContentTag, view: View) {
		if (!selectionController.onItemClick(manga.id)) {
			router.openList(tag)
		}
	}

	override fun onRetryClick(error: Throwable) {
		viewModel.retry()
	}

	override fun onFilterOptionClick(option: ListFilterOption) = Unit

	override fun onFilterClick(view: View?) = Unit

	override fun onEmptyActionClick() = viewModel.continueSearch()

	override fun onListHeaderClick(item: ListHeader, view: View) = Unit

	override fun onFooterButtonClick() = viewModel.continueSearch()

	override fun onPrimaryButtonClick(tipView: TipView) = Unit

	override fun onSecondaryButtonClick(tipView: TipView) = Unit

	override fun onSelectionChanged(controller: ListSelectionController, count: Int) {
		viewBinding.recyclerView.invalidateNestedItemDecorations()
	}

	override fun onCreateActionMode(
		controller: ListSelectionController,
		menuInflater: MenuInflater,
		menu: Menu
	): Boolean {
		menuInflater.inflate(R.menu.mode_remote, menu)
		return true
	}

	override fun onActionItemClicked(controller: ListSelectionController, mode: ActionMode?, item: MenuItem): Boolean {
		return when (item.itemId) {
			R.id.action_share -> {
				ShareHelper(this).shareContentLinks(collectSelectedItems())
				mode?.finish()
				true
			}

			R.id.action_favourite -> {
				router.showFavoriteDialog(collectSelectedItems())
				mode?.finish()
				true
			}

			R.id.action_save -> {
				router.showDownloadDialog(collectSelectedItems(), viewBinding.recyclerView)
				mode?.finish()
				true
			}

			else -> false
		}
	}

	private fun collectSelectedItems(): Set<Content> {
		return viewModel.getItems(selectionController.peekCheckedIds())
	}

	private fun setupAdvancedSearchPanel() {
		val advancedQuery = viewModel.advancedQuery
		if (advancedQuery != null) {
			viewBinding.layoutAdvancedSearch.visibility = View.VISIBLE
			viewBinding.iconAdvancedToggle.rotation = 180f
			viewBinding.editAdvancedTitle.setText(advancedQuery.title)
			viewBinding.editAdvancedTags.setText(advancedQuery.tags)
			viewBinding.editAdvancedAuthor.setText(advancedQuery.author)
		}

		viewBinding.buttonAdvancedToggle.setOnClickListener {
			val isExpanded = viewBinding.layoutAdvancedSearch.visibility == View.VISIBLE
			if (isExpanded) {
				viewBinding.layoutAdvancedSearch.visibility = View.GONE
				viewBinding.iconAdvancedToggle.animate().rotation(0f).start()
			} else {
				viewBinding.layoutAdvancedSearch.visibility = View.VISIBLE
				viewBinding.iconAdvancedToggle.animate().rotation(180f).start()
			}
		}
	}

	private fun submitEditedQuery() {
		val newQuery = viewBinding.editQueryInput.text?.toString()?.trim().orEmpty()
		if (newQuery.isEmpty() || newQuery == viewModel.query) {
			return
		}
		router.openSearch(
			query = newQuery,
			kind = viewModel.kind,
			sourceTypes = viewModel.getSourceTypes(),
			contentKinds = viewModel.getContentKinds(),
		)
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
			overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out, 0)
		} else {
			overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
		}
		finishAfterTransition()
	}

	private fun updateTvBoxRepositoryLabel() {
		val title = viewModel.activeTvBoxRepositoryTitle.value
		val shouldShow = !title.isNullOrBlank() && viewModel.isTvBoxSourceTypeActive.value
		viewBinding.textViewTvboxRepository.visibility = if (shouldShow) View.VISIBLE else View.GONE
		if (shouldShow) {
			viewBinding.textViewTvboxRepository.text = getString(R.string.tvbox_repository_current_label, title)
		}
	}
}
