package org.skepsun.kototoro.video.player

import android.content.Context
import android.view.Surface
import dev.jdtech.mpv.MPVLib
import java.util.concurrent.CopyOnWriteArrayList
import android.util.Log
import java.io.File

class MpvPlayer(private val context: Context) : MPVLib.EventObserver {

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

	var durationMs: Long = 0L
		private set
	var positionMs: Long = 0L
		private set
	var isPlaying: Boolean = false
		private set

	fun initialize() {
		if (isInitialized) return
		MPVLib.create(context.applicationContext)
		MPVLib.init()
		MPVLib.observeProperty("time-pos", MPVLib.MPV_FORMAT_DOUBLE)
		MPVLib.observeProperty("duration", MPVLib.MPV_FORMAT_DOUBLE)
		MPVLib.observeProperty("pause", MPVLib.MPV_FORMAT_FLAG)
		MPVLib.observeProperty("eof-reached", MPVLib.MPV_FORMAT_FLAG)
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
		runCatching { MPVLib.detachSurface() }
		MPVLib.removeObserver(this)
		MPVLib.destroy()
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
		val headerValue = if (headers.isNotEmpty()) {
			headers.entries.joinToString(",") { "${it.key}: ${it.value}" }
		} else {
			""
		}
		MPVLib.setOptionString("http-header-fields", headerValue)
		MPVLib.command(arrayOf("loadfile", url, "replace"))
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
		MPVLib.command(arrayOf("seek", seconds.toString(), "absolute"))
		listeners.forEach { it.onSeek(positionMs) }
	}

	fun setRate(speed: Double) {
		MPVLib.setPropertyDouble("speed", speed)
	}

	fun setVolume(volume: Double) {
		MPVLib.setPropertyDouble("volume", volume)
	}

	fun setDeband(enabled: Boolean) {
		MPVLib.setPropertyBoolean("deband", enabled)
	}

	fun applyCacheSettings(sizeMb: Int, cacheDir: File) {
		val clampedMb = sizeMb.coerceAtLeast(64)
		val bytes = clampedMb.toLong() * 1024L * 1024L
		if (!cacheDir.exists()) {
			cacheDir.mkdirs()
		}
		MPVLib.setOptionString("cache", "yes")
		MPVLib.setOptionString("demuxer-cache-dir", cacheDir.absolutePath)
		MPVLib.setOptionString("demuxer-max-bytes", bytes.toString())
		MPVLib.setOptionString("demuxer-max-back-bytes", (bytes / 2).toString())
		Log.d("MpvPlayer", "applyCacheSettings: ${clampedMb}MB dir=${cacheDir.absolutePath}")
	}

	fun applyShaderList(shaderPaths: String?) {
		Log.d("MpvPlayer", "applyShaderList: ${shaderPaths ?: "none"}")
		if (shaderPaths.isNullOrBlank()) {
			MPVLib.command(arrayOf("change-list", "glsl-shaders", "clr", ""))
            return
        }
        MPVLib.command(arrayOf("change-list", "glsl-shaders", "set", shaderPaths))
    }

	fun getPropertyString(name: String): String? {
		return runCatching { MPVLib.getPropertyString(name) }.getOrNull()
	}

	override fun event(eventId: Int) {
		when (eventId) {
			MPVLib.MPV_EVENT_FILE_LOADED -> {
				listeners.forEach { it.onFileLoaded() }
				pendingSeekMs?.let { seekTo(it) }
				pendingSeekMs = null
			}
			MPVLib.MPV_EVENT_END_FILE -> listeners.forEach { it.onPlaybackEnded() }
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
}
