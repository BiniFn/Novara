package org.skepsun.kototoro.download.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import java.lang.ref.WeakReference
import org.skepsun.kototoro.core.model.parcelable.ParcelableContent
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.download.ui.compose.DownloadDialog

class DownloadDialogFragment : DialogFragment() {

    private fun currentSnackbarHost(): View? = callbackSnackbarHost?.get()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val parcelableArray = arguments?.getParcelableArray(AppRouter.KEY_MANGA)
        val mangaList = parcelableArray?.mapNotNull { (it as? ParcelableContent)?.manga } ?: emptyList()

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val snackbarHostState = remember { SnackbarHostState() }
                KototoroTheme {
                    DownloadDialog(
                        mangaList = mangaList,
                        snackbarHostState = snackbarHostState,
                        snackbarHostView = currentSnackbarHost(),
                        onOpenDownloads = {
                            currentSnackbarHost()?.let { host ->
                                AppRouter.from(host)?.openDownloads()
                            }
                        },
                        onDismiss = { dismiss() }
                    )
                }
            }
        }
    }

    companion object {
        private var callbackSnackbarHost: WeakReference<View>? = null

        fun registerCallback(fm: FragmentManager, lifecycleOwner: LifecycleOwner, snackbarHost: View) {
            callbackSnackbarHost = WeakReference(snackbarHost)
        }

        fun unregisterCallback(fm: FragmentManager) {
            callbackSnackbarHost = null
        }
    }
}
