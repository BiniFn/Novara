package com.github.tvbox.osc.util;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;

public class MD5 {
    public static String encode(String source) {
        return string2MD5(source);
    }

    public static String string2MD5(String source) {
        if (source == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(source.getBytes("UTF-8"));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                int value = b & 0xff;
                if (value < 16) builder.append('0');
                builder.append(Integer.toHexString(value));
            }
            return builder.toString();
        } catch (Exception e) {
            LOG.e("MD5", e);
            return null;
        }
    }

    public static String getFileMd5(File file) {
        if (file == null || !file.exists()) return "";
        try (FileInputStream input = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            byte[] bytes = digest.digest();
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                int value = b & 0xff;
                if (value < 16) builder.append('0');
                builder.append(Integer.toHexString(value));
            }
            return builder.toString();
        } catch (Exception e) {
            LOG.e("MD5", e);
            return "";
        }
    }
}
