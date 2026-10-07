package com.youlong.priv;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.youlong.hd.YouLongApp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public final class YlPrivLauncher {

    private static final String TAG = "YlPrivLauncher";

    
    public static final String SERVER_ENTRY_CLASS =
            "com.youlong.priv.server.YlServerMain";

    
    public static final String SERVER_NICE_NAME = "youlong_priv";

    private YlPrivLauncher() {}

    
    public static final class SpawnResult {
        public final boolean ok;
        public final long pid;
        public final String message;

        SpawnResult(boolean ok, long pid, String message) {
            this.ok = ok;
            this.pid = pid;
            this.message = message;
        }
    }

    
    public static String apkPath() {
        try {
            YouLongApp app = YouLongApp.instance();
            if (app != null) {
                android.content.pm.ApplicationInfo ai = app.getApplicationInfo();
                if (ai.publicSourceDir != null) return ai.publicSourceDir;
                if (ai.sourceDir != null) return ai.sourceDir;
            }
        } catch (Throwable t) {
            Log.w(TAG, "从 YouLongApp 取 APK 路径失败", t);
        }
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object app = at.getMethod("currentApplication").invoke(null);
            if (app instanceof android.app.Application) {
                android.content.pm.ApplicationInfo ai =
                        ((android.app.Application) app).getApplicationInfo();
                if (ai.publicSourceDir != null) return ai.publicSourceDir;
                if (ai.sourceDir != null) return ai.sourceDir;
            }
        } catch (Throwable t) {
            Log.w(TAG, "反射 ActivityThread 取 APK 路径失败", t);
        }
        return null;
    }

    
    public static String appProcessPath() {
        boolean is64 = "64".equals(System.getProperty("sun.arch.data.model"))
                || isAbi64(System.getProperty("os.arch"));
        if (!is64 && Build.SUPPORTED_64_BIT_ABIS != null) {
            is64 = Build.SUPPORTED_64_BIT_ABIS.length > 0;
        }
        String path = is64 ? "/system/bin/app_process64" : "/system/bin/app_process32";
        if (new File(path).exists()) return path;
        String fallback = is64 ? "/system/bin/app_process32" : "/system/bin/app_process64";
        if (new File(fallback).exists()) return fallback;
        return "/system/bin/app_process";
    }

    private static boolean isAbi64(String abi) {
        return abi != null && abi.contains("64");
    }

    
    public static SpawnResult spawnServer(Context ctx) {
        String apk = apkPath();
        if (apk == null) {
            return new SpawnResult(false, -1, "无法定位本应用 APK 路径");
        }
        String appProcess = appProcessPath();
        List<String> argv = new ArrayList<>();
        argv.add("sh");
        argv.add("-c");
        
        String cmd = "CLASSPATH=" + shellQuote(apk) + " "
                + appProcess + " /system/bin"
                + " --nice-name=" + SERVER_NICE_NAME
                + " " + SERVER_ENTRY_CLASS
                + " >/dev/null 2>&1 &";
        argv.add(cmd);
        try {
            Process p = new ProcessBuilder(argv).start();
            int code = p.waitFor();
            if (code != 0) {
                return new SpawnResult(false, -1, "启动命令退出码=" + code);
            }
            long pid = waitForServerPid(6000L);
            Log.i(TAG, "已拉起特权服务端 pid=" + pid + " app_process=" + appProcess);
            return new SpawnResult(true, pid, "启动命令已下发");
        } catch (IOException e) {
            return new SpawnResult(false, -1, "启动失败: " + e);
        } catch (Throwable t) {
            return new SpawnResult(false, -1, "启动异常: " + t);
        }
    }

    
    private static long waitForServerPid(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "pidof " + SERVER_NICE_NAME)
                        .redirectErrorStream(true).start();
                java.io.BufferedReader r = new java.io.BufferedReader(
                        new java.io.InputStreamReader(p.getInputStream(), "UTF-8"));
                String line = r.readLine();
                r.close();
                p.waitFor();
                if (line != null && !line.trim().isEmpty()) {
                    String first = line.trim().split("\\s+")[0];
                    return Long.parseLong(first);
                }
            } catch (Throwable ignored) {
            }
            try { Thread.sleep(200); } catch (InterruptedException e) { break; }
        }
        return -1;
    }

    private static String shellQuote(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
