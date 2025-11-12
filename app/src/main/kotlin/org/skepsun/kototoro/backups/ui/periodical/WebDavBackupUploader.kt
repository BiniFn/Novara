package org.skepsun.kototoro.backups.ui.periodical

import androidx.annotation.CheckResult
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.skepsun.kototoro.core.network.BaseHttpClient
import org.skepsun.kototoro.core.prefs.AppSettings
import org.skepsun.kototoro.parsers.util.await
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.xml.parsers.DocumentBuilderFactory

data class BackupFileInfo(
    val name: String,
    val lastModified: Date,
    val size: Long,
    val dataVersion: Int? = null
)

class WebDavBackupUploader @Inject constructor(
    private val settings: AppSettings,
    @BaseHttpClient private val client: OkHttpClient,
) {

	private fun requireServerUrl(): String = checkNotNull(settings.backupWebDavServerUrl) {
		"WebDAV server URL not set in settings"
	}

	private fun requireRemotePath(): String = settings.backupWebDavRemotePath ?: ""

	@CheckResult
	private fun basicAuthHeaderOrNull(): String? {
		val user = settings.backupWebDavUsername
		val pass = settings.backupWebDavPassword
		return if (!user.isNullOrEmpty() && pass != null) Credentials.basic(user, pass) else null
	}

	private fun composeUrl(fileName: String?): String {
		// Compose URL like: <server>/<remotePath>/<fileName?>
		// When targeting a directory (fileName == null), ensure trailing slash for better WebDAV compatibility
		val base = requireServerUrl().trimEnd('/')
		val path = requireRemotePath().trim('/').let { if (it.isEmpty()) "" else "/$it" }
		return if (fileName == null) {
			"$base$path/"
		} else {
			"$base$path/$fileName"
		}
	}

    private fun buildVersionedRemoteName(version: Int): String {
        // 延续使用 .zip 扩展名，版本写在文件名：
        // kototoro-v<version>-<yyyyMMdd-HHmmss>.zip
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "kototoro-v${version}-${ts}.zip"
    }

    suspend fun uploadBackup(file: File, targetVersion: Int = settings.backupWebDavDataVersion) {
        val remoteName = buildVersionedRemoteName(targetVersion)
        val url = composeUrl(remoteName)
        val body = file.asRequestBody("application/zip".toMediaTypeOrNull())

        // 简单重试 + 退避，提升上传可靠性
        withRetry(maxAttempts = 3, initialDelayMs = 1000) {
            val builder = Request.Builder().url(url).put(body)
            basicAuthHeaderOrNull()?.let { builder.header("Authorization", it) }
            val resp = client.newCall(builder.build()).await()
            if (!resp.isSuccessful) {
                val code = resp.code
                val msg = resp.message
                resp.close()
                throw RuntimeException("WebDAV upload failed: $code $msg")
            }
            resp.close()
        }

        // 按最多保留数量进行修剪（失败不影响主流程）
        try {
            trimRemote(maxCount = 10)
        } catch (_: Exception) {
            // ignore trimming errors
        }
    }

    private suspend fun <T> withRetry(maxAttempts: Int = 3, initialDelayMs: Long = 1000, block: suspend () -> T): T {
        var attempt = 0
        var delayMs = initialDelayMs
        var lastError: Exception? = null
        while (attempt < maxAttempts) {
            try {
                return block()
            } catch (e: Exception) {
                lastError = e
                if (attempt == maxAttempts - 1) break
                delay(delayMs)
                delayMs *= 2
                attempt++
            }
        }
        throw lastError ?: RuntimeException("Unknown error in withRetry")
    }

	suspend fun sendTestConnection() {
		// Use PROPFIND Depth: 0 against the directory URL (with trailing slash)
		// Many WebDAV servers do not support HEAD on directories reliably
		val url = composeUrl(null)
		val propfindBody = """
			<?xml version="1.0" encoding="utf-8" ?>
			<D:propfind xmlns:D="DAV:">
				<D:prop>
					<D:displayname/>
				</D:prop>
			</D:propfind>
		""".trimIndent()

		val builder = Request.Builder()
			.url(url)
			.method("PROPFIND", okhttp3.RequestBody.create("application/xml".toMediaTypeOrNull(), propfindBody))
			.header("Depth", "0")
		basicAuthHeaderOrNull()?.let { builder.header("Authorization", it) }

		val resp = client.newCall(builder.build()).await()
		if (!resp.isSuccessful) {
			val code = resp.code
			val msg = resp.message
			resp.close()
			throw RuntimeException("WebDAV connection failed: $code $msg")
		}
		resp.close()
	}

    suspend fun listBackupFiles(): List<BackupFileInfo> {
		val url = composeUrl(null)
		val propfindBody = """
			<?xml version="1.0" encoding="utf-8" ?>
			<D:propfind xmlns:D="DAV:">
				<D:prop>
					<D:displayname/>
					<D:getlastmodified/>
					<D:getcontentlength/>
				</D:prop>
			</D:propfind>
		""".trimIndent()

		val builder = Request.Builder()
			.url(url)
			.method("PROPFIND", okhttp3.RequestBody.create("application/xml".toMediaTypeOrNull(), propfindBody))
			.header("Depth", "1")
		basicAuthHeaderOrNull()?.let { builder.header("Authorization", it) }

		val resp = client.newCall(builder.build()).await()
		if (!resp.isSuccessful) {
			val code = resp.code
			val msg = resp.message
			resp.close()
			throw RuntimeException("WebDAV PROPFIND failed: $code $msg")
		}

		val responseBody = resp.body?.string() ?: ""
		resp.close()

        return parseWebDavResponse(responseBody)
            .filter { isBackupFileName(it.name) }
            .map { it.copy(dataVersion = parseDataVersion(it.name)) }
            .sortedByDescending { it.lastModified } // 最新在前
    }

	suspend fun downloadBackup(fileName: String, destinationFile: File) {
		val url = composeUrl(fileName)
		val builder = Request.Builder().url(url).get()
		basicAuthHeaderOrNull()?.let { builder.header("Authorization", it) }

		val resp = client.newCall(builder.build()).await()
		if (!resp.isSuccessful) {
			val code = resp.code
			val msg = resp.message
			resp.close()
			throw RuntimeException("WebDAV download failed: $code $msg")
		}

		resp.body?.let { body ->
			FileOutputStream(destinationFile).use { output ->
				body.byteStream().use { input ->
					input.copyTo(output)
				}
			}
		}
		resp.close()
	}

    suspend fun getLatestBackup(): BackupFileInfo? {
        return listBackupFiles().firstOrNull()
    }

    private fun parseWebDavResponse(xml: String): List<BackupFileInfo> {
		val factory = DocumentBuilderFactory.newInstance()
		factory.isNamespaceAware = true
		val builder = factory.newDocumentBuilder()
		val document = builder.parse(xml.byteInputStream())

		val responses = document.getElementsByTagNameNS("DAV:", "response")
		val backupFiles = mutableListOf<BackupFileInfo>()

		for (i in 0 until responses.length) {
			val response = responses.item(i) as Element
			val href = response.getElementsByTagNameNS("DAV:", "href").item(0)?.textContent ?: continue
			
			// Skip directory entries
			if (href.endsWith("/")) continue
			
			val fileName = href.substringAfterLast("/")
			if (fileName.isEmpty()) continue

			val propstat = response.getElementsByTagNameNS("DAV:", "propstat").item(0) as? Element ?: continue
			val prop = propstat.getElementsByTagNameNS("DAV:", "prop").item(0) as? Element ?: continue

			val lastModifiedStr = prop.getElementsByTagNameNS("DAV:", "getlastmodified").item(0)?.textContent
			val sizeStr = prop.getElementsByTagNameNS("DAV:", "getcontentlength").item(0)?.textContent

			val lastModified = lastModifiedStr?.let { parseWebDavDate(it) } ?: Date(0)
			val size = sizeStr?.toLongOrNull() ?: 0L

            backupFiles.add(BackupFileInfo(fileName, lastModified, size))
        }

        return backupFiles
    }

    private fun parseWebDavDate(dateStr: String): Date {
		return try {
			// WebDAV uses RFC 2822 format: "Tue, 13 Dec 2022 10:30:00 GMT"
			val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH)
			format.parse(dateStr) ?: Date(0)
		} catch (e: Exception) {
			Date(0)
	}

    }

    private fun parseDataVersion(fileName: String): Int? {
        // 优先匹配新的 .kototoro 命名：kototoro-v<version>-<ts>.kototoro
        val strict = Regex("^kototoro(?:-data)?-v(\\d+)-")
        val m1 = strict.find(fileName)
        if (m1 != null) return m1.groupValues.getOrNull(1)?.toIntOrNull()
        // 兼容其它命名包含 -v<version>- 的形式
        val fallback = Regex("-v(\\d+)-")
        val m2 = fallback.find(fileName) ?: return null
        return m2.groupValues.getOrNull(1)?.toIntOrNull()
    }

    private fun isBackupFileName(name: String): Boolean {
        // 仅识别 .zip 备份文件
        return name.endsWith(".zip", ignoreCase = true)
    }

    suspend fun deleteRemote(fileName: String) {
        val url = composeUrl(fileName)
        val builder = Request.Builder().url(url).delete()
        basicAuthHeaderOrNull()?.let { builder.header("Authorization", it) }
        val resp = client.newCall(builder.build()).await()
        if (!resp.isSuccessful && resp.code != 404) {
            val code = resp.code
            val msg = resp.message
            resp.close()
            throw RuntimeException("WebDAV delete failed: $code $msg")
        }
        resp.close()
    }

    suspend fun trimRemote(maxCount: Int) {
        val files = listBackupFiles()
        if (files.size <= maxCount) return
        val toDelete = files.drop(maxCount)
        toDelete.forEach { file ->
            runCatching { deleteRemote(file.name) }
        }
    }
}