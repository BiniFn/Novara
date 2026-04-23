package org.skepsun.kototoro.main.ui.compose

import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
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

    Box(modifier = modifier) {
        IconButton(
            onClick = {
                if (!onButtonClickIntercept(null)) {
                    expanded = true
                }
            },
            modifier = Modifier.size(CompactSourceTagButtonSize),
        ) {
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
            shape = MaterialTheme.shapes.extraSmall,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            offset = DpOffset(x = 0.dp, y = 4.dp),
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.all)) },
                onClick = {
                    onTagSelected(null)
                },
                leadingIcon = {
                    Checkbox(
                        checked = selectedTags.isEmpty(),
                        onCheckedChange = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
            )

            entries.forEach { tag ->
                DropdownMenuItem(
                    text = { Text(stringResource(tag.titleRes)) },
                    onClick = {
                        onTagSelected(tag)
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
}
