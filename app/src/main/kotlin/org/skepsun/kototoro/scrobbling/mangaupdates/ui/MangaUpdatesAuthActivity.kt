package org.skepsun.kototoro.scrobbling.mangaupdates.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.TextView
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.ui.BaseActivity
import org.skepsun.kototoro.core.ui.util.DefaultTextWatcher
import org.skepsun.kototoro.core.util.ext.consume
import org.skepsun.kototoro.databinding.ActivityMangaupdatesAuthBinding
import org.skepsun.kototoro.parsers.util.urlEncoded

class MangaUpdatesAuthActivity : BaseActivity<ActivityMangaupdatesAuthBinding>(),
	View.OnClickListener,
	DefaultTextWatcher,
	TextView.OnEditorActionListener {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityMangaupdatesAuthBinding.inflate(layoutInflater))
		viewBinding.buttonCancel.setOnClickListener(this)
		viewBinding.buttonDone.setOnClickListener(this)
		viewBinding.editUsername.addTextChangedListener(this)
		viewBinding.editUsername.setOnEditorActionListener(this)
		viewBinding.editPassword.addTextChangedListener(this)
		viewBinding.editPassword.setOnEditorActionListener(this)
	}

	override fun onApplyWindowInsets(
		v: View,
		insets: WindowInsetsCompat
	): WindowInsetsCompat {
		val typeMask = WindowInsetsCompat.Type.systemBars()
		val screenPadding = v.resources.getDimensionPixelOffset(R.dimen.screen_padding)
		val barsInsets = insets.getInsets(typeMask)
		viewBinding.root.updatePadding(top = barsInsets.top)
		viewBinding.dockedToolbarChild.updateLayoutParams<MarginLayoutParams> {
			leftMargin = barsInsets.left
			rightMargin = barsInsets.right
			bottomMargin = barsInsets.bottom
		}
		viewBinding.layoutUsername.updateLayoutParams<MarginLayoutParams> {
			leftMargin = barsInsets.left + screenPadding
			rightMargin = barsInsets.right + screenPadding
		}
		viewBinding.layoutPassword.updateLayoutParams<MarginLayoutParams> {
			leftMargin = barsInsets.left + screenPadding
			rightMargin = barsInsets.right + screenPadding
		}
		return insets.consume(v, typeMask)
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.button_cancel -> finish()
			R.id.button_done -> continueAuth()
		}
	}

	override fun onEditorAction(
		v: TextView,
		actionId: Int,
		event: KeyEvent?
	): Boolean = when (v.id) {
		R.id.edit_username -> {
			viewBinding.editPassword.requestFocus()
			true
		}

		R.id.edit_password -> {
			if (viewBinding.buttonDone.isEnabled) {
				continueAuth()
				true
			} else {
				false
			}
		}

		else -> false
	}

	override fun afterTextChanged(s: Editable?) {
		val username = viewBinding.editUsername.text?.toString()?.trim()
		val password = viewBinding.editPassword.text?.toString()?.trim()
		viewBinding.buttonDone.isEnabled = !username.isNullOrEmpty()
			&& !password.isNullOrEmpty()
	}

	@SuppressLint("UnsafeImplicitIntentLaunch")
	private fun continueAuth() {
		val username = viewBinding.editUsername.text?.toString()?.trim().orEmpty()
		val password = viewBinding.editPassword.text?.toString()?.trim().orEmpty()
		// Re-use logic: username;password
		val url = "kototoro://mangaupdates-auth?code=" + "$username;$password".urlEncoded()
		val intent = Intent(Intent.ACTION_VIEW, url.toUri())
		startActivity(intent)
		finishAfterTransition()
	}
}
