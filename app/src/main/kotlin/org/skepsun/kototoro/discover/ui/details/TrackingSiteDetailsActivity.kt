package org.skepsun.kototoro.discover.ui.details

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.BaseActivityEntryPoint
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.main.ui.protect.ScreenshotPolicyHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

@AndroidEntryPoint
class TrackingSiteDetailsActivity :
	AppCompatActivity(),
	ScreenshotPolicyHelper.ContentContainer {

	@Inject
	lateinit var settings: AppSettings

	private val viewModel: TrackingSiteDetailsViewModel by viewModels()

	override fun onCreate(savedInstanceState: Bundle?) {
		val entryPoint = EntryPointAccessors.fromApplication<BaseActivityEntryPoint>(applicationContext)
		val settingsSnapshot = entryPoint.settings
		setTheme(settingsSnapshot.colorScheme.styleResId)
		if (settingsSnapshot.isAmoledTheme) {
			setTheme(R.style.ThemeOverlay_Kototoro_Amoled)
		}
		enableEdgeToEdge()
		super.onCreate(savedInstanceState)
		setContent {
			KototoroTheme {
				TrackingSiteDetailsScreen(
					viewModel = viewModel,
					settings = settings,
					appRouter = router,
					onBackClick = { onBackPressedDispatcher.onBackPressed() },
				)
			}
		}
	}

	override fun isNsfwContent(): Flow<Boolean> = flowOf(false)
}
