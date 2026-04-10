package org.skepsun.kototoro.main.ui.compose

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.view.ActionProvider
import android.view.ContextMenu
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import androidx.annotation.IdRes
import com.google.android.material.navigation.NavigationBarView
import kotlinx.coroutines.flow.MutableStateFlow
import org.skepsun.kototoro.core.prefs.NavItem
import org.skepsun.kototoro.core.ui.widgets.BottomNavState
import org.skepsun.kototoro.core.ui.widgets.BadgeInfo
import org.skepsun.kototoro.main.ui.AppNavBarDelegator

class ComposeAppNavBarDelegator(
    private val ctx: Context,
    val stateFlow: MutableStateFlow<BottomNavState>
) : AppNavBarDelegator {

    private var onItemSelectedListener: NavigationBarView.OnItemSelectedListener? = null
    private var onItemReselectedListener: NavigationBarView.OnItemReselectedListener? = null

    override var selectedItemId: Int
        get() = stateFlow.value.selectedItemId
        set(value) {
            stateFlow.value = stateFlow.value.copy(selectedItemId = value)
            onItemSelectedListener?.onNavigationItemSelected(StubMenuItem(value))
        }

    override var labelVisibilityMode: Int = 0

    override fun setOnItemSelectedListener(listener: NavigationBarView.OnItemSelectedListener?) {
        onItemSelectedListener = listener
    }

    override fun setOnItemReselectedListener(listener: NavigationBarView.OnItemReselectedListener?) {
        onItemReselectedListener = listener
    }

    fun handleItemSelected(id: Int) {
        if (stateFlow.value.selectedItemId == id) {
             onItemReselectedListener?.onNavigationItemReselected(StubMenuItem(id))
        } else {
             stateFlow.value = stateFlow.value.copy(selectedItemId = id)
             onItemSelectedListener?.onNavigationItemSelected(StubMenuItem(id))
        }
    }

    override fun isMenuEmpty(): Boolean = stateFlow.value.items.isEmpty()

    override fun setupMenu(items: List<NavItem>) {
        stateFlow.value = stateFlow.value.copy(items = items)
        if (items.isNotEmpty()) {
            stateFlow.value = stateFlow.value.copy(selectedItemId = items.first().id)
        }
    }

    override fun setBadgeNumber(itemId: Int, number: Int) {
        val badges = stateFlow.value.badges.toMutableMap()
        val current = badges[itemId] ?: BadgeInfo()
        badges[itemId] = current.copy(number = number, isVisible = true)
        stateFlow.value = stateFlow.value.copy(badges = badges)
    }

    override fun clearBadge(itemId: Int) {
        val badges = stateFlow.value.badges.toMutableMap()
        val current = badges[itemId] ?: BadgeInfo()
        badges[itemId] = current.copy(number = 0, isVisible = true)
        stateFlow.value = stateFlow.value.copy(badges = badges)
    }

    override fun setBadgeVisible(itemId: Int, isVisible: Boolean) {
        val badges = stateFlow.value.badges.toMutableMap()
        val current = badges[itemId] ?: BadgeInfo()
        badges[itemId] = current.copy(isVisible = isVisible)
        stateFlow.value = stateFlow.value.copy(badges = badges)
    }

    override fun setItemVisibility(itemId: Int, isVisible: Boolean) {
        val vi = stateFlow.value.itemVisibility.toMutableMap()
        vi[itemId] = isVisible
        stateFlow.value = stateFlow.value.copy(itemVisibility = vi)
    }

    override fun isItemVisible(itemId: Int): Boolean {
        return stateFlow.value.itemVisibility[itemId] != false
    }

    override fun isItemChecked(itemId: Int): Boolean {
        return stateFlow.value.selectedItemId == itemId
    }

    override fun getFirstVisibleItemId(): Int? {
        return stateFlow.value.items.firstOrNull { isItemVisible(it.id) }?.id
    }

    override fun getItemTitle(itemId: Int): CharSequence? {
        val res = stateFlow.value.items.find { it.id == itemId }?.title ?: return null
        return ctx.getString(res)
    }

    override fun asView(): View? = null

    // Helper Stub
    private class StubMenuItem(private val id: Int) : MenuItem {
        override fun getItemId(): Int = id
        override fun getGroupId(): Int = 0
        override fun getOrder(): Int = 0
        override fun setTitle(p0: CharSequence?): MenuItem = this
        override fun setTitle(p0: Int): MenuItem = this
        override fun getTitle(): CharSequence = ""
        override fun setTitleCondensed(p0: CharSequence?): MenuItem = this
        override fun getTitleCondensed(): CharSequence = ""
        override fun setIcon(p0: Drawable?): MenuItem = this
        override fun setIcon(p0: Int): MenuItem = this
        override fun getIcon(): Drawable? = null
        override fun setIntent(p0: Intent?): MenuItem = this
        override fun getIntent(): Intent? = null
        override fun setShortcut(p0: Char, p1: Char): MenuItem = this
        override fun setNumericShortcut(p0: Char): MenuItem = this
        override fun getNumericShortcut(): Char = '0'
        override fun setAlphabeticShortcut(p0: Char): MenuItem = this
        override fun getAlphabeticShortcut(): Char = '0'
        override fun setCheckable(p0: Boolean): MenuItem = this
        override fun isCheckable(): Boolean = true
        override fun setChecked(p0: Boolean): MenuItem = this
        override fun isChecked(): Boolean = true
        override fun setVisible(p0: Boolean): MenuItem = this
        override fun isVisible(): Boolean = true
        override fun setEnabled(p0: Boolean): MenuItem = this
        override fun isEnabled(): Boolean = true
        override fun hasSubMenu(): Boolean = false
        override fun getSubMenu(): SubMenu? = null
        override fun setOnMenuItemClickListener(p0: MenuItem.OnMenuItemClickListener?): MenuItem = this
        override fun getMenuInfo(): ContextMenu.ContextMenuInfo? = null
        override fun setShowAsAction(p0: Int) {}
        override fun setShowAsActionFlags(p0: Int): MenuItem = this
        override fun setActionView(p0: View?): MenuItem = this
        override fun setActionView(p0: Int): MenuItem = this
        override fun getActionView(): View? = null
        override fun setActionProvider(p0: ActionProvider?): MenuItem = this
        override fun getActionProvider(): ActionProvider? = null
        override fun expandActionView(): Boolean = false
        override fun collapseActionView(): Boolean = false
        override fun isActionViewExpanded(): Boolean = false
        override fun setOnActionExpandListener(p0: MenuItem.OnActionExpandListener?): MenuItem = this
    }
}
