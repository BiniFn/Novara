package org.skepsun.kototoro.main.ui

import android.view.MenuItem
import android.view.View
import androidx.annotation.IdRes
import com.google.android.material.navigation.NavigationBarView

interface AppNavBarDelegator {
    var selectedItemId: Int
    var labelVisibilityMode: Int

    fun setOnItemSelectedListener(listener: NavigationBarView.OnItemSelectedListener?)
    fun setOnItemReselectedListener(listener: NavigationBarView.OnItemReselectedListener?)

    fun isMenuEmpty(): Boolean
    fun setupMenu(items: List<org.skepsun.kototoro.core.prefs.NavItem>)
    
    fun setBadgeNumber(@IdRes itemId: Int, number: Int)
    fun clearBadge(@IdRes itemId: Int)
    fun setBadgeVisible(@IdRes itemId: Int, isVisible: Boolean)
    
    fun setItemVisibility(@IdRes itemId: Int, isVisible: Boolean)
    fun isItemVisible(@IdRes itemId: Int): Boolean
    fun isItemChecked(@IdRes itemId: Int): Boolean
    
    fun getFirstVisibleItemId(): Int?
    fun getItemTitle(@IdRes itemId: Int): CharSequence?

    // Used for navigation rail specifics
    fun asView(): View?
}
