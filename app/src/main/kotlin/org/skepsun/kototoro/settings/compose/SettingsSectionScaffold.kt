package org.skepsun.kototoro.settings.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
	actions: (@Composable BoxScope.() -> Unit)? = null,
	content: @Composable () -> Unit,
) {
	Column(
		modifier = modifier
			.fillMaxSize()
			.windowInsetsPadding(WindowInsets.navigationBars),
	) {
		Surface(
			tonalElevation = 2.dp,
			color = MaterialTheme.colorScheme.surface,
		) {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.windowInsetsPadding(WindowInsets.statusBars)
					.padding(horizontal = 4.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				IconButton(onClick = onNavigateUp) {
					Icon(
						imageVector = Icons.AutoMirrored.Filled.ArrowBack,
						contentDescription = null,
					)
				}
				Text(
					text = title,
					modifier = Modifier.weight(1f),
					style = MaterialTheme.typography.titleLarge,
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
