package org.skepsun.kototoro.video.performance

import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.VideoDecoderMode
import org.skepsun.kototoro.core.prefs.VideoRendererMode
import org.skepsun.kototoro.core.prefs.VideoSuperResolutionMode

data class EffectiveVideoPlaybackConfig(
	val rendererMode: VideoRendererMode,
	val decoderMode: VideoDecoderMode,
	val superResolutionMode: VideoSuperResolutionMode,
	val allowShaderPipeline: Boolean,
)

object VideoPlaybackPolicy {

	fun resolve(
		settings: AppSettings,
		deviceInfo: DevicePerformanceInfo,
	): EffectiveVideoPlaybackConfig {
		val userDecoderMode = settings.videoDecoderMode
		val userRendererMode = settings.videoRendererMode
		val userSuperResolutionMode = settings.videoSuperResolutionMode

		return when (deviceInfo.tier) {
			DevicePerformanceTier.LOW -> EffectiveVideoPlaybackConfig(
				rendererMode = VideoRendererMode.MEDIACODEC_EMBED,
				decoderMode = userDecoderMode,
				superResolutionMode = VideoSuperResolutionMode.OFF,
				allowShaderPipeline = false,
			)

			DevicePerformanceTier.MID -> {
				val effectiveRendererMode = when (userRendererMode) {
					VideoRendererMode.AUTO,
					VideoRendererMode.GPU_NEXT,
					-> VideoRendererMode.GPU

					else -> userRendererMode
				}
				val effectiveSuperResolutionMode = if (userSuperResolutionMode == VideoSuperResolutionMode.OFF) {
					VideoSuperResolutionMode.OFF
				} else {
					VideoSuperResolutionMode.PERFORMANCE
				}
				EffectiveVideoPlaybackConfig(
					rendererMode = effectiveRendererMode,
					decoderMode = userDecoderMode,
					superResolutionMode = effectiveSuperResolutionMode,
					allowShaderPipeline = effectiveSuperResolutionMode != VideoSuperResolutionMode.OFF,
				)
			}

			DevicePerformanceTier.HIGH -> EffectiveVideoPlaybackConfig(
				rendererMode = userRendererMode,
				decoderMode = userDecoderMode,
				superResolutionMode = userSuperResolutionMode,
				allowShaderPipeline = userSuperResolutionMode != VideoSuperResolutionMode.OFF,
			)
		}
	}
}
