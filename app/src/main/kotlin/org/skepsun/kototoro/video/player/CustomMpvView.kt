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
	}

	override fun postInitOptions() = Unit

	override fun observeProperties() = Unit
}
