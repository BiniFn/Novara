package org.skepsun.kototoro.core.ui.widgets

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.core.prefs.observeAsState
import org.skepsun.kototoro.core.ui.BaseActivityEntryPoint
import org.skepsun.kototoro.core.ui.glass.GlassBottomBarContainer
import org.skepsun.kototoro.core.ui.glass.GlassDefaults
import org.skepsun.kototoro.core.ui.glass.GlassSurface
import dagger.hilt.android.EntryPointAccessors

@Immutable
private data class BottomNavPrefs(
    val isFloating: Boolean,
    val isLabelsVisible: Boolean,
    val navHeight: Int,
    val navFloatingHeight: Int,
)

@Composable
fun KototoroBottomNav(
    state: StateFlow<BottomNavState>,
    onItemSelected: (Int) -> Unit,
    onItemReselected: (Int) -> Unit
) {
    val navState by state.collectAsState()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val layoutDirection = LocalLayoutDirection.current
    val appSettings = remember {
        EntryPointAccessors.fromApplication<BaseActivityEntryPoint>(context.applicationContext).settings
    }

    val prefs by appSettings.observeAsState(
        AppSettings.KEY_NAV_FLOATING,
        AppSettings.KEY_NAV_LABELS,
        AppSettings.KEY_NAV_HEIGHT,
        AppSettings.KEY_NAV_FLOATING_HEIGHT,
    ) {
        BottomNavPrefs(
            isFloating = isNavFloating,
            isLabelsVisible = isNavLabelsVisible,
            navHeight = navHeight,
            navFloatingHeight = navFloatingHeight,
        )
    }
    val isFloating = prefs.isFloating
    val isLabelsVisible = prefs.isLabelsVisible
    val navHeight = prefs.navHeight
    val navFloatingHeight = prefs.navFloatingHeight

    val activeItems = navState.items.filter { navState.itemVisibility[it.id] != false }
    val useNavigationRail = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val systemBarsPadding = WindowInsets.systemBars.asPaddingValues()
    val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val railStartInset = systemBarsPadding.calculateStartPadding(layoutDirection)
    val railEndInset = systemBarsPadding.calculateEndPadding(layoutDirection)
    val railBottomInset = systemBarsPadding.calculateBottomPadding()

    val targetAlpha = 0.84f

    val horizontalPadding by androidx.compose.animation.core.animateDpAsState(
        if (isFloating && !useNavigationRail) 24.dp else 0.dp,
    )
    val verticalPadding by androidx.compose.animation.core.animateDpAsState(
        if (isFloating && !useNavigationRail) 16.dp else 0.dp,
    )
    val railHorizontalPadding by androidx.compose.animation.core.animateDpAsState(
        if (isFloating && useNavigationRail) 12.dp else 0.dp,
    )
    val railVerticalPadding by androidx.compose.animation.core.animateDpAsState(
        if (isFloating && useNavigationRail) 18.dp else 0.dp,
    )

    val navBarModifier = Modifier
        .then(
            if (useNavigationRail) {
                Modifier
                    .fillMaxHeight()
                    .padding(
                        start = railHorizontalPadding + railStartInset,
                        end = railHorizontalPadding + railEndInset,
                        top = railVerticalPadding + statusBarTopPadding,
                        bottom = railVerticalPadding + railBottomInset,
                    )
            } else {
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding)
                    .run { if (isFloating) navigationBarsPadding() else this }
            },
        )

    val currentExplicitHeight by androidx.compose.animation.core.animateDpAsState(
        if (isFloating) navFloatingHeight.dp else navHeight.dp
    )
    val railWidth = if (isFloating) 88.dp else 84.dp

    val navContainerStyle = if (isFloating) {
        GlassDefaults.prominentStyle().copy(
            containerAlpha = targetAlpha,
            borderAlpha = 0.10f,
            shadowElevation = 0.dp,
        )
    } else {
        GlassDefaults.regularStyle().copy(
            containerAlpha = (targetAlpha - 0.06f).coerceAtLeast(0.70f),
            borderAlpha = 0.10f,
            shadowElevation = 0.dp,
        )
    }

    if (useNavigationRail) {
        GlassBottomBarContainer(
            modifier = navBarModifier,
            style = navContainerStyle,
        ) {
            NavigationRail(
                containerColor = Color.Transparent,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(railWidth),
                windowInsets = WindowInsets(0),
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                activeItems.forEach { item ->
                    val isSelected = navState.selectedItemId == item.id
                    val badge = navState.badges[item.id]

                    NavigationRailItem(
                        selected = isSelected,
                        onClick = {
                            if (isSelected) onItemReselected(item.id) else onItemSelected(item.id)
                        },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (badge?.isVisible == true) {
                                        if (badge.number > 0) {
                                            Badge { Text(badge.number.toString()) }
                                        } else {
                                            Badge()
                                        }
                                    }
                                },
                            ) {
                                Icon(
                                    painter = getPremiumPainter(item.id, isSelected),
                                    contentDescription = stringResource(item.title),
                                )
                            }
                        },
                        label = if (isLabelsVisible) {
                            { Text(stringResource(item.title)) }
                        } else {
                            null
                        },
                        alwaysShowLabel = isLabelsVisible,
                        colors = NavigationRailItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    } else if (isFloating) {
        GlassBottomBarContainer(
            modifier = navBarModifier,
            style = navContainerStyle,
        ) {
            NavigationBar(
                containerColor = Color.Transparent,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(currentExplicitHeight),
                windowInsets = WindowInsets(0),
            ) {
                activeItems.forEach { item ->
                    val isSelected = navState.selectedItemId == item.id
                    val badge = navState.badges[item.id]

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            if (isSelected) onItemReselected(item.id) else onItemSelected(item.id)
                        },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (badge?.isVisible == true) {
                                        if (badge.number > 0) {
                                            Badge { Text(badge.number.toString()) }
                                        } else {
                                            Badge()
                                        }
                                    }
                                },
                            ) {
                                Icon(
                                    painter = getPremiumPainter(item.id, isSelected),
                                    contentDescription = stringResource(item.title),
                                )
                            }
                        },
                        label = if (isLabelsVisible) {
                            { Text(stringResource(item.title)) }
                        } else null,
                        alwaysShowLabel = isLabelsVisible,
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        }
    } else {
        GlassSurface(
            modifier = navBarModifier,
            style = navContainerStyle,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                topStart = 32.dp,
                topEnd = 32.dp,
                bottomStart = 0.dp,
                bottomEnd = 0.dp,
            ),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(currentExplicitHeight),
                    windowInsets = WindowInsets(0),
                ) {
                    activeItems.forEach { item ->
                        val isSelected = navState.selectedItemId == item.id
                        val badge = navState.badges[item.id]

                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) onItemReselected(item.id) else onItemSelected(item.id)
                            },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (badge?.isVisible == true) {
                                            if (badge.number > 0) {
                                                Badge { Text(badge.number.toString()) }
                                            } else {
                                                Badge()
                                            }
                                        }
                                    },
                                ) {
                                    Icon(
                                        painter = getPremiumPainter(item.id, isSelected),
                                        contentDescription = stringResource(item.title),
                                    )
                                }
                            },
                            label = if (isLabelsVisible) {
                                { Text(stringResource(item.title)) }
                            } else null,
                            alwaysShowLabel = isLabelsVisible,
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
                Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}

@Composable
private fun getPremiumPainter(itemId: Int, isSelected: Boolean): Painter {
    val resId = when (itemId) {
        R.id.nav_home -> R.drawable.ic_home
        R.id.nav_history -> R.drawable.ic_history
        R.id.nav_favorites -> if (isSelected) R.drawable.ic_heart else R.drawable.ic_heart_outline
        R.id.nav_explore -> if (isSelected) R.drawable.ic_explore_checked else R.drawable.ic_explore_normal
        R.id.nav_discover -> if (isSelected) R.drawable.ic_bangumi else R.drawable.ic_bangumi_outline
        R.id.nav_suggestions -> if (isSelected) R.drawable.ic_suggestion_checked else R.drawable.ic_suggestion
        R.id.nav_feed -> R.drawable.ic_feed
        R.id.nav_updated -> if (isSelected) R.drawable.ic_updated_checked else R.drawable.ic_updated
        R.id.nav_bookmarks -> if (isSelected) R.drawable.ic_bookmark_checked else R.drawable.ic_bookmark
        R.id.nav_local -> if (isSelected) R.drawable.ic_storage_checked else R.drawable.ic_storage
        else -> R.drawable.ic_home // fallback
    }
    return painterResource(id = resId)
}
