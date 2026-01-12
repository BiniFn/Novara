package org.skepsun.kototoro.settings.sources.jsonsource.edit

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import android.view.View
import androidx.core.view.WindowInsetsCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.ui.BaseActivity
import org.skepsun.kototoro.databinding.ActivityJsonSourceEditBinding

/**
 * Activity for editing JSON source configurations.
 * 
 * Allows editing basic source information and rule configurations.
 */
@AndroidEntryPoint
class JsonSourceEditActivity : BaseActivity<ActivityJsonSourceEditBinding>() {
	
	private val viewModel: JsonSourceEditViewModel by viewModels()
	
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityJsonSourceEditBinding.inflate(layoutInflater))
		
		val sourceId = intent.getStringExtra(EXTRA_SOURCE_ID)
		if (sourceId != null) {
			viewModel.loadSource(sourceId)
		}
		
		setupViews()
		observeViewModel()
	}
	
	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val barsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
		v.setPadding(barsInsets.left, 0, barsInsets.right, barsInsets.bottom)
		return insets
	}
	
	private fun setupViews() {
		setSupportActionBar(viewBinding.toolbar)
		supportActionBar?.setDisplayHomeAsUpEnabled(true)
		val isEdit = intent.hasExtra(EXTRA_SOURCE_ID)
		supportActionBar?.title = getString(if (isEdit) R.string.edit_source else R.string.add_source)
		
		viewBinding.toolbar.setNavigationOnClickListener { 
			finish() 
		}
	}
	
	private fun observeViewModel() {
		lifecycleScope.launch {
			viewModel.source.collect { source ->
				source?.let { fillSourceData(it) }
			}
		}
		
		lifecycleScope.launch {
			viewModel.saveResult.collect { result ->
				when (result) {
					is SaveResult.Success -> {
						Toast.makeText(this@JsonSourceEditActivity, R.string.saved, Toast.LENGTH_SHORT).show()
						finish()
					}
					is SaveResult.Error -> {
						Toast.makeText(this@JsonSourceEditActivity, result.message, Toast.LENGTH_LONG).show()
					}
					null -> { /* Initial state */ }
				}
			}
		}
	}
	
	private fun fillSourceData(source: SourceEditData) {
		viewBinding.apply {
			editSourceName.setText(source.name)
			editSourceUrl.setText(source.url)
			editSourceGroup.setText(source.group)
			editSearchUrl.setText(source.searchUrl)
			editExploreUrl.setText(source.exploreUrl)
			switchEnabled.isChecked = source.enabled
		}
	}
	
	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		menuInflater.inflate(R.menu.menu_json_source_edit, menu)
		return true
	}
	
	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		return when (item.itemId) {
			R.id.action_save -> {
				saveSource()
				true
			}
			else -> super.onOptionsItemSelected(item)
		}
	}
	
	private fun saveSource() {
		val data = SourceEditData(
			name = viewBinding.editSourceName.text.toString(),
			url = viewBinding.editSourceUrl.text.toString(),
			group = viewBinding.editSourceGroup.text.toString().takeIf { it.isNotBlank() },
			searchUrl = viewBinding.editSearchUrl.text.toString().takeIf { it.isNotBlank() },
			exploreUrl = viewBinding.editExploreUrl.text.toString().takeIf { it.isNotBlank() },
			enabled = viewBinding.switchEnabled.isChecked
		)
		viewModel.saveSource(data)
	}
	
	companion object {
		const val EXTRA_SOURCE_ID = "source_id"
	}
}
