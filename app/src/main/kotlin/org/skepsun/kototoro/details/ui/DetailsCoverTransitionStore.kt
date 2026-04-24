package org.skepsun.kototoro.details.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import android.view.View
import androidx.core.view.drawToBitmap
import androidx.compose.ui.geometry.Rect
import org.skepsun.kototoro.parsers.model.Content

object DetailsCoverTransitionStore {

    private const val TAG = "HeroTransition"
    private var pending: PendingCoverTransition? = null

    fun set(content: Content, bounds: Rect?) {
        val key = content.transitionKey()
        val existingPending = pending?.takeIf { it.key == key }
        Log.d(TAG, "set: key=$key bounds=$bounds keepSnapshot=${existingPending?.backgroundSnapshot != null}")
        if (bounds == null) {
            if (existingPending != null) {
                pending = existingPending
            }
            return
        }
        pending = bounds
            ?.takeIf { it.width > 0f && it.height > 0f }
            ?.let {
                PendingCoverTransition(
                    key = key,
                    bounds = it,
                    backgroundSnapshot = existingPending?.backgroundSnapshot,
                )
            }
    }

    fun captureBackground(content: Content, view: View?) {
        val snapshot = pending ?: run {
            Log.d(TAG, "captureBackground: skip key=${content.transitionKey()} reason=no_pending")
            return
        }
        if (snapshot.key != content.transitionKey() || snapshot.backgroundSnapshot != null) {
            Log.d(
                TAG,
                "captureBackground: skip key=${content.transitionKey()} pendingKey=${snapshot.key} alreadyCaptured=${snapshot.backgroundSnapshot != null}",
            )
            return
        }
        val sourceView = view ?: run {
            Log.d(TAG, "captureBackground: skip key=${snapshot.key} reason=view_null")
            return
        }
        val contentRoot = sourceView.rootView.findViewById<View>(android.R.id.content)
        val captureCandidates = listOfNotNull(contentRoot, sourceView.rootView, sourceView)
        val captureView = captureCandidates.firstOrNull { it.width > 0 && it.height > 0 }
        if (captureView == null) {
            Log.d(
                TAG,
                "captureBackground: skip key=${snapshot.key} reason=no_sized_view contentRoot=${contentRoot?.width}x${contentRoot?.height} root=${sourceView.rootView.width}x${sourceView.rootView.height} source=${sourceView.width}x${sourceView.height}",
            )
            return
        }
        val bitmap = runCatching {
            captureView.drawToBitmap(config = Bitmap.Config.RGB_565)
        }.recoverCatching {
            Log.d(TAG, "captureBackground: drawToBitmap failed key=${snapshot.key} view=${captureView.javaClass.simpleName} error=${it.javaClass.simpleName}:${it.message}")
            Bitmap.createBitmap(captureView.width, captureView.height, Bitmap.Config.RGB_565).also { target ->
                val canvas = Canvas(target)
                captureView.draw(canvas)
            }
        }.getOrElse {
            Log.d(TAG, "captureBackground: fallback draw failed key=${snapshot.key} error=${it.javaClass.simpleName}:${it.message}")
            return
        }
        snapshot.backgroundSnapshot = bitmap
        Log.d(
            TAG,
            "captureBackground: success key=${snapshot.key} size=${bitmap.width}x${bitmap.height} view=${captureView.javaClass.simpleName}",
        )
    }

    fun setBackgroundSnapshot(content: Content, bitmap: Bitmap?) {
        val snapshot = pending ?: run {
            Log.d(TAG, "setBackgroundSnapshot: skip key=${content.transitionKey()} reason=no_pending")
            return
        }
        if (snapshot.key != content.transitionKey()) {
            Log.d(TAG, "setBackgroundSnapshot: skip key=${content.transitionKey()} pendingKey=${snapshot.key}")
            return
        }
        snapshot.backgroundSnapshot = bitmap
        Log.d(
            TAG,
            "setBackgroundSnapshot: key=${snapshot.key} success=${bitmap != null} size=${bitmap?.width}x${bitmap?.height}",
        )
    }

    fun consume(content: Content): PendingCoverTransition? {
        val snapshot = pending ?: return null
        pending = null
        val result = if (snapshot.key == content.transitionKey()) snapshot else null
        Log.d(
            TAG,
            "consume: key=${content.transitionKey()} matched=${result != null} hasSnapshot=${result?.backgroundSnapshot != null} bounds=${result?.bounds}",
        )
        return result
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
