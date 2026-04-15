package org.skepsun.kototoro.settings.about.crashlog

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.logs.CrashLogManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashLogActivity : AppCompatActivity() {

	private lateinit var recyclerView: RecyclerView
	private lateinit var emptyView: TextView
	private var logFiles: List<File> = emptyList()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_crash_log)
		setSupportActionBar(findViewById(R.id.toolbar))
		supportActionBar?.setDisplayHomeAsUpEnabled(true)
		supportActionBar?.setTitle(R.string.crash_logs)

		recyclerView = findViewById(R.id.recycler_view)
		emptyView = findViewById(R.id.text_empty)

		recyclerView.layoutManager = LinearLayoutManager(this)
		recyclerView.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

		refreshList()
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		menuInflater.inflate(R.menu.opt_crash_log, menu)
		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		return when (item.itemId) {
			android.R.id.home -> {
				finish()
				true
			}
			R.id.action_clear_all -> {
				MaterialAlertDialogBuilder(this)
					.setTitle(R.string.clear_crash_logs)
					.setMessage(R.string.clear_crash_logs_confirm)
					.setPositiveButton(R.string.clear_crash_logs) { _, _ ->
						CrashLogManager.clearAll(this)
						refreshList()
						Toast.makeText(this, R.string.crash_logs_cleared, Toast.LENGTH_SHORT).show()
					}
					.setNegativeButton(android.R.string.cancel, null)
					.show()
				true
			}
			else -> super.onOptionsItemSelected(item)
		}
	}

	private fun refreshList() {
		logFiles = CrashLogManager.getLogFiles(this)
		if (logFiles.isEmpty()) {
			recyclerView.visibility = View.GONE
			emptyView.visibility = View.VISIBLE
		} else {
			recyclerView.visibility = View.VISIBLE
			emptyView.visibility = View.GONE
			recyclerView.adapter = CrashLogAdapter(logFiles) { file ->
				startActivity(CrashLogDetailActivity.newIntent(this, file.absolutePath))
			}
		}
	}

	private class CrashLogAdapter(
		private val files: List<File>,
		private val onClick: (File) -> Unit,
	) : RecyclerView.Adapter<CrashLogAdapter.ViewHolder>() {

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
			val view = LayoutInflater.from(parent.context).inflate(R.layout.item_crash_log, parent, false)
			return ViewHolder(view)
		}

		override fun onBindViewHolder(holder: ViewHolder, position: Int) {
			val file = files[position]
			holder.bind(file)
			holder.itemView.setOnClickListener { onClick(file) }
		}

		override fun getItemCount() = files.size

		class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
			private val titleView: TextView = view.findViewById(R.id.text_title)
			private val subtitleView: TextView = view.findViewById(R.id.text_subtitle)

			fun bind(file: File) {
				val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
				titleView.text = dateFormat.format(Date(file.lastModified()))

				// Read first few lines to get the crash summary
				val content = file.readText()
				val firstException = content.lines()
					.firstOrNull { it.contains("Exception") || it.contains("Error") }
					?.trim()
					?: file.name
				subtitleView.text = firstException
			}
		}
	}

	companion object {
		fun newIntent(context: Context) = Intent(context, CrashLogActivity::class.java)
	}
}
