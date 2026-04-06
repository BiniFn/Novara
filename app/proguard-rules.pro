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

# Preference XML and FragmentManager may instantiate fragments by class name.
# Keep all Fragment classes in the app to avoid release-only ClassNotFoundException.
-keep class org.skepsun.kototoro.**Fragment { *; }

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

# Mihon / Aniyomi Extension Support
# Extensions are separate APKs that depend on these classes in the host app.
# If they are stripped or renamed, extensions will fail to load or crash.

# Keep attributes needed for reflection and serialization
-keepattributes Signature
-keepattributes Annotation
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# Tachiyomi / Mihon API classes - keep everything including constructors
-keep class eu.kanade.tachiyomi.** { *; }
-keep interface eu.kanade.tachiyomi.** { *; }
-keepclassmembers class eu.kanade.tachiyomi.** {
    public <init>(...);
    public protected *;
}

# QuickJS compat classes for extensions
-keep class app.cash.quickjs.** { *; }
-keep interface app.cash.quickjs.** { *; }
-keepclassmembers class app.cash.quickjs.** {
    public <init>(...);
    public protected *;
}

# Dokar QuickJS (underlying engine or native requests)
-keep class com.dokar.quickjs.** { *; }
-keep interface com.dokar.quickjs.** { *; }
-keepclassmembers class com.dokar.quickjs.** {
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

# Keep the Mihon / Aniyomi bridge and model classes - preserve constructors for reflection
-keep class org.skepsun.kototoro.mihon.** { *; }
-keepclassmembers class org.skepsun.kototoro.mihon.** {
    public <init>(...);
    public protected *;
}
-keep class org.skepsun.kototoro.mihon.util.ChildFirstPathClassLoader { *; }
-keep class org.skepsun.kototoro.mihon.compat.** { *; }

-keep class org.skepsun.kototoro.aniyomi.** { *; }
-keepclassmembers class org.skepsun.kototoro.aniyomi.** {
    public <init>(...);
    public protected *;
}
-keep class org.skepsun.kototoro.aniyomi.util.ChildFirstPathClassLoader { *; }
-keep class org.skepsun.kototoro.aniyomi.compat.** { *; }

# IReader API classes
-keep class ireader.** { *; }
-keep interface ireader.** { *; }
-keepclassmembers class ireader.** {
    public <init>(...);
    public protected *;
}

# Kototoro IReader bridge classes
-keep class org.skepsun.kototoro.ireader.** { *; }
-keepclassmembers class org.skepsun.kototoro.ireader.** {
    public <init>(...);
    public protected *;
}

# Jsoup (used by ParsedHttpSource)
-keep class org.jsoup.** { *; }
-keepclassmembers class org.jsoup.** {
    public <init>(...);
}
-dontwarn com.google.re2j.**

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

# MPV (is.xyz.mpv)
-keep class is.xyz.mpv.** { *; }
-keep interface is.xyz.mpv.** { *; }
-keepclassmembers class is.xyz.mpv.** { *; }

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

# TVBox jar runtime classes loaded via reflection / DexClassLoader
-keep class com.github.catvod.** { *; }
-keep class com.github.tvbox.osc.** { *; }

# Bangumi tracking discovery – prevent R8 from stripping HTML-parsing code paths
-keep class org.skepsun.kototoro.scrobbling.bangumi.data.BangumiRepository { *; }
-keep class org.skepsun.kototoro.tracking.discovery.** { *; }

# IReader Extension Support
# IReader extensions are separate APKs. ChildFirstPathClassLoader delegates
# ireader.core.* to the parent (host app) ClassLoader, so these classes must
# be preserved. The bridge classes use reflection to instantiate sources.
-keep class ireader.core.** { *; }
-keep interface ireader.core.** { *; }
-keepclassmembers class ireader.core.** {
    public <init>(...);
    public protected *;
}

# Keep the IReader bridge and model classes
-keep class org.skepsun.kototoro.ireader.** { *; }
-keepclassmembers class org.skepsun.kototoro.ireader.** {
    public <init>(...);
    public protected *;
}

# LNReader Plugin Support
# LNReader uses QuickJS-kt to execute JS plugins. The bridge and model classes
# must be preserved for serialization and reflection.
-keep class org.skepsun.kototoro.core.lnreader.** { *; }
-keepclassmembers class org.skepsun.kototoro.core.lnreader.** {
    public <init>(...);
    public protected *;
}

# Ksoup (com.fleeksoft.ksoup) - used by IReader extensions at runtime.
# The host app bundles ksoup via io.github.ireaderorg:source-api, but R8 may strip
# methods that the host doesn't directly reference. Extensions call these methods
# via the shared ClassLoader, so all members must be preserved.
-keep class com.fleeksoft.ksoup.** { *; }
-keep interface com.fleeksoft.ksoup.** { *; }
-keepclassmembers class com.fleeksoft.ksoup.** {
    public <init>(...);
    public protected *;
}

# Ktor is used heavily by IReader extensions
-keep class io.ktor.** { *; }
-keep interface io.ktor.** { *; }
-keepclassmembers class io.ktor.** {
    public <init>(...);
    public protected *;
}
-dontwarn io.ktor.**

# JAR Extension Parser APIs (Kototoro / Kotatsu)
# Required because JarExtensionLoader resolves classes via string reflection against the parent ClassLoader.
-keep class org.skepsun.kototoro.parsers.** { *; }
-keep interface org.skepsun.kototoro.parsers.** { *; }
-keep @interface org.skepsun.kototoro.parsers.** { *; }
-keepclassmembers class org.skepsun.kototoro.parsers.** {
    public <init>(...);
    public protected *;
}

-keep class org.skepsun.kototoro.core.parser.** { *; }
-keep interface org.skepsun.kototoro.core.parser.** { *; }
-keepclassmembers class org.skepsun.kototoro.core.parser.** {
    public <init>(...);
    public protected *;
}

-keep class org.koitharu.kotatsu.parsers.** { *; }
-keep interface org.koitharu.kotatsu.parsers.** { *; }
-keep @interface org.koitharu.kotatsu.parsers.** { *; }
-keepclassmembers class org.koitharu.kotatsu.parsers.** {
    public <init>(...);
    public protected *;
}

-keep class org.koitharu.kotatsu.core.parser.** { *; }
-keep interface org.koitharu.kotatsu.core.parser.** { *; }
-keepclassmembers class org.koitharu.kotatsu.core.parser.** {
    public <init>(...);
    public protected *;
}

# AndroidX Collection - JAR plugins may reference SparseArrayCompat and other collection classes.
# R8 may strip these if the host app no longer directly uses them, but runtime-loaded JARs still need them.
-keep class androidx.collection.** { *; }
-keep interface androidx.collection.** { *; }


# ONNX Runtime JNI classes
-keep class ai.onnxruntime.** { *; }

# Google LiteRT (TFLite) JNI classes

# RealCUGAN Engine JNI classes
-keep class com.akari.realcugan_ncnn_android.** { *; }
-keep class RealCUGANOption { *; }
-keep class ModelName { *; }

# Workaround for NoSuchFieldError on buggy API 26-28 OEM ROMs missing Java 8 UnicodeBlock extensions.
# This prevents R8 from aggressively outlining the field or removing guarding try-catch blocks.
-dontwarn java.lang.Character$UnicodeBlock
-keepclassmembers class * {
    java.lang.Character$UnicodeBlock CJK_UNIFIED_IDEOGRAPHS*;
}
-keep,allowshrinking,allowobfuscation class com.equationl.** { *; }
-keep,allowshrinking,allowobfuscation class ai.djl.** { *; }
-keep,allowshrinking,allowobfuscation class ai.onnxruntime.** { *; }


# Deep Java Library (DJL) tokenizers and sentencepiece
-keep class ai.djl.** { *; }
-dontwarn java.awt.**
-dontwarn java.lang.management.**
-dontwarn javax.imageio.**
-dontwarn javax.sound.**
-dontwarn javax.tools.**
