package org.skepsun.kototoro.tracking.discovery.domain

import java.util.Locale

fun TrackingSiteItem.displayTitle(): String {
    return primaryTitle?.takeIf { it.isNotBlank() }
        ?: title
}

fun TrackingSiteItem.displaySecondaryTitle(): String? {
    return sequenceOf(
        secondaryTitle,
        altTitle,
    ).filterNotNull().firstOrNull { candidate ->
        candidate.isNotBlank() &&
            !candidate.equals(primaryTitle, ignoreCase = true) &&
            !candidate.equals(title, ignoreCase = true)
    }
}

fun TrackingSiteItem.displaySupportingText(): String? {
    return listOfNotNull(
        progressText?.takeIf { it.isNotBlank() },
        updatedAtText?.takeIf { it.isNotBlank() },
        subtitle?.takeIf { it.isNotBlank() },
    ).joinToString(separator = " · ").ifBlank { null }
}

fun TrackingSiteItem.displaySubtitle(): String? {
    return listOfNotNull(
        displaySecondaryTitle(),
        displaySupportingText(),
    ).joinToString(separator = " · ").ifBlank { null }
}

fun TrackingSiteItem.displayScoreText(): String? {
    val value = score ?: return null
    val normalizedValue = ((value / resolveScoreScale(value)) * 10f)
        .coerceIn(0f, 10f)
    return normalizedValue.formatScoreNumber()
}

private fun TrackingSiteItem.resolveScoreScale(value: Float): Float {
    val explicitMax = scoreMax?.takeIf { it > 0f }
    if (explicitMax != null) {
        return explicitMax
    }
    return if (value > 10f) 100f else 10f
}

private fun Float.formatScoreNumber(): String {
    val whole = toInt().toFloat()
    return if (this == whole) {
        whole.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", this)
    }
}
