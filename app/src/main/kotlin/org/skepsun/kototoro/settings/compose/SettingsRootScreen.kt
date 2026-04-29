package org.skepsun.kototoro.settings.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.ui.compose.rememberSafePainter
import org.skepsun.kototoro.core.ui.glass.GlassDefaults
import org.skepsun.kototoro.core.ui.glass.GlassSurface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import org.skepsun.kototoro.settings.search.SettingsItem

data class SettingsRootSection(
    val title: String,
    val items: List<SettingsRootItem>,
)

data class SettingsRootItem(
    val key: String,
    @DrawableRes val iconRes: Int,
    val title: String,
    val summary: String,
    val onClick: () -> Unit,
)

@Composable
fun SettingsRootScreen(
    sections: List<SettingsRootSection>,
    title: String,
    subtitle: String,
    searchQuery: String,
    searchResults: List<SettingsItem>,
    onSearchQueryChange: (String) -> Unit,
    onSearchResultClick: (SettingsItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Transparent,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 20.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "hero") {
            SettingsHeroCard(
                title = title,
                subtitle = subtitle,
            )
        }

        item(key = "search") {
            SettingsSearchField(
                query = searchQuery,
                onValueChange = onSearchQueryChange,
            )
        }

        if (searchQuery.isBlank()) {
            items(sections, key = { it.title }, contentType = { "settings_section" }) { section ->
                SettingsSectionCard(section = section)
            }
        } else {
            item(key = "search_results") {
                SettingsSearchResultsCard(
                    results = searchResults,
                    onItemClick = onSearchResultClick,
                )
            }
        }
    }
    }
}

@Composable
private fun SettingsHeroCard(
    title: String,
    subtitle: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsSectionCard(
    section: SettingsRootSection,
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        style = GlassDefaults.subtleStyle(),
        allowRuntimeHaze = false,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            section.items.forEachIndexed { index, item ->
                SettingsRootRow(item = item)
                if (index != section.items.lastIndex) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 68.dp, end = 20.dp)
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSearchField(
	query: String,
	onValueChange: (String) -> Unit,
) {
	OutlinedTextField(
		value = query,
		onValueChange = onValueChange,
		modifier = Modifier.fillMaxWidth(),
		singleLine = true,
		leadingIcon = {
			Icon(
				imageVector = Icons.Filled.Search,
				contentDescription = null,
			)
		},
		trailingIcon = {
			if (query.isNotEmpty()) {
				IconButton(onClick = { onValueChange("") }) {
					Icon(
						imageVector = Icons.Filled.Close,
						contentDescription = stringResource(android.R.string.cancel),
					)
				}
			}
		},
		label = { Text(stringResource(R.string.search)) },
	)
}

@Composable
private fun SettingsSearchResultsCard(
	results: List<SettingsItem>,
	onItemClick: (SettingsItem) -> Unit,
) {
	GlassSurface(
		modifier = Modifier.fillMaxWidth(),
		shape = RoundedCornerShape(24.dp),
		style = GlassDefaults.subtleStyle(),
		allowRuntimeHaze = false,
	) {
		if (results.isEmpty()) {
			Text(
				text = stringResource(R.string.nothing_found),
				modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		} else {
			Column(
				modifier = Modifier.padding(vertical = 8.dp),
			) {
				results.forEachIndexed { index, item ->
					SettingsSearchResultRow(
						item = item,
						onClick = { onItemClick(item) },
					)
					if (index != results.lastIndex) {
						Spacer(
							modifier = Modifier
								.fillMaxWidth()
								.padding(start = 20.dp, end = 20.dp)
								.height(1.dp)
								.background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)),
						)
					}
				}
			}
		}
	}
}

@Composable
private fun SettingsSearchResultRow(
	item: SettingsItem,
	onClick: () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(onClick = onClick)
			.padding(horizontal = 16.dp, vertical = 14.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Column(
			modifier = Modifier.weight(1f),
			verticalArrangement = Arrangement.spacedBy(4.dp),
		) {
			Text(
				text = item.title.toString(),
				style = MaterialTheme.typography.titleMedium,
				color = MaterialTheme.colorScheme.onSurface,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			Text(
				text = item.breadcrumbs.joinToString(" / "),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis,
			)
		}
		Spacer(modifier = Modifier.width(8.dp))
		Icon(
			imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
			contentDescription = null,
			tint = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

@Composable
private fun SettingsRootRow(
    item: SettingsRootItem,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = item.onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = rememberSafePainter(item.iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
