package org.skepsun.kototoro.extensions.runtime

import android.os.Bundle
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExternalExtensionMetadataSupportTest {

	@Test
	fun `getSourceClassNameOrNull prefers source class then factory`() {
		val sourceMeta = mockk<Bundle>()
		every { sourceMeta.getString("source.class") } returns "org.example.Source"
		every { sourceMeta.getString("source.factory") } returns "org.example.Factory"
		val factoryMeta = mockk<Bundle>()
		every { factoryMeta.getString("source.class") } returns null
		every { factoryMeta.getString("source.factory") } returns "org.example.Factory"
		val emptyMeta = mockk<Bundle>()
		every { emptyMeta.getString(any()) } returns null

		assertEquals(
			"org.example.Source",
			ExternalExtensionMetadataSupport.getSourceClassNameOrNull(
				metaData = sourceMeta,
				sourceClassKey = "source.class",
				sourceFactoryKey = "source.factory",
			),
		)
		assertEquals(
			"org.example.Factory",
			ExternalExtensionMetadataSupport.getSourceClassNameOrNull(
				metaData = factoryMeta,
				sourceClassKey = "source.class",
				sourceFactoryKey = "source.factory",
			),
		)
		assertNull(
			ExternalExtensionMetadataSupport.getSourceClassNameOrNull(
				metaData = emptyMeta,
				sourceClassKey = "source.class",
				sourceFactoryKey = "source.factory",
			),
		)
	}

	@Test
	fun `isNsfw follows integer manifest flag`() {
		val metaData = mockk<Bundle>()
		every { metaData.getInt("ext.nsfw", 0) } returns 1
		val emptyMeta = mockk<Bundle>()
		every { emptyMeta.getInt("ext.nsfw", 0) } returns 0

		assertTrue(ExternalExtensionMetadataSupport.isNsfw(metaData, "ext.nsfw"))
		assertFalse(ExternalExtensionMetadataSupport.isNsfw(emptyMeta, "ext.nsfw"))
	}

	@Test
	fun `hasDeclaredSource checks source class and factory keys`() {
		val sourceMeta = mockk<Bundle>()
		every { sourceMeta.containsKey("source.class") } returns true
		every { sourceMeta.containsKey("source.factory") } returns false
		val factoryMeta = mockk<Bundle>()
		every { factoryMeta.containsKey("source.class") } returns false
		every { factoryMeta.containsKey("source.factory") } returns true
		val emptyMeta = mockk<Bundle>()
		every { emptyMeta.containsKey(any()) } returns false

		assertTrue(
			ExternalExtensionMetadataSupport.hasDeclaredSource(
				metaData = sourceMeta,
				sourceClassKey = "source.class",
				sourceFactoryKey = "source.factory",
			),
		)
		assertTrue(
			ExternalExtensionMetadataSupport.hasDeclaredSource(
				metaData = factoryMeta,
				sourceClassKey = "source.class",
				sourceFactoryKey = "source.factory",
			),
		)
		assertFalse(
			ExternalExtensionMetadataSupport.hasDeclaredSource(
				metaData = emptyMeta,
				sourceClassKey = "source.class",
				sourceFactoryKey = "source.factory",
			),
		)
		assertFalse(
			ExternalExtensionMetadataSupport.hasDeclaredSource(
				metaData = null,
				sourceClassKey = "source.class",
				sourceFactoryKey = "source.factory",
			),
		)
	}

	@Test
	fun `getDeclaredSourceMetadataOrNull returns grouped manifest values`() {
		val metaData = mockk<Bundle>()
		every { metaData.getString("source.class") } returns "org.example.Source"
		every { metaData.getString("source.factory") } returns null
		every { metaData.getInt("ext.nsfw", 0) } returns 1
		val emptyMeta = mockk<Bundle>()
		every { emptyMeta.getString(any()) } returns null

		assertEquals(
			ExternalExtensionMetadataSupport.DeclaredSourceMetadata(
				sourceClassName = "org.example.Source",
				isNsfw = true,
			),
			ExternalExtensionMetadataSupport.getDeclaredSourceMetadataOrNull(
				metaData = metaData,
				sourceClassKey = "source.class",
				sourceFactoryKey = "source.factory",
				nsfwKey = "ext.nsfw",
			),
		)
		assertNull(
			ExternalExtensionMetadataSupport.getDeclaredSourceMetadataOrNull(
				metaData = emptyMeta,
				sourceClassKey = "source.class",
				sourceFactoryKey = "source.factory",
				nsfwKey = "ext.nsfw",
			),
		)
	}
}
