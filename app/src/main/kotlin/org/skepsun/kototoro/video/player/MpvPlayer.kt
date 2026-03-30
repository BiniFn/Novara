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
		fun onSubtitleTextChanged(text: String?) = Unit
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
		MPVLib.observeProperty("sub-text", MPVLib.MpvFormat.MPV_FORMAT_STRING)
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

	fun seekExact(positionMs: Long) {
		val seconds = positionMs / 1000.0
		MPVLib.command("seek", seconds.toString(), "absolute")
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
				MPVLib.setOptionString("keepaspect", "yes")
			}
			2 -> { // 16:9
				MPVLib.setPropertyDouble("panscan", 0.0)
				MPVLib.setOptionString("video-aspect-override", "16/9")
				MPVLib.setOptionString("keepaspect", "yes")
			}
			3 -> { // 4:3
				MPVLib.setPropertyDouble("panscan", 0.0)
				MPVLib.setOptionString("video-aspect-override", "4/3")
				MPVLib.setOptionString("keepaspect", "yes")
			}
			4 -> { // Stretch
				MPVLib.setPropertyDouble("panscan", 0.0)
				MPVLib.setOptionString("video-aspect-override", "-1")
				MPVLib.setOptionString("keepaspect", "no")
			}
			else -> { // Default / Fit
				MPVLib.setPropertyDouble("panscan", 0.0)
				MPVLib.setOptionString("video-aspect-override", "-1")
				MPVLib.setOptionString("keepaspect", "yes")
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

	data class TrackInfo(
		val id: Int,
		val type: String, // "video", "audio", "sub"
		val title: String?,
		val language: String?,
		val codec: String?,
		val isDefault: Boolean,
		val isSelected: Boolean,
	) {
		fun displayName(): String {
			val parts = mutableListOf<String>()
			if (!title.isNullOrBlank()) parts.add(title)
			if (!language.isNullOrBlank() && language != title) parts.add("[$language]")
			if (!codec.isNullOrBlank()) parts.add("($codec)")
			return if (parts.isEmpty()) "Track $id" else parts.joinToString(" ")
		}
	}

	fun getTrackList(): List<TrackInfo> {
		// Try multiple approaches to get track count
		val count = getTrackCount()
		Log.d("MpvPlayer", "getTrackList: track count=$count")
		if (count <= 0) return emptyList()

		return (0 until count).mapNotNull { i ->
			try {
				val id = getTrackPropertyInt("track-list/$i/id") ?: return@mapNotNull null
				val type = getTrackPropertyString("track-list/$i/type") ?: return@mapNotNull null
				val title = getTrackPropertyString("track-list/$i/title")
				val lang = getTrackPropertyString("track-list/$i/lang")
				val codec = getTrackPropertyString("track-list/$i/codec")
				val isDefault = getTrackPropertyFlag("track-list/$i/default")
				val isSelected = getTrackPropertyFlag("track-list/$i/selected")
				TrackInfo(id, type, title, lang, codec, isDefault, isSelected).also {
					Log.d("MpvPlayer", "  track[$i]: id=$id type=$type lang=$lang title=$title codec=$codec selected=$isSelected")
				}
			} catch (e: Exception) {
				Log.e("MpvPlayer", "getTrackList: failed to read track $i", e)
				null
			}
		}
	}

	private fun getTrackCount(): Int {
		// Method 1: Try as int property
		try {
			@Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
			val count: Int = MPVLib.getPropertyInt("track-list/count")!!
			Log.d("MpvPlayer", "getTrackCount via getPropertyInt: $count")
			return count
		} catch (e: Exception) {
			Log.d("MpvPlayer", "getTrackCount getPropertyInt failed: ${e.message}")
		}
		// Method 2: Try as string property
		try {
			val countStr = MPVLib.getPropertyString("track-list/count")
			val count = countStr?.toIntOrNull()
			if (count != null) {
				Log.d("MpvPlayer", "getTrackCount via getPropertyString: $count")
				return count
			}
			Log.d("MpvPlayer", "getTrackCount getPropertyString returned: '$countStr'")
		} catch (e: Exception) {
			Log.d("MpvPlayer", "getTrackCount getPropertyString failed: ${e.message}")
		}
		// Method 3: Probe by iterating until property lookup fails
		Log.d("MpvPlayer", "getTrackCount: probing by iteration")
		var probed = 0
		while (probed < 50) { // safety limit
			try {
				val type = MPVLib.getPropertyString("track-list/$probed/type")
				if (type.isNullOrEmpty()) break
				probed++
			} catch (e: Exception) {
				break
			}
		}
		Log.d("MpvPlayer", "getTrackCount via probing: $probed")
		return probed
	}

	private fun getTrackPropertyString(prop: String): String? {
		return try {
			MPVLib.getPropertyString(prop)
		} catch (e: Exception) {
			null
		}
	}

	private fun getTrackPropertyInt(prop: String): Int? {
		// Try int first, then string
		runCatching { MPVLib.getPropertyInt(prop) }.getOrNull()?.let { return it }
		return runCatching { MPVLib.getPropertyString(prop)?.toIntOrNull() }.getOrNull()
	}

	private fun getTrackPropertyFlag(prop: String): Boolean {
		// Try boolean first, then string "yes"/"true"
		runCatching { MPVLib.getPropertyBoolean(prop) }.getOrNull()?.let { return it }
		val str = runCatching { MPVLib.getPropertyString(prop) }.getOrNull()
		return str == "yes" || str == "true"
	}

	fun getSubtitleTracks(): List<TrackInfo> {
		val tracks = getTrackList().filter { it.type == "sub" }
		Log.d("MpvPlayer", "getSubtitleTracks: found ${tracks.size} tracks: ${tracks.map { "[id=${it.id} lang=${it.language} title=${it.title} codec=${it.codec} selected=${it.isSelected}]" }}")
		return tracks
	}

	fun getAudioTracks(): List<TrackInfo> = getTrackList().filter { it.type == "audio" }

	fun setSubtitleTrack(id: Int?) {
		if (id == null || id <= 0) {
			MPVLib.setPropertyString("sid", "no")
			// Explicitly hide subtitles - try boolean first, then string
			runCatching { MPVLib.setPropertyBoolean("sub-visibility", false) }
				.onFailure { runCatching { MPVLib.setPropertyString("sub-visibility", "no") } }
		} else {
			runCatching { MPVLib.setPropertyInt("sid", id) }.onFailure {
				MPVLib.setPropertyString("sid", id.toString())
			}
			// Explicitly enable subtitle rendering - try boolean first, then string
			runCatching { MPVLib.setPropertyBoolean("sub-visibility", true) }
				.onFailure { runCatching { MPVLib.setPropertyString("sub-visibility", "yes") } }
			// Also ensure OSD is enabled (needed for subtitle overlay)
			runCatching { MPVLib.setPropertyString("osd-level", "1") }
		}
		val currentSid = runCatching { MPVLib.getPropertyString("sid") }.getOrNull()
		val subVis = runCatching { MPVLib.getPropertyString("sub-visibility") }.getOrNull()
		val subVisBool = runCatching { MPVLib.getPropertyBoolean("sub-visibility") }.getOrNull()
		val subText = runCatching { MPVLib.getPropertyString("sub-text") }.getOrNull()
		val subScale = runCatching { MPVLib.getPropertyString("sub-scale") }.getOrNull()
		val blendSubs = runCatching { MPVLib.getPropertyString("blend-subtitles") }.getOrNull()
		Log.d("MpvPlayer", "setSubtitleTrack: requested=$id, sid=$currentSid, sub-visibility=$subVis/$subVisBool, sub-text='$subText', sub-scale=$subScale, blend-subs=$blendSubs")
	}

	fun setAudioTrack(id: Int) {
		MPVLib.setPropertyString("aid", id.toString())
		Log.d("MpvPlayer", "setAudioTrack: $id")
	}

	/**
	 * Add an external subtitle track by URL.
	 * Must be called after loadfile (ideally after FILE_LOADED event).
	 */
	fun addSubtitleTrack(url: String, title: String? = null, lang: String? = null) {
		try {
			// sub-add <url> [<flags> [<title> [<lang>]]]
			val args = mutableListOf("sub-add", url, "auto")
			args.add(title ?: "")
			args.add(lang ?: "")
			MPVLib.command(*args.toTypedArray())
			Log.d("MpvPlayer", "addSubtitleTrack: url=$url title=$title lang=$lang")
		} catch (e: Exception) {
			Log.e("MpvPlayer", "addSubtitleTrack failed: url=$url", e)
		}
	}

	/**
	 * Add an external audio track by URL.
	 */
	fun addAudioTrack(url: String, title: String? = null, lang: String? = null) {
		try {
			val args = mutableListOf("audio-add", url, "auto")
			args.add(title ?: "")
			args.add(lang ?: "")
			MPVLib.command(*args.toTypedArray())
			Log.d("MpvPlayer", "addAudioTrack: url=$url title=$title lang=$lang")
		} catch (e: Exception) {
			Log.e("MpvPlayer", "addAudioTrack failed: url=$url", e)
		}
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

	override fun eventProperty(property: String, value: String) {
		when (property) {
			"sub-text" -> {
				listeners.forEach { it.onSubtitleTextChanged(value.ifEmpty { null }) }
			}
		}
	}

	override fun eventProperty(property: String, value: MPVNode) = Unit
}
