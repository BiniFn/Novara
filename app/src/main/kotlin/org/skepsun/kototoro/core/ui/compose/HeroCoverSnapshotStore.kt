package org.skepsun.kototoro.core.ui.compose

import coil3.Image
import java.util.LinkedHashMap

object HeroCoverSnapshotStore {

    private const val MaxEntries = 48

    private val snapshots = object : LinkedHashMap<String, Image>(MaxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Image>?): Boolean {
            return size > MaxEntries
        }
    }

    fun put(key: String, image: Image) {
        synchronized(snapshots) {
            snapshots[key] = image
        }
    }

    fun get(key: String): Image? {
        return synchronized(snapshots) {
            snapshots[key]
        }
    }
}
