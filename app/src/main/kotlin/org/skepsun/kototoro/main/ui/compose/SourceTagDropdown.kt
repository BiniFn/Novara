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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.ui.compose.rememberSafePainter
import org.skepsun.kototoro.explore.ui.model.SourceTag

private val CompactSourceTagButtonSize = 40.dp
private val CompactSourceTagIconSize = 20.dp

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
        modifier = modifier.size(CompactSourceTagButtonSize),
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
            modifier = Modifier.size(CompactSourceTagIconSize),
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

