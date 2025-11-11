package org.skepsun.kototoro.filter.ui.tags

import android.content.Context
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.skepsun.kototoro.core.ui.BaseListAdapter
import org.skepsun.kototoro.core.ui.list.OnListItemClickListener
import org.skepsun.kototoro.core.ui.list.fastscroll.FastScroller
import org.skepsun.kototoro.core.util.ext.setChecked
import org.skepsun.kototoro.databinding.ItemCheckableNewBinding
import org.skepsun.kototoro.filter.ui.model.TagCatalogItem
import org.skepsun.kototoro.list.ui.ListModelDiffCallback
import org.skepsun.kototoro.list.ui.adapter.ListItemType
import org.skepsun.kototoro.list.ui.adapter.errorFooterAD
import org.skepsun.kototoro.list.ui.adapter.errorStateListAD
import org.skepsun.kototoro.list.ui.adapter.loadingFooterAD
import org.skepsun.kototoro.list.ui.adapter.loadingStateAD
import org.skepsun.kototoro.list.ui.model.ListModel

class TagsCatalogAdapter(
	listener: OnListItemClickListener<TagCatalogItem>,
) : BaseListAdapter<ListModel>(), FastScroller.SectionIndexer {

	init {
		addDelegate(ListItemType.FILTER_TAG, tagCatalogDelegate(listener))
		addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
		addDelegate(ListItemType.FOOTER_LOADING, loadingFooterAD())
		addDelegate(ListItemType.FOOTER_ERROR, errorFooterAD(null))
		addDelegate(ListItemType.STATE_ERROR, errorStateListAD(null))
	}

	override fun getSectionText(context: Context, position: Int): CharSequence? {
		return (items.getOrNull(position) as? TagCatalogItem)?.tag?.title?.firstOrNull()?.uppercase()
	}

	private fun tagCatalogDelegate(
		listener: OnListItemClickListener<TagCatalogItem>,
	) = adapterDelegateViewBinding<TagCatalogItem, ListModel, ItemCheckableNewBinding>(
		{ layoutInflater, parent -> ItemCheckableNewBinding.inflate(layoutInflater, parent, false) },
	) {

		itemView.setOnClickListener {
			listener.onItemClick(item, itemView)
		}

		bind { payloads ->
			binding.root.text = item.tag.title
			binding.root.setChecked(item.isChecked, ListModelDiffCallback.PAYLOAD_CHECKED_CHANGED in payloads)
		}
	}
}
