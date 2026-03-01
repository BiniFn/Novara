package org.skepsun.kototoro.video.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.VideoSuperResolutionMode
import org.skepsun.kototoro.core.prefs.VideoSuperResolutionShader
import org.skepsun.kototoro.core.ui.sheet.BaseAdaptiveSheet
import org.skepsun.kototoro.core.util.ext.consume
import org.skepsun.kototoro.databinding.SheetVideoSuperResolutionBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

@AndroidEntryPoint
class VideoSuperResolutionSheet : BaseAdaptiveSheet<SheetVideoSuperResolutionBinding>() {

	@Inject
	lateinit var appSettings: AppSettings

	private var isUpdating = false

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	): SheetVideoSuperResolutionBinding {
		return SheetVideoSuperResolutionBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(
		binding: SheetVideoSuperResolutionBinding,
		savedInstanceState: Bundle?,
	) {
		super.onViewBindingCreated(binding, savedInstanceState)
		binding.headerBar.setTitle(R.string.video_super_resolution)
		binding.buttonAdvancedSettings.setOnClickListener {
			openAdvancedSheet()
		}

		binding.switchOff.setOnCheckedChangeListener { _, isChecked ->
			if (isUpdating || !isChecked) return@setOnCheckedChangeListener
			updateMode(VideoSuperResolutionMode.OFF, false)
		}
		binding.switchQuality.setOnCheckedChangeListener { _, isChecked ->
			if (isUpdating || !isChecked) return@setOnCheckedChangeListener
			updateMode(VideoSuperResolutionMode.QUALITY, false)
		}
		binding.buttonQualityMode.setOnClickListener {
			showSubModeDialog(VideoSuperResolutionMode.QUALITY)
		}
		binding.switchBalanced.setOnCheckedChangeListener { _, isChecked ->
			if (isUpdating || !isChecked) return@setOnCheckedChangeListener
			updateMode(VideoSuperResolutionMode.BALANCED, false)
		}
		binding.buttonBalancedMode.setOnClickListener {
			showSubModeDialog(VideoSuperResolutionMode.BALANCED)
		}
		binding.switchPerformance.setOnCheckedChangeListener { _, isChecked ->
			if (isUpdating || !isChecked) return@setOnCheckedChangeListener
			updateMode(VideoSuperResolutionMode.PERFORMANCE, false)
		}
		binding.buttonPerformanceMode.setOnClickListener {
			showSubModeDialog(VideoSuperResolutionMode.PERFORMANCE)
		}
		binding.switchAdvanced.setOnCheckedChangeListener { _, isChecked ->
			if (isUpdating || !isChecked) return@setOnCheckedChangeListener
			updateMode(VideoSuperResolutionMode.ADVANCED, true)
		}

		updateMode(appSettings.videoSuperResolutionMode, false)
	}

    private fun updateMode(mode: VideoSuperResolutionMode, openAdvanced: Boolean) {
        isUpdating = true
        appSettings.videoSuperResolutionMode = mode
        requireViewBinding().apply {
			switchOff.isChecked = mode == VideoSuperResolutionMode.OFF
            switchQuality.isChecked = mode == VideoSuperResolutionMode.QUALITY
			switchBalanced.isChecked = mode == VideoSuperResolutionMode.BALANCED
			switchPerformance.isChecked = mode == VideoSuperResolutionMode.PERFORMANCE
			switchAdvanced.isChecked = mode == VideoSuperResolutionMode.ADVANCED
			buttonQualityMode.isVisible = mode == VideoSuperResolutionMode.QUALITY
			buttonBalancedMode.isVisible = mode == VideoSuperResolutionMode.BALANCED
			buttonPerformanceMode.isVisible = mode == VideoSuperResolutionMode.PERFORMANCE
            buttonAdvancedSettings.isVisible = mode == VideoSuperResolutionMode.ADVANCED
        }
        isUpdating = false
		updateSubModeLabels()
        (activity as? VideoPlayerActivity)?.applySuperResolutionFromSettings()
        if (mode == VideoSuperResolutionMode.ADVANCED && openAdvanced) {
            openAdvancedSheet()
        }
    }

	private fun updateSubModeLabels() {
		val binding = viewBinding ?: return
		val quality = getShaderForMode(VideoSuperResolutionMode.QUALITY)
		val balanced = getShaderForMode(VideoSuperResolutionMode.BALANCED)
		val performance = getShaderForMode(VideoSuperResolutionMode.PERFORMANCE)
		binding.buttonQualityMode.text = getString(
			R.string.video_super_resolution_submode_format,
			getShaderLabel(quality),
		)
		binding.buttonBalancedMode.text = getString(
			R.string.video_super_resolution_submode_format,
			getShaderLabel(balanced),
		)
		binding.buttonPerformanceMode.text = getString(
			R.string.video_super_resolution_submode_format,
			getShaderLabel(performance),
		)
	}

	private fun showSubModeDialog(mode: VideoSuperResolutionMode) {
		val options = listOf(
			VideoSuperResolutionShader.MODE_A,
			VideoSuperResolutionShader.MODE_B,
			VideoSuperResolutionShader.MODE_C,
			VideoSuperResolutionShader.MODE_AA,
			VideoSuperResolutionShader.MODE_BB,
			VideoSuperResolutionShader.MODE_CA,
			VideoSuperResolutionShader.CUSTOM,
		)
		val labels = options.map { getShaderLabel(it) }.toTypedArray()
		val current = getShaderForMode(mode)
		val checkedIndex = options.indexOf(current).coerceAtLeast(0)
		val titleRes = when (mode) {
			VideoSuperResolutionMode.OFF -> R.string.video_super_resolution_off
			VideoSuperResolutionMode.QUALITY -> R.string.video_super_resolution_submode_quality
			VideoSuperResolutionMode.BALANCED -> R.string.video_super_resolution_submode_balanced
			VideoSuperResolutionMode.PERFORMANCE -> R.string.video_super_resolution_submode_performance
			VideoSuperResolutionMode.ADVANCED -> R.string.video_super_resolution_advanced
		}
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(titleRes)
			.setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
				val selectedShader = options[which]
				setShaderForMode(mode, selectedShader)
				updateSubModeLabels()
				(activity as? VideoPlayerActivity)?.applySuperResolutionFromSettings()
				dialog.dismiss()
				// If custom is selected, maybe open the advanced sheet to configure it?
				// But let's keep it simple for now. 
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	private fun getShaderForMode(mode: VideoSuperResolutionMode): VideoSuperResolutionShader {
		return when (mode) {
			VideoSuperResolutionMode.OFF -> appSettings.videoSuperResolutionShader
			VideoSuperResolutionMode.QUALITY -> appSettings.videoSuperResolutionQualityShader
			VideoSuperResolutionMode.BALANCED -> appSettings.videoSuperResolutionBalancedShader
			VideoSuperResolutionMode.PERFORMANCE -> appSettings.videoSuperResolutionPerformanceShader
			VideoSuperResolutionMode.ADVANCED -> appSettings.videoSuperResolutionShader
		}
	}

	private fun setShaderForMode(mode: VideoSuperResolutionMode, shader: VideoSuperResolutionShader) {
		when (mode) {
			VideoSuperResolutionMode.OFF -> appSettings.videoSuperResolutionShader = shader
			VideoSuperResolutionMode.QUALITY -> appSettings.videoSuperResolutionQualityShader = shader
			VideoSuperResolutionMode.BALANCED -> appSettings.videoSuperResolutionBalancedShader = shader
			VideoSuperResolutionMode.PERFORMANCE -> appSettings.videoSuperResolutionPerformanceShader = shader
			VideoSuperResolutionMode.ADVANCED -> appSettings.videoSuperResolutionShader = shader
		}
	}

	private fun getShaderLabel(shader: VideoSuperResolutionShader): String {
		return when (shader) {
			VideoSuperResolutionShader.MODE_A -> getString(R.string.video_super_resolution_mode_a)
			VideoSuperResolutionShader.MODE_B -> getString(R.string.video_super_resolution_mode_b)
			VideoSuperResolutionShader.MODE_C -> getString(R.string.video_super_resolution_mode_c)
			VideoSuperResolutionShader.MODE_AA -> getString(R.string.video_super_resolution_mode_aa)
			VideoSuperResolutionShader.MODE_BB -> getString(R.string.video_super_resolution_mode_bb)
			VideoSuperResolutionShader.MODE_CA -> getString(R.string.video_super_resolution_mode_ca)
			VideoSuperResolutionShader.CUSTOM -> getString(R.string.video_super_resolution_mode_custom)
		}
	}

	private fun openAdvancedSheet() {
		val fm = parentFragmentManager
		val tag = "VideoSuperResolutionAdvancedSheet"
		if (fm.findFragmentByTag(tag) == null) {
			VideoSuperResolutionAdvancedSheet().show(fm, tag)
		}
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val typeMask = WindowInsetsCompat.Type.systemBars()
		viewBinding?.scrollView?.updatePadding(
			bottom = insets.getInsets(typeMask).bottom,
		)
		return insets.consume(v, typeMask, bottom = true)
	}
}
