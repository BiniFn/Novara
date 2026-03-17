package org.skepsun.kototoro.filter.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.ui.BaseFragment
import org.skepsun.kototoro.core.ui.widgets.ChipsView
import org.skepsun.kototoro.core.util.ext.isAnimationsEnabled
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.databinding.FragmentFilterHeaderBinding
import org.skepsun.kototoro.filter.data.PersistableFilter
import org.skepsun.kototoro.filter.ui.model.FilterHeaderModel
import org.skepsun.kototoro.parsers.model.ContentRating
import org.skepsun.kototoro.parsers.model.ContentType
import org.skepsun.kototoro.parsers.model.Demographic
import org.skepsun.kototoro.parsers.model.ContentState
import org.skepsun.kototoro.parsers.model.ContentTag
import org.skepsun.kototoro.parsers.model.YEAR_UNKNOWN
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class FilterHeaderFragment : BaseFragment<FragmentFilterHeaderBinding>(), ChipsView.OnChipClickListener,
    ChipsView.OnChipCloseClickListener {

    @Inject
    lateinit var filterHeaderProducer: FilterHeaderProducer

    private val filter: FilterCoordinator
        get() = (requireActivity() as FilterCoordinator.Owner).filterCoordinator

    override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentFilterHeaderBinding {
        return FragmentFilterHeaderBinding.inflate(inflater, container, false)
    }

    override fun onViewBindingCreated(binding: FragmentFilterHeaderBinding, savedInstanceState: Bundle?) {
        super.onViewBindingCreated(binding, savedInstanceState)
        binding.chipsTags.onChipClickListener = this
        binding.chipsTags.onChipCloseClickListener = this
        filterHeaderProducer.observeHeader(filter)
            .flowOn(Dispatchers.Default)
            .observe(viewLifecycleOwner, ::onDataChanged)
    }

    override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat = insets

    override fun onChipClick(chip: Chip, data: Any?) {
        when (data) {
            is ContentTag -> filter.toggleTag(data, !chip.isChecked)
            is PersistableFilter -> if (chip.isChecked) {
                filter.reset()
            } else {
                filter.setAdjusted(data.filter)
            }

            is String -> Unit
            null -> router.showTagsCatalogSheet(excludeMode = false)
        }
    }

    override fun onChipCloseClick(chip: Chip, data: Any?) {
        when (data) {
            is String -> if (data == filter.snapshot().listFilter.author) {
                filter.setAuthor(null)
            } else {
                filter.setQuery(null)
            }

            is ContentRating -> filter.toggleContentRating(data, false)
            is Demographic -> filter.toggleDemographic(data, false)
            is ContentType -> filter.toggleContentType(data, false)
            is ContentState -> filter.toggleState(data, false)
            is Locale -> filter.setLocale(null)
            is Int -> filter.setYear(YEAR_UNKNOWN)
            is IntRange -> filter.setYearRange(YEAR_UNKNOWN, YEAR_UNKNOWN)
        }
    }

    private fun onDataChanged(header: FilterHeaderModel) {
        val binding = viewBinding ?: return
        val chips = header.chips
        if (chips.isEmpty()) {
            binding.chipsTags.setChips(emptyList())
            binding.root.isVisible = false
            return
        }
        binding.chipsTags.setChips(header.chips)
        binding.root.isVisible = true
        if (binding.root.context.isAnimationsEnabled) {
            binding.scrollView.smoothScrollTo(0, 0)
        } else {
            binding.scrollView.scrollTo(0, 0)
        }
    }
}
