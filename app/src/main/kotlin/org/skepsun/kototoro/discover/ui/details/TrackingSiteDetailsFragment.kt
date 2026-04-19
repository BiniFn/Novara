package org.skepsun.kototoro.discover.ui.details


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import javax.inject.Inject

@AndroidEntryPoint
class TrackingSiteDetailsFragment : Fragment() {

	@Inject
	lateinit var settings: AppSettings

	private val viewModel by viewModels<TrackingSiteDetailsViewModel>()

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View {
		return ComposeView(requireContext()).apply {
			setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
			setContent {
				KototoroTheme {
					TrackingSiteDetailsScreen(
						viewModel = viewModel,
						settings = settings,
						appRouter = router,
						onBackClick = { activity?.finish() },
					)
				}
			}
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		(activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.hide()
		(activity as? org.skepsun.kototoro.core.ui.FragmentContainerActivity)?.findViewById<View>(org.skepsun.kototoro.R.id.appbar)?.visibility = View.GONE
	}
}
