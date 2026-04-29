package org.skepsun.kototoro.settings

import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsActivityLayoutBindingTest {

	@Test
	fun inflate_usesXmlLayoutForCurrentConfiguration() {
		val context = ApplicationProvider.getApplicationContext<android.content.Context>()
		val binding = SettingsActivityLayoutBinding.inflate(LayoutInflater.from(context))
		val expectedRootClass = if (context.resources.configuration.screenWidthDp >= 600) {
			ConstraintLayout::class.java
		} else {
			CoordinatorLayout::class.java
		}

		assertTrue(
			"SettingsActivity should use the existing XML layout container for this configuration",
			expectedRootClass.isInstance(binding.root),
		)
	}
}
