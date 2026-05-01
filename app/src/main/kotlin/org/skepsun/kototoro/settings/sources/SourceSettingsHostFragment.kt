package org.skepsun.kototoro.settings.sources

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.parser.EmptyContentRepository
import org.skepsun.kototoro.core.parser.ParserContentRepository
import org.skepsun.kototoro.core.parser.kotatsu.KotatsuParserRepository
import org.skepsun.kototoro.core.util.ext.withArgs

@AndroidEntryPoint
class SourceSettingsHostFragment : Fragment() {

    private val viewModel: SourceSettingsViewModel by viewModels()
    private val containerId = View.generateViewId()

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return FrameLayout(requireContext()).apply {
            id = containerId
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState != null) {
            return
        }
        childFragmentManager.commit {
            setReorderingAllowed(true)
            replace(
                containerId,
                if (shouldUseComposeRoute()) {
                    SourceComposeSettingsFragment::class.java
                } else {
                    SourceSettingsFragment::class.java
                },
                arguments,
            )
        }
    }

    private fun shouldUseComposeRoute(): Boolean {
        return when (viewModel.repository) {
            is ParserContentRepository,
            is KotatsuParserRepository,
            is EmptyContentRepository,
            -> true

            else -> false
        }
    }

    companion object {

        fun newInstance(source: org.skepsun.kototoro.parsers.model.ContentSource) =
            SourceSettingsHostFragment().withArgs(1) {
                putString(AppRouter.KEY_SOURCE, source.name)
            }
    }
}
