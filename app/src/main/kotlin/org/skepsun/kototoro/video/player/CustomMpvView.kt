package org.skepsun.kototoro.video.player

import android.content.Context
import android.util.AttributeSet
import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.MPVLib

class CustomMpvView(context: Context, attrs: AttributeSet) : BaseMPVView(context, attrs) {

	override fun initOptions() {
		setVo("gpu")
		MPVLib.setOptionString("hwdec", "auto")
		MPVLib.setOptionString("ao", "audiotrack,opensles")
		MPVLib.setOptionString("gpu-context", "android")
		MPVLib.setOptionString("keep-open", "yes")

		// 关键比例参数，避免旋转/PiP 后出现裁切或偏移
		MPVLib.setOptionString("keepaspect", "yes")
		MPVLib.setOptionString("panscan", "0.0")
		MPVLib.setOptionString("video-aspect-override", "-1")

		// Subtitle options: ensure embedded and external subtitles are detected and rendered
		MPVLib.setOptionString("sid", "auto")                       // Auto-select first subtitle track
		MPVLib.setOptionString("sub-visibility", "yes")             // Enable subtitle rendering
		MPVLib.setOptionString("sub-auto", "fuzzy")                 // Auto-load external subtitle files
		MPVLib.setOptionString("demuxer-mkv-subtitle-preroll", "yes") // Preload MKV embedded subtitles
		MPVLib.setOptionString("subs-with-matching-audio", "yes")   // Show subs even when audio language matches
		MPVLib.setOptionString("blend-subtitles", "yes")            // Render subs into video frame (fixes gpu-next OSD issue)
		MPVLib.setOptionString("sub-font-size", "55")               // Default subtitle font size
		MPVLib.setOptionString("sub-color", "#FFFFFFFF")            // White subtitle text
		MPVLib.setOptionString("sub-border-size", "3")              // Black border for readability
		MPVLib.setOptionString("sub-border-color", "#FF000000")     // Black border color
	}

	override fun postInitOptions() = Unit

	override fun observeProperties() = Unit
}
