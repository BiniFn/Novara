package org.skepsun.kototoro.settings.sources.aniyomi

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.ui.BaseFragment
import org.skepsun.kototoro.core.util.ext.observe
import org.skepsun.kototoro.databinding.FragmentAniyomiExtensionsBinding

/**
 * Fragment for managing Aniyomi extensions.
 * 
 * Shows a list of installed Aniyomi extensions and their sources,
 * allowing users to see which extensions are loaded.
 */
@AndroidEntryPoint
class AniyomiExtensionsFragment : BaseFragment<FragmentAniyomiExtensionsBinding>() {

    private val viewModel by viewModels<AniyomiExtensionsViewModel>()
    
    private var adapter: AniyomiExtensionsAdapter? = null

    override fun onCreateViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentAniyomiExtensionsBinding {
        return FragmentAniyomiExtensionsBinding.inflate(inflater, container, false)
    }

    override fun onViewBindingCreated(binding: FragmentAniyomiExtensionsBinding, savedInstanceState: Bundle?) {
        super.onViewBindingCreated(binding, savedInstanceState)
        
        adapter = AniyomiExtensionsAdapter()
        
        with(binding) {
            recyclerView.layoutManager = LinearLayoutManager(context)
            recyclerView.adapter = adapter
            
            swipeRefresh.setOnRefreshListener {
                viewModel.refresh()
            }
        }
        
        observeViewModel()
    }
    
    override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
        return insets
    }
    
    private fun observeViewModel() {
        val binding = requireViewBinding()
        
        viewModel.extensions.observe(viewLifecycleOwner) { extensions ->
            adapter?.submitList(extensions)
            
            binding.emptyGroup.isVisible = extensions.isEmpty() && !viewModel.isLoading.value
            binding.recyclerView.isVisible = extensions.isNotEmpty()
        }
        
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
            binding.progressBar.isVisible = isLoading && adapter?.currentList.isNullOrEmpty()
        }
        
        viewModel.extensionCount.observe(viewLifecycleOwner) { count ->
            binding.textExtensionCount.text = getString(R.string.aniyomi_extension_count, count)
            binding.textExtensionCount.isVisible = count > 0
        }
        
        viewModel.sourceCount.observe(viewLifecycleOwner) { count ->
            binding.textSourceCount.text = getString(R.string.aniyomi_source_count, count)
            binding.textSourceCount.isVisible = count > 0
        }
    }
    
    override fun onDestroyView() {
        adapter = null
        super.onDestroyView()
    }
}
