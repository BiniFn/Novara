package org.skepsun.kototoro.favourites.ui

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.core.nav.router
import org.skepsun.kototoro.core.ui.theme.KototoroTheme
import org.skepsun.kototoro.favourites.ui.compose.KototoroFavoritesHostRoute

@AndroidEntryPoint
class FavouritesActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            KototoroTheme {
                KototoroFavoritesHostRoute(
                    appRouter = router,
                    contentPadding = PaddingValues(0.dp)
                )
            }
        }
    }
}
