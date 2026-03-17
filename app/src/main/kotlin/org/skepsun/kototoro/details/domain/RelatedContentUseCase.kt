package org.skepsun.kototoro.details.domain

import org.skepsun.kototoro.core.parser.MangaRepository
import org.skepsun.kototoro.core.util.ext.printStackTraceDebug
import org.skepsun.kototoro.parsers.model.Manga
import org.skepsun.kototoro.parsers.util.runCatchingCancellable
import javax.inject.Inject

class RelatedMangaUseCase @Inject constructor(
	private val mangaRepositoryFactory: MangaRepository.Factory,
) {

	suspend operator fun invoke(seed: Manga) = runCatchingCancellable {
		mangaRepositoryFactory.create(seed.source).getRelated(seed)
	}.onFailure {
		it.printStackTraceDebug()
	}.getOrNull()
}
