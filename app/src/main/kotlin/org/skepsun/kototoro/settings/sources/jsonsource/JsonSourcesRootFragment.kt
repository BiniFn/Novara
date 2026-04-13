package org.skepsun.kototoro.settings.sources.jsonsource
import org.skepsun.kototoro.core.util.ext.setSupportTitle

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.db.entity.JsonSourceType
import org.skepsun.kototoro.core.ui.BaseFragment
import org.skepsun.kototoro.databinding.FragmentJsonSourcesRootBinding

class JsonSourcesRootFragment : BaseFragment<FragmentJsonSourcesRootBinding>() {

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	): FragmentJsonSourcesRootBinding {
		return FragmentJsonSourcesRootBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: FragmentJsonSourcesRootBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		binding.pager.adapter = JsonSourcesPagerAdapter(this)
		TabLayoutMediator(binding.tabs, binding.pager) { tab, position ->
			tab.setText(
				when (position) {
					0 -> R.string.source_type_legado
					1 -> R.string.source_type_tvbox
					else -> R.string.source_type_lnreader
				},
			)
		}.attach()
	}

	override fun onApplyWindowInsets(v: android.view.View, insets: WindowInsetsCompat): WindowInsetsCompat = insets

	override fun onResume() {
		super.onResume()
		setSupportTitle(R.string.json_sources_directory)
	}
}

private class JsonSourcesPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

	private val types = listOf(JsonSourceType.LEGADO, JsonSourceType.TVBOX, JsonSourceType.LNREADER)

	override fun getItemCount(): Int = types.size

	override fun createFragment(position: Int): Fragment {
		return when (types[position]) {
			JsonSourceType.LNREADER -> LNReaderRepoFragment()
			else -> JsonSourcesFragment().apply {
				arguments = Bundle(1).apply {
					putString(JsonSourcesFragment.ARG_SOURCE_TYPE, types[position].name)
				}
			}
		}
	}
}
