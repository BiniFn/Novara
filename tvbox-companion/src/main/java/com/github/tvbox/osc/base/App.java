package com.github.tvbox.osc.base;

import android.app.Application;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ChangedPackages;
import android.content.pm.FeatureInfo;
import android.content.pm.InstrumentationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.SharedLibraryInfo;
import android.content.pm.VersionedPackage;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;

import androidx.multidex.MultiDexApplication;

import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.util.AppManager;
import com.p2p.P2PClass;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class App extends MultiDexApplication {
    public static final String HOST_PACKAGE_NAME = "com.github.tvbox.osc";
    private static final String HOST_APPLICATION_CLASS_NAME = "com.github.tvbox.osc.base.App";

    private static volatile App instance;
    private static volatile Context appContext;
    private static volatile ApplicationInfo bridgedApplicationInfo;
    private static volatile PackageManager bridgedPackageManager;
    private static volatile File bridgedCacheDir;
    private static volatile File bridgedCodeCacheDir;
    private static volatile File bridgedFilesDir;
    private static volatile ClassLoader bridgedClassLoader;
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static volatile boolean attached = false;
    private static volatile P2PClass p;
    public static String burl;
    private static volatile String dashData;

    private volatile VodInfo vodInfo;

    public App() {
        instance = this;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        attached = true;
        appContext = normalizeContext(base);
        instance = this;
    }

    public static synchronized void init(Context context) {
        if (context != null) {
            appContext = normalizeContext(context);
            bridgedApplicationInfo = null;
            bridgedPackageManager = null;
            if (instance == null && context instanceof App) {
                instance = (App) context;
                attached = true;
            }
        }
    }

    public static App getInstance() {
        return instance;
    }

    public static P2PClass getp2p() {
        P2PClass current = p;
        if (current != null) {
            return current;
        }
        synchronized (App.class) {
            if (p == null) {
                File externalCacheDir = getInstance() != null ? getInstance().getExternalCacheDir() : null;
                p = new P2PClass(externalCacheDir != null ? externalCacheDir.getAbsolutePath() : "");
            }
            return p;
        }
    }

    public static synchronized void configureRuntimeDirs(File cacheDir, File codeCacheDir, File filesDir) {
        bridgedCacheDir = cacheDir;
        bridgedCodeCacheDir = codeCacheDir;
        bridgedFilesDir = filesDir;
    }

    public static synchronized void configureRuntimeClassLoader(ClassLoader classLoader) {
        bridgedClassLoader = classLoader;
    }

    public static void post(Runnable runnable) {
        if (runnable != null) {
            MAIN_HANDLER.post(runnable);
        }
    }

    public static void post(Runnable runnable, long delayMillis) {
        if (runnable == null) {
            return;
        }
        MAIN_HANDLER.removeCallbacks(runnable);
        if (delayMillis >= 0) {
            MAIN_HANDLER.postDelayed(runnable, delayMillis);
        }
    }

    public Activity getCurrentActivity() {
        return AppManager.getInstance().currentActivity();
    }

    public void setVodInfo(VodInfo vodinfo) {
        this.vodInfo = vodinfo;
    }

    public VodInfo getVodInfo() {
        return vodInfo;
    }

    public void setDashData(String data) {
        dashData = data;
    }

    public String getDashData() {
        return dashData;
    }

    private Context requireContext() {
        if (appContext == null) {
            throw new IllegalStateException("TVBox App bridge is not initialized");
        }
        return appContext;
    }

    private Context realContextOrNull() {
        if (appContext != null && appContext != this) {
            return appContext;
        }
        Context base = getBaseContext();
        if (base != null && base != this) {
            return base;
        }
        return null;
    }

    private static Context normalizeContext(Context context) {
        if (context == null) {
            return null;
        }
        if (!(context instanceof App)) {
            Context applicationContext = context.getApplicationContext();
            if (applicationContext != null && applicationContext != context && !(applicationContext instanceof App)) {
                return applicationContext;
            }
            return context;
        }
        Context base = ((App) context).getBaseContext();
        if (base != null && base != context) {
            Context applicationContext = base.getApplicationContext();
            if (applicationContext != null && applicationContext != base && !(applicationContext instanceof App)) {
                return applicationContext;
            }
            return base;
        }
        return context;
    }

    private void attach(Context context) {
        super.attachBaseContext(context);
    }

    @Override
    public Context getApplicationContext() {
        return appContext != null ? appContext : this;
    }

    @Override
    public ApplicationInfo getApplicationInfo() {
        Context realContext = realContextOrNull();
        if (realContext == null) {
            return super.getApplicationInfo();
        }
        ApplicationInfo cached = bridgedApplicationInfo;
        if (cached != null) {
            return cached;
        }
        ApplicationInfo realInfo = realContext.getApplicationInfo();
        ApplicationInfo bridgedInfo = new ApplicationInfo(realInfo);
        bridgedInfo.packageName = HOST_PACKAGE_NAME;
        bridgedInfo.processName = buildHostProcessName(realInfo.processName);
        bridgedInfo.className = HOST_APPLICATION_CLASS_NAME;
        bridgedApplicationInfo = bridgedInfo;
        return bridgedInfo;
    }

    @Override
    public AssetManager getAssets() {
        Context realContext = realContextOrNull();
        return realContext != null ? realContext.getAssets() : super.getAssets();
    }

    @Override
    public File getCacheDir() {
        if (bridgedCacheDir != null) {
            return bridgedCacheDir;
        }
        Context realContext = realContextOrNull();
        return realContext != null ? realContext.getCacheDir() : super.getCacheDir();
    }

    @Override
    public File getCodeCacheDir() {
        if (bridgedCodeCacheDir != null) {
            return bridgedCodeCacheDir;
        }
        Context realContext = realContextOrNull();
        return realContext != null ? realContext.getCodeCacheDir() : super.getCodeCacheDir();
    }

    @Override
    public File getDir(String name, int mode) {
        Context realContext = realContextOrNull();
        return realContext != null ? realContext.getDir(name, mode) : super.getDir(name, mode);
    }

    @Override
    public File getDatabasePath(String name) {
        Context realContext = realContextOrNull();
        return realContext != null ? realContext.getDatabasePath(name) : super.getDatabasePath(name);
    }

    @Override
    public File getFilesDir() {
        if (bridgedFilesDir != null) {
            return bridgedFilesDir;
        }
        Context realContext = realContextOrNull();
        return realContext != null ? realContext.getFilesDir() : super.getFilesDir();
    }

    @Override
    public File getExternalCacheDir() {
        Context realContext = realContextOrNull();
        return realContext != null ? realContext.getExternalCacheDir() : super.getExternalCacheDir();
    }

    @Override
    public ClassLoader getClassLoader() {
        Context realContext = realContextOrNull();
        if (bridgedClassLoader != null) {
            return bridgedClassLoader;
        }
        return realContext != null ? realContext.getClassLoader() : super.getClassLoader();
    }

    @Override
    public PackageManager getPackageManager() {
        Context realContext = realContextOrNull();
        if (realContext == null) {
            return super.getPackageManager();
        }
        PackageManager cached = bridgedPackageManager;
        if (cached != null) {
            return cached;
        }
        PackageManager bridged = new BridgedPackageManager(this, realContext);
        bridgedPackageManager = bridged;
        return bridged;
    }

    @Override
    public Context createPackageContext(String packageName, int flags) throws PackageManager.NameNotFoundException {
        if (HOST_PACKAGE_NAME.equals(packageName)) {
            return this;
        }
        Context realContext = realContextOrNull();
        return realContext != null ? realContext.createPackageContext(packageName, flags) : super.createPackageContext(packageName, flags);
    }

    @Override
    public String getPackageCodePath() {
        Context realContext = realContextOrNull();
        return realContext != null ? realContext.getPackageCodePath() : super.getPackageCodePath();
    }

    @Override
    public Resources getResources() {
        Context realContext = realContextOrNull();
        return realContext != null ? realContext.getResources() : super.getResources();
    }

    @Override
    public String getPackageResourcePath() {
        Context realContext = realContextOrNull();
        return realContext != null ? realContext.getPackageResourcePath() : super.getPackageResourcePath();
    }

    @Override
    public String getPackageName() {
        return HOST_PACKAGE_NAME;
    }

    private String buildHostProcessName(String realProcessName) {
        if (realProcessName == null || realProcessName.isEmpty()) {
            return HOST_PACKAGE_NAME;
        }
        int suffixIndex = realProcessName.indexOf(':');
        if (suffixIndex >= 0) {
            return HOST_PACKAGE_NAME + realProcessName.substring(suffixIndex);
        }
        return HOST_PACKAGE_NAME;
    }

    private boolean isHostPackageName(String packageName) {
        return HOST_PACKAGE_NAME.equals(packageName);
    }

    private String getRealPackageName() {
        return requireContext().getPackageName();
    }

    private String mapToRealPackageName(String packageName) {
        if (isHostPackageName(packageName)) {
            return getRealPackageName();
        }
        return packageName;
    }

    private String bridgeReportedPackageName(String packageName) {
        if (getRealPackageName().equals(packageName)) {
            return HOST_PACKAGE_NAME;
        }
        return packageName;
    }

    private ApplicationInfo bridgeApplicationInfoIfNeeded(ApplicationInfo info) {
        if (info == null || !getRealPackageName().equals(info.packageName)) {
            return info;
        }
        ApplicationInfo bridgedInfo = new ApplicationInfo(info);
        bridgedInfo.packageName = HOST_PACKAGE_NAME;
        bridgedInfo.processName = buildHostProcessName(info.processName);
        bridgedInfo.className = HOST_APPLICATION_CLASS_NAME;
        return bridgedInfo;
    }

    private PackageInfo bridgePackageInfoIfNeeded(PackageInfo info) {
        if (info == null || !getRealPackageName().equals(info.packageName)) {
            return info;
        }
        PackageInfo bridgedInfo = shallowCopyPackageInfo(info);
        bridgedInfo.packageName = HOST_PACKAGE_NAME;
        bridgedInfo.applicationInfo = bridgeApplicationInfoIfNeeded(info.applicationInfo);
        return bridgedInfo;
    }

    private PackageInfo shallowCopyPackageInfo(PackageInfo source) {
        PackageInfo copy = new PackageInfo();
        Field[] fields = PackageInfo.class.getFields();
        for (Field field : fields) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) {
                continue;
            }
            try {
                field.set(copy, field.get(source));
            } catch (IllegalAccessException ignored) {
            }
        }
        return copy;
    }

    private static final class BridgedPackageManager extends PackageManager {
        private final App owner;
        private final Context realContext;
        private final PackageManager base;

        private BridgedPackageManager(App owner, Context realContext) {
            this.owner = owner;
            this.realContext = realContext;
            this.base = realContext.getPackageManager();
        }

        private String mapPackageName(String packageName) {
            return owner.mapToRealPackageName(packageName);
        }

        private String bridgePackageName(String packageName) {
            return owner.bridgeReportedPackageName(packageName);
        }

        private ApplicationInfo bridgeApplicationInfo(ApplicationInfo info) {
            return owner.bridgeApplicationInfoIfNeeded(info);
        }

        private PackageInfo bridgePackageInfo(PackageInfo info) {
            return owner.bridgePackageInfoIfNeeded(info);
        }

        private ApplicationInfo mapApplicationInfo(ApplicationInfo info) {
            if (info == null) {
                return null;
            }
            if (owner.isHostPackageName(info.packageName)) {
                return bridgeApplicationInfo(realContext.getApplicationInfo());
            }
            return info;
        }

        private List<ApplicationInfo> bridgeInstalledApplications(List<ApplicationInfo> infos) {
            List<ApplicationInfo> bridged = new ArrayList<>(infos.size());
            for (ApplicationInfo info : infos) {
                bridged.add(bridgeApplicationInfo(info));
            }
            return bridged;
        }

        private List<PackageInfo> bridgeInstalledPackages(List<PackageInfo> infos) {
            List<PackageInfo> bridged = new ArrayList<>(infos.size());
            for (PackageInfo info : infos) {
                bridged.add(bridgePackageInfo(info));
            }
            return bridged;
        }

        private String[] bridgePackagesForUid(String[] packages) {
            if (packages == null || packages.length == 0) {
                return packages;
            }
            String[] bridged = Arrays.copyOf(packages, packages.length);
            for (int index = 0; index < bridged.length; index++) {
                bridged[index] = bridgePackageName(bridged[index]);
            }
            return bridged;
        }

        @Override
        public void addPackageToPreferred(String packageName) {
            base.addPackageToPreferred(mapPackageName(packageName));
        }

        @Override
        public boolean addPermission(PermissionInfo info) {
            return base.addPermission(info);
        }

        @Override
        public boolean addPermissionAsync(PermissionInfo info) {
            return base.addPermissionAsync(info);
        }

        @Override
        public void addPreferredActivity(IntentFilter filter, int match, ComponentName[] set, ComponentName activity) {
            base.addPreferredActivity(filter, match, set, activity);
        }

        @Override
        public boolean canRequestPackageInstalls() {
            return base.canRequestPackageInstalls();
        }

        @Override
        public String[] canonicalToCurrentPackageNames(String[] names) {
            return base.canonicalToCurrentPackageNames(names);
        }

        @Override
        public int checkPermission(String permName, String packageName) {
            return base.checkPermission(permName, mapPackageName(packageName));
        }

        @Override
        public int checkSignatures(int uid1, int uid2) {
            return base.checkSignatures(uid1, uid2);
        }

        @Override
        public int checkSignatures(String pkg1, String pkg2) {
            return base.checkSignatures(mapPackageName(pkg1), mapPackageName(pkg2));
        }

        @Override
        public void clearInstantAppCookie() {
            base.clearInstantAppCookie();
        }

        @Override
        public void clearPackagePreferredActivities(String packageName) {
            base.clearPackagePreferredActivities(mapPackageName(packageName));
        }

        @Override
        public String[] currentToCanonicalPackageNames(String[] names) {
            return base.currentToCanonicalPackageNames(names);
        }

        @Override
        public void extendVerificationTimeout(int id, int verificationCodeAtTimeout, long millisecondsToDelay) {
            base.extendVerificationTimeout(id, verificationCodeAtTimeout, millisecondsToDelay);
        }

        @Override
        public Drawable getActivityBanner(ComponentName activityName) throws NameNotFoundException {
            return base.getActivityBanner(activityName);
        }

        @Override
        public Drawable getActivityBanner(Intent intent) throws NameNotFoundException {
            return base.getActivityBanner(intent);
        }

        @Override
        public Drawable getActivityIcon(ComponentName activityName) throws NameNotFoundException {
            return base.getActivityIcon(activityName);
        }

        @Override
        public Drawable getActivityIcon(Intent intent) throws NameNotFoundException {
            return base.getActivityIcon(intent);
        }

        @Override
        public ActivityInfo getActivityInfo(ComponentName component, int flags) throws NameNotFoundException {
            return base.getActivityInfo(component, flags);
        }

        @Override
        public Drawable getActivityLogo(ComponentName activityName) throws NameNotFoundException {
            return base.getActivityLogo(activityName);
        }

        @Override
        public Drawable getActivityLogo(Intent intent) throws NameNotFoundException {
            return base.getActivityLogo(intent);
        }

        @Override
        public List<PermissionGroupInfo> getAllPermissionGroups(int flags) {
            return base.getAllPermissionGroups(flags);
        }

        @Override
        public Drawable getApplicationBanner(ApplicationInfo info) {
            return base.getApplicationBanner(mapApplicationInfo(info));
        }

        @Override
        public Drawable getApplicationBanner(String packageName) throws NameNotFoundException {
            return base.getApplicationBanner(mapPackageName(packageName));
        }

        @Override
        public int getApplicationEnabledSetting(String packageName) {
            return base.getApplicationEnabledSetting(mapPackageName(packageName));
        }

        @Override
        public Drawable getApplicationIcon(ApplicationInfo info) {
            return base.getApplicationIcon(mapApplicationInfo(info));
        }

        @Override
        public Drawable getApplicationIcon(String packageName) throws NameNotFoundException {
            return base.getApplicationIcon(mapPackageName(packageName));
        }

        @Override
        public ApplicationInfo getApplicationInfo(String packageName, int flags) throws NameNotFoundException {
            return bridgeApplicationInfo(base.getApplicationInfo(mapPackageName(packageName), flags));
        }

        @Override
        public CharSequence getApplicationLabel(ApplicationInfo info) {
            return base.getApplicationLabel(mapApplicationInfo(info));
        }

        @Override
        public Drawable getApplicationLogo(ApplicationInfo info) {
            return base.getApplicationLogo(mapApplicationInfo(info));
        }

        @Override
        public Drawable getApplicationLogo(String packageName) throws NameNotFoundException {
            return base.getApplicationLogo(mapPackageName(packageName));
        }

        @Override
        public ChangedPackages getChangedPackages(int sequenceNumber) {
            return base.getChangedPackages(sequenceNumber);
        }

        @Override
        public int getComponentEnabledSetting(ComponentName componentName) {
            return base.getComponentEnabledSetting(componentName);
        }

        @Override
        public Drawable getDefaultActivityIcon() {
            return base.getDefaultActivityIcon();
        }

        @Override
        public Drawable getDrawable(String packageName, int resid, ApplicationInfo info) {
            return base.getDrawable(mapPackageName(packageName), resid, mapApplicationInfo(info));
        }

        @Override
        public List<ApplicationInfo> getInstalledApplications(int flags) {
            return bridgeInstalledApplications(base.getInstalledApplications(flags));
        }

        @Override
        public List<PackageInfo> getInstalledPackages(int flags) {
            return bridgeInstalledPackages(base.getInstalledPackages(flags));
        }

        @Override
        public String getInstallerPackageName(String packageName) {
            return base.getInstallerPackageName(mapPackageName(packageName));
        }

        @Override
        public byte[] getInstantAppCookie() {
            return base.getInstantAppCookie();
        }

        @Override
        public int getInstantAppCookieMaxBytes() {
            return base.getInstantAppCookieMaxBytes();
        }

        @Override
        public InstrumentationInfo getInstrumentationInfo(ComponentName className, int flags) throws NameNotFoundException {
            return base.getInstrumentationInfo(className, flags);
        }

        @Override
        public Intent getLaunchIntentForPackage(String packageName) {
            return base.getLaunchIntentForPackage(mapPackageName(packageName));
        }

        @Override
        public Intent getLeanbackLaunchIntentForPackage(String packageName) {
            return base.getLeanbackLaunchIntentForPackage(mapPackageName(packageName));
        }

        @Override
        public String getNameForUid(int uid) {
            return bridgePackageName(base.getNameForUid(uid));
        }

        @Override
        public int[] getPackageGids(String packageName) throws NameNotFoundException {
            return base.getPackageGids(mapPackageName(packageName));
        }

        @Override
        public int[] getPackageGids(String packageName, int flags) throws NameNotFoundException {
            return base.getPackageGids(mapPackageName(packageName), flags);
        }

        @Override
        public PackageInfo getPackageInfo(VersionedPackage versionedPackage, int flags) throws NameNotFoundException {
            VersionedPackage mapped = versionedPackage;
            if (versionedPackage != null && owner.isHostPackageName(versionedPackage.getPackageName())) {
                mapped = new VersionedPackage(owner.getRealPackageName(), versionedPackage.getLongVersionCode());
            }
            return bridgePackageInfo(base.getPackageInfo(mapped, flags));
        }

        @Override
        public PackageInfo getPackageInfo(String packageName, int flags) throws NameNotFoundException {
            return bridgePackageInfo(base.getPackageInfo(mapPackageName(packageName), flags));
        }

        @Override
        public PackageInstaller getPackageInstaller() {
            return base.getPackageInstaller();
        }

        @Override
        public int getPackageUid(String packageName, int flags) throws NameNotFoundException {
            return base.getPackageUid(mapPackageName(packageName), flags);
        }

        @Override
        public String[] getPackagesForUid(int uid) {
            return bridgePackagesForUid(base.getPackagesForUid(uid));
        }

        @Override
        public List<PackageInfo> getPackagesHoldingPermissions(String[] permissions, int flags) {
            return bridgeInstalledPackages(base.getPackagesHoldingPermissions(permissions, flags));
        }

        @Override
        public PermissionGroupInfo getPermissionGroupInfo(String name, int flags) throws NameNotFoundException {
            return base.getPermissionGroupInfo(name, flags);
        }

        @Override
        public PermissionInfo getPermissionInfo(String name, int flags) throws NameNotFoundException {
            return base.getPermissionInfo(name, flags);
        }

        @Override
        public int getPreferredActivities(List<IntentFilter> outFilters, List<ComponentName> outActivities, String packageName) {
            return base.getPreferredActivities(outFilters, outActivities, mapPackageName(packageName));
        }

        @Override
        public List<PackageInfo> getPreferredPackages(int flags) {
            return bridgeInstalledPackages(base.getPreferredPackages(flags));
        }

        @Override
        public ProviderInfo getProviderInfo(ComponentName component, int flags) throws NameNotFoundException {
            return base.getProviderInfo(component, flags);
        }

        @Override
        public ActivityInfo getReceiverInfo(ComponentName component, int flags) throws NameNotFoundException {
            return base.getReceiverInfo(component, flags);
        }

        @Override
        public Resources getResourcesForActivity(ComponentName activityName) throws NameNotFoundException {
            return base.getResourcesForActivity(activityName);
        }

        @Override
        public Resources getResourcesForApplication(ApplicationInfo app) throws NameNotFoundException {
            return base.getResourcesForApplication(mapApplicationInfo(app));
        }

        @Override
        public Resources getResourcesForApplication(String packageName) throws NameNotFoundException {
            return base.getResourcesForApplication(mapPackageName(packageName));
        }

        @Override
        public ServiceInfo getServiceInfo(ComponentName component, int flags) throws NameNotFoundException {
            return base.getServiceInfo(component, flags);
        }

        @Override
        public List<SharedLibraryInfo> getSharedLibraries(int flags) {
            return base.getSharedLibraries(flags);
        }

        @Override
        public FeatureInfo[] getSystemAvailableFeatures() {
            return base.getSystemAvailableFeatures();
        }

        @Override
        public String[] getSystemSharedLibraryNames() {
            return base.getSystemSharedLibraryNames();
        }

        @Override
        public CharSequence getText(String packageName, int resid, ApplicationInfo info) {
            return base.getText(mapPackageName(packageName), resid, mapApplicationInfo(info));
        }

        @Override
        public Drawable getUserBadgedDrawableForDensity(Drawable drawable, UserHandle user, Rect badgeLocation, int badgeDensity) {
            return base.getUserBadgedDrawableForDensity(drawable, user, badgeLocation, badgeDensity);
        }

        @Override
        public Drawable getUserBadgedIcon(Drawable icon, UserHandle user) {
            return base.getUserBadgedIcon(icon, user);
        }

        @Override
        public CharSequence getUserBadgedLabel(CharSequence label, UserHandle user) {
            return base.getUserBadgedLabel(label, user);
        }

        @Override
        public XmlResourceParser getXml(String packageName, int resid, ApplicationInfo info) {
            return base.getXml(mapPackageName(packageName), resid, mapApplicationInfo(info));
        }

        @Override
        public boolean hasSystemFeature(String name) {
            return base.hasSystemFeature(name);
        }

        @Override
        public boolean hasSystemFeature(String name, int version) {
            return base.hasSystemFeature(name, version);
        }

        @Override
        public boolean isInstantApp() {
            return base.isInstantApp();
        }

        @Override
        public boolean isInstantApp(String packageName) {
            return base.isInstantApp(mapPackageName(packageName));
        }

        @Override
        public boolean isPermissionRevokedByPolicy(String permName, String packageName) {
            return base.isPermissionRevokedByPolicy(permName, mapPackageName(packageName));
        }

        @Override
        public boolean isSafeMode() {
            return base.isSafeMode();
        }

        @Override
        public List<ResolveInfo> queryBroadcastReceivers(Intent intent, int flags) {
            return base.queryBroadcastReceivers(intent, flags);
        }

        @Override
        public List<ProviderInfo> queryContentProviders(String processName, int uid, int flags) {
            return base.queryContentProviders(processName, uid, flags);
        }

        @Override
        public List<InstrumentationInfo> queryInstrumentation(String targetPackage, int flags) {
            return base.queryInstrumentation(mapPackageName(targetPackage), flags);
        }

        @Override
        public List<ResolveInfo> queryIntentActivities(Intent intent, int flags) {
            return base.queryIntentActivities(intent, flags);
        }

        @Override
        public List<ResolveInfo> queryIntentActivityOptions(ComponentName caller, Intent[] specifics, Intent intent, int flags) {
            return base.queryIntentActivityOptions(caller, specifics, intent, flags);
        }

        @Override
        public List<ResolveInfo> queryIntentContentProviders(Intent intent, int flags) {
            return base.queryIntentContentProviders(intent, flags);
        }

        @Override
        public List<ResolveInfo> queryIntentServices(Intent intent, int flags) {
            return base.queryIntentServices(intent, flags);
        }

        @Override
        public List<PermissionInfo> queryPermissionsByGroup(String group, int flags) throws NameNotFoundException {
            return base.queryPermissionsByGroup(group, flags);
        }

        @Override
        public void removePackageFromPreferred(String packageName) {
            base.removePackageFromPreferred(mapPackageName(packageName));
        }

        @Override
        public void removePermission(String name) {
            base.removePermission(name);
        }

        @Override
        public ResolveInfo resolveActivity(Intent intent, int flags) {
            return base.resolveActivity(intent, flags);
        }

        @Override
        public ProviderInfo resolveContentProvider(String name, int flags) {
            return base.resolveContentProvider(name, flags);
        }

        @Override
        public ResolveInfo resolveService(Intent intent, int flags) {
            return base.resolveService(intent, flags);
        }

        @Override
        public void setApplicationCategoryHint(String packageName, int categoryHint) {
            base.setApplicationCategoryHint(mapPackageName(packageName), categoryHint);
        }

        @Override
        public void setApplicationEnabledSetting(String packageName, int newState, int flags) {
            base.setApplicationEnabledSetting(mapPackageName(packageName), newState, flags);
        }

        @Override
        public void setComponentEnabledSetting(ComponentName componentName, int newState, int flags) {
            base.setComponentEnabledSetting(componentName, newState, flags);
        }

        @Override
        public void setInstallerPackageName(String targetPackage, String installerPackageName) {
            base.setInstallerPackageName(mapPackageName(targetPackage), mapPackageName(installerPackageName));
        }

        @Override
        public void updateInstantAppCookie(byte[] cookie) {
            base.updateInstantAppCookie(cookie);
        }

        @Override
        public void verifyPendingInstall(int id, int verificationCode) {
            base.verifyPendingInstall(id, verificationCode);
        }
    }
}
