package org.skepsun.kototoro.settings.sources.extensions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.ui.BaseFragment
import org.skepsun.kototoro.databinding.FragmentExtensionsRootBinding
import org.skepsun.kototoro.extensions.repo.ExternalExtensionType

class ExtensionsRootFragment : BaseFragment<FragmentExtensionsRootBinding>() {

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	): FragmentExtensionsRootBinding {
		return FragmentExtensionsRootBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: FragmentExtensionsRootBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		binding.pager.adapter = ExtensionsPagerAdapter(this)
		TabLayoutMediator(binding.tabs, binding.pager) { tab, position ->
			tab.setText(
				when (position) {
					0 -> R.string.manga
					1 -> R.string.video
					else -> R.string.ireader_sources
				},
			)
		}.attach()
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat = insets

	override fun onResume() {
		super.onResume()
		activity?.setTitle(R.string.extensions)
	}
}

private class ExtensionsPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

	private val types = listOf(ExternalExtensionType.MIHON, ExternalExtensionType.ANIYOMI, ExternalExtensionType.IREADER)

	override fun getItemCount(): Int = types.size

	override fun createFragment(position: Int): Fragment {
		return ExtensionsBrowserFragment().apply {
			arguments = Bundle(1).apply {
				putString(ARG_EXTENSION_TYPE, types[position].name)
			}
		}
	}
}
