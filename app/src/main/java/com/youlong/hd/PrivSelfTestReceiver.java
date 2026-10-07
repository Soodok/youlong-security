package com.youlong.hd;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;


public class PrivSelfTestReceiver extends BroadcastReceiver {

    
    public static final String ACTION = "com.youlong.hd.PRIV_SELFTEST";
    
    public static final String ACTION_ENABLE = "com.youlong.hd.PRIV_SELFTEST_ENABLE";
    
    public static final String ACTION_DISABLE = "com.youlong.hd.PRIV_SELFTEST_DISABLE";
    
    public static final String ACTION_REQUEST = "com.youlong.hd.PRIV_SELFTEST_REQUEST";
    
    public static final String ACTION_OPEN_APPLIST = "com.youlong.hd.PRIV_SELFTEST_OPEN_APPLIST";
    
    public static final String ACTION_OPEN_PAIRING = "com.youlong.hd.PRIV_SELFTEST_OPEN_PAIRING";

    static final String PREFS = "shield_prefs";
    static final String KEY_SELFTEST = "priv_selftest_enabled";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        final String action = intent.getAction();

        if (ACTION_ENABLE.equals(action)) {
            setEnabled(context, true);
            return;
        }
        if (ACTION_DISABLE.equals(action)) {
            setEnabled(context, false);
            return;
        }
        if (ACTION_REQUEST.equals(action)) {
            if (!isDebugEnabled(context)) {
                CrashLogger.event("[特权面板] 授权请求被忽略（调试开关未开）");
                return;
            }
            
            
            
            
            CrashLogger.event("[特权面板] 调试发起授权请求");
            try {
                roro.stellar.Stellar.INSTANCE.requestPermission("stellar", 1001);
            } catch (Throwable t) {
                CrashLogger.event("[特权面板] 发起授权失败", t);
            }
            return;
        }
        if (ACTION_OPEN_PAIRING.equals(action)) {
            if (!isDebugEnabled(context)) {
                CrashLogger.event("[无线配对] 打开配对页被忽略（调试开关未开）");
                return;
            }
            
            
            CrashLogger.event("[无线配对] 调试打开无线调试配对页");
            try {
                android.content.Intent i = new android.content.Intent(context, AdbPairActivity.class);
                i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
            } catch (Throwable t) {
                CrashLogger.event("[无线配对] 打开配对页失败", t);
            }
            return;
        }
        if (ACTION_OPEN_APPLIST.equals(action)) {
            if (!isDebugEnabled(context)) {
                CrashLogger.event("[特权面板] 打开应用列表被忽略（调试开关未开）");
                return;
            }
            
            
            CrashLogger.event("[特权面板] 调试打开应用列表页");
            try {
                android.content.Intent i = new android.content.Intent(context, AppListActivity.class);
                i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
            } catch (Throwable t) {
                CrashLogger.event("[特权面板] 打开应用列表失败", t);
            }
            return;
        }
        if (!ACTION.equals(action)) return;

        if (!isDebugEnabled(context)) {
            CrashLogger.event("[特权面板] 自检广播被忽略（调试开关未开）");
            return;
        }
        CrashLogger.event("[特权面板] 收到自检广播，开始采集状态");
        new Thread(() -> {
            try {
                PrivStatus.Snapshot s = PrivStatus.collect();
                CrashLogger.event("[特权面板] " + s.summary());
                CrashLogger.event("[应用列表] " + AppListActivity.summarize(context));
                
                CrashLogger.event("[内核诊断] ping=" + safePing()
                        + " checkSelf=" + safeCheck()
                        + " rawExec(id)=" + PrivStatus.rawExec("id"));
                
                CrashLogger.event("[特权来源] " + PrivRouter.describe(context));
                CrashLogger.event("[Shizuku诊断] rawExec(id)=" + PrivStatus.rawExecShizuku("id")
                        + " | 拒绝投递=" + YlShizukuProvider.rejectedInfo());
                android.app.Application app = YouLongApp.instance();
                if (app != null) {
                    CrashLogger.dumpEventsToFile(app, "probe.txt", "=== 自研特权面板 自检 ===");
                }
            } catch (Throwable t) {
                CrashLogger.event("[特权面板] 自检失败: " + t);
            }
        }, "priv-selftest-rx").start();
    }

    
    static boolean isDebugEnabled(Context ctx) {
        try {
            return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getBoolean(KEY_SELFTEST, false);
        } catch (Throwable t) {
            return false;
        }
    }

    private static void setEnabled(Context ctx, boolean enabled) {
        try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_SELFTEST, enabled).apply();
            CrashLogger.event("[特权面板] 自检调试开关 = " + enabled);
        } catch (Throwable ignored) {
        }
    }
private static String safePing() {
        try { return String.valueOf(roro.stellar.Stellar.INSTANCE.pingBinder()); }
        catch (Throwable t) { return "EX:" + t; }
    }

    private static String safeCheck() {
        try { return String.valueOf(roro.stellar.Stellar.INSTANCE.checkSelfPermission("stellar")); }
        catch (Throwable t) { return "EX:" + t; }
    }
}