package org.skepsun.kototoro.reader.ui.pager.webtoon

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import org.skepsun.kototoro.core.exceptions.resolve.ExceptionResolver
import org.skepsun.kototoro.core.os.NetworkState
import org.skepsun.kototoro.databinding.ItemPageWebtoonBinding
import org.skepsun.kototoro.reader.domain.PageLoader
import org.skepsun.kototoro.reader.ui.config.ReaderSettings
import org.skepsun.kototoro.reader.ui.pager.BaseReaderAdapter

class WebtoonAdapter(
	private val lifecycleOwner: LifecycleOwner,
	loader: PageLoader,
	readerSettingsProducer: ReaderSettings.Producer,
	networkState: NetworkState,
	exceptionResolver: ExceptionResolver,
) : BaseReaderAdapter<WebtoonHolder>(loader, readerSettingsProducer, networkState, exceptionResolver) {

	override fun onCreateViewHolder(
		parent: ViewGroup,
		loader: PageLoader,
		readerSettingsProducer: ReaderSettings.Producer,
		networkState: NetworkState,
		exceptionResolver: ExceptionResolver,
	) = WebtoonHolder(
		owner = lifecycleOwner,
		binding = ItemPageWebtoonBinding.inflate(
			LayoutInflater.from(parent.context),
			parent,
			false,
		),
		loader = loader,
		readerSettingsProducer = readerSettingsProducer,
		networkState = networkState,
		exceptionResolver = exceptionResolver,
	)
}
