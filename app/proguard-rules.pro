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

# Keep attributes needed for reflection and serialization
-keepattributes Signature
-keepattributes Annotation
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations

# Tachiyomi / Mihon API classes - keep everything including constructors
-keep class eu.kanade.tachiyomi.** { *; }
-keep interface eu.kanade.tachiyomi.** { *; }
-keepclassmembers class eu.kanade.tachiyomi.** {
    public <init>(...);
    public protected *;
}

# Injekt dependency injection (used by extensions via injectLazy)
-keep class uy.kohesive.injekt.** { *; }
-keep interface uy.kohesive.injekt.** { *; }
-keepclassmembers class uy.kohesive.injekt.** {
    public <init>(...);
    public protected *;
}

# RxJava (used by legacy extension API)
-keep class rx.** { *; }
-keep interface rx.** { *; }
-dontwarn rx.**

# OkHttp and Okio are used by extensions
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keepclassmembers class okhttp3.** {
    public <init>(...);
}
-keep class okio.** { *; }
-keep interface okio.** { *; }
-dontwarn okio.**
-dontwarn okhttp3.**

# Keep the Mihon bridge and model classes - preserve constructors for reflection
-keep class org.skepsun.kototoro.mihon.** { *; }
-keepclassmembers class org.skepsun.kototoro.mihon.** {
    public <init>(...);
    public protected *;
}
-keep class org.skepsun.kototoro.mihon.util.ChildFirstPathClassLoader { *; }
-keep class org.skepsun.kototoro.mihon.compat.** { *; }

# Jsoup (used by ParsedHttpSource)
-keep class org.jsoup.** { *; }
-keepclassmembers class org.jsoup.** {
    public <init>(...);
}

# Gson (some extensions may use it)
-keep class com.google.gson.** { *; }

# kotlinx.serialization (used by some extensions)
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.** { *; }
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <methods>;
}
-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
}
-keepclassmembers class **$$serializer {
    *** INSTANCE;
}

# Dalvik ClassLoader (used by ChildFirstPathClassLoader)
-keep class dalvik.system.** { *; }
-dontwarn dalvik.system.**

# Application class (Injekt injects Application instances)
-keep class android.app.Application { *; }
-keepclassmembers class * extends android.app.Application {
    public <init>(...);
}

# SharedPreferences (ConfigurableSource uses it)
-keep class android.content.SharedPreferences { *; }
-keep interface android.content.SharedPreferences$** { *; }

# Kotlin stdlib - essential classes needed by Mihon extensions
# Extensions use kotlin.Lazy, kotlin.LazyKt, etc. for lazy initialization
-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }
-dontwarn kotlin.**

# Specifically keep LazyKt and related classes
-keep class kotlin.LazyKt { *; }
-keep class kotlin.LazyKt__LazyJVMKt { *; }
-keep class kotlin.LazyKt__LazyKt { *; }
-keep class kotlin.SynchronizedLazyImpl { *; }
-keep class kotlin.UnsafeLazyImpl { *; }

# Kotlin reflection (some extensions may use it)
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**

# Kotlin coroutines (used by extensions-lib 1.5+)
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# AndroidX Preference (ConfigurableSource uses it to setup settings screen)
-keep class androidx.preference.** { *; }
-keep interface androidx.preference.** { *; }
-keepclassmembers class androidx.preference.** {
    public <init>(...);
    public protected *;
}

# RxJava 2/3 (used by newer extension APIs)
-keep class io.reactivex.** { *; }
-keep interface io.reactivex.** { *; }
-dontwarn io.reactivex.**

