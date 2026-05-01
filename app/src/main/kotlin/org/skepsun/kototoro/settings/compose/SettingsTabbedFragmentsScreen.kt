package org.skepsun.kototoro.settings.compose

import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit

data class SettingsTabFragmentPage(
    val title: String,
    val tag: String,
    val createFragment: () -> Fragment,
)

@Composable
fun SettingsTabbedFragmentsScreen(
    pages: List<SettingsTabFragmentPage>,
    fragmentManager: FragmentManager,
    modifier: Modifier = Modifier,
) {
    if (pages.isEmpty()) return

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val containerId = remember { View.generateViewId() }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.fillMaxWidth(),
            ) {
                pages.forEachIndexed { index, page ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(text = page.title) },
                    )
                }
            }
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                factory = { context ->
                    FragmentContainerView(context).apply {
                        id = containerId
                    }
                },
                update = {
                    if (fragmentManager.isStateSaved) return@AndroidView
                    val selectedPage = pages[selectedTabIndex]
                    val currentFragment = fragmentManager.findFragmentByTag(selectedPage.tag)
                    val visibleFragment = fragmentManager.fragments.firstOrNull { fragment ->
                        fragment.view?.parent === it && fragment.isVisible && !fragment.isHidden
                    }
                    if (visibleFragment?.tag == selectedPage.tag && currentFragment != null) {
                        return@AndroidView
                    }
                    fragmentManager.commit {
                        setReorderingAllowed(true)
                        pages.forEachIndexed { index, page ->
                            val fragment = fragmentManager.findFragmentByTag(page.tag)
                            if (index == selectedTabIndex) {
                                if (fragment == null) {
                                    add(containerId, page.createFragment(), page.tag)
                                } else {
                                    show(fragment)
                                }
                            } else if (fragment != null) {
                                hide(fragment)
                            }
                        }
                    }
                },
            )
        }
    }
}
