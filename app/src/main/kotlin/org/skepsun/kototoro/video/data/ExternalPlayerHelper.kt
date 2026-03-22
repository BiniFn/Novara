package org.skepsun.kototoro.video.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import org.skepsun.kototoro.R

object ExternalPlayerHelper {

    fun openInExternalPlayer(context: Context, proxyUrl: String, title: String?) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(proxyUrl), "video/*")
            title?.let { putExtra(Intent.EXTRA_TITLE, it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, context.getString(R.string.open_external_player))
        if (chooser.resolveActivity(context.packageManager) != null || intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(chooser)
        } else {
            Toast.makeText(context, R.string.no_external_player, Toast.LENGTH_SHORT).show()
        }
    }
}
