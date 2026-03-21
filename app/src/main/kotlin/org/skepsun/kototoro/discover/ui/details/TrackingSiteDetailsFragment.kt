package org.skepsun.kototoro.discover.ui.details

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.text.parseAsHtml
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.core.graphics.ColorUtils
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.ui.BaseFragment
import org.skepsun.kototoro.core.util.ext.consume
import org.skepsun.kototoro.core.util.ext.getThemeColor
import org.skepsun.kototoro.core.util.ext.getThemeDimensionPixelSize
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.core.util.ext.sanitize
import org.skepsun.kototoro.databinding.FragmentTrackingSiteDetailsBinding
import org.skepsun.kototoro.tracking.discovery.domain.TrackingSiteItemDetails
import org.skepsun.kototoro.core.prefs.AppSettings
import androidx.constraintlayout.widget.Guideline
import coil3.ImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.allowRgb565
import coil3.request.crossfade
import coil3.request.lifecycle
import android.os.Build
import android.util.TypedValue
import javax.inject.Inject

@AndroidEntryPoint
class TrackingSiteDetailsFragment : BaseFragment<FragmentTrackingSiteDetailsBinding>() {

	@Inject
	lateinit var coil: ImageLoader

	@Inject
	lateinit var settings: AppSettings

	private val viewModel by viewModels<TrackingSiteDetailsViewModel>()
	private var infoboxExpanded = false

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	): FragmentTrackingSiteDetailsBinding {
		return FragmentTrackingSiteDetailsBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: FragmentTrackingSiteDetailsBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		(activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.hide()
		(activity as? org.skepsun.kototoro.core.ui.FragmentContainerActivity)?.appBar?.visibility = View.GONE

		binding.appbar.setBackgroundColor(android.graphics.Color.TRANSPARENT)
		binding.appbar.outlineProvider = null
		binding.toolbar.setBackgroundColor(android.graphics.Color.TRANSPARENT)
		binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
		binding.toolbar.setNavigationOnClickListener { activity?.finish() }

		val surfaceColor = requireContext().getThemeColor(com.google.android.material.R.attr.colorSurface)
		val location = IntArray(2)
		binding.scrollView.setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
			val alpha = (scrollY.toFloat() / 200f).coerceIn(0f, 1f)
			binding.appbar.setBackgroundColor(ColorUtils.setAlphaComponent(surfaceColor, (alpha * 255).toInt()))
			
			binding.textViewTitle.getLocationOnScreen(location)
			var top = location[1] + binding.textViewTitle.height
			(v as View).getLocationOnScreen(location)
			top -= location[1]
			binding.toolbar.title = if (top < 0) viewModel.details.value?.title else ""
		}

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
		val binding = requireViewBinding()
		
		val hostAppbarPadding = (activity as? org.skepsun.kototoro.core.ui.FragmentContainerActivity)?.appBar?.paddingTop ?: 0
		val trueTopInset = if (systemBars.top > 0) systemBars.top else hostAppbarPadding

		binding.swipeRefreshLayout.updatePadding(
			left = systemBars.left,
			right = systemBars.right,
			bottom = systemBars.bottom,
		)
		binding.appbar.updatePadding(top = trueTopInset)
		val totalTopOffset = trueTopInset + view.context.getThemeDimensionPixelSize(androidx.appcompat.R.attr.actionBarSize)
		binding.scrollView.findViewById<Guideline>(R.id.guideline_status_bar)?.setGuidelineBegin(totalTopOffset)

		return insets.consume(view, WindowInsetsCompat.Type.systemBars(), start = true, end = true, bottom = true)
	}

	private fun renderDetails(details: TrackingSiteItemDetails?) {
		val binding = requireViewBinding()
		if (details == null) {
			binding.contentGroup.isVisible = false
			return
		}

		binding.contentGroup.isVisible = true
		binding.errorGroup.isVisible = false
		
		binding.imageViewCover.setImageAsync(details.coverUrl)
		loadPanoramaCover(details.coverUrl)

		binding.textViewTitle.text = details.title
		binding.textViewAltTitle.text = details.altTitle
		binding.textViewAltTitle.isVisible = !details.altTitle.isNullOrBlank()
		binding.textViewSite.setText(details.service.titleResId)
		binding.textViewScore.text = details.score?.let { getString(R.string.discover_score, it) }
		binding.textViewScore.isVisible = details.score != null
		binding.textViewScoreLabel.isVisible = details.score != null
		
		binding.textViewRank.text = details.rank?.let { getString(R.string.discover_rank_value, it) }
		binding.textViewRank.isVisible = details.rank != null
		binding.textViewRankLabel.isVisible = details.rank != null

		binding.textViewBindingStatus.isVisible = true

		binding.textViewYear.text = details.year?.toString()
		binding.layoutYear.isVisible = details.year != null

		binding.textViewEpisodes.text = details.totalEpisodes?.toString()
		binding.layoutEpisodes.isVisible = details.totalEpisodes != null
		
		binding.textViewAuthors.text = details.authors.joinToString()
		binding.layoutAuthors.isVisible = details.authors.isNotEmpty()

		binding.chipsTags.isVisible = details.tags.isNotEmpty()
		binding.chipsTags.setChips(details.tags.map { org.skepsun.kototoro.core.ui.widgets.ChipsView.ChipModel(title = it, data = it) })

		binding.textViewDescription.text = details.description?.parseAsHtml()?.sanitize()
		binding.layoutDescription.isVisible = !details.description.isNullOrBlank()

		binding.buttonOpenSite.isVisible = !details.url.isNullOrBlank()

		// Render infobox properties
		renderInfobox(binding, details.infoboxProperties)

		// Render episodes
		renderEpisodes(binding, details.episodes)

		// Render related works
		renderRelatedWorks(binding, details.relatedWorks)

		// Render recommendations
		renderRecommendations(binding, details.recommendations)
	}

	private fun renderInfobox(binding: FragmentTrackingSiteDetailsBinding, properties: List<Pair<String, String>>) {
		val container = binding.root.findViewById<LinearLayout>(R.id.infoboxContainer) ?: return
		val layout = binding.root.findViewById<LinearLayout>(R.id.layoutInfobox) ?: return
		val toggleButton = binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.buttonInfoboxToggle)

		container.removeAllViews()

		if (properties.isEmpty()) {
			layout.isVisible = false
			return
		}

		layout.isVisible = true
		val maxInitial = 5

		properties.forEachIndexed { index, (key, value) ->
			val row = LinearLayout(requireContext()).apply {
				orientation = LinearLayout.HORIZONTAL
				layoutParams = LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT,
				).apply { bottomMargin = dpToPx(4) }
			}
			val keyView = TextView(requireContext()).apply {
				text = key
				setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
				layoutParams = LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.WRAP_CONTENT,
					LinearLayout.LayoutParams.WRAP_CONTENT,
				).apply { marginEnd = dpToPx(8) }
				minWidth = dpToPx(80)
			}
			val valueView = TextView(requireContext()).apply {
				text = value
				setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
				layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
			}
			row.addView(keyView)
			row.addView(valueView)
			container.addView(row)

			if (index >= maxInitial && !infoboxExpanded) {
				row.isVisible = false
			}
		}

		if (properties.size > maxInitial) {
			toggleButton?.isVisible = true
			toggleButton?.text = if (infoboxExpanded) "收起" else "展开更多 (${properties.size - maxInitial})"
			toggleButton?.setOnClickListener {
				infoboxExpanded = !infoboxExpanded
				for (i in maxInitial until container.childCount) {
					container.getChildAt(i).isVisible = infoboxExpanded
				}
				toggleButton.text = if (infoboxExpanded) "收起" else "展开更多 (${properties.size - maxInitial})"
			}
		} else {
			toggleButton?.isVisible = false
		}
	}

	private fun renderEpisodes(binding: FragmentTrackingSiteDetailsBinding, episodes: List<TrackingSiteItemDetails.EpisodeInfo>) {
		val container = binding.root.findViewById<LinearLayout>(R.id.episodesContainer) ?: return
		val layout = binding.root.findViewById<LinearLayout>(R.id.layoutEpisodesList) ?: return
		container.removeAllViews()

		if (episodes.isEmpty()) {
			layout.isVisible = false
			return
		}

		layout.isVisible = true

		episodes.forEach { ep ->
			val row = TextView(requireContext()).apply {
				text = "${ep.number}. ${ep.title}"
				setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
				setPadding(dpToPx(4), dpToPx(8), dpToPx(4), dpToPx(8))
				isClickable = true
				isFocusable = true
				val outValue = TypedValue()
				context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
				setBackgroundResource(outValue.resourceId)
				setOnClickListener {
					AlertDialog.Builder(requireContext())
						.setTitle(ep.title)
						.setMessage("第 ${ep.number} 话\n\n${ep.title}")
						.setPositiveButton(android.R.string.ok, null)
						.show()
				}
			}
			container.addView(row)
		}
	}

	private fun renderRelatedWorks(binding: FragmentTrackingSiteDetailsBinding, relatedWorks: List<TrackingSiteItemDetails.RelatedWork>) {
		val container = binding.root.findViewById<LinearLayout>(R.id.relatedWorksContainer) ?: return
		val layout = binding.root.findViewById<LinearLayout>(R.id.layoutRelatedWorks) ?: return
		container.removeAllViews()

		if (relatedWorks.isEmpty()) {
			layout.isVisible = false
			return
		}

		layout.isVisible = true
		relatedWorks.forEach { work -> addWorkCard(container, work, showRelationship = true) }
	}

	private fun renderRecommendations(binding: FragmentTrackingSiteDetailsBinding, recommendations: List<TrackingSiteItemDetails.RelatedWork>) {
		val container = binding.root.findViewById<LinearLayout>(R.id.recommendationsContainer) ?: return
		val layout = binding.root.findViewById<LinearLayout>(R.id.layoutRecommendations) ?: return
		container.removeAllViews()

		if (recommendations.isEmpty()) {
			layout.isVisible = false
			return
		}

		layout.isVisible = true
		recommendations.forEach { work -> addWorkCard(container, work, showRelationship = false) }
	}

	private fun addWorkCard(container: LinearLayout, work: TrackingSiteItemDetails.RelatedWork, showRelationship: Boolean) {
		val cardWidth = dpToPx(100)
		val card = LinearLayout(requireContext()).apply {
			orientation = LinearLayout.VERTICAL
			layoutParams = LinearLayout.LayoutParams(cardWidth, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
				marginEnd = dpToPx(8)
			}
			isClickable = true
			isFocusable = true
			val outValue = TypedValue()
			context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
			setBackgroundResource(outValue.resourceId)
			setOnClickListener {
				router.openTrackingSiteDetails(
					service = viewModel.details.value?.service ?: return@setOnClickListener,
					remoteId = work.id,
					url = work.url,
				)
			}
		}

		val coverView = ImageView(requireContext()).apply {
			layoutParams = LinearLayout.LayoutParams(cardWidth, dpToPx(140))
			scaleType = ImageView.ScaleType.CENTER_CROP
			clipToOutline = true
			setBackgroundResource(com.google.android.material.R.drawable.m3_tabs_background)
		}
		if (work.coverUrl.isNotBlank()) {
			val req = ImageRequest.Builder(requireContext())
				.data(work.coverUrl)
				.lifecycle(this)
				.crossfade(true)
				.target(onSuccess = { coverView.setImageDrawable(it.asDrawable(resources)) })
				.build()
			coil.enqueue(req)
		}
		card.addView(coverView)

		if (showRelationship && !work.relationship.isNullOrBlank()) {
			val relLabel = TextView(requireContext()).apply {
				text = work.relationship
				setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelSmall)
				setTextColor(requireContext().getThemeColor(androidx.appcompat.R.attr.colorPrimary))
				setPadding(0, dpToPx(2), 0, 0)
			}
			card.addView(relLabel)
		}

		val titleView = TextView(requireContext()).apply {
			text = work.title
			setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
			maxLines = 2
			ellipsize = android.text.TextUtils.TruncateAt.END
			setPadding(0, dpToPx(4), 0, 0)
		}
		card.addView(titleView)

		container.addView(card)
	}

	private fun dpToPx(dp: Int): Int {
		return TypedValue.applyDimension(
			TypedValue.COMPLEX_UNIT_DIP,
			dp.toFloat(),
			resources.displayMetrics,
		).toInt()
	}

	private fun loadPanoramaCover(imageUrl: String?) {
		val binding = requireViewBinding()
		val panoramaView = binding.imageViewPanorama
		val scrimView = binding.viewPanoramaScrim
		val bottomGradientView = binding.viewPanoramaBottomGradient

		if (!settings.isPanoramaCoverEnabled || imageUrl.isNullOrEmpty()) {
			panoramaView.isVisible = false
			scrimView.isVisible = false
			bottomGradientView.isVisible = false
			return
		}

		panoramaView.isVisible = true
		scrimView.isVisible = true
		bottomGradientView.isVisible = true

		val blurEnabled = settings.isPanoramaCoverBlurred

		val request = ImageRequest.Builder(requireContext())
			.data(imageUrl)
			.lifecycle(this)
			.crossfade(true)
			.allowRgb565(true)
			.target(
				onSuccess = { result ->
					panoramaView.setImageDrawable(result.asDrawable(resources))
					if (blurEnabled) {
						applyBlurEffect(panoramaView)
					}
				},
				onError = {
					panoramaView.isVisible = false
					scrimView.isVisible = false
					bottomGradientView.isVisible = false
				},
			)
			.build()
		coil.enqueue(request)
	}

	private fun applyBlurEffect(imageView: android.widget.ImageView) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			imageView.setRenderEffect(
				android.graphics.RenderEffect.createBlurEffect(
					25f, 25f,
					android.graphics.Shader.TileMode.MIRROR,
				),
			)
		} else {
			imageView.alpha = 0.3f
		}
	}
}
