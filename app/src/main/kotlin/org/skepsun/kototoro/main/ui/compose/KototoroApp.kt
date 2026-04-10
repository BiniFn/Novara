package org.skepsun.kototoro.main.ui.compose

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.core.ui.widgets.BottomNavState
import org.skepsun.kototoro.core.ui.widgets.KototoroBottomNav

@Composable
fun KototoroApp(
    navStateFlow: StateFlow<BottomNavState>,
    onNavItemSelected: (Int) -> Unit,
    onNavItemReselected: (Int) -> Unit,
    contentView: android.view.View? = null // Ignored for now
) {
    KototoroTheme {
		KototoroBottomNav(
			state = navStateFlow,
			onItemSelected = onNavItemSelected,
			onItemReselected = onNavItemReselected
		)
    }
}
