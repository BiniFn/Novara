package org.skepsun.kototoro.video.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.VideoSuperResolutionShader
import org.skepsun.kototoro.core.ui.sheet.BaseAdaptiveSheet
import org.skepsun.kototoro.core.util.ext.consume
import org.skepsun.kototoro.databinding.SheetVideoSuperResolutionAdvancedBinding

@AndroidEntryPoint
class VideoSuperResolutionAdvancedSheet : BaseAdaptiveSheet<SheetVideoSuperResolutionAdvancedBinding>() {

	@Inject
	lateinit var appSettings: AppSettings

	private var isUpdating = false

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	): SheetVideoSuperResolutionAdvancedBinding {
		return SheetVideoSuperResolutionAdvancedBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(
		binding: SheetVideoSuperResolutionAdvancedBinding,
		savedInstanceState: Bundle?,
	) {
		super.onViewBindingCreated(binding, savedInstanceState)
		binding.headerBar.setTitle(R.string.video_super_resolution_advanced)

		val shadersDir = org.skepsun.kototoro.video.player.MpvShaderManager.ensureShadersCopied(requireContext())
		val glslFiles = shadersDir.listFiles { _, name -> name.endsWith(".glsl", true) }?.map { it.name }?.sorted() ?: emptyList()
		
		val customShadersStr = appSettings.videoSuperResolutionCustomShaders
		val customShaders = customShadersStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()

		binding.shadersContainer.removeAllViews()
		for (file in glslFiles) {
			val switch = com.google.android.material.materialswitch.MaterialSwitch(requireContext()).apply {
				text = file
				isChecked = customShaders.contains(file)
				layoutParams = android.widget.LinearLayout.LayoutParams(
					android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
					android.view.ViewGroup.LayoutParams.WRAP_CONTENT
				).apply {
					topMargin = (8 * resources.displayMetrics.density).toInt()
					bottomMargin = (8 * resources.displayMetrics.density).toInt()
				}
				setOnCheckedChangeListener { _, isChecked ->
					if (isUpdating) return@setOnCheckedChangeListener
					
					val current = appSettings.videoSuperResolutionCustomShaders.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
					if (isChecked && !current.contains(file)) {
						current.add(file)
					} else if (!isChecked) {
						current.remove(file)
					}
					appSettings.videoSuperResolutionCustomShaders = current.joinToString(",")
					appSettings.videoSuperResolutionShader = VideoSuperResolutionShader.CUSTOM
					
					(activity as? VideoPlayerActivity)?.applySuperResolutionFromSettings()
				}
			}
			binding.shadersContainer.addView(switch)
			
			// Add a divider
			val divider = View(requireContext()).apply {
				layoutParams = android.widget.LinearLayout.LayoutParams(
					android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
					1
				)
				setBackgroundColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline))
			}
			binding.shadersContainer.addView(divider)
		}

		// Ensure we are using custom shader mode if we are doing this
		if (appSettings.videoSuperResolutionShader != VideoSuperResolutionShader.CUSTOM) {
			appSettings.videoSuperResolutionShader = VideoSuperResolutionShader.CUSTOM
			(activity as? VideoPlayerActivity)?.applySuperResolutionFromSettings()
		}

		binding.buttonCustomSettings.visibility = View.GONE
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val typeMask = WindowInsetsCompat.Type.systemBars()
		viewBinding?.scrollView?.updatePadding(
			bottom = insets.getInsets(typeMask).bottom,
		)
		return insets.consume(v, typeMask, bottom = true)
	}
}
