package org.skepsun.kototoro.reader.ui

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import android.content.Intent

data class PageCropRequest(
    val source: Uri,
    val destination: Uri,
    val compressFormat: Bitmap.CompressFormat,
    val compressQuality: Int,
    val sourceWidth: Int,
    val sourceHeight: Int,
)

class PageCropContract : ActivityResultContract<PageCropRequest, Uri?>() {

    override fun createIntent(context: Context, input: PageCropRequest): Intent {
        return Intent(context, PageCropActivity::class.java).apply {
            putExtra(PageCropActivity.EXTRA_SOURCE_URI, input.source)
            putExtra(PageCropActivity.EXTRA_OUTPUT_URI, input.destination)
            putExtra(PageCropActivity.EXTRA_COMPRESS_FORMAT, input.compressFormat.name)
            putExtra(PageCropActivity.EXTRA_COMPRESS_QUALITY, input.compressQuality)
            putExtra(PageCropActivity.EXTRA_SOURCE_WIDTH, input.sourceWidth)
            putExtra(PageCropActivity.EXTRA_SOURCE_HEIGHT, input.sourceHeight)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (resultCode != Activity.RESULT_OK) {
            return null
        }
        return intent?.data
    }
}
