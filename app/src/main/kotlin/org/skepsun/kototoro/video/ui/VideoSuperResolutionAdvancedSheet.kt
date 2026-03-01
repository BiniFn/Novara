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
			val frame = android.widget.LinearLayout(requireContext()).apply {
				orientation = android.widget.LinearLayout.VERTICAL
				layoutParams = android.widget.LinearLayout.LayoutParams(
					android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
					android.view.ViewGroup.LayoutParams.WRAP_CONTENT
				).apply {
					topMargin = (12 * resources.displayMetrics.density).toInt()
					bottomMargin = (12 * resources.displayMetrics.density).toInt()
				}
			}
			
			val titleContainer = android.widget.LinearLayout(requireContext()).apply {
				orientation = android.widget.LinearLayout.HORIZONTAL
				layoutParams = android.widget.LinearLayout.LayoutParams(
					android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
					android.view.ViewGroup.LayoutParams.WRAP_CONTENT
				)
			}

			val titleText = android.widget.TextView(requireContext()).apply {
				text = file
				setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
				layoutParams = android.widget.LinearLayout.LayoutParams(
					0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f
				)
			}
			val materialSwitch = com.google.android.material.materialswitch.MaterialSwitch(requireContext()).apply {
				isChecked = customShaders.contains(file)
				layoutParams = android.widget.LinearLayout.LayoutParams(
					android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT
				)
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
			titleContainer.addView(titleText)
			titleContainer.addView(materialSwitch)
			frame.addView(titleContainer)

			val descResName = "video_super_resolution_shader_desc_" + file.removeSuffix(".glsl").lowercase()
			val descResId = resources.getIdentifier(descResName, "string", requireContext().packageName)
			val descText = if (descResId != 0) getString(descResId) else null
			
			if (descText != null) {
				val subText = android.widget.TextView(requireContext()).apply {
					text = descText
					setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
					setTextColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
					layoutParams = android.widget.LinearLayout.LayoutParams(
						android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT
					)
				}
				frame.addView(subText)
			}

			frame.setOnClickListener {
				materialSwitch.isChecked = !materialSwitch.isChecked
			}

			binding.shadersContainer.addView(frame)
			
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
