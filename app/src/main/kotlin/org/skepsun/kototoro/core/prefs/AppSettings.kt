package org.skepsun.kototoro.core.prefs

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.FloatRange
import androidx.appcompat.app.AppCompatDelegate
import androidx.collection.ArraySet
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.ZoomMode
import org.skepsun.kototoro.core.network.DoHProvider
import org.skepsun.kototoro.explore.data.SourcesSortOrder
import org.skepsun.kototoro.list.domain.ListSortOrder
import org.skepsun.kototoro.core.prefs.VideoDecoderMode
import org.skepsun.kototoro.core.prefs.VideoRendererMode
import org.skepsun.kototoro.core.prefs.VideoSuperResolutionMode
import org.skepsun.kototoro.core.prefs.VideoSuperResolutionShader
import org.skepsun.kototoro.parsers.model.SortOrder
import org.skepsun.kototoro.parsers.util.find
import org.skepsun.kototoro.parsers.util.mapNotNullToSet
import org.skepsun.kototoro.parsers.util.mapToSet
import org.skepsun.kototoro.parsers.util.nullIfEmpty
import org.skepsun.kototoro.core.util.ext.connectivityManager
import org.skepsun.kototoro.core.util.ext.getEnumValue
import org.skepsun.kototoro.core.util.ext.observeChanges
import org.skepsun.kototoro.core.util.ext.putAll
import org.skepsun.kototoro.core.util.ext.putEnumValue
import org.skepsun.kototoro.core.util.ext.takeIfReadable
import org.skepsun.kototoro.core.util.ext.toUriOrNull
import org.skepsun.kototoro.reader.domain.ReaderColorFilter
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService
import java.io.File
import java.net.Proxy
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppSettings @Inject constructor(@ApplicationContext private val context: Context) {

	private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
	private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
	private val mangaListBadgesDefault = ArraySet(context.resources.getStringArray(R.array.values_list_badges))

	var hasSeenPluginWelcome: Boolean
		get() = prefs.getBoolean("has_seen_plugin_welcome", false)
		set(value) = prefs.edit { putBoolean("has_seen_plugin_welcome", value) }

	var listMode: ListMode
		get() = prefs.getEnumValue(KEY_LIST_MODE, ListMode.GRID)
		set(value) = prefs.edit { putEnumValue(KEY_LIST_MODE, value) }

	val theme: Int
		get() = prefs.getString(KEY_THEME, null)?.toIntOrNull()
			?: AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM

	val colorScheme: ColorScheme
		get() = prefs.getEnumValue(KEY_COLOR_THEME, ColorScheme.default)

	val isAmoledTheme: Boolean
		get() = prefs.getBoolean(KEY_THEME_AMOLED, false)

	var mainNavItems: List<NavItem>
		get() {
			val rawStr = prefs.getString(KEY_NAV_MAIN, null)
			if (rawStr == "HOME,HISTORY,FAVORITES,EXPLORE,FEED") {
				val newDefaults = listOf(NavItem.HOME, NavItem.FAVORITES, NavItem.EXPLORE)
				prefs.edit { putString(KEY_NAV_MAIN, newDefaults.joinToString(",") { it.name }) }
				return newDefaults
			}
			val raw = rawStr?.split(',')
			return if (raw.isNullOrEmpty()) {
				listOf(NavItem.HOME, NavItem.FAVORITES, NavItem.EXPLORE)
			} else {
				raw.mapNotNull { x -> NavItem.entries.find(x) }
				.filterNot { it == NavItem.DISCOVER }
				.ifEmpty { listOf(NavItem.HOME, NavItem.FAVORITES, NavItem.EXPLORE) }
			}
		}
		set(value) {
			prefs.edit {
				putString(KEY_NAV_MAIN, value.joinToString(",") { it.name })
			}
		}

	val isNavLabelsVisible: Boolean
		get() = prefs.getBoolean(KEY_NAV_LABELS, true)

	val isNavBarPinned: Boolean
		get() = prefs.getBoolean(KEY_NAV_PINNED, false)

	var isNavFloating: Boolean
		get() = prefs.getBoolean(KEY_NAV_FLOATING, false)
		set(value) = prefs.edit { putBoolean(KEY_NAV_FLOATING, value) }

	val navHeight: Int
		get() = prefs.getInt(KEY_NAV_HEIGHT, 80)

	val navFloatingHeight: Int
		get() = prefs.getInt(KEY_NAV_FLOATING_HEIGHT, 64)

	val isMainFabEnabled: Boolean
		get() = prefs.getBoolean(KEY_MAIN_FAB, true)

	var gridSize: Int
		get() = prefs.getInt(KEY_GRID_SIZE, 100)
		set(value) = prefs.edit { putInt(KEY_GRID_SIZE, value) }

	var gridSizePages: Int
		get() = prefs.getInt(KEY_GRID_SIZE_PAGES, 100)
		set(value) = prefs.edit { putInt(KEY_GRID_SIZE_PAGES, value) }

	val isQuickFilterEnabled: Boolean
		get() = prefs.getBoolean(KEY_QUICK_FILTER, true)

	var isSearchBarFilterHidden: Boolean
		get() = prefs.getBoolean(KEY_SEARCH_BAR_FILTER_HIDDEN, false)
		set(value) = prefs.edit { putBoolean(KEY_SEARCH_BAR_FILTER_HIDDEN, value) }

	val isDescriptionExpanded: Boolean
		get() = !prefs.getBoolean(KEY_COLLAPSE_DESCRIPTION, true)

	var isPanoramaCoverEnabled: Boolean
		get() = prefs.getBoolean(KEY_PANORAMA_ENABLED, true)
		set(value) = prefs.edit { putBoolean(KEY_PANORAMA_ENABLED, value) }

	var panoramaCoverBlur: Int
		get() = prefs.getInt(KEY_PANORAMA_BLUR, 35)
		set(value) = prefs.edit { putInt(KEY_PANORAMA_BLUR, value) }

	var panoramaCoverExtraHeight: Int
		get() = prefs.getInt(KEY_PANORAMA_EXTRA_HEIGHT, 50)
		set(value) = prefs.edit { putInt(KEY_PANORAMA_EXTRA_HEIGHT, value) }

	var panoramaBottomGradientAlpha: Int
		get() = prefs.getInt(KEY_PANORAMA_BOTTOM_GRADIENT_ALPHA, 100)
		set(value) = prefs.edit { putInt(KEY_PANORAMA_BOTTOM_GRADIENT_ALPHA, value) }

	var historyListMode: ListMode
		get() = prefs.getEnumValue(KEY_LIST_MODE_HISTORY, listMode)
		set(value) = prefs.edit { putEnumValue(KEY_LIST_MODE_HISTORY, value) }

	var suggestionsListMode: ListMode
		get() = prefs.getEnumValue(KEY_LIST_MODE_SUGGESTIONS, listMode)
		set(value) = prefs.edit { putEnumValue(KEY_LIST_MODE_SUGGESTIONS, value) }

	var favoritesListMode: ListMode
		get() = prefs.getEnumValue(KEY_LIST_MODE_FAVORITES, listMode)
		set(value) = prefs.edit { putEnumValue(KEY_LIST_MODE_FAVORITES, value) }

	val isTagsWarningsEnabled: Boolean
		get() = prefs.getBoolean(KEY_TAGS_WARNINGS, true)

	var isNsfwContentDisabled: Boolean
		get() = prefs.getBoolean(KEY_DISABLE_NSFW, true)
		set(value) = prefs.edit { putBoolean(KEY_DISABLE_NSFW, value) }

	var isHistoryExcludeNsfw: Boolean
		get() = prefs.getBoolean(KEY_HISTORY_EXCLUDE_NSFW, false)
		set(value) = prefs.edit { putBoolean(KEY_HISTORY_EXCLUDE_NSFW, value) }

	var isFavouritesExcludeNsfw: Boolean
		get() = prefs.getBoolean(KEY_FAVOURITES_EXCLUDE_NSFW, false)
		set(value) = prefs.edit { putBoolean(KEY_FAVOURITES_EXCLUDE_NSFW, value) }

	var appLocales: LocaleListCompat
		get() {
			val raw = prefs.getString(KEY_APP_LOCALE, null)
			return LocaleListCompat.forLanguageTags(raw)
		}
		set(value) {
			prefs.edit {
				putString(KEY_APP_LOCALE, value.toLanguageTags())
			}
		}

	var contentLanguages: Set<String>
		get() = prefs.getStringSet(KEY_CONTENT_LANGUAGES, null) ?: setOf("zh", "en", "ja", "")
		set(value) = prefs.edit { putStringSet(KEY_CONTENT_LANGUAGES, value) }

	enum class GitHubMirror(val value: String) {
		NATIVE("native"),
		KKGITHUB("kkgithub"),
		GHPROXY("ghproxy"),
		GHPROXY_NET("ghproxy_net");
		companion object {
			fun fromValue(value: String?): GitHubMirror =
				entries.find { it.value == value } ?: NATIVE
		}
	}

	var gitHubMirror: GitHubMirror
		get() = GitHubMirror.fromValue(prefs.getString(KEY_GITHUB_MIRROR, GitHubMirror.NATIVE.value))
		set(value) = prefs.edit { putString(KEY_GITHUB_MIRROR, value.value) }

	enum class HuggingFaceMirror(val value: String) {
		NATIVE("native"),
		HF_MIRROR("hf_mirror");
		companion object {
			fun fromValue(value: String?): HuggingFaceMirror =
				entries.find { it.value == value } ?: NATIVE
		}
	}

	var huggingFaceMirror: HuggingFaceMirror
		get() = HuggingFaceMirror.fromValue(prefs.getString(KEY_HUGGINGFACE_MIRROR, HuggingFaceMirror.NATIVE.value))
		set(value) = prefs.edit { putString(KEY_HUGGINGFACE_MIRROR, value.value) }

	var extensionLanguages: Set<String>
		get() = prefs.getStringSet(KEY_EXTENSION_LANGUAGES, null) ?: emptySet()
		set(value) = prefs.edit { putStringSet(KEY_EXTENSION_LANGUAGES, value) }

	var activeTvBoxRepositoryLocator: String?
		get() = prefs.getString(KEY_TVBOX_ACTIVE_REPOSITORY, null)?.takeIf { it.isNotBlank() }
		set(value) = prefs.edit {
			if (value.isNullOrBlank()) {
				remove(KEY_TVBOX_ACTIVE_REPOSITORY)
			} else {
				putString(KEY_TVBOX_ACTIVE_REPOSITORY, value)
			}
		}

	var activeTvBoxRepositoryTitle: String?
		get() = prefs.getString(KEY_TVBOX_ACTIVE_REPOSITORY_TITLE, null)?.takeIf { it.isNotBlank() }
		set(value) = prefs.edit {
			if (value.isNullOrBlank()) {
				remove(KEY_TVBOX_ACTIVE_REPOSITORY_TITLE)
			} else {
				putString(KEY_TVBOX_ACTIVE_REPOSITORY_TITLE, value)
			}
		}

	var lnReaderRepoUrls: Set<String>
		get() = prefs.getStringSet(KEY_LNREADER_REPOS, null)
			?: setOf(org.skepsun.kototoro.core.lnreader.LNReaderRepository.OFFICIAL_REPO_URL)
		set(value) = prefs.edit { putStringSet(KEY_LNREADER_REPOS, value) }

	var isReaderDoubleOnLandscape: Boolean
		get() = prefs.getBoolean(KEY_READER_DOUBLE_PAGES, false)
		set(value) = prefs.edit { putBoolean(KEY_READER_DOUBLE_PAGES, value) }

	var isReaderDoubleOnFoldable: Boolean
		get() = prefs.getBoolean(KEY_READER_DOUBLE_FOLDABLE, false)
		set(value) = prefs.edit { putBoolean(KEY_READER_DOUBLE_FOLDABLE, value) }

	var isReaderSplitPagesEnabled: Boolean
		get() = prefs.getBoolean(KEY_READER_SPLIT_PAGES, false)
		set(value) = prefs.edit { putBoolean(KEY_READER_SPLIT_PAGES, value) }

	@get:FloatRange(0.0, 1.0)
	var readerDoublePagesSensitivity: Float
		get() = prefs.getFloat(KEY_READER_DOUBLE_PAGES_SENSITIVITY, 0.5f)
		set(@FloatRange(0.0, 1.0) value) = prefs.edit { putFloat(KEY_READER_DOUBLE_PAGES_SENSITIVITY, value) }

	val readerScreenOrientation: Int
		get() = prefs.getString(KEY_READER_ORIENTATION, null)?.toIntOrNull()
			?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

	val isReaderVolumeButtonsEnabled: Boolean
		get() = prefs.getBoolean(KEY_READER_VOLUME_BUTTONS, false)

	val isReaderZoomButtonsEnabled: Boolean
		get() = prefs.getBoolean(KEY_READER_ZOOM_BUTTONS, false)

	val isReaderControlAlwaysLTR: Boolean
		get() = prefs.getBoolean(KEY_READER_CONTROL_LTR, false)

	val isReaderNavigationInverted: Boolean
		get() = prefs.getBoolean(KEY_READER_NAVIGATION_INVERTED, false)

	val isReaderFullscreenEnabled: Boolean
		get() = prefs.getBoolean(KEY_READER_FULLSCREEN, true)

	var isReaderToolbarFloating: Boolean
		get() = prefs.getBoolean(KEY_READER_TOOLBAR_FLOATING, false)
		set(value) = prefs.edit { putBoolean(KEY_READER_TOOLBAR_FLOATING, value) }

	val isReaderOptimizationEnabled: Boolean
		get() = prefs.getBoolean(KEY_READER_OPTIMIZE, false)

	val readerControls: Set<ReaderControl>
		get() = prefs.getStringSet(KEY_READER_CONTROLS, null)?.mapNotNullTo(EnumSet.noneOf(ReaderControl::class.java)) {
			ReaderControl.entries.find(it)
		} ?: ReaderControl.DEFAULT

	val isOfflineCheckDisabled: Boolean
		get() = prefs.getBoolean(KEY_OFFLINE_DISABLED, false)

	var isAllFavouritesVisible: Boolean
		get() = prefs.getBoolean(KEY_ALL_FAVOURITES_VISIBLE, true)
		set(value) = prefs.edit { putBoolean(KEY_ALL_FAVOURITES_VISIBLE, value) }

	val isTrackerEnabled: Boolean
		get() = prefs.getBoolean(KEY_TRACKER_ENABLED, true)

	val isTrackerWifiOnly: Boolean
		get() = prefs.getBoolean(KEY_TRACKER_WIFI_ONLY, false)

	val trackerFrequencyFactor: Float
		get() = prefs.getString(KEY_TRACKER_FREQUENCY, null)?.toFloatOrNull() ?: 1f

	val isTrackerNotificationsEnabled: Boolean
		get() = prefs.getBoolean(KEY_TRACKER_NOTIFICATIONS, true)

	var preferredTrackingSite: ScrobblerService
		get() = prefs.getEnumValue(KEY_PREFERRED_TRACKING_SITE, ScrobblerService.BANGUMI)
		set(value) = prefs.edit { putEnumValue(KEY_PREFERRED_TRACKING_SITE, value) }

	val isTrackerNsfwDisabled: Boolean
		get() = prefs.getBoolean(KEY_TRACKER_NO_NSFW, false)

	val trackerDownloadStrategy: TrackerDownloadStrategy
		get() = prefs.getEnumValue(KEY_TRACKER_DOWNLOAD, TrackerDownloadStrategy.DISABLED)

	var notificationSound: Uri
		get() = prefs.getString(KEY_NOTIFICATIONS_SOUND, null)?.toUriOrNull()
			?: Settings.System.DEFAULT_NOTIFICATION_URI
		set(value) = prefs.edit { putString(KEY_NOTIFICATIONS_SOUND, value.toString()) }

	val notificationVibrate: Boolean
		get() = prefs.getBoolean(KEY_NOTIFICATIONS_VIBRATE, false)

	val notificationLight: Boolean
		get() = prefs.getBoolean(KEY_NOTIFICATIONS_LIGHT, true)

	val readerAnimation: ReaderAnimation
		get() = prefs.getEnumValue(KEY_READER_ANIMATION, ReaderAnimation.DEFAULT)

	val readerBackground: ReaderBackground
		get() = prefs.getEnumValue(KEY_READER_BACKGROUND, ReaderBackground.DEFAULT)

	var videoDecoderMode: VideoDecoderMode
		get() = prefs.getEnumValue(KEY_VIDEO_DECODER_MODE, VideoDecoderMode.HARDWARE)
		set(value) = prefs.edit { putEnumValue(KEY_VIDEO_DECODER_MODE, value) }

	var videoRendererMode: VideoRendererMode
		get() = prefs.getEnumValue(KEY_VIDEO_RENDERER_MODE, VideoRendererMode.AUTO)
		set(value) = prefs.edit { putEnumValue(KEY_VIDEO_RENDERER_MODE, value) }

	var videoBackground: ReaderBackground
		get() = prefs.getEnumValue(KEY_VIDEO_BACKGROUND, ReaderBackground.DEFAULT)
		set(value) = prefs.edit { putEnumValue(KEY_VIDEO_BACKGROUND, value) }

	var videoSuperResolutionMode: VideoSuperResolutionMode
		get() = prefs.getEnumValue(KEY_VIDEO_SUPER_RES_MODE, VideoSuperResolutionMode.BALANCED)
		set(value) = prefs.edit { putEnumValue(KEY_VIDEO_SUPER_RES_MODE, value) }

	var videoSuperResolutionShader: VideoSuperResolutionShader
		get() = prefs.getEnumValue(KEY_VIDEO_SUPER_RES_SHADER, VideoSuperResolutionShader.MODE_A)
		set(value) = prefs.edit { putEnumValue(KEY_VIDEO_SUPER_RES_SHADER, value) }

	var videoSuperResolutionQualityShader: VideoSuperResolutionShader
		get() = prefs.getEnumValue(KEY_VIDEO_SUPER_RES_QUALITY_SHADER, VideoSuperResolutionShader.MODE_A)
		set(value) = prefs.edit { putEnumValue(KEY_VIDEO_SUPER_RES_QUALITY_SHADER, value) }

	var videoSuperResolutionBalancedShader: VideoSuperResolutionShader
		get() = prefs.getEnumValue(KEY_VIDEO_SUPER_RES_BALANCED_SHADER, VideoSuperResolutionShader.MODE_B)
		set(value) = prefs.edit { putEnumValue(KEY_VIDEO_SUPER_RES_BALANCED_SHADER, value) }

	var videoSuperResolutionPerformanceShader: VideoSuperResolutionShader
		get() = prefs.getEnumValue(KEY_VIDEO_SUPER_RES_PERFORMANCE_SHADER, VideoSuperResolutionShader.MODE_C)
		set(value) = prefs.edit { putEnumValue(KEY_VIDEO_SUPER_RES_PERFORMANCE_SHADER, value) }

	var videoSuperResolutionCustomShaders: String
		get() = prefs.getString(KEY_VIDEO_SUPER_RES_CUSTOM_SHADERS, "") ?: ""
		set(value) = prefs.edit { putString(KEY_VIDEO_SUPER_RES_CUSTOM_SHADERS, value) }

	var videoDanmakuEnabled: Boolean
		get() = prefs.getBoolean(KEY_VIDEO_DANMAKU_ENABLED, false)
		set(value) = prefs.edit { putBoolean(KEY_VIDEO_DANMAKU_ENABLED, value) }

	var videoDanmakuSizePercent: Int
		get() = prefs.getInt(KEY_VIDEO_DANMAKU_SIZE, 100)
		set(value) = prefs.edit { putInt(KEY_VIDEO_DANMAKU_SIZE, value) }

	var videoDanmakuSpeedPercent: Int
		get() = prefs.getInt(KEY_VIDEO_DANMAKU_SPEED, 100)
		set(value) = prefs.edit { putInt(KEY_VIDEO_DANMAKU_SPEED, value) }

	var videoDanmakuOpacityPercent: Int
		get() = prefs.getInt(KEY_VIDEO_DANMAKU_OPACITY, 100)
		set(value) = prefs.edit { putInt(KEY_VIDEO_DANMAKU_OPACITY, value) }

	var videoDanmakuStrokePercent: Int
		get() = prefs.getInt(KEY_VIDEO_DANMAKU_STROKE, 50)
		set(value) = prefs.edit { putInt(KEY_VIDEO_DANMAKU_STROKE, value) }

	var videoDanmakuShowScroll: Boolean
		get() = prefs.getBoolean(KEY_VIDEO_DANMAKU_SHOW_SCROLL, true)
		set(value) = prefs.edit { putBoolean(KEY_VIDEO_DANMAKU_SHOW_SCROLL, value) }

	var videoDanmakuShowTop: Boolean
		get() = prefs.getBoolean(KEY_VIDEO_DANMAKU_SHOW_TOP, true)
		set(value) = prefs.edit { putBoolean(KEY_VIDEO_DANMAKU_SHOW_TOP, value) }

	var videoDanmakuShowBottom: Boolean
		get() = prefs.getBoolean(KEY_VIDEO_DANMAKU_SHOW_BOTTOM, true)
		set(value) = prefs.edit { putBoolean(KEY_VIDEO_DANMAKU_SHOW_BOTTOM, value) }

	var videoDanmakuMaxScrollLines: Int
		get() = prefs.getInt(KEY_VIDEO_DANMAKU_MAX_SCROLL_LINES, 0)
		set(value) = prefs.edit { putInt(KEY_VIDEO_DANMAKU_MAX_SCROLL_LINES, value) }

	var videoDanmakuMaxTopLines: Int
		get() = prefs.getInt(KEY_VIDEO_DANMAKU_MAX_TOP_LINES, 0)
		set(value) = prefs.edit { putInt(KEY_VIDEO_DANMAKU_MAX_TOP_LINES, value) }

	var videoDanmakuMaxBottomLines: Int
		get() = prefs.getInt(KEY_VIDEO_DANMAKU_MAX_BOTTOM_LINES, 0)
		set(value) = prefs.edit { putInt(KEY_VIDEO_DANMAKU_MAX_BOTTOM_LINES, value) }

	var videoDanmakuMaxScreenNum: Int
		get() = prefs.getInt(KEY_VIDEO_DANMAKU_MAX_SCREEN_NUM, 0)
		set(value) = prefs.edit { putInt(KEY_VIDEO_DANMAKU_MAX_SCREEN_NUM, value) }

	var videoDanmakuSourceDanDan: Boolean
		get() = prefs.getBoolean(KEY_VIDEO_DANMAKU_SOURCE_DANDAN, true)
		set(value) = prefs.edit { putBoolean(KEY_VIDEO_DANMAKU_SOURCE_DANDAN, value) }

	var videoDanmakuSourceBilibili: Boolean
		get() = prefs.getBoolean(KEY_VIDEO_DANMAKU_SOURCE_BILIBILI, false)
		set(value) = prefs.edit { putBoolean(KEY_VIDEO_DANMAKU_SOURCE_BILIBILI, value) }

	var videoDanmakuSourceQq: Boolean
		get() = prefs.getBoolean(KEY_VIDEO_DANMAKU_SOURCE_QQ, false)
		set(value) = prefs.edit { putBoolean(KEY_VIDEO_DANMAKU_SOURCE_QQ, value) }

	var videoPlaybackSpeed: Float
		get() = prefs.getInt(KEY_VIDEO_PLAYBACK_SPEED, 100) / 100f
		set(value) = prefs.edit { putInt(KEY_VIDEO_PLAYBACK_SPEED, (value * 100).toInt()) }

	var videoDefaultSpeed: Float
		get() = prefs.getInt(KEY_VIDEO_DEFAULT_SPEED, 100) / 100f
		set(value) = prefs.edit { putInt(KEY_VIDEO_DEFAULT_SPEED, (value * 100).toInt()) }

	var videoSeekForwardMs: Int
		get() = prefs.getInt(KEY_VIDEO_SEEK_FORWARD_MS, 10_000)
		set(value) = prefs.edit { putInt(KEY_VIDEO_SEEK_FORWARD_MS, value) }

	var videoSeekBackwardMs: Int
		get() = prefs.getInt(KEY_VIDEO_SEEK_BACKWARD_MS, 10_000)
		set(value) = prefs.edit { putInt(KEY_VIDEO_SEEK_BACKWARD_MS, value) }

	var videoVolumeBoostEnabled: Boolean
		get() = prefs.getBoolean(KEY_VIDEO_VOLUME_BOOST, false)
		set(value) = prefs.edit { putBoolean(KEY_VIDEO_VOLUME_BOOST, value) }

	var videoAutoNextEnabled: Boolean
		get() = prefs.getBoolean(KEY_VIDEO_AUTO_NEXT, true)
		set(value) = prefs.edit { putBoolean(KEY_VIDEO_AUTO_NEXT, value) }

	var videoLandscapeSensorEnabled: Boolean
		get() = prefs.getBoolean(KEY_VIDEO_LANDSCAPE_SENSOR, false)
		set(value) = prefs.edit { putBoolean(KEY_VIDEO_LANDSCAPE_SENSOR, value) }

	var videoCacheSizeMb: Int
		get() = prefs.getInt(KEY_VIDEO_CACHE_MB, 1024)
		set(value) = prefs.edit { putInt(KEY_VIDEO_CACHE_MB, value) }

	var videoAspectRatio: Int
		get() = prefs.getInt(KEY_VIDEO_ASPECT_RATIO, 0)
		set(value) = prefs.edit { putInt(KEY_VIDEO_ASPECT_RATIO, value) }

	var videoDoubleTapSeekEnabled: Boolean
		get() = prefs.getBoolean(KEY_VIDEO_DOUBLE_TAP_SEEK_ENABLED, true)
		set(value) = prefs.edit { putBoolean(KEY_VIDEO_DOUBLE_TAP_SEEK_ENABLED, value) }

	var videoSubtitleFontSize: Float
		get() = prefs.getFloat(KEY_VIDEO_SUBTITLE_FONT_SIZE, 18f)
		set(value) = prefs.edit { putFloat(KEY_VIDEO_SUBTITLE_FONT_SIZE, value) }

	var videoSubtitleBold: Boolean
		get() = prefs.getBoolean(KEY_VIDEO_SUBTITLE_BOLD, false)
		set(value) = prefs.edit { putBoolean(KEY_VIDEO_SUBTITLE_BOLD, value) }

	var videoSubtitleItalic: Boolean
		get() = prefs.getBoolean(KEY_VIDEO_SUBTITLE_ITALIC, false)
		set(value) = prefs.edit { putBoolean(KEY_VIDEO_SUBTITLE_ITALIC, value) }

	var videoSubtitleTextColor: Int
		get() = prefs.getInt(KEY_VIDEO_SUBTITLE_TEXT_COLOR, android.graphics.Color.WHITE)
		set(value) = prefs.edit { putInt(KEY_VIDEO_SUBTITLE_TEXT_COLOR, value) }

	var videoSubtitleBorderColor: Int
		get() = prefs.getInt(KEY_VIDEO_SUBTITLE_BORDER_COLOR, android.graphics.Color.BLACK)
		set(value) = prefs.edit { putInt(KEY_VIDEO_SUBTITLE_BORDER_COLOR, value) }

	var videoSubtitleBorderSize: Float
		get() = prefs.getFloat(KEY_VIDEO_SUBTITLE_BORDER_SIZE, 8f)
		set(value) = prefs.edit { putFloat(KEY_VIDEO_SUBTITLE_BORDER_SIZE, value) }

	var videoSubtitleBgColor: Int
		get() = prefs.getInt(KEY_VIDEO_SUBTITLE_BG_COLOR, 0x66000000)
		set(value) = prefs.edit { putInt(KEY_VIDEO_SUBTITLE_BG_COLOR, value) }

	var videoSubtitleAlignX: Int
		get() = prefs.getInt(KEY_VIDEO_SUBTITLE_ALIGN_X, 1) // 0=left, 1=center, 2=right
		set(value) = prefs.edit { putInt(KEY_VIDEO_SUBTITLE_ALIGN_X, value) }

	var videoSubtitlePosition: Int
		get() = prefs.getInt(KEY_VIDEO_SUBTITLE_POSITION, 80)
		set(value) = prefs.edit { putInt(KEY_VIDEO_SUBTITLE_POSITION, value) }

	@get:FloatRange(0.3, 1.0)
	var videoControlsAlpha: Float
		get() = prefs.getInt(KEY_VIDEO_CONTROLS_ALPHA, 90) / 100f
		set(@FloatRange(0.3, 1.0) value) = prefs.edit { putInt(KEY_VIDEO_CONTROLS_ALPHA, (value * 100).toInt()) }

	var preferredVideoQuality: String
		get() = prefs.getString(KEY_VIDEO_PREFERRED_QUALITY, "1080p, 720p, 480p") ?: "1080p, 720p, 480p"
		set(value) = prefs.edit { putString(KEY_VIDEO_PREFERRED_QUALITY, value) }

	@get:FloatRange(0.0, 1.0)
	var videoGradientAlpha: Float
		get() = prefs.getInt(KEY_VIDEO_GRADIENT_ALPHA, 70) / 100f
		set(@FloatRange(0.0, 1.0) value) = prefs.edit { putInt(KEY_VIDEO_GRADIENT_ALPHA, (value * 100).toInt()) }

	val defaultReaderMode: ReaderMode
		get() = prefs.getEnumValue(KEY_READER_MODE, ReaderMode.STANDARD)

	val isReaderModeDetectionEnabled: Boolean
		get() = prefs.getBoolean(KEY_READER_MODE_DETECT, true)

	var isHistoryGroupingEnabled: Boolean
		get() = prefs.getBoolean(KEY_HISTORY_GROUPING, true)
		set(value) = prefs.edit { putBoolean(KEY_HISTORY_GROUPING, value) }

	var isUpdatedGroupingEnabled: Boolean
		get() = prefs.getBoolean(KEY_UPDATED_GROUPING, true)
		set(value) = prefs.edit { putBoolean(KEY_UPDATED_GROUPING, value) }

	var isFeedHeaderVisible: Boolean
		get() = prefs.getBoolean(KEY_FEED_HEADER, true)
		set(value) = prefs.edit { putBoolean(KEY_FEED_HEADER, value) }

	val progressIndicatorMode: ProgressIndicatorMode
		get() = prefs.getEnumValue(KEY_PROGRESS_INDICATORS, ProgressIndicatorMode.PERCENT_READ)

	enum class LoadingCircleStyle(val value: String) {
		THICK_STRAIGHT("thick_straight"),
		THICK_WAVY("thick_wavy"),
		THIN_STRAIGHT("thin_straight"),
		THIN_WAVY("thin_wavy");

		companion object {
			fun fromValue(value: String?): LoadingCircleStyle =
				entries.find { it.value == value } ?: THICK_STRAIGHT
		}
	}

	var loadingCircleStyle: LoadingCircleStyle
		get() = LoadingCircleStyle.fromValue(prefs.getString(KEY_LOADING_CIRCLE_STYLE, LoadingCircleStyle.THICK_STRAIGHT.value))
		set(value) = prefs.edit { putString(KEY_LOADING_CIRCLE_STYLE, value.value) }

	enum class BlurMode(val value: String) {
		STANDARD("standard"),
		IMMERSIVE("immersive"),
		ENHANCED("enhanced");

		companion object {
			fun fromValue(value: String?): BlurMode =
				entries.find { it.value == value } ?: STANDARD
		}
	}

	var blurMode: BlurMode
		get() = BlurMode.fromValue(prefs.getString(KEY_BLUR_MODE, BlurMode.STANDARD.value))
		set(value) = prefs.edit { putString(KEY_BLUR_MODE, value.value) }

	var incognitoModeForNsfw: TriStateOption
		get() = prefs.getEnumValue(KEY_INCOGNITO_NSFW, TriStateOption.ASK)
		set(value) = prefs.edit { putEnumValue(KEY_INCOGNITO_NSFW, value) }

	var isIncognitoModeEnabled: Boolean
		get() = prefs.getBoolean(KEY_INCOGNITO_MODE, false)
		set(value) = prefs.edit { putBoolean(KEY_INCOGNITO_MODE, value) }

	val isReaderMultiTaskEnabled: Boolean
		get() = prefs.getBoolean(KEY_READER_MULTITASK, false)

	var isChaptersReverse: Boolean
		get() = prefs.getBoolean(KEY_REVERSE_CHAPTERS, false)
		set(value) = prefs.edit { putBoolean(KEY_REVERSE_CHAPTERS, value) }

	var isChaptersGridView: Boolean
		get() = prefs.getBoolean(KEY_GRID_VIEW_CHAPTERS, false)
		set(value) = prefs.edit { putBoolean(KEY_GRID_VIEW_CHAPTERS, value) }

	val zoomMode: ZoomMode
		get() = prefs.getEnumValue(KEY_ZOOM_MODE, ZoomMode.FIT_CENTER)

	val trackSources: Set<String>
		get() = prefs.getStringSet(KEY_TRACK_SOURCES, null) ?: setOf(TRACK_FAVOURITES)

	var appPassword: String?
		get() = prefs.getString(KEY_APP_PASSWORD, null)
		set(value) = prefs.edit {
			if (value != null) putString(KEY_APP_PASSWORD, value) else remove(KEY_APP_PASSWORD)
		}

	var isAppPasswordNumeric: Boolean
		get() = prefs.getBoolean(KEY_APP_PASSWORD_NUMERIC, false)
		set(value) = prefs.edit { putBoolean(KEY_APP_PASSWORD_NUMERIC, value) }

	val searchSuggestionTypes: Set<SearchSuggestionType>
		get() = prefs.getStringSet(KEY_SEARCH_SUGGESTION_TYPES, null)?.let { stringSet ->
			stringSet.mapNotNullTo(EnumSet.noneOf(SearchSuggestionType::class.java)) { x ->
				enumValueOf<SearchSuggestionType>(x)
			}
		} ?: EnumSet.allOf(SearchSuggestionType::class.java)

	var isBiometricProtectionEnabled: Boolean
		get() = prefs.getBoolean(KEY_PROTECT_APP_BIOMETRIC, true)
		set(value) = prefs.edit { putBoolean(KEY_PROTECT_APP_BIOMETRIC, value) }

	val isMirrorSwitchingEnabled: Boolean
		get() = prefs.getBoolean(KEY_MIRROR_SWITCHING, false)

	val isExitConfirmationEnabled: Boolean
		get() = prefs.getBoolean(KEY_EXIT_CONFIRM, false)

	val isDynamicShortcutsEnabled: Boolean
		get() = prefs.getBoolean(KEY_SHORTCUTS, true)

	val isUnstableUpdatesAllowed: Boolean
		get() = prefs.getBoolean(KEY_UPDATES_UNSTABLE, false)

	val isPagesTabEnabled: Boolean
		get() = prefs.getBoolean(KEY_PAGES_TAB, true)

	val defaultDetailsTab: Int
		get() = if (isPagesTabEnabled) {
			val raw = prefs.getString(KEY_DETAILS_TAB, null)?.toIntOrNull() ?: -1
			if (raw == -1) {
				lastDetailsTab
			} else {
				raw
			}.coerceIn(0, 2)
		} else {
			0
		}

	var lastDetailsTab: Int
		get() = prefs.getInt(KEY_DETAILS_LAST_TAB, 0)
		set(value) = prefs.edit { putInt(KEY_DETAILS_LAST_TAB, value) }

	val isContentPrefetchEnabled: Boolean
		get() {
			if (isBackgroundNetworkRestricted()) {
				return false
			}
			val policy =
				NetworkPolicy.from(prefs.getString(KEY_PREFETCH_CONTENT, null), NetworkPolicy.NEVER)
			return policy.isNetworkAllowed(connectivityManager)
		}

	var sourcesSortOrder: SourcesSortOrder
		get() = prefs.getEnumValue(KEY_SOURCES_ORDER, SourcesSortOrder.MANUAL)
		set(value) = prefs.edit { putEnumValue(KEY_SOURCES_ORDER, value) }

	var isSourcesGroupedByLanguage: Boolean
		get() = prefs.getBoolean(KEY_SOURCES_GROUPED_BY_LANGUAGE, false)
		set(value) = prefs.edit { putBoolean(KEY_SOURCES_GROUPED_BY_LANGUAGE, value) }

	var isSourcesGridMode: Boolean
		get() = prefs.getBoolean(KEY_SOURCES_GRID, true)
		set(value) = prefs.edit { putBoolean(KEY_SOURCES_GRID, value) }

	var sourcesVersion: Int
		get() = prefs.getInt(KEY_SOURCES_VERSION, 0)
		set(value) = prefs.edit { putInt(KEY_SOURCES_VERSION, value) }

	var isAllSourcesEnabled: Boolean
		get() = prefs.getBoolean(KEY_SOURCES_ENABLED_ALL, false)
		set(value) = prefs.edit { putBoolean(KEY_SOURCES_ENABLED_ALL, value) }

	var isExtensionsFilterLangEnabled: Boolean
		get() = prefs.getBoolean(KEY_EXTENSIONS_FILTER_LANG, false)
		set(value) = prefs.edit { putBoolean(KEY_EXTENSIONS_FILTER_LANG, value) }

	var isExtensionsGridMode: Boolean
		get() = prefs.getBoolean(KEY_EXTENSIONS_GRID, false)
		set(value) = prefs.edit { putBoolean(KEY_EXTENSIONS_GRID, value) }

	var isKotatsuSourcesEnabled: Boolean
		get() = prefs.getBoolean(KEY_ENABLE_KOTATSU_SOURCES, true)
		set(value) = prefs.edit { putBoolean(KEY_ENABLE_KOTATSU_SOURCES, value) }

	var isShowBrokenSources: Boolean
		get() = prefs.getBoolean(KEY_SHOW_BROKEN_SOURCES, false)
		set(value) = prefs.edit { putBoolean(KEY_SHOW_BROKEN_SOURCES, value) }

	val isPagesNumbersEnabled: Boolean
		get() = prefs.getBoolean(KEY_PAGES_NUMBERS, false)

	var isReaderTranslationEnabled: Boolean
		get() = prefs.getBoolean(KEY_READER_TRANSLATION_ENABLED, false)
		set(value) = prefs.edit { putBoolean(KEY_READER_TRANSLATION_ENABLED, value) }

	var isReaderTranslationShowTranslated: Boolean
		get() = prefs.getBoolean(KEY_READER_TRANSLATION_SHOW_TRANSLATED, true)
		set(value) = prefs.edit { putBoolean(KEY_READER_TRANSLATION_SHOW_TRANSLATED, value) }

	val isReaderTranslationDebugLogsEnabled: Boolean
		get() = prefs.getBoolean(KEY_READER_TRANSLATION_DEBUG_LOGS, false)

	var isReaderTranslationQualityFilterEnabled: Boolean
		get() = prefs.getBoolean(KEY_READER_TRANSLATION_QUALITY_FILTER_ENABLED, true)
		set(value) = prefs.edit { putBoolean(KEY_READER_TRANSLATION_QUALITY_FILTER_ENABLED, value) }

	var readerTranslationSourceLanguage: String
		get() = prefs.getString(KEY_READER_TRANSLATION_SOURCE_LANG, "auto") ?: "auto"
		set(value) = prefs.edit { putString(KEY_READER_TRANSLATION_SOURCE_LANG, value) }

	var readerTranslationTargetLanguage: String
		get() = prefs.getString(KEY_READER_TRANSLATION_TARGET_LANG, "zh") ?: "zh"
		set(value) = prefs.edit { putString(KEY_READER_TRANSLATION_TARGET_LANG, value) }

	val readerTranslationOcrEngine: ReaderOcrEngine
		get() = prefs.getEnumValue(KEY_READER_TRANSLATION_OCR_ENGINE, ReaderOcrEngine.MLKIT)

	val readerTranslationMode: ReaderTranslationMode
		get() = prefs.getEnumValue(KEY_READER_TRANSLATION_MODE, ReaderTranslationMode.LOCAL_FIRST)

	val readerTranslationPipelineMode: org.skepsun.kototoro.core.prefs.ReaderTranslationPipelineMode
		get() = prefs.getEnumValue(KEY_READER_TRANSLATION_PIPELINE_MODE, org.skepsun.kototoro.core.prefs.ReaderTranslationPipelineMode.TWO_STAGE)

	val readerTranslationApiEndpoint: String
		get() = prefs.getString(KEY_READER_TRANSLATION_API_ENDPOINT, "") ?: ""

	val readerTranslationApiKey: String
		get() = prefs.getString(KEY_READER_TRANSLATION_API_KEY, "") ?: ""

	val readerTranslationApiModel: String
		get() = prefs.getString(KEY_READER_TRANSLATION_API_MODEL, "gpt-4o-mini") ?: "gpt-4o-mini"

	val readerTranslationApiProviderPreset: String
		get() = prefs.getString(KEY_READER_TRANSLATION_API_PROVIDER_PRESET, "CUSTOM") ?: "CUSTOM"

	val readerE2eApiEndpoint: String
		get() = prefs.getString(KEY_READER_E2E_API_ENDPOINT, "") ?: ""

	val readerE2eApiKey: String
		get() = prefs.getString(KEY_READER_E2E_API_KEY, "") ?: ""

	val readerE2eApiModel: String
		get() = prefs.getString(KEY_READER_E2E_API_MODEL, "gemini-2.0-flash") ?: "gemini-2.0-flash"

	val readerE2eApiProviderPreset: String
		get() = prefs.getString(KEY_READER_E2E_API_PROVIDER_PRESET, "GEMINI") ?: "GEMINI"

	val readerE2eApiConcurrency: Int
		get() = prefs.getString(KEY_READER_E2E_API_CONCURRENCY, "3")?.toIntOrNull() ?: 3

	val readerTranslationBubbleGroupingTuning: String
		get() = prefs.getString(KEY_READER_TRANSLATION_BUBBLE_GROUPING_TUNING, "BALANCED") ?: "BALANCED"

	val readerTranslationOcrPipelineStrategy: String
		get() = prefs.getString(KEY_READER_TRANSLATION_OCR_PIPELINE_STRATEGY, "HYBRID") ?: "HYBRID"

	var isReaderTranslationBubbleDetectorEnabled: Boolean
		get() = prefs.getBoolean(KEY_READER_TRANSLATION_BUBBLE_DETECTOR_ENABLED, true)
		set(value) = prefs.edit { putBoolean(KEY_READER_TRANSLATION_BUBBLE_DETECTOR_ENABLED, value) }

	var isReaderTranslationBubbleGroupingEnabled: Boolean
		get() = prefs.getBoolean(KEY_READER_TRANSLATION_BUBBLE_GROUPING_ENABLED, true)
		set(value) = prefs.edit { putBoolean(KEY_READER_TRANSLATION_BUBBLE_GROUPING_ENABLED, value) }

	val readerTranslationOverlayCompactness: String
		get() = prefs.getString(KEY_READER_TRANSLATION_OVERLAY_COMPACTNESS, "BALANCED") ?: "BALANCED"

	val readerTranslationPaddleModelPath: String
		get() = prefs.getString(KEY_READER_TRANSLATION_PADDLE_MODEL_PATH, "") ?: ""

	val readerTranslationPaddleOfficialModelId: String
		get() = prefs.getString(KEY_READER_TRANSLATION_PADDLE_OFFICIAL_MODEL_ID, "") ?: ""

	val readerTranslationPaddleDetModelId: String
		get() = prefs.getString(KEY_READER_TRANSLATION_PADDLE_DET_MODEL_ID, "MLKIT") ?: "MLKIT"

	val readerTranslationPaddleModelUrl: String
		get() = prefs.getString(KEY_READER_TRANSLATION_PADDLE_MODEL_URL, null) 
			?: context.getString(R.string.reader_translation_paddle_model_url_default)

	val readerTranslationPaddleModelVersion: String
		get() = prefs.getString(KEY_READER_TRANSLATION_PADDLE_MODEL_VERSION, null)
			?: context.getString(R.string.reader_translation_paddle_model_version_default)

	val readerTranslationPaddleModelSha256: String
		get() = prefs.getString(KEY_READER_TRANSLATION_PADDLE_MODEL_SHA256, null)
			?: context.getString(R.string.reader_translation_paddle_model_sha256_default)

	val readerTranslationPaddleDetModelUrl: String
		get() = prefs.getString(KEY_READER_TRANSLATION_PADDLE_DET_MODEL_URL, "") ?: ""

	val readerTranslationPaddleDetModelVersion: String
		get() = prefs.getString(KEY_READER_TRANSLATION_PADDLE_DET_MODEL_VERSION, "") ?: ""

	val readerTranslationPaddleDetModelSha256: String
		get() = prefs.getString(KEY_READER_TRANSLATION_PADDLE_DET_MODEL_SHA256, "") ?: ""

	val readerTranslationPaddleRecModelUrl: String
		get() = prefs.getString(KEY_READER_TRANSLATION_PADDLE_REC_MODEL_URL, "") ?: ""

	val readerTranslationPaddleRecModelVersion: String
		get() = prefs.getString(KEY_READER_TRANSLATION_PADDLE_REC_MODEL_VERSION, "") ?: ""

	val readerTranslationPaddleRecModelSha256: String
		get() = prefs.getString(KEY_READER_TRANSLATION_PADDLE_REC_MODEL_SHA256, "") ?: ""

	val readerTranslationPaddleClsModelUrl: String
		get() = prefs.getString(KEY_READER_TRANSLATION_PADDLE_CLS_MODEL_URL, "") ?: ""

	val readerTranslationPaddleClsModelVersion: String
		get() = prefs.getString(KEY_READER_TRANSLATION_PADDLE_CLS_MODEL_VERSION, "") ?: ""

	val readerTranslationPaddleClsModelSha256: String
		get() = prefs.getString(KEY_READER_TRANSLATION_PADDLE_CLS_MODEL_SHA256, "") ?: ""

	var readerTranslationBubbleYoloUrl: String
		get() = prefs.getString(KEY_READER_TRANSLATION_BUBBLE_YOLO_URL, "") ?: ""
		set(value) = prefs.edit { putString(KEY_READER_TRANSLATION_BUBBLE_YOLO_URL, value) }

	var readerTranslationOnnxModelId: String
		get() = prefs.getString(KEY_READER_TRANSLATION_ONNX_MODEL_ID, "") ?: ""
		set(value) = prefs.edit { putString(KEY_READER_TRANSLATION_ONNX_MODEL_ID, value) }

	var readerTranslationBubbleDetectorModelId: String
		get() = prefs.getString(KEY_READER_TRANSLATION_BUBBLE_DETECTOR_MODEL_ID, "AUTO") ?: "AUTO"
		set(value) = prefs.edit { putString(KEY_READER_TRANSLATION_BUBBLE_DETECTOR_MODEL_ID, value) }

	fun getReaderTranslationBubbleDetectorNmsKey(modelId: String): String {
		return "reader_translation_bubble_detector_nms_${modelId.replace(Regex("[^a-zA-Z0-9]"), "_")}"
	}

	fun getBubbleDetectorNms(modelId: String, defaultIsDetr: Boolean): Float {
		val key = getReaderTranslationBubbleDetectorNmsKey(modelId)
		val defaultVal = if (defaultIsDetr) 85 else 45
		return prefs.getInt(key, defaultVal) / 100f
	}

	fun setBubbleDetectorNms(modelId: String, value: Float) {
		val key = getReaderTranslationBubbleDetectorNmsKey(modelId)
		prefs.edit { putInt(key, (value * 100).toInt()) }
	}

	var readerThreads: Int
		get() = prefs.getInt(KEY_READER_THREADS, 3)
		set(value) = prefs.edit { putInt(KEY_READER_THREADS, value.coerceIn(1, 10)) }

	var readerPrefetchLimit: Int
		get() = prefs.getInt(KEY_READER_PREFETCH_LIMIT, 6)
		set(value) = prefs.edit { putInt(KEY_READER_PREFETCH_LIMIT, value.coerceIn(1, 20)) }

	val screenshotsPolicy: ScreenshotsPolicy
		get() = prefs.getEnumValue(KEY_SCREENSHOTS_POLICY, ScreenshotsPolicy.ALLOW)

	val isAdBlockEnabled: Boolean
		get() = prefs.getBoolean(KEY_ADBLOCK, false)

	var userSpecifiedContentDirectories: Set<File>
		get() {
			val set = prefs.getStringSet(KEY_LOCAL_MANGA_DIRS, emptySet()).orEmpty()
			return set.mapNotNullToSet { File(it).takeIfReadable() }
		}
		set(value) {
			val set = value.mapToSet { it.absolutePath }
			prefs.edit { putStringSet(KEY_LOCAL_MANGA_DIRS, set) }
		}

	var mangaStorageDir: File?
		get() = prefs.getString(KEY_LOCAL_STORAGE, null)?.let {
			File(it)
		}?.takeIf { it.exists() && it in userSpecifiedContentDirectories }
		set(value) = prefs.edit {
			if (value == null) {
				remove(KEY_LOCAL_STORAGE)
			} else {
				val userDirs = userSpecifiedContentDirectories
				if (value !in userDirs) {
					userSpecifiedContentDirectories = userDirs + value
				}
				putString(KEY_LOCAL_STORAGE, value.path)
			}
		}

	var allowDownloadOnMeteredNetwork: TriStateOption
		get() = prefs.getEnumValue(KEY_DOWNLOADS_METERED_NETWORK, TriStateOption.ASK)
		set(value) = prefs.edit { putEnumValue(KEY_DOWNLOADS_METERED_NETWORK, value) }

	val preferredDownloadFormat: DownloadFormat
		get() = prefs.getEnumValue(KEY_DOWNLOADS_FORMAT, DownloadFormat.AUTOMATIC)

	var isDownloadAlignedWithReader: Boolean
		get() = prefs.getBoolean(KEY_DOWNLOADS_ALIGN_READER, false)
		set(value) = prefs.edit { putBoolean(KEY_DOWNLOADS_ALIGN_READER, value) }

	var isDownloadAutoRetryOnNetworkError: Boolean
		get() = prefs.getBoolean(KEY_DOWNLOADS_AUTO_RETRY, false)
		set(value) = prefs.edit { putBoolean(KEY_DOWNLOADS_AUTO_RETRY, value) }

	var downloadThreads: Int
		get() = prefs.getInt(KEY_DOWNLOADS_THREADS, readerThreads).coerceIn(1, 10)
		set(value) = prefs.edit { putInt(KEY_DOWNLOADS_THREADS, value.coerceIn(1, 10)) }

	var downloadRequestDelayMs: Int
		get() = prefs.getInt(KEY_DOWNLOADS_REQUEST_DELAY, DOWNLOADS_REQUEST_DELAY_DEFAULT).coerceIn(0, 5000)
		set(value) = prefs.edit { putInt(KEY_DOWNLOADS_REQUEST_DELAY, value.coerceIn(0, 5000)) }

	var downloadRetryCount: Int
		get() = prefs.getInt(KEY_DOWNLOADS_RETRY_COUNT, DOWNLOADS_RETRY_COUNT_DEFAULT).coerceIn(1, 10)
		set(value) = prefs.edit { putInt(KEY_DOWNLOADS_RETRY_COUNT, value.coerceIn(1, 10)) }

	var downloadRetryDelayMs: Int
		get() = prefs.getInt(KEY_DOWNLOADS_RETRY_DELAY, DOWNLOADS_RETRY_DELAY_DEFAULT).coerceIn(500, 10_000)
		set(value) = prefs.edit { putInt(KEY_DOWNLOADS_RETRY_DELAY, value.coerceIn(500, 10_000)) }

	var downloadChapterDelay: Int
		get() = prefs.getInt(KEY_DOWNLOADS_CHAPTER_DELAY, 0).coerceIn(0, 10)
		set(value) = prefs.edit { putInt(KEY_DOWNLOADS_CHAPTER_DELAY, value.coerceIn(0, 10)) }

	var isSuggestionsEnabled: Boolean
		get() = prefs.getBoolean(KEY_SUGGESTIONS, false)
		set(value) = prefs.edit { putBoolean(KEY_SUGGESTIONS, value) }

	val isSuggestionsWiFiOnly: Boolean
		get() = prefs.getBoolean(KEY_SUGGESTIONS_WIFI_ONLY, false)

	val isSuggestionsExcludeNsfw: Boolean
		get() = prefs.getBoolean(KEY_SUGGESTIONS_EXCLUDE_NSFW, false)

	val isSuggestionsIncludeDisabledSources: Boolean
		get() = prefs.getBoolean(KEY_SUGGESTIONS_DISABLED_SOURCES, false)

	val isSuggestionsNotificationAvailable: Boolean
		get() = prefs.getBoolean(KEY_SUGGESTIONS_NOTIFICATIONS, false)

	val suggestionsTagsBlacklist: Set<String>
		get() {
			val string = prefs.getString(KEY_SUGGESTIONS_EXCLUDE_TAGS, null)?.trimEnd(' ', ',')
			if (string.isNullOrEmpty()) {
				return emptySet()
			}
			return string.split(',').mapToSet { it.trim() }
		}

	val suggestionsTagsWhitelist: Set<String>
		get() {
			val string = prefs.getString(KEY_SUGGESTIONS_PREFERRED_TAGS, null)?.trimEnd(' ', ',')
			if (string.isNullOrEmpty()) {
				return emptySet()
			}
			return string.split(',').mapToSet { it.trim() }
		}

	val isReaderBarEnabled: Boolean
		get() = prefs.getBoolean(KEY_READER_BAR, true)

	val isReaderBarTransparent: Boolean
		get() = prefs.getBoolean(KEY_READER_BAR_TRANSPARENT, true)

	val isReaderChapterToastEnabled: Boolean
		get() = prefs.getBoolean(KEY_READER_CHAPTER_TOAST, true)

	var isReaderSuperResolutionEnabled: Boolean
		get() = prefs.getBoolean(KEY_READER_SUPER_RESOLUTION_ENABLED, false)
		set(value) = prefs.edit().putBoolean(KEY_READER_SUPER_RESOLUTION_ENABLED, value).apply()

	val readerSuperResolutionEngine: String
		get() = prefs.getString(KEY_READER_SUPER_RESOLUTION_ENGINE, "ANIME4K") ?: "ANIME4K"

	val readerSuperResolutionAnime4kMode: String
		get() = prefs.getString(KEY_READER_SUPER_RESOLUTION_ANIME4K_MODE, "ANIME4K_A") ?: "ANIME4K_A"

	val readerSuperResolutionModel: String
		get() = prefs.getString(KEY_READER_SUPER_RESOLUTION_MODEL, "SE") ?: "SE"

	val readerSuperResolutionNoiseLevel: Int
		get() = prefs.getString(KEY_READER_SUPER_RESOLUTION_NOISE_LEVEL, "-1")?.toIntOrNull() ?: -1

	val readerSuperResolutionCacheLimitMb: Int
		get() = prefs.getString(KEY_READER_SUPER_RESOLUTION_CACHE_LIMIT, "512")?.toIntOrNull() ?: 512

	val isReaderKeepScreenOn: Boolean
		get() = prefs.getBoolean(KEY_READER_SCREEN_ON, true)

	var readerColorFilter: ReaderColorFilter?
		get() = runCatching {
			ReaderColorFilter(
				brightness = prefs.getFloat(KEY_CF_BRIGHTNESS, ReaderColorFilter.EMPTY.brightness),
				contrast = prefs.getFloat(KEY_CF_CONTRAST, ReaderColorFilter.EMPTY.contrast),
				isInverted = prefs.getBoolean(KEY_CF_INVERTED, ReaderColorFilter.EMPTY.isInverted),
				isGrayscale = prefs.getBoolean(KEY_CF_GRAYSCALE, ReaderColorFilter.EMPTY.isGrayscale),
				isBookBackground = prefs.getBoolean(KEY_CF_BOOK, ReaderColorFilter.EMPTY.isBookBackground),
			).takeUnless { it.isEmpty }
		}.getOrNull()
		set(value) {
			prefs.edit {
				if (value != null) {
					putFloat(KEY_CF_BRIGHTNESS, value.brightness)
					putFloat(KEY_CF_CONTRAST, value.contrast)
					putBoolean(KEY_CF_INVERTED, value.isInverted)
					putBoolean(KEY_CF_GRAYSCALE, value.isGrayscale)
					putBoolean(KEY_CF_BOOK, value.isBookBackground)
				} else {
					remove(KEY_CF_BRIGHTNESS)
					remove(KEY_CF_CONTRAST)
					remove(KEY_CF_INVERTED)
					remove(KEY_CF_GRAYSCALE)
					remove(KEY_CF_BOOK)
				}
			}
		}

	val imagesProxy: Int
		get() {
			val raw = prefs.getString(KEY_IMAGES_PROXY, null)?.toIntOrNull()
			return raw ?: if (prefs.getBoolean(KEY_IMAGES_PROXY_OLD, false)) 0 else -1
		}

	val dnsOverHttps: DoHProvider
		get() = prefs.getEnumValue(KEY_DOH, DoHProvider.NONE)

	val dohCustomUrl: String?
		get() = prefs.getString(KEY_DOH_CUSTOM_URL, null)?.nullIfEmpty()

	val dohCustomIps: String?
		get() = prefs.getString(KEY_DOH_CUSTOM_IPS, null)?.nullIfEmpty()

	var isSSLBypassEnabled: Boolean
		get() = prefs.getBoolean(KEY_SSL_BYPASS, false)
		set(value) = prefs.edit { putBoolean(KEY_SSL_BYPASS, value) }

	val proxyType: Proxy.Type
		get() {
			val raw = prefs.getString(KEY_PROXY_TYPE, null) ?: return Proxy.Type.DIRECT
			return enumValues<Proxy.Type>().find { it.name == raw } ?: Proxy.Type.DIRECT
		}

	val proxyAddress: String?
		get() = prefs.getString(KEY_PROXY_ADDRESS, null)

	val proxyPort: Int
		get() = prefs.getString(KEY_PROXY_PORT, null)?.toIntOrNull() ?: 0

	val proxyLogin: String?
		get() = prefs.getString(KEY_PROXY_LOGIN, null)?.nullIfEmpty()

	val proxyPassword: String?
		get() = prefs.getString(KEY_PROXY_PASSWORD, null)?.nullIfEmpty()

	var localListOrder: SortOrder
		get() = prefs.getEnumValue(KEY_LOCAL_LIST_ORDER, SortOrder.NEWEST)
		set(value) = prefs.edit { putEnumValue(KEY_LOCAL_LIST_ORDER, value) }

	var historySortOrder: ListSortOrder
		get() = prefs.getEnumValue(KEY_HISTORY_ORDER, ListSortOrder.LAST_READ)
		set(value) = prefs.edit { putEnumValue(KEY_HISTORY_ORDER, value) }

	var allFavoritesSortOrder: ListSortOrder
		get() = prefs.getEnumValue(KEY_FAVORITES_ORDER, ListSortOrder.NEWEST)
		set(value) = prefs.edit { putEnumValue(KEY_FAVORITES_ORDER, value) }

	val isRelatedContentEnabled: Boolean
		get() = prefs.getBoolean(KEY_RELATED_MANGA, true)

	val isWebtoonZoomEnabled: Boolean
		get() = prefs.getBoolean(KEY_WEBTOON_ZOOM, true)

	var isWebtoonGapsEnabled: Boolean
		get() = prefs.getBoolean(KEY_WEBTOON_GAPS, false)
		set(value) = prefs.edit { putBoolean(KEY_WEBTOON_GAPS, value) }

	var isWebtoonPullGestureEnabled: Boolean
		get() = prefs.getBoolean(KEY_WEBTOON_PULL_GESTURE, false)
		set(value) = prefs.edit { putBoolean(KEY_WEBTOON_PULL_GESTURE, value) }


	@get:FloatRange(from = 0.0, to = 0.5)
	val defaultWebtoonZoomOut: Float
		get() = prefs.getInt(KEY_WEBTOON_ZOOM_OUT, 0).coerceIn(0, 50) / 100f

	@get:FloatRange(from = 0.0, to = 1.0)
	var readerAutoscrollSpeed: Float
		get() = prefs.getFloat(KEY_READER_AUTOSCROLL_SPEED, 0f)
		set(@FloatRange(from = 0.0, to = 1.0) value) = prefs.edit {
			putFloat(
				KEY_READER_AUTOSCROLL_SPEED,
				value,
			)
		}

	var isReaderAutoscrollFabVisible: Boolean
		get() = prefs.getBoolean(KEY_READER_AUTOSCROLL_FAB, true)
		set(value) = prefs.edit { putBoolean(KEY_READER_AUTOSCROLL_FAB, value) }

	val isPagesPreloadEnabled: Boolean
		get() {
			if (isBackgroundNetworkRestricted()) {
				return false
			}
			val policy = NetworkPolicy.from(
				prefs.getString(KEY_PAGES_PRELOAD, null),
				NetworkPolicy.NON_METERED,
			)
			return policy.isNetworkAllowed(connectivityManager)
		}

	val is32BitColorsEnabled: Boolean
		get() = prefs.getBoolean(KEY_32BIT_COLOR, false)

	val isDiscordRpcEnabled: Boolean
		get() = prefs.getBoolean(KEY_DISCORD_RPC, false)

	val isDiscordRpcSkipNsfw: Boolean
		get() = prefs.getBoolean(KEY_DISCORD_RPC_SKIP_NSFW, false)

	var discordToken: String?
		get() = prefs.getString(KEY_DISCORD_TOKEN, null)?.trim()?.nullIfEmpty()
		set(value) = prefs.edit { putString(KEY_DISCORD_TOKEN, value?.nullIfEmpty()) }

	val isPeriodicalBackupEnabled: Boolean
		get() = isBackupWebDavUploadEnabled

	val periodicalBackupFrequency: Float
		get() = prefs.getString(KEY_BACKUP_PERIODICAL_FREQUENCY, null)?.toFloatOrNull() ?: 7f

	val periodicalBackupFrequencyMillis: Long
		get() = (TimeUnit.DAYS.toMillis(1) * periodicalBackupFrequency).toLong()

	val periodicalBackupMaxCount: Int
		get() = if (prefs.getBoolean(KEY_BACKUP_PERIODICAL_TRIM, true)) {
			prefs.getInt(KEY_BACKUP_PERIODICAL_COUNT, 10)
		} else {
			Int.MAX_VALUE
		}

	var periodicalBackupDirectory: Uri?
		get() = prefs.getString(KEY_BACKUP_PERIODICAL_OUTPUT, null)?.toUriOrNull()
		set(value) = prefs.edit { putString(KEY_BACKUP_PERIODICAL_OUTPUT, value?.toString()) }

	val isBackupTelegramUploadEnabled: Boolean
		get() = prefs.getBoolean(KEY_BACKUP_TG_ENABLED, false)

	val backupTelegramChatId: String?
		get() = prefs.getString(KEY_BACKUP_TG_CHAT, null)?.nullIfEmpty()

	// WebDAV backup settings
	val isBackupWebDavUploadEnabled: Boolean
		get() = prefs.getBoolean(KEY_BACKUP_WEBDAV_ENABLED, false)

	// 是否在上传到 WebDAV 后保留本地副本
	val isBackupWebDavKeepLocalCopyEnabled: Boolean
		get() = prefs.getBoolean(KEY_BACKUP_WEBDAV_KEEP_LOCAL_COPY, true)

	val backupWebDavServerUrl: String?
		get() = prefs.getString(KEY_BACKUP_WEBDAV_URL, null)?.trim()?.nullIfEmpty()

	val backupWebDavUsername: String?
		get() = prefs.getString(KEY_BACKUP_WEBDAV_USERNAME, null)?.trim()?.nullIfEmpty()

	val backupWebDavPassword: String?
		get() = prefs.getString(KEY_BACKUP_WEBDAV_PASSWORD, null)?.nullIfEmpty()

	val backupWebDavRemotePath: String?
		get() = prefs.getString(KEY_BACKUP_WEBDAV_PATH, null)?.trim()?.nullIfEmpty()

	// 是否启用数据自动同步（监听数据变更并自动上传至 WebDAV）
	val isBackupWebDavAutoSyncEnabled: Boolean
		get() = prefs.getBoolean(KEY_BACKUP_WEBDAV_AUTO_SYNC, false)

	// 数据版本号（用于版本化命名与兼容性判断）
	var backupWebDavDataVersion: Int
		get() = prefs.getInt(KEY_BACKUP_WEBDAV_DATA_VERSION, 1)
		set(value) = prefs.edit { putInt(KEY_BACKUP_WEBDAV_DATA_VERSION, value) }

	val isBackupWebDavAutoRestoreEnabled: Boolean
		get() = prefs.getBoolean(KEY_BACKUP_WEBDAV_AUTO_RESTORE, false)

	var backupWebDavLastRestoreTime: Long
		get() = prefs.getLong(KEY_BACKUP_WEBDAV_LAST_RESTORE_TIME, 0L)
		set(value) = prefs.edit { putLong(KEY_BACKUP_WEBDAV_LAST_RESTORE_TIME, value) }

	var backupWebDavLastUploadTime: Long
		get() = prefs.getLong(KEY_BACKUP_WEBDAV_LAST_UPLOAD_TIME, 0L)
		set(value) = prefs.edit { putLong(KEY_BACKUP_WEBDAV_LAST_UPLOAD_TIME, value) }

    // 自动恢复最近一次“检查”的时间（不一定发生了恢复，仅记录检查节流）
    var backupWebDavLastAutoRestoreCheckTime: Long
        get() = prefs.getLong(KEY_BACKUP_WEBDAV_LAST_AUTO_RESTORE_CHECK_TIME, 0L)
        set(value) = prefs.edit { putLong(KEY_BACKUP_WEBDAV_LAST_AUTO_RESTORE_CHECK_TIME, value) }

	// 最近一次 WebDAV 上传类型："auto"（自动）或 "manual"（手动）
	var backupWebDavLastUploadKind: String?
		get() = prefs.getString(KEY_BACKUP_WEBDAV_LAST_UPLOAD_KIND, null)
		set(value) = prefs.edit { putString(KEY_BACKUP_WEBDAV_LAST_UPLOAD_KIND, value) }

	var backupWebDavLastManualRestoreTime: Long
		get() = prefs.getLong(KEY_BACKUP_WEBDAV_LAST_MANUAL_RESTORE_TIME, 0L)
		set(value) = prefs.edit { putLong(KEY_BACKUP_WEBDAV_LAST_MANUAL_RESTORE_TIME, value) }

	val isReadingTimeEstimationEnabled: Boolean
		get() = prefs.getBoolean(KEY_READING_TIME, true)

	val isPagesSavingAskEnabled: Boolean
		get() = prefs.getBoolean(KEY_PAGES_SAVE_ASK, true)

	val isStatsEnabled: Boolean
		get() = prefs.getBoolean(KEY_STATS_ENABLED, false)

	val isAutoLocalChaptersCleanupEnabled: Boolean
		get() = prefs.getBoolean(KEY_CHAPTERS_CLEAR_AUTO, false)

	fun isPagesCropEnabled(mode: ReaderMode): Boolean {
		val rawValue = prefs.getStringSet(KEY_READER_CROP, emptySet())
		if (rawValue.isNullOrEmpty()) {
			return false
		}
		val needle = if (mode == ReaderMode.WEBTOON) READER_CROP_WEBTOON else READER_CROP_PAGED
		return needle.toString() in rawValue
	}

	fun isTipEnabled(tip: String): Boolean {
		return prefs.getStringSet(KEY_TIPS_CLOSED, emptySet())?.contains(tip) != true
	}

	fun closeTip(tip: String) {
		val closedTips = prefs.getStringSet(KEY_TIPS_CLOSED, emptySet()).orEmpty()
		if (tip in closedTips) {
			return
		}
		prefs.edit { putStringSet(KEY_TIPS_CLOSED, closedTips + tip) }
	}

	fun isIncognitoModeEnabled(isNsfw: Boolean): Boolean {
		return isIncognitoModeEnabled || (isNsfw && incognitoModeForNsfw == TriStateOption.ENABLED)
	}

	fun getPagesSaveDir(context: Context): DocumentFile? =
		prefs.getString(KEY_PAGES_SAVE_DIR, null)?.toUriOrNull()?.let {
			DocumentFile.fromTreeUri(context, it)?.takeIf { it.canWrite() }
		}

	fun setPagesSaveDir(uri: Uri?) {
		prefs.edit { putString(KEY_PAGES_SAVE_DIR, uri?.toString()) }
	}

	fun getContentListBadges(): Int {
		val raw = prefs.getStringSet(KEY_MANGA_LIST_BADGES, mangaListBadgesDefault).orEmpty()
		var result = 0
		for (item in raw) {
			result = result or item.toInt()
		}
		return result
	}

	fun subscribe(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
		prefs.registerOnSharedPreferenceChangeListener(listener)
	}

	fun unsubscribe(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
		prefs.unregisterOnSharedPreferenceChangeListener(listener)
	}

	fun observeChanges() = prefs.observeChanges()

	fun observe(vararg keys: String): Flow<String?> = prefs.observeChanges()
		.filter { key -> key == null || key in keys }
		.onStart { emit(null) }
		.flowOn(Dispatchers.IO)

	fun getAllValues(): Map<String, *> = prefs.all

	fun upsertAll(m: Map<String, *>) = prefs.edit {
		clear()
		putAll(m)
	}

	private fun String.toUriOrNull(): Uri? = if (isBlank()) null else Uri.parse(this)

	private fun File.takeIfReadable(): File? = takeIf { canRead() }

	private fun <E : Enum<E>> SharedPreferences.getEnumValue(key: String, defaultValue: E): E {
		val raw = getString(key, null) ?: return defaultValue
		return defaultValue.javaClass.enumConstants?.firstOrNull { it.name == raw } ?: defaultValue
	}

	private fun <E : Enum<E>> SharedPreferences.Editor.putEnumValue(key: String, value: E?) {
		putString(key, value?.name)
	}

	private fun SharedPreferences.observeChanges(): Flow<String?> = callbackFlow {
		val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
			trySend(key)
		}
		registerOnSharedPreferenceChangeListener(listener)
		awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
	}

	private fun SharedPreferences.Editor.putAll(values: Map<String, *>) {
		values.forEach { (key, value) ->
			when (value) {
				is Boolean -> putBoolean(key, value)
				is Int -> putInt(key, value)
				is Long -> putLong(key, value)
				is Float -> putFloat(key, value)
				is String -> putString(key, value)
				is Set<*> -> {
					@Suppress("UNCHECKED_CAST")
					putStringSet(key, value as? Set<String>)
				}
			}
		}
	}
	
	/**
	 * Get the selected browse group tab ID
	 */
	fun getSelectedGroupTab(): String? {
		return prefs.getString(KEY_SELECTED_GROUP_TAB, null)
	}
	
	/**
	 * Set the selected browse group tab ID
	 */
	fun setSelectedGroupTab(tabId: String) {
		prefs.edit { putString(KEY_SELECTED_GROUP_TAB, tabId) }
	}
	
	/**
	 * Get the selected source filter ID
	 */
	fun getSelectedSourceFilter(): String? {
		return prefs.getString(KEY_SELECTED_SOURCE_FILTER, null)
	}
	
	/**
	 * Set the selected source filter ID
	 */
	fun setSelectedSourceFilter(filterId: String) {
		prefs.edit { putString(KEY_SELECTED_SOURCE_FILTER, filterId) }
	}

	/**
	 * Get the selected source tags (comma-separated) for browse page
	 */
	fun getSelectedSourceTags(): Set<String> {
		val raw = prefs.getString(KEY_SELECTED_SOURCE_TAGS, null) ?: return emptySet()
		return raw.split(",").mapNotNull { it.trim().takeIf { part -> part.isNotEmpty() } }.toSet()
	}

	/**
	 * Set the selected source tags for browse page
	 */
	fun setSelectedSourceTags(tags: Set<String>) {
		val value = tags.joinToString(separator = ",")
		prefs.edit { putString(KEY_SELECTED_SOURCE_TAGS, value) }
	}
	
	/**
	 * Get the selected adult filter ID for browse page
	 */
	fun getSelectedAdultFilter(): String? {
		return prefs.getString(KEY_SELECTED_ADULT_FILTER, null)
	}
	
	/**
	 * Set the selected adult filter ID for browse page
	 */
	fun setSelectedAdultFilter(filterId: String) {
		prefs.edit { putString(KEY_SELECTED_ADULT_FILTER, filterId) }
	}

	private fun isBackgroundNetworkRestricted(): Boolean {
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			connectivityManager.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
		} else {
			false
		}
	}

	companion object {

		const val KEY_SEARCH_BAR_FILTER_HIDDEN = "search_filter_hidden"
		const val KEY_SOURCES_GROUPED_BY_LANGUAGE = "sources_grouped_by_language"

		const val TRACK_HISTORY = "history"
		const val TRACK_FAVOURITES = "favourites"

		const val KEY_ADBLOCK = "adblock"
		const val KEY_LIST_MODE = "list_mode_2"
		const val KEY_LIST_MODE_HISTORY = "list_mode_history"
		const val KEY_LIST_MODE_FAVORITES = "list_mode_favorites"
		const val KEY_LIST_MODE_SUGGESTIONS = "list_mode_suggestions"
		const val KEY_THEME = "theme"
		const val KEY_COLOR_THEME = "color_theme"
		const val KEY_THEME_AMOLED = "amoled_theme"
		const val KEY_OFFLINE_DISABLED = "no_offline"
		const val KEY_PAGES_CACHE_CLEAR = "pages_cache_clear"
		const val KEY_VIDEO_CACHE_CLEAR = "video_cache_clear"
		const val KEY_HTTP_CACHE_CLEAR = "http_cache_clear"
		const val KEY_COOKIES_CLEAR = "cookies_clear"
		const val KEY_CHAPTERS_CLEAR = "chapters_clear"
		const val KEY_CHAPTERS_CLEAR_AUTO = "chapters_clear_auto"
		const val KEY_THUMBS_CACHE_CLEAR = "thumbs_cache_clear"
		const val KEY_SEARCH_HISTORY_CLEAR = "search_history_clear"
		const val KEY_UPDATES_FEED_CLEAR = "updates_feed_clear"
		const val KEY_GRID_SIZE = "grid_size"
		const val KEY_GRID_SIZE_PAGES = "grid_size_pages"
		const val KEY_REMOTE_SOURCES = "remote_sources"
		const val KEY_LOCAL_STORAGE = "local_storage"
		const val KEY_READER_DOUBLE_PAGES = "reader_double_pages"
		const val KEY_READER_DOUBLE_PAGES_SENSITIVITY = "reader_double_pages_sensitivity_2"
		const val KEY_READER_DOUBLE_FOLDABLE = "reader_double_foldable"
		const val KEY_READER_SPLIT_PAGES = "reader_split_pages"
		const val KEY_READER_ZOOM_BUTTONS = "reader_zoom_buttons"
		const val KEY_READER_CONTROL_LTR = "reader_taps_ltr"
		const val KEY_READER_NAVIGATION_INVERTED = "reader_navigation_inverted"
		const val KEY_READER_FULLSCREEN = "reader_fullscreen"
		const val KEY_READER_VOLUME_BUTTONS = "reader_volume_buttons"
		const val KEY_READER_ORIENTATION = "reader_orientation"
		const val KEY_TRACKER_ENABLED = "tracker_enabled"
		const val KEY_TRACKER_WIFI_ONLY = "tracker_wifi"
		const val KEY_TRACKER_FREQUENCY = "tracker_freq"
		const val KEY_TRACK_SOURCES = "track_sources"
		const val KEY_TRACK_CATEGORIES = "track_categories"
		const val KEY_TRACK_WARNING = "track_warning"
		const val KEY_TRACKER_NOTIFICATIONS = "tracker_notifications"
		const val KEY_PREFERRED_TRACKING_SITE = "preferred_tracking_site"
		const val KEY_TRACKER_NO_NSFW = "tracker_no_nsfw"
		const val KEY_TRACKER_DOWNLOAD = "tracker_download"
		const val KEY_NOTIFICATIONS_SETTINGS = "notifications_settings"
		const val KEY_NOTIFICATIONS_SOUND = "notifications_sound"
		const val KEY_NOTIFICATIONS_VIBRATE = "notifications_vibrate"
		const val KEY_NOTIFICATIONS_LIGHT = "notifications_light"
		const val KEY_NOTIFICATIONS_INFO = "tracker_notifications_info"
		const val KEY_READER_ANIMATION = "reader_animation2"
		const val KEY_READER_CONTROLS = "reader_controls"
		const val KEY_READER_MODE = "reader_mode"
		const val KEY_READER_MODE_DETECT = "reader_mode_detect"
		const val KEY_READER_CROP = "reader_crop"
		const val KEY_APP_PASSWORD = "app_password"
		const val KEY_APP_PASSWORD_NUMERIC = "app_password_num"
		const val KEY_PROTECT_APP = "protect_app"
		const val KEY_PROTECT_APP_BIOMETRIC = "protect_app_bio"
		const val KEY_ZOOM_MODE = "zoom_mode"
		const val KEY_BACKUP = "backup"
		const val KEY_RESTORE = "restore"
		const val KEY_BACKUP_PERIODICAL_ENABLED = "backup_periodic"
		const val KEY_BACKUP_PERIODICAL_FREQUENCY = "backup_periodic_freq"
		const val KEY_BACKUP_PERIODICAL_TRIM = "backup_periodic_trim"
		const val KEY_BACKUP_PERIODICAL_COUNT = "backup_periodic_count"
		const val KEY_BACKUP_PERIODICAL_OUTPUT = "backup_periodic_output"
		const val KEY_BACKUP_PERIODICAL_LAST = "backup_periodic_last"
		const val KEY_HISTORY_GROUPING = "history_grouping"
		const val KEY_UPDATED_GROUPING = "updated_grouping"
		const val KEY_PROGRESS_INDICATORS = "progress_indicators"
		const val KEY_REVERSE_CHAPTERS = "reverse_chapters"
		const val KEY_GRID_VIEW_CHAPTERS = "grid_view_chapters"
		const val KEY_INCOGNITO_NSFW = "incognito_nsfw"
		const val KEY_PAGES_NUMBERS = "pages_numbers"
		const val KEY_READER_TRANSLATION_ENABLED = "reader_translation_enabled"
		const val KEY_READER_TRANSLATION_SHOW_TRANSLATED = "reader_translation_show_translated"
		const val KEY_READER_TRANSLATION_DEBUG_LOGS = "reader_translation_debug_logs"
		const val KEY_READER_TRANSLATION_QUALITY_FILTER_ENABLED = "reader_translation_quality_filter_enabled"
		const val KEY_READER_TRANSLATION_SOURCE_LANG = "reader_translation_source_lang"
		const val KEY_READER_TRANSLATION_TARGET_LANG = "reader_translation_target_lang"
		const val KEY_READER_TRANSLATION_OCR_ENGINE = "reader_translation_ocr_engine"
		const val KEY_READER_TRANSLATION_MODE = "reader_translation_mode"
		const val KEY_READER_TRANSLATION_PIPELINE_MODE = "reader_translation_pipeline_mode"
		const val KEY_READER_TRANSLATION_API_ENDPOINT = "reader_translation_api_endpoint"
		const val KEY_READER_TRANSLATION_API_KEY = "reader_translation_api_key"
		const val KEY_READER_TRANSLATION_API_MODEL = "reader_translation_api_model"
		const val KEY_READER_TRANSLATION_API_PROVIDER_PRESET = "reader_translation_api_provider_preset"
		const val KEY_READER_TRANSLATION_API_FETCH_MODELS = "reader_translation_api_fetch_models"

		const val KEY_READER_E2E_API_ENDPOINT = "reader_e2e_api_endpoint"
		const val KEY_READER_E2E_API_KEY = "reader_e2e_api_key"
		const val KEY_READER_E2E_API_MODEL = "reader_e2e_api_model"
		const val KEY_READER_E2E_API_PROVIDER_PRESET = "reader_e2e_api_provider_preset"
		const val KEY_READER_E2E_API_FETCH_MODELS = "reader_e2e_api_fetch_models"
		const val KEY_READER_E2E_API_CONCURRENCY = "reader_e2e_api_concurrency"
		const val KEY_READER_TRANSLATION_OCR_PIPELINE_STRATEGY = "reader_translation_ocr_pipeline_strategy"
		const val KEY_READER_TRANSLATION_BUBBLE_GROUPING_TUNING = "reader_translation_bubble_grouping_tuning"
			const val KEY_READER_TRANSLATION_BUBBLE_DETECTOR_ENABLED = "reader_translation_bubble_detector_enabled"
			const val KEY_READER_TRANSLATION_BUBBLE_GROUPING_ENABLED = "reader_translation_bubble_grouping_enabled"
			const val KEY_READER_TRANSLATION_OVERLAY_COMPACTNESS = "reader_translation_overlay_compactness"
		const val KEY_READER_TRANSLATION_PADDLE_MODEL_PATH = "reader_translation_paddle_model_path"
		const val KEY_READER_TRANSLATION_PADDLE_OCR_ONLY = "reader_translation_paddle_ocr_only"
		const val KEY_READER_TRANSLATION_PADDLE_OFFICIAL_MODEL_ID = "reader_translation_paddle_official_model_id"
		const val KEY_READER_TRANSLATION_PADDLE_DET_MODEL_ID = "reader_translation_paddle_det_model_id"
		const val KEY_READER_TRANSLATION_PADDLE_MODEL_URL = "reader_translation_paddle_model_url"
		const val KEY_READER_TRANSLATION_PADDLE_MODEL_VERSION = "reader_translation_paddle_model_version"
		const val KEY_READER_TRANSLATION_PADDLE_MODEL_SHA256 = "reader_translation_paddle_model_sha256"
		const val KEY_READER_TRANSLATION_PADDLE_DET_MODEL_URL = "reader_translation_paddle_det_model_url"
		const val KEY_READER_TRANSLATION_PADDLE_DET_MODEL_VERSION = "reader_translation_paddle_det_model_version"
		const val KEY_READER_TRANSLATION_PADDLE_DET_MODEL_SHA256 = "reader_translation_paddle_det_model_sha256"
		const val KEY_READER_TRANSLATION_PADDLE_REC_MODEL_URL = "reader_translation_paddle_rec_model_url"
		const val KEY_READER_TRANSLATION_PADDLE_REC_MODEL_VERSION = "reader_translation_paddle_rec_model_version"
		const val KEY_READER_TRANSLATION_PADDLE_REC_MODEL_SHA256 = "reader_translation_paddle_rec_model_sha256"
		const val KEY_READER_TRANSLATION_PADDLE_CLS_MODEL_URL = "reader_translation_paddle_cls_model_url"
		const val KEY_READER_TRANSLATION_PADDLE_CLS_MODEL_VERSION = "reader_translation_paddle_cls_model_version"
		const val KEY_READER_TRANSLATION_PADDLE_CLS_MODEL_SHA256 = "reader_translation_paddle_cls_model_sha256"
		const val KEY_READER_TRANSLATION_PADDLE_DOWNLOAD_NOW = "reader_translation_paddle_download_now"
		const val KEY_READER_TRANSLATION_REC_DOWNLOAD_NOW = "reader_translation_rec_download_now"
		const val KEY_READER_TRANSLATION_ONNX_MODEL_ID = "reader_translation_onnx_model_id"
		const val KEY_READER_TRANSLATION_BUBBLE_DETECTOR_MODEL_ID = "reader_translation_bubble_detector_model_id"
		const val KEY_READER_TRANSLATION_BUBBLE_YOLO_URL = "reader_translation_bubble_yolo_url"
		const val KEY_SCREENSHOTS_POLICY = "screenshots_policy"
		const val KEY_READER_THREADS = "reader_threads"
		const val KEY_READER_PREFETCH_LIMIT = "reader_prefetch_limit"
		const val KEY_PAGES_PRELOAD = "pages_preload"
		const val KEY_SUGGESTIONS = "suggestions"
		const val KEY_SUGGESTIONS_WIFI_ONLY = "suggestions_wifi"
		const val KEY_SUGGESTIONS_EXCLUDE_NSFW = "suggestions_exclude_nsfw"
		const val KEY_SUGGESTIONS_EXCLUDE_TAGS = "suggestions_exclude_tags"
		const val KEY_SUGGESTIONS_PREFERRED_TAGS = "suggestions_preferred_tags"
		const val KEY_SUGGESTIONS_DISABLED_SOURCES = "suggestions_disabled_sources"
		const val KEY_SUGGESTIONS_NOTIFICATIONS = "suggestions_notifications"
		const val KEY_SHIKIMORI = "shikimori"
		const val KEY_ANILIST = "anilist"
		const val KEY_MAL = "mal"
		const val KEY_KITSU = "kitsu"
		const val KEY_BANGUMI = "bangumi"
		const val KEY_MANGAUPDATES = "mangaupdates"
		const val KEY_DOWNLOADS_METERED_NETWORK = "downloads_metered_network"
		const val KEY_DOWNLOADS_FORMAT = "downloads_format"
		const val KEY_DOWNLOADS_ALIGN_READER = "downloads_align_reader"
		const val KEY_DOWNLOADS_AUTO_RETRY = "downloads_auto_retry"
		const val KEY_DOWNLOADS_THREADS = "downloads_threads"
		const val KEY_DOWNLOADS_REQUEST_DELAY = "downloads_request_delay"
		const val KEY_DOWNLOADS_RETRY_COUNT = "downloads_retry_count"
		const val KEY_DOWNLOADS_RETRY_DELAY = "downloads_retry_delay"
		const val KEY_DOWNLOADS_CHAPTER_DELAY = "downloads_chapter_delay"
		const val KEY_ALL_FAVOURITES_VISIBLE = "all_favourites_visible"
		const val KEY_DOH = "doh"
		const val KEY_DOH_CUSTOM_URL = "doh_custom_url"
		const val KEY_DOH_CUSTOM_IPS = "doh_custom_ips"
		const val KEY_EXIT_CONFIRM = "exit_confirm"
		const val KEY_INCOGNITO_MODE = "incognito"
		const val KEY_READER_MULTITASK = "reader_multitask"
		const val KEY_SYNC = "sync"
		const val KEY_SYNC_SETTINGS = "sync_settings"
		const val KEY_READER_BAR = "reader_bar"
		const val KEY_READER_BAR_TRANSPARENT = "reader_bar_transparent"
		const val KEY_READER_CHAPTER_TOAST = "reader_chapter_toast"
		const val KEY_READER_SUPER_RESOLUTION_ENABLED = "reader_super_resolution_enabled"
		const val KEY_READER_SUPER_RESOLUTION_ENGINE = "reader_super_resolution_engine"
		const val KEY_READER_SUPER_RESOLUTION_ANIME4K_MODE = "reader_super_resolution_anime4k_mode"
		const val KEY_READER_SUPER_RESOLUTION_MODEL = "reader_super_resolution_model"
		const val KEY_READER_SUPER_RESOLUTION_NOISE_LEVEL = "reader_super_resolution_noise_level"
		const val KEY_READER_SUPER_RESOLUTION_CACHE_LIMIT = "reader_super_resolution_cache_limit"
		const val KEY_READER_BACKGROUND = "reader_background"
		const val KEY_VIDEO_DECODER_MODE = "video_decoder_mode"
		const val KEY_VIDEO_RENDERER_MODE = "video_renderer_mode"
		const val KEY_VIDEO_BACKGROUND = "video_background"
		const val KEY_VIDEO_PREFERRED_QUALITY = "video_preferred_quality"
		const val KEY_VIDEO_SUPER_RES_MODE = "video_super_resolution_mode"
		const val KEY_VIDEO_SUPER_RES_SHADER = "video_super_resolution_shader"
		const val KEY_VIDEO_SUPER_RES_QUALITY_SHADER = "video_super_resolution_quality_shader"
		const val KEY_VIDEO_SUPER_RES_BALANCED_SHADER = "video_super_resolution_balanced_shader"
		const val KEY_VIDEO_SUPER_RES_PERFORMANCE_SHADER = "video_super_resolution_performance_shader"
		const val KEY_VIDEO_SUPER_RES_CUSTOM_SHADERS = "video_super_resolution_custom_shaders"
		const val KEY_VIDEO_DANMAKU_ENABLED = "video_danmaku_enabled"
		const val KEY_VIDEO_DANMAKU_SIZE = "video_danmaku_size"
		const val KEY_VIDEO_DANMAKU_SPEED = "video_danmaku_speed"
		const val KEY_VIDEO_DANMAKU_OPACITY = "video_danmaku_opacity"
		const val KEY_VIDEO_DANMAKU_STROKE = "video_danmaku_stroke"
		const val KEY_VIDEO_DANMAKU_SHOW_SCROLL = "video_danmaku_show_scroll"
		const val KEY_VIDEO_DANMAKU_SHOW_TOP = "video_danmaku_show_top"
		const val KEY_VIDEO_DANMAKU_SHOW_BOTTOM = "video_danmaku_show_bottom"
		const val KEY_VIDEO_DANMAKU_MAX_SCROLL_LINES = "video_danmaku_max_scroll_lines"
		const val KEY_VIDEO_DANMAKU_MAX_TOP_LINES = "video_danmaku_max_top_lines"
		const val KEY_VIDEO_DANMAKU_MAX_BOTTOM_LINES = "video_danmaku_max_bottom_lines"
		const val KEY_VIDEO_DANMAKU_MAX_SCREEN_NUM = "video_danmaku_max_screen_num"
		const val KEY_VIDEO_DANMAKU_SOURCE_DANDAN = "video_danmaku_source_dandan"
		const val KEY_VIDEO_DANMAKU_SOURCE_BILIBILI = "video_danmaku_source_bilibili"
		const val KEY_VIDEO_DANMAKU_SOURCE_QQ = "video_danmaku_source_qq"
		const val KEY_VIDEO_PLAYBACK_SPEED = "video_playback_speed"
		const val KEY_VIDEO_DEFAULT_SPEED = "video_default_speed"
		const val KEY_VIDEO_SEEK_FORWARD_MS = "video_seek_forward_ms"
		const val KEY_VIDEO_SEEK_BACKWARD_MS = "video_seek_backward_ms"

		const val KEY_VIDEO_SUBTITLE_FONT_SIZE = "video_subtitle_font_size"
		const val KEY_VIDEO_SUBTITLE_BOLD = "video_subtitle_bold"
		const val KEY_VIDEO_SUBTITLE_ITALIC = "video_subtitle_italic"
		const val KEY_VIDEO_SUBTITLE_TEXT_COLOR = "video_subtitle_text_color"
		const val KEY_VIDEO_SUBTITLE_BORDER_COLOR = "video_subtitle_border_color"
		const val KEY_VIDEO_SUBTITLE_BORDER_SIZE = "video_subtitle_border_size"
		const val KEY_VIDEO_SUBTITLE_BG_COLOR = "video_subtitle_bg_color"
		const val KEY_VIDEO_SUBTITLE_ALIGN_X = "video_subtitle_align_x"
		const val KEY_VIDEO_SUBTITLE_POSITION = "video_subtitle_position"
		const val KEY_VIDEO_VOLUME_BOOST = "video_volume_boost"
		const val KEY_VIDEO_AUTO_NEXT = "video_auto_next"
		const val KEY_VIDEO_LANDSCAPE_SENSOR = "video_landscape_sensor"
		const val KEY_VIDEO_CACHE_MB = "video_cache_mb"
		const val KEY_VIDEO_ASPECT_RATIO = "video_aspect_ratio"
		const val KEY_VIDEO_DOUBLE_TAP_SEEK_ENABLED = "video_double_tap_seek_enabled"
		const val KEY_VIDEO_CONTROLS_ALPHA = "video_controls_alpha"
		const val KEY_VIDEO_GRADIENT_ALPHA = "video_gradient_alpha"
		const val KEY_READER_SCREEN_ON = "reader_screen_on"
		const val KEY_SHORTCUTS = "dynamic_shortcuts"
		const val KEY_READER_TAP_ACTIONS = "reader_tap_actions"
		const val KEY_READER_OPTIMIZE = "reader_optimize"
		const val KEY_LOCAL_LIST_ORDER = "local_order"
		const val KEY_HISTORY_ORDER = "history_order"
		const val KEY_FAVORITES_ORDER = "fav_order"
		const val KEY_WEBTOON_GAPS = "webtoon_gaps"
		const val KEY_WEBTOON_ZOOM = "webtoon_zoom"
		const val KEY_WEBTOON_ZOOM_OUT = "webtoon_zoom_out"
		private const val DOWNLOADS_REQUEST_DELAY_DEFAULT = 1600
		private const val DOWNLOADS_RETRY_COUNT_DEFAULT = 5
		private const val DOWNLOADS_RETRY_DELAY_DEFAULT = 2000
		const val KEY_WEBTOON_PULL_GESTURE = "webtoon_pull_gesture"
		const val KEY_PREFETCH_CONTENT = "prefetch_content"
		const val KEY_APP_LOCALE = "app_locale"
		const val KEY_CONTENT_LANGUAGES = "content_languages"
		const val KEY_EXTENSION_LANGUAGES = "extension_languages"
		const val KEY_GITHUB_MIRROR = "github_mirror"
		const val KEY_HUGGINGFACE_MIRROR = "huggingface_mirror"
		const val KEY_TVBOX_ACTIVE_REPOSITORY = "tvbox_active_repository"
		const val KEY_TVBOX_ACTIVE_REPOSITORY_TITLE = "tvbox_active_repository_title"
		const val KEY_LNREADER_REPOS = "lnreader_repository_urls"
		const val KEY_SOURCES_GRID = "sources_grid"
		const val KEY_UPDATES_UNSTABLE = "updates_unstable"
		const val KEY_TIPS_CLOSED = "tips_closed"
		const val KEY_SSL_BYPASS = "ssl_bypass"
		const val KEY_READER_AUTOSCROLL_SPEED = "as_speed"
		const val KEY_READER_AUTOSCROLL_FAB = "as_fab"
		const val KEY_MIRROR_SWITCHING = "mirror_switching"
		const val KEY_PROXY = "proxy"
		const val KEY_PROXY_TYPE = "proxy_type_2"
		const val KEY_PROXY_ADDRESS = "proxy_address"
		const val KEY_PROXY_PORT = "proxy_port"
		const val KEY_PROXY_AUTH = "proxy_auth"
		const val KEY_PROXY_LOGIN = "proxy_login"
		const val KEY_PROXY_PASSWORD = "proxy_password"
		const val KEY_IMAGES_PROXY = "images_proxy_2"
		const val KEY_LOCAL_MANGA_DIRS = "local_manga_dirs"
		const val KEY_HISTORY_EXCLUDE_NSFW = "history_exclude_nsfw"
		const val KEY_FAVOURITES_EXCLUDE_NSFW = "favourites_exclude_nsfw"
		const val KEY_DISABLE_NSFW = "no_nsfw"
		const val KEY_RELATED_MANGA = "related_manga"
		const val KEY_NAV_MAIN = "nav_main"
		const val KEY_NAV_LABELS = "nav_labels"
		const val KEY_NAV_PINNED = "nav_pinned"
		const val KEY_NAV_FLOATING = "nav_floating"
		const val KEY_NAV_HEIGHT = "nav_height"
		const val KEY_NAV_FLOATING_HEIGHT = "nav_floating_height"
		const val KEY_READER_TOOLBAR_FLOATING = "reader_toolbar_floating"
		const val KEY_LOADING_CIRCLE_STYLE = "loading_circle_style"
		const val KEY_BLUR_MODE = "blur_mode"
		const val KEY_MAIN_FAB = "main_fab"
		const val KEY_32BIT_COLOR = "enhanced_colors"
		const val KEY_SOURCES_ORDER = "sources_sort_order"
		const val KEY_SOURCES_CATALOG = "sources_catalog"
		const val KEY_CF_BRIGHTNESS = "cf_brightness"
		const val KEY_CF_CONTRAST = "cf_contrast"
		const val KEY_CF_INVERTED = "cf_inverted"
		const val KEY_CF_GRAYSCALE = "cf_grayscale"
		const val KEY_CF_BOOK = "cf_book"
		const val KEY_PAGES_TAB = "pages_tab"
		const val KEY_DETAILS_TAB = "details_tab"
		const val KEY_DETAILS_LAST_TAB = "details_last_tab"
		const val KEY_READING_TIME = "reading_time"
		const val KEY_PAGES_SAVE_DIR = "pages_dir"
		const val KEY_PAGES_SAVE_ASK = "pages_dir_ask"
		const val KEY_STATS_ENABLED = "stats_on"
		const val KEY_FEED_HEADER = "feed_header"
		const val KEY_SEARCH_SUGGESTION_TYPES = "search_suggest_types"
		const val KEY_SOURCES_VERSION = "sources_version"
		const val KEY_SOURCES_ENABLED_ALL = "sources_enabled_all"
		const val KEY_EXTENSIONS_FILTER_LANG = "extensions_filter_lang"
		const val KEY_EXTENSIONS_GRID = "extensions_grid"
		const val KEY_ENABLE_KOTATSU_SOURCES = "enable_kotatsu_sources"
		const val KEY_SHOW_BROKEN_SOURCES = "show_broken_sources"
		const val KEY_EXTENSIONS = "extensions"
		const val KEY_JSON_SOURCES = "json_sources"
		const val KEY_MIHON_EXTENSIONS = "mihon_extensions"
		const val KEY_ANIYOMI_EXTENSIONS = "aniyomi_extensions"
		const val KEY_QUICK_FILTER = "quick_filter"
		const val KEY_COLLAPSE_DESCRIPTION = "description_collapse"
		const val KEY_PANORAMA_ENABLED = "panorama_enabled"
		const val KEY_PANORAMA_BLUR = "panorama_blur"
		const val KEY_PANORAMA_EXTRA_HEIGHT = "panorama_extra_height"
		const val KEY_PANORAMA_BOTTOM_GRADIENT_ALPHA = "panorama_bottom_gradient_alpha"
		const val KEY_BACKUP_TG_ENABLED = "backup_periodic_tg_enabled"
		const val KEY_BACKUP_TG_CHAT = "backup_periodic_tg_chat_id"
		// WebDAV backup keys
		const val KEY_BACKUP_WEBDAV = "backup_periodic_webdav"
		const val KEY_BACKUP_WEBDAV_ENABLED = "backup_periodic_webdav_enabled"
		const val KEY_BACKUP_WEBDAV_URL = "backup_periodic_webdav_server_url"
		const val KEY_BACKUP_WEBDAV_USERNAME = "backup_periodic_webdav_username"
		const val KEY_BACKUP_WEBDAV_PASSWORD = "backup_periodic_webdav_password"
		const val KEY_BACKUP_WEBDAV_PATH = "backup_periodic_webdav_remote_path"
		const val KEY_BACKUP_WEBDAV_KEEP_LOCAL_COPY = "backup_periodic_webdav_keep_local_copy"
		const val KEY_BACKUP_WEBDAV_TEST = "backup_periodic_webdav_test"
		const val KEY_BACKUP_WEBDAV_UPLOAD_NOW = "backup_periodic_webdav_upload_now"
		const val KEY_BACKUP_WEBDAV_RESTORE_NOW = "backup_periodic_webdav_restore_now"
		const val KEY_BACKUP_WEBDAV_AUTO_RESTORE = "backup_periodic_webdav_auto_restore"
		const val KEY_BACKUP_WEBDAV_LAST_RESTORE_TIME = "backup_periodic_webdav_last_restore_time"
		const val KEY_BACKUP_WEBDAV_LAST_UPLOAD_TIME = "backup_periodic_webdav_last_upload_time"
		const val KEY_BACKUP_WEBDAV_LAST_AUTO_RESTORE_CHECK_TIME = "backup_periodic_webdav_last_auto_restore_check_time"
		const val KEY_BACKUP_WEBDAV_LAST_UPLOAD_KIND = "backup_periodic_webdav_last_upload_kind"
		const val KEY_BACKUP_WEBDAV_LAST_MANUAL_RESTORE_TIME = "backup_periodic_webdav_last_manual_restore_time"
		const val KEY_BACKUP_WEBDAV_LAST_ACTIONS = "backup_periodic_webdav_last_actions"

		// WebDAV 自动同步与数据版本
		const val KEY_BACKUP_WEBDAV_AUTO_SYNC = "backup_periodic_webdav_auto_sync"
		const val KEY_BACKUP_WEBDAV_DATA_VERSION = "backup_periodic_webdav_data_version"

		const val KEY_BACKUP_WEBDAV_POLICY_NOTE = "backup_periodic_webdav_policy_note"
		const val KEY_MANGA_LIST_BADGES = "manga_list_badges"
		const val KEY_TAGS_WARNINGS = "tags_warnings"
		const val KEY_DISCORD_RPC = "discord_rpc"
		const val KEY_DISCORD_RPC_SKIP_NSFW = "discord_rpc_skip_nsfw"
		const val KEY_DISCORD_TOKEN = "discord_token"
		const val KEY_SELECTED_GROUP_TAB = "selected_group_tab"
		const val KEY_SELECTED_SOURCE_FILTER = "selected_source_filter"
		const val KEY_SELECTED_SOURCE_TAGS = "selected_source_tags"
		const val KEY_SELECTED_ADULT_FILTER = "selected_adult_filter"

		// keys for non-persistent preferences
		const val KEY_APP_VERSION = "app_version"
		const val KEY_IGNORE_DOZE = "ignore_dose"
		const val KEY_TRACKER_DEBUG = "tracker_debug"
		const val KEY_LINK_WEBLATE = "about_app_translation"
		const val KEY_LINK_DISCORD = "about_discord"
		const val KEY_LINK_GITHUB = "about_github"
		const val KEY_LINK_DONATE = "about_donate"
		const val KEY_LINK_MANUAL = "about_help"
		const val KEY_PROXY_TEST = "proxy_test"
		const val KEY_OPEN_BROWSER = "open_browser"
		const val KEY_HANDLE_LINKS = "handle_links"
		const val KEY_BACKUP_TG = "backup_periodic_tg"
		const val KEY_BACKUP_TG_OPEN = "backup_periodic_tg_open"
		const val KEY_BACKUP_TG_TEST = "backup_periodic_tg_test"
		const val KEY_CLEAR_MANGA_DATA = "manga_data_clear"
		const val KEY_STORAGE_USAGE = "storage_usage"
		const val KEY_WEBVIEW_CLEAR = "webview_clear"

		// old keys are for migration only
		private const val KEY_IMAGES_PROXY_OLD = "images_proxy"

		// values
		private const val READER_CROP_PAGED = 1
		private const val READER_CROP_WEBTOON = 2
		
		const val KEY_FILTER_PILL_DEFAULT = "filter_pill_default"
		const val KEY_FILTER_PILL_LEFT = "filter_pill_left"
		const val KEY_FILTER_PILL_RIGHT = "filter_pill_right"
	}

	// ==================== Video Intro/Outro Skip ====================

	private val skipPrefs by lazy {
		context.getSharedPreferences("video_skip_times", Context.MODE_PRIVATE)
	}

	fun getIntroEndMs(mangaId: Long): Long = skipPrefs.getLong("intro_end_$mangaId", 0L)

	fun setIntroEndMs(mangaId: Long, ms: Long) {
		skipPrefs.edit { putLong("intro_end_$mangaId", ms) }
	}

	fun clearIntroEndMs(mangaId: Long) {
		skipPrefs.edit { remove("intro_end_$mangaId") }
	}

	fun getOutroStartMs(mangaId: Long): Long = skipPrefs.getLong("outro_start_$mangaId", 0L)

	fun setOutroStartMs(mangaId: Long, ms: Long) {
		skipPrefs.edit { putLong("outro_start_$mangaId", ms) }
	}

	fun clearOutroStartMs(mangaId: Long) {
		skipPrefs.edit { remove("outro_start_$mangaId") }
	}

	// ==================== Filter Pill Settings ====================

	var filterPillDefaultType: org.skepsun.kototoro.parsers.model.ContentType
		get() {
			val name = prefs.getString(KEY_FILTER_PILL_DEFAULT, org.skepsun.kototoro.parsers.model.ContentType.MANGA.name) ?: org.skepsun.kototoro.parsers.model.ContentType.MANGA.name
			return try { enumValueOf(name) } catch (e: Exception) { org.skepsun.kototoro.parsers.model.ContentType.MANGA }
		}
		set(value) = prefs.edit { putString(KEY_FILTER_PILL_DEFAULT, value.name) }

	var filterPillSwipeLeftType: org.skepsun.kototoro.parsers.model.ContentType
		get() {
			val name = prefs.getString(KEY_FILTER_PILL_LEFT, org.skepsun.kototoro.parsers.model.ContentType.VIDEO.name) ?: org.skepsun.kototoro.parsers.model.ContentType.VIDEO.name
			return try { enumValueOf(name) } catch (e: Exception) { org.skepsun.kototoro.parsers.model.ContentType.VIDEO }
		}
		set(value) = prefs.edit { putString(KEY_FILTER_PILL_LEFT, value.name) }

	var filterPillSwipeRightType: org.skepsun.kototoro.parsers.model.ContentType
		get() {
			val name = prefs.getString(KEY_FILTER_PILL_RIGHT, org.skepsun.kototoro.parsers.model.ContentType.NOVEL.name) ?: org.skepsun.kototoro.parsers.model.ContentType.NOVEL.name
			return try { enumValueOf(name) } catch (e: Exception) { org.skepsun.kototoro.parsers.model.ContentType.NOVEL }
		}
		set(value) = prefs.edit { putString(KEY_FILTER_PILL_RIGHT, value.name) }
}
