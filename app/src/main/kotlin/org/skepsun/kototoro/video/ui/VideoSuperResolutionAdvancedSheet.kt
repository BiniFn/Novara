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

		binding.switchModeA.setOnCheckedChangeListener { _, isChecked ->
			if (isUpdating || !isChecked) return@setOnCheckedChangeListener
			updateShader(VideoSuperResolutionShader.MODE_A)
		}
		binding.switchModeB.setOnCheckedChangeListener { _, isChecked ->
			if (isUpdating || !isChecked) return@setOnCheckedChangeListener
			updateShader(VideoSuperResolutionShader.MODE_B)
		}
		binding.switchModeC.setOnCheckedChangeListener { _, isChecked ->
			if (isUpdating || !isChecked) return@setOnCheckedChangeListener
			updateShader(VideoSuperResolutionShader.MODE_C)
		}
		binding.switchModeAa.setOnCheckedChangeListener { _, isChecked ->
			if (isUpdating || !isChecked) return@setOnCheckedChangeListener
			updateShader(VideoSuperResolutionShader.MODE_AA)
		}
		binding.switchModeBb.setOnCheckedChangeListener { _, isChecked ->
			if (isUpdating || !isChecked) return@setOnCheckedChangeListener
			updateShader(VideoSuperResolutionShader.MODE_BB)
		}
		binding.switchModeCa.setOnCheckedChangeListener { _, isChecked ->
			if (isUpdating || !isChecked) return@setOnCheckedChangeListener
			updateShader(VideoSuperResolutionShader.MODE_CA)
		}

		updateShader(appSettings.videoSuperResolutionShader)
	}

    private fun updateShader(shader: VideoSuperResolutionShader) {
        isUpdating = true
        appSettings.videoSuperResolutionShader = shader
        requireViewBinding().apply {
            switchModeA.isChecked = shader == VideoSuperResolutionShader.MODE_A
			switchModeB.isChecked = shader == VideoSuperResolutionShader.MODE_B
			switchModeC.isChecked = shader == VideoSuperResolutionShader.MODE_C
			switchModeAa.isChecked = shader == VideoSuperResolutionShader.MODE_AA
			switchModeBb.isChecked = shader == VideoSuperResolutionShader.MODE_BB
            switchModeCa.isChecked = shader == VideoSuperResolutionShader.MODE_CA
        }
        isUpdating = false
        (activity as? VideoPlayerActivity)?.applySuperResolutionFromSettings()
    }

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val typeMask = WindowInsetsCompat.Type.systemBars()
		viewBinding?.scrollView?.updatePadding(
			bottom = insets.getInsets(typeMask).bottom,
		)
		return insets.consume(v, typeMask, bottom = true)
	}
}
