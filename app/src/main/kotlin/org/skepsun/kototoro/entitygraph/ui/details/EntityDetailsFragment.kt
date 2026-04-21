package org.skepsun.kototoro.entitygraph.ui.details

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.getTitle
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.ui.BaseFragment
import org.skepsun.kototoro.core.util.ext.consume
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.setSupportTitle
import org.skepsun.kototoro.databinding.FragmentEntityDetailsBinding
import org.skepsun.kototoro.entitygraph.domain.Entity
import org.skepsun.kototoro.entitygraph.domain.EntityBinding
import org.skepsun.kototoro.entitygraph.domain.EntityType
import org.skepsun.kototoro.entitygraph.domain.SourceResult
import java.util.Locale

@AndroidEntryPoint
class EntityDetailsFragment : BaseFragment<FragmentEntityDetailsBinding>() {

    private val viewModel by viewModels<EntityDetailsViewModel>()

    override fun onCreateViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentEntityDetailsBinding {
        return FragmentEntityDetailsBinding.inflate(inflater, container, false)
    }

    override fun onViewBindingCreated(binding: FragmentEntityDetailsBinding, savedInstanceState: Bundle?) {
        super.onViewBindingCreated(binding, savedInstanceState)
        setSupportTitle(getString(R.string.entity_graph_detail_title))

        binding.swipeRefreshLayout.setOnRefreshListener(viewModel::refresh)
        binding.buttonRetry.setOnClickListener { viewModel.refresh() }
        binding.buttonOpenTrackingDetails.setOnClickListener {
            viewModel.screenState.value.trackingReference?.let { reference ->
                router.openTrackingSiteRawDetails(reference.service, reference.remoteId, reference.url)
            }
        }
        binding.buttonOpenTrackingFallback.setOnClickListener {
            viewModel.screenState.value.trackingReference?.let { reference ->
                router.openTrackingSiteRawDetails(reference.service, reference.remoteId, reference.url)
            }
        }

        viewModel.screenState.observe(viewLifecycleOwner, ::renderState)
        viewModel.error.observe(viewLifecycleOwner) { error ->
            binding.errorGroup.isVisible = error != null
            binding.contentGroup.isVisible = error == null && viewModel.screenState.value.entity != null
            binding.buttonOpenTrackingFallback.isVisible = error != null && viewModel.screenState.value.trackingReference != null
        }
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefreshLayout.isRefreshing = isLoading
            binding.progressBar.isVisible = isLoading && viewModel.screenState.value.entity == null
        }
    }

    override fun onApplyWindowInsets(view: View, insets: WindowInsetsCompat): WindowInsetsCompat {
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        requireViewBinding().swipeRefreshLayout.updatePadding(
            left = systemBars.left,
            right = systemBars.right,
            bottom = systemBars.bottom,
        )
        return insets.consume(view, WindowInsetsCompat.Type.systemBars(), start = true, end = true, bottom = true)
    }

    private fun renderState(state: EntityDetailsScreenState) {
        val binding = requireViewBinding()
        val entity = state.entity
        if (entity == null) {
            binding.contentGroup.isVisible = false
            binding.buttonOpenTrackingDetails.isVisible = state.trackingReference != null
            binding.buttonOpenTrackingFallback.isVisible = state.trackingReference != null && viewModel.error.value != null
            return
        }
        binding.contentGroup.isVisible = viewModel.error.value == null
        binding.errorGroup.isVisible = viewModel.error.value != null

        setSupportTitle(entity.primaryName)
        binding.textViewTitle.text = entity.primaryName
        binding.textViewType.text = getString(entity.type.titleRes)

        renderAliases(binding, entity)
        renderBindings(binding, state.bindings)
        renderRelationSections(binding, state.relationSections)
        renderSourceResults(binding, entity, state.sourceResults)

        binding.buttonOpenTrackingDetails.isVisible = state.trackingReference != null
        binding.buttonOpenTrackingFallback.isVisible = state.trackingReference != null && viewModel.error.value != null
    }

    private fun renderAliases(binding: FragmentEntityDetailsBinding, entity: Entity) {
        val aliases = entity.aliases.filter { it.isNotBlank() }
        binding.textViewAliasesLabel.isVisible = aliases.isNotEmpty()
        binding.textViewAliases.isVisible = aliases.isNotEmpty()
        if (aliases.isNotEmpty()) {
            binding.textViewAliases.text = aliases.joinToString(separator = "\n")
        }
    }

    private fun renderBindings(binding: FragmentEntityDetailsBinding, bindings: List<EntityBinding>) {
        val items = bindings.sortedWith(
            compareByDescending<EntityBinding> { it.isPrimary }
                .thenBy { it.source }
                .thenBy { it.externalId },
        ).map { entityBinding ->
            "${entityBinding.toDisplaySourceName()} · ${entityBinding.externalId}"
        }
        binding.textViewBindings.text = if (items.isEmpty()) {
            getString(R.string.entity_graph_no_bindings)
        } else {
            items.joinToString(separator = "\n")
        }
    }

    private fun renderRelationSections(
        binding: FragmentEntityDetailsBinding,
        sections: List<EntityRelationSection>,
    ) {
        binding.relationsContainer.removeAllViews()
        if (sections.isEmpty()) {
            binding.relationsContainer.addView(createInfoTextView(getString(R.string.entity_graph_no_relations)))
            return
        }
        sections.forEach { section ->
            binding.relationsContainer.addView(createSectionTitle(getString(section.titleRes)))
            section.items.forEach { item ->
                binding.relationsContainer.addView(
                    createClickableCard(
                        title = item.name,
                        subtitle = getString(item.type.titleRes),
                    ) {
                        router.openEntityDetails(item.entityId)
                    },
                )
            }
        }
    }

    private fun renderSourceResults(
        binding: FragmentEntityDetailsBinding,
        entity: Entity,
        sourceResults: List<SourceResult>,
    ) {
        val isWork = entity.type == EntityType.WORK
        binding.textViewSourceResultsTitle.isVisible = isWork
        binding.sourceResultsContainer.isVisible = isWork
        if (!isWork) {
            return
        }
        binding.sourceResultsContainer.removeAllViews()
        if (sourceResults.isEmpty()) {
            binding.sourceResultsContainer.addView(
                createInfoTextView(getString(R.string.entity_graph_no_source_results)),
            )
            return
        }
        sourceResults.forEach { result ->
            val subtitle = getString(
                R.string.entity_graph_source_result_subtitle,
                result.source.getTitle(requireContext()),
                (result.confidence * 100f).toInt(),
            )
            binding.sourceResultsContainer.addView(
                createClickableCard(
                    title = result.content.title,
                    subtitle = subtitle,
                ) {
                    router.openDetails(result.content)
                },
            )
        }
    }

    private fun createSectionTitle(title: String): View {
        return TextView(requireContext()).apply {
            text = title
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            setPadding(dp(4), dp(12), dp(4), dp(8))
        }
    }

    private fun createInfoTextView(text: String): View {
        return TextView(requireContext()).apply {
            this.text = text
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
    }

    private fun createClickableCard(
        title: String,
        subtitle: String,
        onClick: () -> Unit,
    ): View {
        val context = requireContext()
        return MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp(8)
            }
            isClickable = true
            isFocusable = true
            radius = dp(12).toFloat()
            setOnClickListener { onClick() }
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                    addView(
                        TextView(context).apply {
                            text = title
                            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                        },
                    )
                    addView(
                        TextView(context).apply {
                            text = subtitle
                            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                        },
                    )
                },
            )
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}

private val EntityType.titleRes: Int
    get() = when (this) {
        EntityType.WORK -> R.string.entity_graph_type_work
        EntityType.CHARACTER -> R.string.entity_graph_type_character
        EntityType.PERSON -> R.string.entity_graph_type_person
        EntityType.ORGANIZATION -> R.string.entity_graph_type_organization
    }

private fun EntityBinding.toDisplaySourceName(): String {
    return source.replaceFirstChar { char ->
        if (char.isLowerCase()) {
            char.titlecase(Locale.getDefault())
        } else {
            char.toString()
        }
    }
}
