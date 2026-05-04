package org.skepsun.kototoro.filter.ui.sheet

import android.os.Bundle
import android.text.InputFilter
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.CheckedTextView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.R as appcompatR
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMarginsRelative
import androidx.core.view.updatePadding
import androidx.core.widget.TextViewCompat
import com.google.android.material.chip.Chip
import com.google.android.material.slider.RangeSlider
import com.google.android.material.slider.Slider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.titleResId
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.ui.dialog.buildAlertDialog
import org.skepsun.kototoro.core.ui.dialog.setEditText
import org.skepsun.kototoro.core.ui.model.titleRes
import org.skepsun.kototoro.core.ui.sheet.BaseAdaptiveSheet
import org.skepsun.kototoro.core.ui.widgets.ChipsView
import org.skepsun.kototoro.core.util.AlphanumComparator
import org.skepsun.kototoro.core.util.ext.consume
import org.skepsun.kototoro.core.util.ext.getDisplayMessage
import org.skepsun.kototoro.core.util.ext.getDisplayName
import org.skepsun.kototoro.core.util.ext.getThemeColorStateList
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.parentView
import org.skepsun.kototoro.core.util.ext.setValueRounded
import org.skepsun.kototoro.core.util.ext.setValuesRounded
import org.skepsun.kototoro.databinding.SheetFilterBinding
import org.skepsun.kototoro.filter.data.PersistableFilter
import org.skepsun.kototoro.filter.data.PersistableFilter.Companion.MAX_TITLE_LENGTH
import org.skepsun.kototoro.filter.ui.FilterCoordinator
import org.skepsun.kototoro.filter.ui.model.FilterProperty
import org.skepsun.kototoro.parsers.model.ContentRating
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.parsers.model.Demographic
import org.skepsun.kototoro.parsers.model.ContentState
import org.skepsun.kototoro.filter.ui.model.UiTagGroup
import org.skepsun.kototoro.parsers.model.SortOrder
import org.skepsun.kototoro.parsers.model.YEAR_UNKNOWN
import org.skepsun.kototoro.parsers.util.mapToSet
import org.skepsun.kototoro.parsers.util.toIntUp
import java.util.Locale
import java.util.TreeSet
import com.google.android.material.R as materialR

class FilterSheetFragment : BaseAdaptiveSheet<SheetFilterBinding>(),
    AdapterView.OnItemSelectedListener,
    View.OnClickListener,
    ChipsView.OnChipClickListener,
    ChipsView.OnChipLongClickListener,
    ChipsView.OnChipCloseClickListener {

    private var lastIncludeGroups: List<UiTagGroup> = emptyList()
    private var lastIncludeSelected: Set<org.skepsun.kototoro.parsers.model.ContentTag> = emptySet()
    private var lastExcludeGroups: List<UiTagGroup> = emptyList()
    private var lastExcludeSelected: Set<org.skepsun.kototoro.parsers.model.ContentTag> = emptySet()
    private var sortExpanded = false

    override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): SheetFilterBinding {
        return SheetFilterBinding.inflate(inflater, container, false)
    }

    override fun onViewBindingCreated(binding: SheetFilterBinding, savedInstanceState: Bundle?) {
        super.onViewBindingCreated(binding, savedInstanceState)
        if (dialog == null) {
            binding.adjustForEmbeddedLayout()
        }
        val filter = FilterCoordinator.require(this)
        filter.sortOrder.observe(viewLifecycleOwner, this::onSortOrderChanged)
        filter.locale.observe(viewLifecycleOwner, this::onLocaleChanged)
        filter.originalLocale.observe(viewLifecycleOwner, this::onOriginalLocaleChanged)
        filter.tags.observe(viewLifecycleOwner, this::onTagsChanged)
        filter.tagsExcluded.observe(viewLifecycleOwner, this::onTagsExcludedChanged)
        filter.authors.observe(viewLifecycleOwner, this::onAuthorsChanged)
        filter.states.observe(viewLifecycleOwner, this::onStateChanged)
        filter.contentTypes.observe(viewLifecycleOwner, this::onContentTypesChanged)
        filter.contentRating.observe(viewLifecycleOwner, this::onContentRatingChanged)
        filter.demographics.observe(viewLifecycleOwner, this::onDemographicsChanged)
        filter.year.observe(viewLifecycleOwner, this::onYearChanged)
        filter.yearRange.observe(viewLifecycleOwner, this::onYearRangeChanged)
        filter.savedFilters.observe(viewLifecycleOwner, ::onSavedPresetsChanged)

        binding.layoutGenres.setTitle(
            if (filter.capabilities.isMultipleTagsSupported) {
                R.string.genres
            } else {
                R.string.genre
            },
        )
        binding.spinnerLocale.onItemSelectedListener = this
        binding.spinnerOriginalLocale.onItemSelectedListener = this
        binding.chipsSavedFilters.onChipClickListener = this
        binding.chipsState.onChipClickListener = this
        binding.chipsTypes.onChipClickListener = this
        binding.chipsContentRating.onChipClickListener = this
        binding.chipsDemographics.onChipClickListener = this
        binding.chipsGenres.onChipClickListener = this
        binding.chipsGenresExclude.onChipClickListener = this
        binding.chipsAuthor.onChipClickListener = this
        binding.chipsSavedFilters.onChipLongClickListener = this
        binding.chipsSavedFilters.onChipCloseClickListener = this
        binding.sliderYear.addOnChangeListener(this::onSliderValueChange)
        binding.sliderYearsRange.addOnChangeListener(this::onRangeSliderValueChange)
        binding.layoutOrder.setOnMoreButtonClickListener {
            sortExpanded = !sortExpanded
            renderSortExpansion(binding)
        }
        combine(
            filter.observe().map { it.listFilter.isNotEmpty() }.distinctUntilChanged(),
            filter.savedFilters.map { it.selectedItems.isEmpty() }.distinctUntilChanged(),
            Boolean::and,
        ).flowOn(Dispatchers.Default)
            .observe(viewLifecycleOwner) {
                binding.buttonSave.isEnabled = it
            }
        binding.buttonSave.setOnClickListener(this)
        binding.buttonReset.setOnClickListener(this)
        binding.buttonDone.setOnClickListener(this)
        renderSortExpansion(binding)
    }

    private fun SheetFilterBinding.adjustForEmbeddedLayout() {
        layoutBody.updatePadding(top = layoutBody.paddingBottom)
        scrollView.scrollIndicators = 0
        buttonDone.isVisible = false
        this.root.layoutParams?.height = ViewGroup.LayoutParams.MATCH_PARENT
        buttonSave.updateLayoutParams<LinearLayout.LayoutParams> {
            weight = 0f
            width = LinearLayout.LayoutParams.WRAP_CONTENT
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
    }

    override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
        val typeMask = WindowInsetsCompat.Type.systemBars()
        viewBinding?.layoutBottom?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = insets.getInsets(typeMask).bottom
        }
        return insets.consume(v, typeMask, bottom = true)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.button_done -> dismiss()
            R.id.button_save -> onSaveFilterClick("")
            R.id.button_reset -> FilterCoordinator.require(this).reset()
        }
    }

    override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
        val filter = FilterCoordinator.require(this)
        when (parent.id) {
            R.id.spinner_locale -> filter.setLocale(filter.locale.value.availableItems[position])
            R.id.spinner_original_locale -> filter.setOriginalLocale(filter.originalLocale.value.availableItems[position])
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>?) = Unit

    private fun onSliderValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        if (!fromUser) {
            return
        }
        val intValue = value.toInt()
        val filter = FilterCoordinator.require(this)
        when (slider.id) {
            R.id.slider_year -> filter.setYear(
                if (intValue <= slider.valueFrom.toIntUp()) {
                    YEAR_UNKNOWN
                } else {
                    intValue
                },
            )
        }
    }

    private fun onRangeSliderValueChange(slider: RangeSlider, value: Float, fromUser: Boolean) {
        if (!fromUser) {
            return
        }
        val filter = FilterCoordinator.require(this)
        when (slider.id) {
            R.id.slider_yearsRange -> filter.setYearRange(
                valueFrom = slider.values.firstOrNull()?.let {
                    if (it <= slider.valueFrom) YEAR_UNKNOWN else it.toInt()
                } ?: YEAR_UNKNOWN,
                valueTo = slider.values.lastOrNull()?.let {
                    if (it >= slider.valueTo) YEAR_UNKNOWN else it.toInt()
                } ?: YEAR_UNKNOWN,
            )
        }
    }

    override fun onChipClick(chip: Chip, data: Any?) {
        val filter = FilterCoordinator.require(this)
        when (data) {
            is ContentState -> filter.toggleState(data, !chip.isChecked)
            is UiTagGroup -> {} // groups are not directly toggled
            is org.skepsun.kototoro.parsers.model.ContentTag -> {
                // Check if this is a text input tag (Mihon Filter.Text)
                if (filter.isTextInputTag(data)) {
                    showTextInputDialog(filter, data)
                } else {
                    val parentTag = (chip.parentView?.parent as? View)?.tag
                    val isExclude = parentTag == GROUP_TAG_VIEW_EXCLUDE || chip.parentView?.id == R.id.chips_genresExclude
                    if (isExclude) filter.toggleTagExclude(data, !chip.isChecked) else filter.toggleTag(data, !chip.isChecked)
                }
            }

            is ContentType -> filter.toggleContentType(data, !chip.isChecked)
            is ContentRating -> filter.toggleContentRating(data, !chip.isChecked)
            is Demographic -> filter.toggleDemographic(data, !chip.isChecked)
            is PersistableFilter -> filter.toggleSavedFilter(data)
            is String -> if (chip.isChecked) {
                filter.setAuthor(null)
            } else {
                filter.setAuthor(data)
            }
            null -> router.showTagsCatalogSheet(
                excludeMode = chip.parentView?.id == R.id.chips_genresExclude,
                groupTitle = null,
            )
        }
    }
    
    private fun showTextInputDialog(filter: FilterCoordinator, tag: org.skepsun.kototoro.parsers.model.ContentTag) {
        val currentValue = filter.getTextInputValue(tag) ?: ""
        val label = filter.getTextInputLabel(tag)
        
        buildAlertDialog(context ?: return) {
            val input = setEditText(
                inputType = EditorInfo.TYPE_CLASS_TEXT,
                singleLine = true,
            )
            input.hint = label
            input.setText(currentValue)
            setTitle(label)
            setPositiveButton(android.R.string.ok) { _, _ ->
                val value = input.text?.toString()?.trim() ?: ""
                filter.setTextInputValue(tag, value)
            }
            setNegativeButton(android.R.string.cancel, null)
            setNeutralButton(R.string.clear) { _, _ ->
                filter.setTextInputValue(tag, "")
            }
        }.show()
    }

    override fun onChipLongClick(chip: Chip, data: Any?): Boolean {
        return when (data) {
            is PersistableFilter -> {
                showSavedFilterMenu(chip, data)
                true
            }

            else -> false
        }
    }

    override fun onChipCloseClick(chip: Chip, data: Any?) {
        when (data) {
            is PersistableFilter -> {
                showSavedFilterMenu(chip, data)
            }
        }
    }

    companion object {
        private const val GROUP_TAG_VIEW = "filter_tag_group"
        private const val GROUP_TAG_VIEW_INCLUDE = "filter_tag_group_include"
        private const val GROUP_TAG_VIEW_EXCLUDE = "filter_tag_group_exclude"
        private const val TAGS_PREVIEW_LIMIT = 10
    }

    private fun onSortOrderChanged(value: FilterProperty<SortOrder>) {
        val b = viewBinding ?: return
        b.layoutOrder.isGone = value.isEmpty()
        if (value.isEmpty()) {
            return
        }
        val filter = FilterCoordinator.require(this)
        val selected = value.selectedItems.single()
        b.layoutOrder.setValueText(resolveSortOrderLabel(filter.mangaSource.name, selected))
        renderSortOptions(
            container = b.layoutOrderOptions,
            sourceName = filter.mangaSource.name,
            items = value.availableItems,
            selected = selected,
        )
        renderSortExpansion(b)
    }

    private fun renderSortExpansion(binding: SheetFilterBinding) {
        binding.cardOrder.isVisible = sortExpanded && binding.layoutOrder.isVisible
    }

    private fun renderSortOptions(
        container: LinearLayout,
        sourceName: String,
        items: List<SortOrder>,
        selected: SortOrder,
    ) {
        container.removeAllViews()
        val filter = FilterCoordinator.require(this)
        items.forEach { order ->
            val optionView = layoutInflater.inflate(
                R.layout.item_category_checkable_single,
                container,
                false,
            ) as CheckedTextView
            optionView.text = resolveSortOrderLabel(sourceName, order)
            optionView.isChecked = order == selected
            optionView.setOnClickListener {
                filter.setSortOrder(order)
                sortExpanded = false
                renderSortExpansion(viewBinding ?: return@setOnClickListener)
            }
            container.addView(optionView)
        }
    }

    private fun resolveSortOrderLabel(sourceName: String, order: SortOrder): String {
        if (sourceName.startsWith("TRACKING_BANGUMI_")) {
            return when (order) {
                SortOrder.RATING -> getString(R.string.sort_by_ranking)
                SortOrder.POPULARITY -> getString(R.string.sort_by_popularity_label)
                SortOrder.ADDED -> getString(R.string.sort_by_collection)
                SortOrder.NEWEST -> getString(R.string.sort_by_date_label)
                SortOrder.ALPHABETICAL -> getString(R.string.sort_by_name_label)
                else -> getString(order.titleRes)
            }
        }
        return getString(order.titleRes)
    }

    private fun onLocaleChanged(value: FilterProperty<Locale?>) {
        val b = viewBinding ?: return
        b.layoutLocale.isGone = value.isEmpty()
        if (value.isEmpty()) {
            return
        }
        val selected = value.selectedItems.singleOrNull()
        b.spinnerLocale.adapter = ArrayAdapter(
            b.spinnerLocale.context,
            android.R.layout.simple_spinner_dropdown_item,
            android.R.id.text1,
            value.availableItems.map { it.getDisplayName(b.spinnerLocale.context) },
        )
        val selectedIndex = value.availableItems.indexOf(selected)
        if (selectedIndex >= 0) {
            b.spinnerLocale.setSelection(selectedIndex, false)
        }
    }

    private fun onOriginalLocaleChanged(value: FilterProperty<Locale?>) {
        val b = viewBinding ?: return
        b.layoutOriginalLocale.isGone = value.isEmpty()
        if (value.isEmpty()) {
            return
        }
        val selected = value.selectedItems.singleOrNull()
        b.spinnerOriginalLocale.adapter = ArrayAdapter(
            b.spinnerOriginalLocale.context,
            android.R.layout.simple_spinner_dropdown_item,
            android.R.id.text1,
            value.availableItems.map { it.getDisplayName(b.spinnerOriginalLocale.context) },
        )
        val selectedIndex = value.availableItems.indexOf(selected)
        if (selectedIndex >= 0) {
            b.spinnerOriginalLocale.setSelection(selectedIndex, false)
        }
    }

    private fun onTagsChanged(value: FilterProperty<UiTagGroup>) {
        val b = viewBinding ?: return
        b.layoutGenres.setError(value.error?.getDisplayMessage(resources))
        lastIncludeGroups = value.availableItems
        lastIncludeSelected = value.selectedItems.flatMap { it.selected }.toSet()
        renderGroupedTags(
            container = b.layoutGenres,
            placeholderChips = b.chipsGenres,
            groups = lastIncludeGroups,
            selected = lastIncludeSelected,
            excludeMode = false,
        )
    }

    private fun onTagsExcludedChanged(value: FilterProperty<UiTagGroup>) {
        val b = viewBinding ?: return
        b.layoutGenresExclude.setError(value.error?.getDisplayMessage(resources))
        lastExcludeGroups = value.availableItems
        lastExcludeSelected = value.selectedItems.flatMap { it.selected }.toSet()
        renderGroupedTags(
            container = b.layoutGenresExclude,
            placeholderChips = b.chipsGenresExclude,
            groups = lastExcludeGroups,
            selected = lastExcludeSelected,
            excludeMode = true,
        )
    }

    private fun renderGroupedTags(
        container: ViewGroup,
        placeholderChips: ChipsView,
        groups: List<UiTagGroup>,
        selected: Set<org.skepsun.kototoro.parsers.model.ContentTag>,
        excludeMode: Boolean,
    ) {
        val limit = TAGS_PREVIEW_LIMIT
        for (i in container.childCount - 1 downTo 0) {
            val child = container.getChildAt(i)
            val tag = child.tag
            if (tag == GROUP_TAG_VIEW || tag == GROUP_TAG_VIEW_INCLUDE || tag == GROUP_TAG_VIEW_EXCLUDE) {
                container.removeViewAt(i)
            }
        }
        if (groups.all { it.tags.isEmpty() }) {
            container.isGone = true
            return
        } else {
            container.isGone = false
        }
        val flatGroup = groups.singleOrNull()
        val useFlat = flatGroup != null && flatGroup.title.equals("Tags", ignoreCase = true)
        if (useFlat) {
            val shown = flatGroup.tags.take(limit)
            placeholderChips.isGone = shown.isEmpty()
            placeholderChips.setChips(
                shown.map { tag ->
                    ChipsView.ChipModel(
                        title = tag.title,
                        isChecked = tag in selected,
                        data = tag,
                    )
                },
            )
            placeholderChips.onChipClickListener = this
            placeholderChips.onChipLongClickListener = this
            placeholderChips.onChipCloseClickListener = this
            (container as? org.skepsun.kototoro.filter.ui.FilterFieldLayout)?.setOnMoreButtonClickListener {
                router.showTagsCatalogSheet(excludeMode = excludeMode, groupTitle = flatGroup.title)
            }
            return
        } else {
            placeholderChips.isGone = true
            (container as? org.skepsun.kototoro.filter.ui.FilterFieldLayout)?.setOnMoreButtonClickListener(null)
        }
        val marginH = resources.getDimensionPixelOffset(R.dimen.margin_small)
        val marginTop = resources.getDimensionPixelOffset(R.dimen.margin_small) / 2
        val titleColor = ContextCompat.getColor(container.context, android.R.color.secondary_text_dark)
        groups.forEach { group ->
            if (group.tags.isEmpty()) return@forEach
            val tags = group.tags.take(limit)
            if (tags.isEmpty()) return@forEach
            val titleRow = LinearLayout(container.context).apply {
                tag = GROUP_TAG_VIEW
                id = View.generateViewId()
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(marginH, marginTop, marginH, 0)
            }
            val title = TextView(container.context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                text = group.title
                TextViewCompat.setTextAppearance(this, materialR.style.TextAppearance_Material3_LabelLarge)
                setTextColor(titleColor)
            }
            val moreIcon = ImageView(container.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                setImageResource(R.drawable.ic_expand_more)
                imageTintList = context.getThemeColorStateList(appcompatR.attr.colorControlNormal)
                contentDescription = context.getString(R.string.more)
                isVisible = group.tags.size > limit
                setOnClickListener {
                    router.showTagsCatalogSheet(excludeMode = excludeMode, groupTitle = group.title)
                }
            }
            titleRow.addView(title)
            titleRow.addView(moreIcon)
            val chipsView = ChipsView(container.context).apply {
                tag = if (excludeMode) GROUP_TAG_VIEW_EXCLUDE else GROUP_TAG_VIEW_INCLUDE
                id = View.generateViewId()
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    updateMarginsRelative(marginH, marginTop / 2, marginH, 0)
                }
                setChips(
                    tags.map { tag ->
                        ChipsView.ChipModel(
                            title = tag.title,
                            isChecked = tag in selected,
                            data = tag,
                        )
                    },
                )
                onChipClickListener = this@FilterSheetFragment
            }
            container.addView(titleRow)
            container.addView(chipsView)
        }
    }

    private fun onAuthorsChanged(value: FilterProperty<String>) {
        val b = viewBinding ?: return
        b.layoutAuthor.isGone = value.isEmpty()
        if (value.isEmpty()) {
            return
        }
        val chips = value.availableItems.map { author ->
            ChipsView.ChipModel(
                title = author,
                isChecked = author in value.selectedItems,
                data = author,
            )
        }
        b.chipsAuthor.setChips(chips)
    }

    private fun onStateChanged(value: FilterProperty<ContentState>) {
        val b = viewBinding ?: return
        b.layoutState.isGone = value.isEmpty()
        if (value.isEmpty()) {
            return
        }
        val chips = value.availableItems.map { state ->
            ChipsView.ChipModel(
                title = getString(state.titleResId),
                isChecked = state in value.selectedItems,
                data = state,
            )
        }
        b.chipsState.setChips(chips)
    }

    private fun onContentTypesChanged(value: FilterProperty<ContentType>) {
        val b = viewBinding ?: return
        b.layoutTypes.isGone = value.isEmpty()
        if (value.isEmpty()) {
            return
        }
        val chips = value.availableItems.map { type ->
            ChipsView.ChipModel(
                title = getString(type.titleResId),
                isChecked = type in value.selectedItems,
                data = type,
            )
        }
        b.chipsTypes.setChips(chips)
    }

    private fun onContentRatingChanged(value: FilterProperty<ContentRating>) {
        val b = viewBinding ?: return
        b.layoutContentRating.isGone = value.isEmpty()
        if (value.isEmpty()) {
            return
        }
        val chips = value.availableItems.map { contentRating ->
            ChipsView.ChipModel(
                title = getString(contentRating.titleResId),
                isChecked = contentRating in value.selectedItems,
                data = contentRating,
            )
        }
        b.chipsContentRating.setChips(chips)
    }

    private fun onDemographicsChanged(value: FilterProperty<Demographic>) {
        val b = viewBinding ?: return
        b.layoutDemographics.isGone = value.isEmpty()
        if (value.isEmpty()) {
            return
        }
        val chips = value.availableItems.map { demographic ->
            ChipsView.ChipModel(
                title = getString(demographic.titleResId),
                isChecked = demographic in value.selectedItems,
                data = demographic,
            )
        }
        b.chipsDemographics.setChips(chips)
    }

    private fun onYearChanged(value: FilterProperty<Int>) {
        val b = viewBinding ?: return
        b.layoutYear.isGone = value.isEmpty()
        if (value.isEmpty()) {
            return
        }
        val currentValue = value.selectedItems.singleOrNull() ?: YEAR_UNKNOWN
        b.layoutYear.setValueText(
            if (currentValue == YEAR_UNKNOWN) {
                getString(R.string.any)
            } else {
                currentValue.toString()
            },
        )
        b.sliderYear.valueFrom = value.availableItems.first().toFloat()
        b.sliderYear.valueTo = value.availableItems.last().toFloat()
        b.sliderYear.setValueRounded(currentValue.toFloat())
    }

    private fun onYearRangeChanged(value: FilterProperty<Int>) {
        val b = viewBinding ?: return
        b.layoutYearsRange.isGone = value.isEmpty()
        if (value.isEmpty()) {
            return
        }
        b.sliderYearsRange.valueFrom = value.availableItems.first().toFloat()
        b.sliderYearsRange.valueTo = value.availableItems.last().toFloat()
        val currentValueFrom = value.selectedItems.firstOrNull()?.toFloat() ?: b.sliderYearsRange.valueFrom
        val currentValueTo = value.selectedItems.lastOrNull()?.toFloat() ?: b.sliderYearsRange.valueTo
        b.layoutYearsRange.setValueText(
            getString(
                R.string.memory_usage_pattern,
                currentValueFrom.toInt().toString(),
                currentValueTo.toInt().toString(),
            ),
        )
        b.sliderYearsRange.setValuesRounded(currentValueFrom, currentValueTo)
    }

    private fun onSavedPresetsChanged(value: FilterProperty<PersistableFilter>) {
        val b = viewBinding ?: return
        b.layoutSavedFilters.isGone = value.isEmpty()
        if (value.isEmpty()) {
            return
        }
        val chips = value.availableItems.map { f ->
            ChipsView.ChipModel(
                title = f.name,
                isChecked = f in value.selectedItems,
                data = f,
                isDropdown = true,
            )
        }
        b.chipsSavedFilters.setChips(chips)
    }

    private fun showSavedFilterMenu(anchor: View, preset: PersistableFilter) {
        val menu = PopupMenu(context ?: return, anchor)
        val filter = FilterCoordinator.require(this)
        menu.inflate(R.menu.popup_saved_filter)
        menu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_delete -> filter.deleteSavedFilter(preset.id)
                R.id.action_rename -> onRenameFilterClick(preset)
            }
            true
        }
        menu.show()
    }

    private fun onSaveFilterClick(name: String) {
        val filter = FilterCoordinator.require(this)
        val existingNames = filter.savedFilters.value.availableItems
            .mapTo(TreeSet(AlphanumComparator()), PersistableFilter::name)
        buildAlertDialog(context ?: return) {
            val input = setEditText(
                entries = existingNames.toList(),
                inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES,
                singleLine = true,
            )
            input.setHint(R.string.enter_name)
            input.setText(name)
            input.filters += InputFilter.LengthFilter(MAX_TITLE_LENGTH)
            setTitle(R.string.save_filter)
            setPositiveButton(R.string.save) { _, _ ->
                val text = input.text?.toString()?.trim()
                if (text.isNullOrEmpty()) {
                    Toast.makeText(context, R.string.invalid_value_message, Toast.LENGTH_SHORT).show()
                    onSaveFilterClick("")
                } else if (text in existingNames) {
                    askForFilterOverwrite(filter, text)
                } else {
                    filter.saveCurrentFilter(text)
                }
            }
            setNegativeButton(android.R.string.cancel, null)
        }.show()
    }

    private fun onRenameFilterClick(preset: PersistableFilter) {
        val filter = FilterCoordinator.require(this)
        val existingNames = filter.savedFilters.value.availableItems.mapToSet { it.name }
        buildAlertDialog(context ?: return) {
            val input = setEditText(
                inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES,
                singleLine = true,
            )
            input.filters += InputFilter.LengthFilter(MAX_TITLE_LENGTH)
            input.setHint(R.string.enter_name)
            input.setText(preset.name)
            setTitle(R.string.rename)
            setPositiveButton(R.string.save) { _, _ ->
                val text = input.text?.toString()?.trim()
                if (text.isNullOrEmpty() || text in existingNames) {
                    Toast.makeText(context, R.string.invalid_value_message, Toast.LENGTH_SHORT).show()
                } else {
                    filter.renameSavedFilter(preset.id, text)
                }
            }
            setNegativeButton(android.R.string.cancel, null)
        }.show()
    }

    private fun askForFilterOverwrite(filter: FilterCoordinator, name: String) {
        buildAlertDialog(context ?: return) {
            setTitle(R.string.save_filter)
            setMessage(getString(R.string.filter_overwrite_confirm, name))
            setPositiveButton(R.string.overwrite) { _, _ ->
                filter.saveCurrentFilter(name)
            }
            setNegativeButton(android.R.string.cancel) { _, _ ->
                onSaveFilterClick(name)
            }
        }.show()
    }
}
