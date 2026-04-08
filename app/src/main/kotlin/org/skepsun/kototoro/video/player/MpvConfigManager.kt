package org.skepsun.kototoro.video.player

import android.content.Context
import java.io.File

object MpvConfigManager {

	private const val FILE_NAME = "mpv.conf"

	fun configFile(context: Context): File = File(context.filesDir, FILE_NAME)

	fun read(context: Context): String {
		val file = configFile(context)
		return if (file.isFile) file.readText() else ""
	}

	fun write(context: Context, content: String) {
		val normalized = content.replace("\r\n", "\n").trimEnd()
		if (normalized.isBlank()) {
			reset(context)
			return
		}
		configFile(context).writeText("$normalized\n")
	}

	fun reset(context: Context): Boolean {
		val file = configFile(context)
		return !file.exists() || file.delete()
	}

	fun hasCustomConfig(context: Context): Boolean = configFile(context).isFile

	fun showMpvConfigDialog(context: Context, rootView: android.view.View?) {
		val isLandscape = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
		val density = context.resources.displayMetrics.density
		val input = com.google.android.material.textfield.TextInputEditText(context).apply {
			setText(read(context))
			typeface = android.graphics.Typeface.MONOSPACE
			gravity = android.view.Gravity.TOP or android.view.Gravity.START
			minLines = if (isLandscape) 4 else 12
			maxLines = if (isLandscape) 10 else 20
			isSingleLine = false
			inputType = android.text.InputType.TYPE_CLASS_TEXT or
				android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
				android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
		}
		val inputLayout = com.google.android.material.textfield.TextInputLayout(context).apply {
			hint = context.getString(org.skepsun.kototoro.R.string.video_mpv_conf)
			helperText = context.getString(org.skepsun.kototoro.R.string.video_mpv_conf_hint)
			addView(
				input,
				android.widget.LinearLayout.LayoutParams(
					android.view.ViewGroup.LayoutParams.MATCH_PARENT,
					android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
				),
			)
		}
		val hintText = android.widget.TextView(context).apply {
			text = android.text.Html.fromHtml(
				context.getString(org.skepsun.kototoro.R.string.video_mpv_conf_guide),
				android.text.Html.FROM_HTML_MODE_COMPACT
			)
			movementMethod = android.text.method.LinkMovementMethod.getInstance()
			textSize = 14f
			setTextColor(android.graphics.Color.GRAY) // Explicit default text color fallback 
			setPadding(0, 0, 0, (12 * density).toInt())
			setLinkTextColor(androidx.core.content.ContextCompat.getColor(context, org.skepsun.kototoro.R.color.blue_primary))
		}
		val container = android.widget.LinearLayout(context).apply {
			orientation = android.widget.LinearLayout.VERTICAL
			val horizontal = (20 * density).toInt()
			val top = (12 * density).toInt()
			setPadding(horizontal, top, horizontal, 0)
			addView(
				hintText,
				android.widget.LinearLayout.LayoutParams(
					android.view.ViewGroup.LayoutParams.MATCH_PARENT,
					android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
				),
			)
			addView(
				inputLayout,
				android.widget.LinearLayout.LayoutParams(
					android.view.ViewGroup.LayoutParams.MATCH_PARENT,
					android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
				),
			)
		}
		val scrollView = android.widget.ScrollView(context).apply {
			addView(container)
		}
		val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
			.setTitle(org.skepsun.kototoro.R.string.video_mpv_conf)
			.setView(scrollView)
			.setPositiveButton(org.skepsun.kototoro.R.string.save) { _, _ ->
				write(context, input.text?.toString().orEmpty())
				rootView?.let { root ->
					com.google.android.material.snackbar.Snackbar.make(root, org.skepsun.kototoro.R.string.video_mpv_conf_saved, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
				}
			}
			.setNegativeButton(android.R.string.cancel, null)
		if (hasCustomConfig(context)) {
			builder.setNeutralButton(org.skepsun.kototoro.R.string.reset) { _, _ ->
				reset(context)
				rootView?.let { root ->
					com.google.android.material.snackbar.Snackbar.make(root, org.skepsun.kototoro.R.string.video_mpv_conf_reset, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
				}
			}
		}
		builder.show()
	}
}
