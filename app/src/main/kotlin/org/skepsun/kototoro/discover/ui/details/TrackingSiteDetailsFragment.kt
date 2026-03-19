package org.skepsun.kototoro.discover.ui.details

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.parseAsHtml
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.ui.BaseFragment
import org.skepsun.kototoro.core.util.ext.consume
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.sanitize
import org.skepsun.kototoro.databinding.FragmentTrackingSiteDetailsBinding
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteItemDetails

@AndroidEntryPoint
class TrackingSiteDetailsFragment : BaseFragment<FragmentTrackingSiteDetailsBinding>() {

	private val viewModel by viewModels<TrackingSiteDetailsViewModel>()

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	): FragmentTrackingSiteDetailsBinding {
		return FragmentTrackingSiteDetailsBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: FragmentTrackingSiteDetailsBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		activity?.setTitle(viewModel.getService().titleResId)
		binding.swipeRefreshLayout.setOnRefreshListener(viewModel::refresh)
		binding.buttonRetry.setOnClickListener { viewModel.refresh() }
		binding.buttonOpenSite.setOnClickListener {
			viewModel.details.value?.url?.let(router::openExternalBrowser)
		}
		binding.buttonOpenLocal.setOnClickListener {
			viewModel.getLinkedContent()?.let(router::openDetails)
		}
		binding.buttonManageBinding.setOnClickListener {
			val details = viewModel.details.value ?: return@setOnClickListener
			router.openScrobblerBinding(
				scrobbler = details.service,
				remoteId = details.remoteId,
				title = details.title,
				url = details.url,
			)
		}

		viewModel.details.observe(viewLifecycleOwner, ::renderDetails)
		viewModel.linkedContent.observe(viewLifecycleOwner) { content ->
			binding.buttonOpenLocal.isVisible = content != null
			binding.buttonManageBinding.setText(
				if (content != null) {
					R.string.discover_manage_binding
				} else {
					R.string.discover_bind_local
				},
			)
			binding.textViewBindingStatus.setText(
				if (content != null) {
					R.string.discover_local_linked
				} else {
					R.string.discover_local_unlinked
				},
			)
		}
		viewModel.error.observe(viewLifecycleOwner) { error ->
			binding.errorGroup.isVisible = error != null
			binding.contentGroup.isVisible = error == null && viewModel.details.value != null
		}
		viewModel.isLoading.observe(viewLifecycleOwner) {
			binding.swipeRefreshLayout.isRefreshing = it
			binding.progressBar.isVisible = it && viewModel.details.value == null
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

	private fun renderDetails(details: TrackingSiteItemDetails?) {
		val binding = requireViewBinding()
		if (details == null) {
			binding.contentGroup.isVisible = false
			return
		}
		activity?.title = details.title
		binding.contentGroup.isVisible = true
		binding.errorGroup.isVisible = false
		binding.imageViewCover.setImageAsync(details.coverUrl)
		binding.textViewTitle.text = details.title
		binding.textViewAltTitle.text = details.altTitle
		binding.textViewAltTitle.isVisible = !details.altTitle.isNullOrBlank()
		binding.textViewSite.setText(details.service.titleResId)
		binding.textViewScore.text = details.score?.let { getString(R.string.discover_score, it) }
		binding.textViewScore.isVisible = details.score != null
		binding.textViewRank.text = details.rank?.let { getString(R.string.discover_rank_value, it) }
		binding.textViewRank.isVisible = details.rank != null
		binding.textViewBindingStatus.isVisible = true
		binding.textViewYear.text = details.year?.toString()
		binding.layoutYear.isVisible = details.year != null
		binding.textViewEpisodes.text = details.totalEpisodes?.toString()
		binding.layoutEpisodes.isVisible = details.totalEpisodes != null
		binding.textViewAuthors.text = details.authors.joinToString()
		binding.layoutAuthors.isVisible = details.authors.isNotEmpty()
		binding.textViewTags.text = details.tags.joinToString()
		binding.layoutTags.isVisible = details.tags.isNotEmpty()
		binding.textViewDescription.text = details.description?.parseAsHtml()?.sanitize()
		binding.layoutDescription.isVisible = !details.description.isNullOrBlank()
		binding.buttonOpenSite.isVisible = !details.url.isNullOrBlank()
	}
}
