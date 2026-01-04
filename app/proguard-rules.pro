-optimizationpasses 8
-dontobfuscate
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
	public static void checkExpressionValueIsNotNull(...);
	public static void checkNotNullExpressionValue(...);
	public static void checkReturnedValueIsNotNull(...);
	public static void checkFieldIsNotNull(...);
	public static void checkParameterIsNotNull(...);
	public static void checkNotNullParameter(...);
}

-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn coil3.PlatformContext

-keep class org.skepsun.kototoro.settings.NotificationSettingsLegacyFragment
-keep class org.skepsun.kototoro.settings.about.changelog.ChangelogFragment

-keep class org.skepsun.kototoro.core.exceptions.* { *; }
-keep class org.skepsun.kototoro.core.prefs.ScreenshotsPolicy { *; }
-keep class org.skepsun.kototoro.backups.ui.periodical.PeriodicalBackupSettingsFragment { *; }
-keep class org.jsoup.parser.Tag
-keep class org.jsoup.internal.StringUtil

-keep class org.acra.security.NoKeyStoreFactory { *; }
-keep class org.acra.config.DefaultRetryPolicy { *; }
-keep class org.acra.attachment.DefaultAttachmentProvider { *; }
-keep class org.acra.sender.JobSenderService

# Rhino JavaScript engine
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.classfile.**

# Mihon / Tachiyomi Extension Support
# Extensions are separate APKs that depend on these classes in the host app.
# If they are stripped or renamed, extensions will fail to load or crash.
-keep class eu.kanade.tachiyomi.** { *; }
-keep interface eu.kanade.tachiyomi.** { *; }
-keep class uy.kohesive.injekt.** { *; }
-keep interface uy.kohesive.injekt.** { *; }
-keep class rx.** { *; }
-keep interface rx.** { *; }

# OkHttp and Okio are used by extensions
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }
-keep interface okio.** { *; }

# Keep the Mihon bridge and model classes
-keep class org.skepsun.kototoro.mihon.** { *; }

# Common dependencies used by extensions
-keep class org.jsoup.** { *; }
-keep class com.google.gson.** { *; }
-dontwarn okio.**
-dontwarn okhttp3.**
-dontwarn rx.**
