package com.github.tvbox.osc.util;

import android.os.Environment;

import com.github.tvbox.osc.base.App;

import java.io.File;

public class FileUtils {
    public static File getCacheDir() {
        return App.getInstance().getCacheDir();
    }

    public static File getExternalCacheDir() {
        return App.getInstance().getExternalCacheDir();
    }

    public static String getCachePath() {
        File dir = getCacheDir();
        return dir != null ? dir.getAbsolutePath() : "";
    }

    public static String getExternalCachePath() {
        File dir = getExternalCacheDir();
        if (dir != null) {
            return dir.getAbsolutePath();
        }
        return getCachePath();
    }

    public static String getFilePath() {
        return App.getInstance().getFilesDir().getAbsolutePath();
    }

    public static String getRootPath() {
        return Environment.getExternalStorageDirectory().getAbsolutePath();
    }

    public static boolean isWeekAgo(File file) {
        if (file == null || !file.exists()) return true;
        long threshold = 15L * 24L * 60L * 60L * 1000L;
        return System.currentTimeMillis() - file.lastModified() > threshold;
    }

    public static String getFileNameWithoutExt(String filePath) {
        if (filePath == null || filePath.isEmpty()) return "";
        String fileName = filePath;
        int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf(File.separatorChar));
        if (slash >= 0) {
            fileName = fileName.substring(slash + 1);
        }
        int dot = fileName.indexOf('.');
        if (dot >= 0) {
            fileName = fileName.substring(0, dot);
        }
        return fileName;
    }
}
