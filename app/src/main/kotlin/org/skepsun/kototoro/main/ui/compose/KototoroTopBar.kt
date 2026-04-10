package org.skepsun.kototoro.main.ui.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KototoroTopBar(
    title: String,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = { Text(text = title) },
        modifier = modifier.fillMaxWidth()
    )
}
