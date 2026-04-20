package org.skepsun.kototoro.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.network.BaseHttpClient
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.core.util.ext.getDisplayMessage
import org.skepsun.kototoro.core.util.ext.printStackTraceDebug
import org.skepsun.kototoro.core.util.ext.viewLifecycleScope
import org.skepsun.kototoro.parsers.util.await
import org.skepsun.kototoro.settings.compose.ProxySettingsScreen
import java.net.Proxy
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@AndroidEntryPoint
class ProxySettingsFragment : Fragment() {

    private var testJob: Job? = null
    private val testSummaryFlow = MutableStateFlow<String?>(null)
    private val isTestRunningFlow = MutableStateFlow(false)

    @Inject
    lateinit var appSettings: AppSettings

    @Inject
    @BaseHttpClient
    lateinit var okHttpClient: OkHttpClient

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val testSummary by testSummaryFlow.collectAsState()
                val isTestRunning by isTestRunningFlow.collectAsState()
                KototoroTheme {
                    ProxySettingsScreen(
                        settings = appSettings,
                        testSummary = testSummary,
                        isTestRunning = isTestRunning,
                        onTestConnection = {
                            testConnection()
                        },
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? SettingsActivity)?.setSectionTitle(getString(R.string.proxy))
    }

    private fun testConnection() {
        testJob?.cancel()
        testJob = viewLifecycleScope.launch {
            testSummaryFlow.value = getString(R.string.loading_)
            isTestRunningFlow.value = true
            try {
                withContext(Dispatchers.Default) {
                    val request = Request.Builder()
                        .get()
                        .url("http://neverssl.com")
                        .build()
                    okHttpClient.newCall(request).await().use { response ->
                        check(response.isSuccessful) { response.message }
                    }
                }
                showTestResult(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                e.printStackTraceDebug()
                showTestResult(e)
            } finally {
                isTestRunningFlow.value = false
                testSummaryFlow.value = null
            }
        }
    }

    private fun showTestResult(error: Throwable?) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.proxy)
            .setMessage(error?.getDisplayMessage(resources) ?: getString(R.string.connection_ok))
            .setPositiveButton(android.R.string.ok, null)
            .setCancelable(true)
            .show()
    }
}
