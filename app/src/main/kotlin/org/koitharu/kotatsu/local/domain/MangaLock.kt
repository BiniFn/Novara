package org.skepsun.kototoro.local.domain

import org.skepsun.kototoro.core.util.MultiMutex
import org.skepsun.kototoro.parsers.model.Manga
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MangaLock @Inject constructor() : MultiMutex<Manga>()
