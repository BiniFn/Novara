package org.skepsun.kototoro.local.domain

import org.skepsun.kototoro.core.util.MultiMutex
import org.skepsun.kototoro.parsers.model.Content
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentLock @Inject constructor() : MultiMutex<Content>()
