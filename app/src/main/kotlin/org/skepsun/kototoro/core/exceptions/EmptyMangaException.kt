package org.skepsun.kototoro.core.exceptions

import org.skepsun.kototoro.details.ui.pager.EmptyMangaReason
import org.skepsun.kototoro.parsers.model.Manga

class EmptyMangaException(
    val reason: EmptyMangaReason?,
    val manga: Manga,
    cause: Throwable?
) : IllegalStateException(cause)
