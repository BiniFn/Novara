package org.skepsun.kototoro.settings.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun SettingsSectionScaffold(
	title: String,
	onNavigateUp: () -> Unit,
	modifier: Modifier = Modifier,
	showTopBar: Boolean = true,
	actions: (@Composable BoxScope.() -> Unit)? = null,
	content: @Composable () -> Unit,
) {
	Column(
		modifier = modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.background)
			.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
			.windowInsetsPadding(WindowInsets.navigationBars),
	) {
		if (showTopBar) {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.background(MaterialTheme.colorScheme.background)
					.windowInsetsPadding(WindowInsets.statusBars)
					.height(56.dp)
					.padding(horizontal = 4.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				IconButton(onClick = onNavigateUp) {
					Icon(
						imageVector = Icons.AutoMirrored.Filled.ArrowBack,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.onSurface,
					)
				}
				Text(
					text = title,
					modifier = Modifier.weight(1f),
					style = MaterialTheme.typography.titleLarge,
					color = MaterialTheme.colorScheme.onSurface,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				if (actions != null) {
					Box(
						modifier = Modifier.fillMaxHeight(),
						contentAlignment = Alignment.CenterEnd,
						content = actions,
					)
				} else {
					Spacer(modifier = Modifier.width(48.dp))
				}
			}
		}
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.weight(1f),
			content = { content() },
		)
	}
}
