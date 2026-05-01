package org.skepsun.kototoro.main.ui

import android.content.Context
import android.view.View
import androidx.annotation.IdRes
import androidx.core.view.isEmpty
import androidx.core.view.iterator
import com.google.android.material.navigation.NavigationBarView
import org.skepsun.kototoro.core.prefs.NavItem

fun NavigationBarView.asAppNavBarDelegator(): AppNavBarDelegator {
    val navView = this
    return object : AppNavBarDelegator {
        override var selectedItemId: Int
            get() = navView.selectedItemId
            set(value) { navView.selectedItemId = value }
            
        override var labelVisibilityMode: Int
            get() = navView.labelVisibilityMode
            set(value) { navView.labelVisibilityMode = value }

        override fun setOnItemSelectedListener(listener: NavigationBarView.OnItemSelectedListener?) {
            navView.setOnItemSelectedListener(listener)
        }

        override fun setOnItemReselectedListener(listener: NavigationBarView.OnItemReselectedListener?) {
            navView.setOnItemReselectedListener(listener)
        }

        override fun isMenuEmpty(): Boolean = navView.menu.isEmpty()

        override fun setupMenu(items: List<NavItem>) {
            val menu = navView.menu
            menu.clear()
            for (item in items) {
                menu.add(android.view.Menu.NONE, item.id, android.view.Menu.NONE, item.title)
                    .setIcon(item.icon)
                if (menu.size() >= navView.maxItemCount) {
                    break
                }
            }
        }

        override fun setBadgeNumber(itemId: Int, number: Int) {
            navView.getOrCreateBadge(itemId).number = number
        }

        override fun clearBadge(itemId: Int) {
            navView.getOrCreateBadge(itemId).clearNumber()
        }

        override fun setBadgeVisible(itemId: Int, isVisible: Boolean) {
            navView.getBadge(itemId)?.isVisible = isVisible
            if (isVisible) navView.getOrCreateBadge(itemId).isVisible = true
        }

        override fun setItemVisibility(itemId: Int, isVisible: Boolean) {
            navView.menu.findItem(itemId)?.isVisible = isVisible
        }

        override fun isItemVisible(itemId: Int): Boolean {
            return navView.menu.findItem(itemId)?.isVisible == true
        }

        override fun isItemChecked(itemId: Int): Boolean {
            return navView.menu.findItem(itemId)?.isChecked == true
        }

        override fun getFirstVisibleItemId(): Int? {
            val menu = navView.menu
            for (item in menu) {
                if (item.isVisible) return item.itemId
            }
            return null
        }

        override fun getItemTitle(itemId: Int): CharSequence? {
            return navView.menu.findItem(itemId)?.title
        }

        override fun asView(): View = navView
    }
}
