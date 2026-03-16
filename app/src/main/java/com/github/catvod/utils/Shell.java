package com.github.catvod.utils;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Shell {

    private static final String TAG = Shell.class.getSimpleName();

    public static String exec(String command) {
        try {
            StringBuilder sb = new StringBuilder();
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            Log.d(TAG, "Shell command '" + command + "' exit code=" + process.waitFor());
            return Util.substring(sb.toString());
        } catch (Exception ignored) {
            return "";
        }
    }
}
