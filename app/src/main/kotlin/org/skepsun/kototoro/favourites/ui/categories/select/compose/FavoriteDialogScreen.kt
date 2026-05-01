package org.skepsun.kototoro.favourites.ui.categories.select.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.model.FavouriteCategory

/**
 * Compose implementation of the Favorite Categories dialog.
 * Replaces the legacy FavoriteDialog (AlertDialogFragment).
 *
 * @param contentTitle   title of the manga being categorized
 * @param allCategories  all available favourite categories
 * @param memberCategoryIds  IDs of categories that the manga currently belongs to
 * @param onCategoryToggle  callback when a category checkbox is toggled (categoryId, newChecked)
 * @param onManageCategories  callback to open the category management screen
 * @param onDismiss  callback when the dialog is dismissed
 */
@Composable
fun FavoriteCategoryDialog(
    contentTitle: String,
    allCategories: List<FavouriteCategory>,
    memberCategoryIds: Set<Long>,
    onCategoryToggle: (categoryId: Long, isChecked: Boolean) -> Unit,
    onManageCategories: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = contentTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        },
        text = {
            if (allCategories.isEmpty()) {
                Text(
                    text = stringResource(R.string.empty_favourite_categories),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                ) {
                    items(
                        items = allCategories,
                        key = { it.id },
                        contentType = { "category_row" },
                    ) { category ->
                        val isChecked = category.id in memberCategoryIds
                        CategoryRow(
                            category = category,
                            isChecked = isChecked,
                            onClick = { onCategoryToggle(category.id, !isChecked) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.done))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onDismiss()
                onManageCategories()
            }) {
                Text(stringResource(R.string.manage))
            }
        },
    )
}

@Composable
private fun CategoryRow(
    category: FavouriteCategory,
    isChecked: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TriStateCheckbox(
            state = if (isChecked) ToggleableState.On else ToggleableState.Off,
            onClick = onClick,
        )
        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = category.title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )

        if (category.isTrackingEnabled) {
            Icon(
                painter = painterResource(R.drawable.ic_notification),
                contentDescription = null,
                modifier = Modifier.padding(horizontal = 4.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!category.isVisibleInLibrary) {
            Icon(
                painter = painterResource(R.drawable.ic_eye_off),
                contentDescription = null,
                modifier = Modifier.padding(horizontal = 4.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
