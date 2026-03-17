package org.skepsun.kototoro.core.exceptions

import org.skepsun.kototoro.details.ui.pager.EmptyContentReason
import org.skepsun.kototoro.parsers.model.Content

class EmptyContentException(
    val reason: EmptyContentReason?,
    val manga: Content,
    cause: Throwable?
) : IllegalStateException(cause)
