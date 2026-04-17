package org.skepsun.kototoro.main.ui.compose

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asAndroidColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.roundToInt
import org.skepsun.kototoro.R
import org.skepsun.kototoro.explore.ui.model.SourceTag

/**
 * Icon button + dropdown menu for selecting a single [SourceTag].
 *
 * The icon tint changes to primary when a tag is actively selected,
 * and shows the specific tag's icon when exactly one is selected.
 */
@Composable
fun SourceTagDropdown(
    selectedTags: Set<SourceTag>,
    entries: List<SourceTag> = SourceTag.quickFilterEntries,
    enabledTags: Set<SourceTag> = entries.toSet(),
    onButtonClickIntercept: (android.view.View?) -> Boolean = { false },
    onTagSelected: (SourceTag?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorView by remember { mutableStateOf<android.view.View?>(null) }

    val iconRes = if (selectedTags.size == 1) {
        selectedTags.first().iconRes
    } else {
        R.drawable.ic_filter_menu
    }
    val tint = if (selectedTags.isNotEmpty()) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    IconButton(
        onClick = {
            if (!onButtonClickIntercept(anchorView)) {
                expanded = true
            }
        },
        modifier = modifier,
    ) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { context ->
                android.view.View(context).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(1, 1)
                }
            },
            update = { anchorView = it },
        )
        Icon(
            painter = rememberSafePainter(iconRes),
            contentDescription = stringResource(R.string.filter),
            tint = tint,
        )
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        // "All" option
        DropdownMenuItem(
            text = { Text(stringResource(R.string.all)) },
            onClick = {
                onTagSelected(null)
                expanded = false
            },
            leadingIcon = {
                Checkbox(
                    checked = selectedTags.isEmpty(),
                    onCheckedChange = null,
                    modifier = Modifier.size(24.dp),
                )
            },
        )

        // Individual tag entries
        entries.forEach { tag ->
            DropdownMenuItem(
                text = { Text(stringResource(tag.titleRes)) },
                onClick = {
                    onTagSelected(tag)
                    expanded = false
                },
                enabled = tag in enabledTags,
                leadingIcon = {
                    Icon(
                        painter = rememberSafePainter(tag.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color.Unspecified,
                    )
                },
                trailingIcon = {
                    if (tag in selectedTags) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun rememberSafePainter(id: Int): Painter {
    val context = LocalContext.current
    return remember(id) {
        val drawable = ContextCompat.getDrawable(context, id)
        if (drawable != null) {
            // Accompanist native drawable painter implementation directly inside Compose
            DrawablePainter(drawable.mutate()) // Mutate so alpha/tint states don't share
        } else {
            ColorPainter(Color.Transparent)
        }
    }
}

private class DrawablePainter(private val drawable: android.graphics.drawable.Drawable) : Painter() {
    override val intrinsicSize: Size
        get() = Size(
            width = drawable.intrinsicWidth.toFloat().takeIf { it >= 0 } ?: Size.Unspecified.width,
            height = drawable.intrinsicHeight.toFloat().takeIf { it >= 0 } ?: Size.Unspecified.height
        )

    override fun DrawScope.onDraw() {
        drawIntoCanvas { canvas ->
            drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
            drawable.draw(canvas.nativeCanvas)
        }
    }

    override fun applyAlpha(alpha: Float): Boolean {
        drawable.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
        return true
    }

    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
        drawable.colorFilter = colorFilter?.asAndroidColorFilter()
        return true
    }
}
