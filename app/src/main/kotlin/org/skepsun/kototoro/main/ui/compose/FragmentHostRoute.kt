package org.skepsun.kototoro.main.ui.compose

import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import org.skepsun.kototoro.main.ui.MainActivity

@Composable
fun FragmentHostRoute(fragmentClass: Class<out Fragment>) {
    val context = LocalContext.current
    AndroidView(
        factory = { ctx ->
            FrameLayout(ctx).apply {
                id = View.generateViewId()
                val fragmentManager = (context as MainActivity).supportFragmentManager
                val fragment = fragmentClass.newInstance()
                fragmentManager.commit {
                    replace(id, fragment)
                }
            }
        },
        update = { },
        modifier = Modifier.fillMaxSize()
    )
}
