package org.skepsun.kototoro.core.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration27To28 : Migration(27, 28) {

	override fun migrate(db: SupportSQLiteDatabase) {
		// 创建epub_chapters表
		db.execSQL(
			"""
			CREATE TABLE IF NOT EXISTS epub_chapters (
				id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
				novel_id TEXT NOT NULL,
				volume_id TEXT NOT NULL,
				chapter_index INTEGER NOT NULL,
				chapter_title TEXT NOT NULL,
				chapter_url TEXT NOT NULL,
				content TEXT NOT NULL,
				downloaded_at INTEGER NOT NULL
			)
			""".trimIndent(),
		)

		// 创建索引
		db.execSQL("CREATE INDEX IF NOT EXISTS index_epub_chapters_novel_id ON epub_chapters(novel_id)")
		db.execSQL("CREATE INDEX IF NOT EXISTS index_epub_chapters_volume_id ON epub_chapters(volume_id)")
		db.execSQL(
			"CREATE INDEX IF NOT EXISTS index_epub_chapters_novel_id_volume_id ON epub_chapters(novel_id, volume_id)",
		)
		db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_epub_chapters_chapter_url ON epub_chapters(chapter_url)")
	}
}
