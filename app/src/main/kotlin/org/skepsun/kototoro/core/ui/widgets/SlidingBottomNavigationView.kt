package org.skepsun.kototoro.core.ui.widgets

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewPropertyAnimator
import android.widget.FrameLayout
import androidx.annotation.IdRes
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isVisible
import androidx.customview.view.AbsSavedState
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import com.google.android.material.navigation.NavigationBarView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.skepsun.kototoro.core.prefs.NavItem
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.core.util.ext.applySystemAnimatorScale
import org.skepsun.kototoro.core.util.ext.measureHeight
import org.skepsun.kototoro.main.ui.AppNavBarDelegator

private const val STATE_DOWN = 1
private const val STATE_UP = 2

private const val SLIDE_UP_ANIMATION_DURATION = 225L
private const val SLIDE_DOWN_ANIMATION_DURATION = 175L

class SlidingBottomNavigationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr),
    CoordinatorLayout.AttachedBehavior, AppNavBarDelegator {

    private var currentAnimator: ViewPropertyAnimator? = null
    private var currentState = STATE_UP
    private var behavior = HideBottomNavigationOnScrollBehavior()

    val navState = MutableStateFlow(BottomNavState())

    init {
        val composeView = ComposeView(context).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                KototoroTheme {
                    KototoroBottomNav(
                        state = navState,
                        onItemSelected = { id ->
                            val handeled = navItemSelectedListener?.onNavigationItemSelected(MenuItemAdapter(id)) ?: false
                            if (handeled) {
                                selectedItemId = id
                            }
                        },
                        onItemReselected = { id ->
                            navItemReselectedListener?.onNavigationItemReselected(MenuItemAdapter(id))
                        }
                    )
                }
            }
        }
        addView(composeView)
    }

    var isPinned: Boolean
        get() = behavior.isPinned
        set(value) {
            behavior.isPinned = value
            if (value) {
                translationX = 0f
            }
        }

    val isShownOrShowing: Boolean
        get() = isVisible && currentState == STATE_UP

    override fun getBehavior(): CoordinatorLayout.Behavior<*> {
        return behavior
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState() ?: AbsSavedState.EMPTY_STATE
        return SavedState(superState, currentState, translationY)
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is SavedState) {
            state.superState?.let { super.onRestoreInstanceState(it) }
            super.setTranslationY(state.translationY)
            currentState = state.currentState
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    override fun setTranslationY(translationY: Float) {
        if (currentState != STATE_DOWN) {
            super.setTranslationY(translationY)
        }
    }

    fun show() {
        if (currentState == STATE_UP) {
            return
        }
        currentAnimator?.cancel()
        clearAnimation()
        currentState = STATE_UP
        animateTranslation(0F, SLIDE_UP_ANIMATION_DURATION, LinearOutSlowInInterpolator())
    }

    fun hide() {
        if (currentState == STATE_DOWN) {
            return
        }
        currentAnimator?.cancel()
        clearAnimation()
        currentState = STATE_DOWN
        val target = measureHeight() + ((layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0)
        if (target == 0) return
        animateTranslation(target.toFloat(), SLIDE_DOWN_ANIMATION_DURATION, FastOutLinearInInterpolator())
    }

    fun showOrHide(show: Boolean) {
        if (show) show() else hide()
    }

    private fun animateTranslation(targetY: Float, duration: Long, interpolator: TimeInterpolator) {
        currentAnimator = animate()
            .translationY(targetY)
            .setInterpolator(interpolator)
            .setDuration(duration)
            .applySystemAnimatorScale(context)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    currentAnimator = null
                    postInvalidate()
                }
            })
    }

    // --- AppNavBarDelegator Implementation ---

    private var navItemSelectedListener: NavigationBarView.OnItemSelectedListener? = null
    private var navItemReselectedListener: NavigationBarView.OnItemReselectedListener? = null

    override var selectedItemId: Int
        get() = navState.value.selectedItemId
        set(value) { navState.update { it.copy(selectedItemId = value) } }

    override var labelVisibilityMode: Int
        get() = navState.value.labelVisibilityMode
        set(value) { navState.update { it.copy(labelVisibilityMode = value) } }

    override fun setOnItemSelectedListener(listener: NavigationBarView.OnItemSelectedListener?) {
        this.navItemSelectedListener = listener
    }

    override fun setOnItemReselectedListener(listener: NavigationBarView.OnItemReselectedListener?) {
        this.navItemReselectedListener = listener
    }

    override fun isMenuEmpty(): Boolean = navState.value.items.isEmpty()

    override fun setupMenu(items: List<NavItem>) {
        navState.update { it.copy(items = items) }
    }

    override fun setBadgeNumber(itemId: Int, number: Int) {
        val badges = navState.value.badges.toMutableMap()
        badges[itemId] = badges[itemId]?.copy(number = number) ?: BadgeInfo(number = number)
        navState.update { it.copy(badges = badges) }
    }

    override fun clearBadge(itemId: Int) {
        val badges = navState.value.badges.toMutableMap()
        badges[itemId] = badges[itemId]?.copy(number = 0) ?: BadgeInfo(number = 0)
        navState.update { it.copy(badges = badges) }
    }

    override fun setBadgeVisible(itemId: Int, isVisible: Boolean) {
        val badges = navState.value.badges.toMutableMap()
        badges[itemId] = badges[itemId]?.copy(isVisible = isVisible) ?: BadgeInfo(isVisible = isVisible)
        navState.update { it.copy(badges = badges) }
    }

    override fun setItemVisibility(itemId: Int, isVisible: Boolean) {
        val visibilityMap = navState.value.itemVisibility.toMutableMap()
        visibilityMap[itemId] = isVisible
        navState.update { it.copy(itemVisibility = visibilityMap) }
    }

    override fun isItemVisible(itemId: Int): Boolean {
        return navState.value.itemVisibility[itemId] != false
    }

    override fun isItemChecked(itemId: Int): Boolean {
        return selectedItemId == itemId
    }

    override fun getFirstVisibleItemId(): Int? {
        return navState.value.items.firstOrNull { isItemVisible(it.id) }?.id
    }

    override fun getItemTitle(itemId: Int): CharSequence? {
        val item = navState.value.items.find { it.id == itemId } ?: return null
        return context.getString(item.title)
    }

    override fun asView(): View = this

    internal class SavedState : AbsSavedState {
        var currentState = STATE_UP
        var translationY = 0F
        constructor(superState: Parcelable, currentState: Int, translationY: Float) : super(superState) {
            this.currentState = currentState
            this.translationY = translationY
        }
        constructor(source: Parcel, loader: ClassLoader?) : super(source, loader) {
            currentState = source.readInt()
            translationY = source.readFloat()
        }
        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(currentState)
            out.writeFloat(translationY)
        }
        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(`in`: Parcel) = SavedState(`in`, SavedState::class.java.classLoader)
                override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
            }
        }
    }
}

data class BadgeInfo(val number: Int = 0, val isVisible: Boolean = false)

data class BottomNavState(
    val items: List<NavItem> = emptyList(),
    val selectedItemId: Int = 0,
    val labelVisibilityMode: Int = NavigationBarView.LABEL_VISIBILITY_AUTO,
    val badges: Map<Int, BadgeInfo> = emptyMap(),
    val itemVisibility: Map<Int, Boolean> = emptyMap(),
)

// A dummy implementation of MenuItem so we can pass IDs back to the NavigationBarView.OnItemSelectedListener.
class MenuItemAdapter(private val itemId: Int) : android.view.MenuItem {
    override fun getItemId() = itemId
    override fun getGroupId() = 0
    override fun getOrder() = 0
    override fun setTitle(title: CharSequence?) = this
    override fun setTitle(title: Int) = this
    override fun getTitle() = null
    override fun setTitleCondensed(title: CharSequence?) = this
    override fun getTitleCondensed() = null
    override fun setIcon(icon: android.graphics.drawable.Drawable?) = this
    override fun setIcon(iconRes: Int) = this
    override fun getIcon() = null
    override fun setIntent(intent: android.content.Intent?) = this
    override fun getIntent() = null
    override fun setShortcut(numericChar: Char, alphaChar: Char) = this
    override fun setNumericShortcut(numericChar: Char) = this
    override fun getNumericShortcut() = '0'
    override fun setAlphabeticShortcut(alphaChar: Char) = this
    override fun getAlphabeticShortcut() = '0'
    override fun setCheckable(checkable: Boolean) = this
    override fun isCheckable() = false
    override fun setChecked(checked: Boolean) = this
    override fun isChecked() = false
    override fun setVisible(visible: Boolean) = this
    override fun isVisible() = true
    override fun setEnabled(enabled: Boolean) = this
    override fun isEnabled() = true
    override fun hasSubMenu() = false
    override fun getSubMenu() = null
    override fun setOnMenuItemClickListener(menuItemClickListener: android.view.MenuItem.OnMenuItemClickListener?) = this
    override fun getMenuInfo() = null
    override fun setShowAsAction(actionEnum: Int) {}
    override fun setShowAsActionFlags(actionEnum: Int) = this
    override fun setActionView(view: View?) = this
    override fun setActionView(resId: Int) = this
    override fun getActionView() = null
    override fun setActionProvider(actionProvider: android.view.ActionProvider?) = this
    override fun getActionProvider() = null
    override fun expandActionView() = false
    override fun collapseActionView() = false
    override fun isActionViewExpanded() = false
    override fun setOnActionExpandListener(listener: android.view.MenuItem.OnActionExpandListener?) = this
}
