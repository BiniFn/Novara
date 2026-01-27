package org.skepsun.kototoro.reader.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.yalantis.ucrop.callback.BitmapCropCallback
import com.yalantis.ucrop.view.CropImageView
import com.yalantis.ucrop.view.GestureCropImageView
import com.yalantis.ucrop.view.OverlayView
import com.yalantis.ucrop.view.TransformImageView
import com.yalantis.ucrop.view.UCropView
import org.skepsun.kototoro.R

class PageCropActivity : AppCompatActivity(), TransformImageView.TransformImageListener {

	private lateinit var cropImageView: GestureCropImageView
	private lateinit var overlayView: OverlayView
	private lateinit var outputUri: Uri
	private lateinit var compressFormat: Bitmap.CompressFormat
	private var compressQuality: Int = DEFAULT_COMPRESS_QUALITY
	private var originalRatio: Float = CropImageView.SOURCE_IMAGE_ASPECT_RATIO
	private var isCropping = false

	private lateinit var ratioOriginal: TextView
	private lateinit var ratio1x1: TextView
	private lateinit var ratio4x3: TextView
	private lateinit var ratio16x9: TextView
	private lateinit var ratioViews: List<TextView>
	private lateinit var ucropView: UCropView
	private lateinit var sourceUri: Uri

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_page_crop)

		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { view, insets ->
			val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
			view.updatePadding(
				left = systemBars.left,
				top = systemBars.top,
				right = systemBars.right,
				bottom = systemBars.bottom,
			)
			insets
		}

		val sourceUri = intent.getParcelableExtra<Uri>(EXTRA_SOURCE_URI)
		val destinationUri = intent.getParcelableExtra<Uri>(EXTRA_OUTPUT_URI)
		if (sourceUri == null || destinationUri == null) {
			setResult(Activity.RESULT_CANCELED)
			finish()
			return
		}
		this.sourceUri = sourceUri
		outputUri = destinationUri
		compressFormat = parseCompressFormat(intent.getStringExtra(EXTRA_COMPRESS_FORMAT))
		compressQuality = intent.getIntExtra(EXTRA_COMPRESS_QUALITY, DEFAULT_COMPRESS_QUALITY)
		val sourceWidth = intent.getIntExtra(EXTRA_SOURCE_WIDTH, 0)
		val sourceHeight = intent.getIntExtra(EXTRA_SOURCE_HEIGHT, 0)
		originalRatio = if (sourceWidth > 0 && sourceHeight > 0) {
			sourceWidth.toFloat() / sourceHeight.toFloat()
		} else {
			CropImageView.SOURCE_IMAGE_ASPECT_RATIO
		}

		ucropView = findViewById(R.id.ucrop)
		cropImageView = ucropView.cropImageView
		overlayView = ucropView.overlayView
		overlayView.setFreestyleCropEnabled(true)
		cropImageView.setTransformImageListener(this)
		cropImageView.setImageToWrapCropBoundsAnimDuration(WRAP_ANIM_DURATION_MS)
		cropImageView.setMaxScaleMultiplier(MAX_SCALE_MULTIPLIER)

		findViewById<TextView>(R.id.button_cancel).setOnClickListener {
			setResult(Activity.RESULT_CANCELED)
			finish()
		}
		findViewById<TextView>(R.id.button_save).setOnClickListener {
			if (isCropping) return@setOnClickListener
			isCropping = true
			cropImageView.cropAndSaveImage(
				compressFormat,
				compressQuality,
				object : BitmapCropCallback {
					override fun onBitmapCropped(
						resultUri: Uri,
						imageWidth: Int,
						imageHeight: Int,
						offsetX: Int,
						offsetY: Int,
					) {
						setResult(Activity.RESULT_OK, Intent().setData(resultUri))
						finish()
					}

					override fun onCropFailure(t: Throwable) {
						setResult(Activity.RESULT_CANCELED)
						finish()
					}
				},
			)
		}

		ratioOriginal = findViewById(R.id.ratio_original)
		ratio1x1 = findViewById(R.id.ratio_1_1)
		ratio4x3 = findViewById(R.id.ratio_4_3)
		ratio16x9 = findViewById(R.id.ratio_16_9)
		ratioViews = listOf(ratioOriginal, ratio1x1, ratio4x3, ratio16x9)

		ratioOriginal.setOnClickListener { applyAspectRatio(originalRatio, it as TextView) }
		ratio1x1.setOnClickListener { applyAspectRatio(1f, it as TextView) }
		ratio4x3.setOnClickListener { applyAspectRatio(4f / 3f, it as TextView) }
		ratio16x9.setOnClickListener { applyAspectRatio(16f / 9f, it as TextView) }

		try {
			cropImageView.setImageUri(sourceUri, outputUri)
		} catch (_: Exception) {
			setResult(Activity.RESULT_CANCELED)
			finish()
		}
	}

	override fun onLoadComplete() {
		applyAspectRatio(originalRatio, ratioOriginal)
		findViewById<UCropView>(R.id.ucrop).alpha = 1f
	}

	override fun onLoadFailure(e: Exception) {
		setResult(Activity.RESULT_CANCELED)
		finish()
	}

	override fun onRotate(currentAngle: Float) = Unit

	override fun onScale(currentScale: Float) = Unit

	private fun applyAspectRatio(ratio: Float, selected: TextView) {
		val targetRatio = if (ratio > 0f) ratio else CropImageView.SOURCE_IMAGE_ASPECT_RATIO
		overlayView.setTargetAspectRatio(targetRatio)
		cropImageView.setTargetAspectRatio(targetRatio)
		resetScaleToMin()
		cropImageView.setImageToWrapCropBounds(true)
		selectRatioView(selected)
	}

	private fun resetScaleToMin() {
		val minScale = cropImageView.minScale
		val currentScale = cropImageView.currentScale
		if (currentScale > minScale && cropImageView.width > 0 && cropImageView.height > 0) {
			val scaleFactor = minScale / currentScale
			cropImageView.postScale(
				scaleFactor,
				cropImageView.width / 2f,
				cropImageView.height / 2f,
			)
		}
	}

	private fun selectRatioView(selected: TextView) {
		ratioViews.forEach { it.isSelected = it == selected }
	}

	private fun parseCompressFormat(formatName: String?): Bitmap.CompressFormat {
		return runCatching { Bitmap.CompressFormat.valueOf(formatName ?: "") }
			.getOrDefault(Bitmap.CompressFormat.PNG)
	}

	companion object {
		internal const val EXTRA_SOURCE_URI = "page_crop_source_uri"
		internal const val EXTRA_OUTPUT_URI = "page_crop_output_uri"
		internal const val EXTRA_COMPRESS_FORMAT = "page_crop_compress_format"
		internal const val EXTRA_COMPRESS_QUALITY = "page_crop_compress_quality"
		internal const val EXTRA_SOURCE_WIDTH = "page_crop_source_width"
		internal const val EXTRA_SOURCE_HEIGHT = "page_crop_source_height"

		private const val DEFAULT_COMPRESS_QUALITY = 95
		private const val WRAP_ANIM_DURATION_MS = 180L
		private const val MAX_SCALE_MULTIPLIER = 20f
	}
}
