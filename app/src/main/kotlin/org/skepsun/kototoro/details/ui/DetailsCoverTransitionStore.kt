package org.skepsun.kototoro.details.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.geometry.Rect
import org.skepsun.kototoro.parsers.model.Content

object DetailsCoverTransitionStore {

    private var pending: PendingCoverTransition? = null

    fun set(content: Content, bounds: Rect?) {
        pending?.backgroundSnapshot = null
        pending = bounds
            ?.takeIf { it.width > 0f && it.height > 0f }
            ?.let {
                PendingCoverTransition(
                    key = content.transitionKey(),
                    bounds = it,
                    backgroundSnapshot = null,
                )
            }
    }

    fun captureBackground(content: Content, view: View?) {
        val snapshot = pending ?: return
        if (snapshot.key != content.transitionKey() || snapshot.backgroundSnapshot != null) {
            return
        }
        val sourceView = view?.rootView ?: view ?: return
        val width = sourceView.width
        val height = sourceView.height
        if (width <= 0 || height <= 0) {
            return
        }
        val bitmap = runCatching {
            Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565).also { target ->
                val canvas = Canvas(target)
                sourceView.draw(canvas)
            }
        }.getOrNull() ?: return
        snapshot.backgroundSnapshot = bitmap
    }

    fun consume(content: Content): PendingCoverTransition? {
        val snapshot = pending ?: return null
        pending = null
        return if (snapshot.key == content.transitionKey()) {
            snapshot
        } else {
            null
        }
    }

    private fun Content.transitionKey(): String {
        return "${source.name}|$url"
    }

    data class PendingCoverTransition(
        val key: String,
        val bounds: Rect,
        var backgroundSnapshot: Bitmap?,
    )
}
