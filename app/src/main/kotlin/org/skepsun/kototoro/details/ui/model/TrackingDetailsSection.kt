package org.skepsun.kototoro.details.ui.model

import androidx.annotation.StringRes
import org.skepsun.kototoro.scrobbling.common.domain.model.ScrobblerService

data class TrackingDetailsSection(
    @StringRes val titleRes: Int? = null,
    val title: String? = null,
    val items: List<TrackingDetailsSectionItem>,
)

data class TrackingDetailsSectionItem(
    val service: ScrobblerService,
    val remoteId: Long,
    val title: String,
    val coverUrl: String?,
    val subtitle: String? = null,
    val url: String? = null,
)

data class TrackingDetailsAction(
    val title: String,
    val url: String,
)
