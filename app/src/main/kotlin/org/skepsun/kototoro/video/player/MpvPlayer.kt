package org.skepsun.kototoro.video.player

import android.view.Surface
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode
import java.util.concurrent.CopyOnWriteArrayList
import android.util.Log
import java.io.File

class MpvPlayer : MPVLib.EventObserver {

	interface Listener {
		fun onPositionChanged(positionMs: Long) = Unit
		fun onDurationChanged(durationMs: Long) = Unit
		fun onIsPlayingChanged(isPlaying: Boolean) = Unit
		fun onPlaybackEnded() = Unit
		fun onFileLoaded() = Unit
		fun onSeek(positionMs: Long) = Unit
	}

	private val listeners = CopyOnWriteArrayList<Listener>()
	private var isInitialized = false
	private var pendingSeekMs: Long? = null
	private var shouldAutoPlayAfterLoad: Boolean = false

	var durationMs: Long = 0L
		private set
	var positionMs: Long = 0L
		private set
	var isPlaying: Boolean = false
		private set

	fun initialize() {
		if (isInitialized) return
		MPVLib.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
		MPVLib.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
		MPVLib.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
		MPVLib.observeProperty("eof-reached", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
		MPVLib.addObserver(this)
		isInitialized = true
	}

	fun attachSurface(surface: Surface) {
		MPVLib.attachSurface(surface)
	}

	fun detachSurface() {
		MPVLib.detachSurface()
	}

	fun release() {
		pendingSeekMs = null
		MPVLib.removeObserver(this)
		isInitialized = false
	}

	fun addListener(listener: Listener) {
		listeners.add(listener)
	}

	fun removeListener(listener: Listener) {
		listeners.remove(listener)
	}

	fun load(url: String, headers: Map<String, String>, startMs: Long? = null) {
		pendingSeekMs = startMs
		shouldAutoPlayAfterLoad = true
		
		// Handle special headers separately for better compatibility
		val userAgent = headers.entries.find { it.key.equals("User-Agent", ignoreCase = true) }?.value
		val referer = headers.entries.find { it.key.equals("Referer", ignoreCase = true) }?.value
		
		if (!userAgent.isNullOrBlank()) {
			MPVLib.setOptionString("user-agent", userAgent)
		}
		if (!referer.isNullOrBlank()) {
			MPVLib.setOptionString("referrer", referer)
		}

		val otherHeaders = headers.filter { 
			!it.key.equals("User-Agent", ignoreCase = true) && !it.key.equals("Referer", ignoreCase = true) 
		}

		val headerValue = if (otherHeaders.isNotEmpty()) {
			// Each header must be in "Header: Value" format, and multiple headers are separated by ","
			// Note: MPV parsing of this string is tricky with commas, but this is the standard way.
			otherHeaders.entries.joinToString(",") { "${it.key}: ${it.value}" }
		} else {
			""
		}
		MPVLib.setOptionString("http-header-fields", headerValue)
		
		Log.d("MpvPlayer", "load: $url with headers count=${headers.size}")
		MPVLib.command("loadfile", url, "replace")
	}

	fun setStreamingOptions(cacheSizeMb: Int? = null) {
		// Optimize for network streaming and seeking
		MPVLib.setOptionString("cache", "yes")
		MPVLib.setOptionString("cache-on-disk", "yes")
		MPVLib.setOptionString("demuxer-seekable-cache", "yes")
		MPVLib.setOptionString("demuxer-max-bytes", "${(cacheSizeMb ?: 128) * 1024 * 1024}")
		MPVLib.setOptionString("demuxer-max-back-bytes", "${(cacheSizeMb ?: 128) * 1024 * 1024 / 2}")
		
		// Readahead and buffer settings
		MPVLib.setOptionString("cache-secs", "30")
		MPVLib.setOptionString("demuxer-readahead-secs", "20")
		
		// Network timeout and retries
		MPVLib.setOptionString("network-timeout", "30")
		MPVLib.setOptionString("tls-verify", "no") // Some sources have cert issues
		
		// Faster seeking
		MPVLib.setOptionString("hr-seek", "default")
		
		Log.d("MpvPlayer", "setStreamingOptions applied")
	}

	fun setHardwareDecoding(enabled: Boolean) {
		MPVLib.setOptionString("hwdec", if (enabled) "auto" else "no")
	}

	fun setHardwareDecodingMode(mode: String) {
		if (mode.isBlank()) return
		MPVLib.setOptionString("hwdec", mode)
		Log.d("MpvPlayer", "setHardwareDecodingMode: $mode")
	}

	fun setVideoOutput(renderer: String) {
		if (renderer.isBlank()) return
		MPVLib.setOptionString("vo", renderer)
		Log.d("MpvPlayer", "setVideoOutput: $renderer")
	}

	fun play() {
		MPVLib.setPropertyBoolean("pause", false)
	}

	fun pause() {
		MPVLib.setPropertyBoolean("pause", true)
	}

	fun seekTo(positionMs: Long) {
		val seconds = positionMs / 1000.0
		MPVLib.command("seek", seconds.toString(), "absolute+keyframes")
		listeners.forEach { it.onSeek(positionMs) }
	}

	fun setRate(speed: Double) {
		MPVLib.setPropertyDouble("speed", speed)
	}


	fun setAspectRatio(type: Int) {
		when (type) {
			1 -> { // Fill
				MPVLib.setPropertyDouble("panscan", 1.0)
				MPVLib.setOptionString("video-aspect-override", "-1")
			}
			2 -> { // 16:9
				MPVLib.setPropertyDouble("panscan", 0.0)
				MPVLib.setOptionString("video-aspect-override", "16/9")
			}
			3 -> { // 4:3
				MPVLib.setPropertyDouble("panscan", 0.0)
				MPVLib.setOptionString("video-aspect-override", "4/3")
			}
			else -> { // Default / Fit
				MPVLib.setPropertyDouble("panscan", 0.0)
				MPVLib.setOptionString("video-aspect-override", "-1")
			}
		}
	}
	fun setVolume(volume: Double) {
		MPVLib.setPropertyDouble("volume", volume)
	}

	fun applyCacheSettings(sizeMb: Int, cacheDir: File) {
		val clampedMb = sizeMb.coerceAtLeast(64)
		val bytes = clampedMb.toLong() * 1024L * 1024L
		if (!cacheDir.exists()) {
			cacheDir.mkdirs()
		}
		MPVLib.setOptionString("cache", "yes")
		MPVLib.setOptionString("cache-on-disk", "yes")
		MPVLib.setOptionString("demuxer-seekable-cache", "yes")
		MPVLib.setOptionString("demuxer-cache-dir", cacheDir.absolutePath)
		MPVLib.setOptionString("demuxer-max-bytes", bytes.toString())
		MPVLib.setOptionString("demuxer-max-back-bytes", (bytes / 2).toString())
		Log.d("MpvPlayer", "applyCacheSettings: ${clampedMb}MB dir=${cacheDir.absolutePath}")
	}

	fun applyShaderList(shaderPaths: String?) {
		Log.d("MpvPlayer", "applyShaderList: ${shaderPaths ?: "none"}")
		if (shaderPaths.isNullOrBlank()) {
			MPVLib.command("change-list", "glsl-shaders", "clr", "")
            return
        }
        MPVLib.command("change-list", "glsl-shaders", "set", shaderPaths)
    }

	fun getPropertyString(name: String): String? {
		return runCatching { MPVLib.getPropertyString(name) }.getOrNull()
	}

	override fun event(eventId: Int) {
		Log.v("MpvPlayer", "MPV Event: $eventId")
		when (eventId) {
			MPVLib.MpvEvent.MPV_EVENT_START_FILE -> Log.d("MpvPlayer", "EVENT_START_FILE")
			MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> {
				Log.d("MpvPlayer", "EVENT_FILE_LOADED")
				if (shouldAutoPlayAfterLoad) {
					MPVLib.setPropertyBoolean("pause", false)
					shouldAutoPlayAfterLoad = false
				}
				listeners.forEach { it.onFileLoaded() }
				pendingSeekMs?.let { seekTo(it) }
				pendingSeekMs = null
			}
			MPVLib.MpvEvent.MPV_EVENT_END_FILE -> {
				Log.d("MpvPlayer", "EVENT_END_FILE")
				listeners.forEach { it.onPlaybackEnded() }
			}
			MPVLib.MpvEvent.MPV_EVENT_IDLE -> Log.d("MpvPlayer", "EVENT_IDLE")
			MPVLib.MpvEvent.MPV_EVENT_SHUTDOWN -> Log.d("MpvPlayer", "EVENT_SHUTDOWN")
		}
	}

	override fun eventProperty(property: String) = Unit

	override fun eventProperty(property: String, value: Long) = Unit

	override fun eventProperty(property: String, value: Double) {
		when (property) {
			"time-pos" -> {
				positionMs = (value * 1000).toLong()
				listeners.forEach { it.onPositionChanged(positionMs) }
			}
			"duration" -> {
				durationMs = (value * 1000).toLong()
				listeners.forEach { it.onDurationChanged(durationMs) }
			}
		}
	}

	override fun eventProperty(property: String, value: Boolean) {
		when (property) {
			"pause" -> {
				isPlaying = !value
				listeners.forEach { it.onIsPlayingChanged(isPlaying) }
			}
			"eof-reached" -> {
				if (value) {
					listeners.forEach { it.onPlaybackEnded() }
				}
			}
		}
	}

	override fun eventProperty(property: String, value: String) = Unit

	override fun eventProperty(property: String, value: MPVNode) = Unit
}
