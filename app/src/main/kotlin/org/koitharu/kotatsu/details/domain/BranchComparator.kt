package org.skepsun.kototoro.details.domain

import org.skepsun.kototoro.core.util.LocaleStringComparator
import org.skepsun.kototoro.details.ui.model.MangaBranch

class BranchComparator : Comparator<MangaBranch> {

	private val delegate = LocaleStringComparator()

	override fun compare(o1: MangaBranch, o2: MangaBranch): Int = delegate.compare(o1.name, o2.name)
}
