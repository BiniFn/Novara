package org.skepsun.kototoro.settings.about.crashlog

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ShareCompat
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.logs.CrashLogManager
import java.io.File

class CrashLogDetailActivity : AppCompatActivity() {

	private var logContent: String = ""

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_crash_log_detail)
		setSupportActionBar(findViewById(R.id.toolbar))
		supportActionBar?.setDisplayHomeAsUpEnabled(true)
		supportActionBar?.setTitle(R.string.crash_log_detail)

		val filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: run {
			finish()
			return
		}

		logContent = CrashLogManager.readLog(File(filePath))
		val textView: TextView = findViewById(R.id.text_log_content)
		textView.text = logContent
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		menuInflater.inflate(R.menu.opt_crash_log_detail, menu)
		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		return when (item.itemId) {
			android.R.id.home -> {
				finish()
				true
			}
			R.id.action_share -> {
				ShareCompat.IntentBuilder(this)
					.setType("text/plain")
					.setText(logContent)
					.setSubject("Kototoro Crash Log")
					.startChooser()
				true
			}
			R.id.action_copy -> {
				val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
				clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Crash Log", logContent))
				Toast.makeText(this, android.R.string.copy, Toast.LENGTH_SHORT).show()
				true
			}
			else -> super.onOptionsItemSelected(item)
		}
	}

	companion object {
		private const val EXTRA_FILE_PATH = "file_path"

		fun newIntent(context: Context, filePath: String): Intent {
			return Intent(context, CrashLogDetailActivity::class.java).apply {
				putExtra(EXTRA_FILE_PATH, filePath)
			}
		}
	}
}
